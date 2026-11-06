package dev.tinow.brainrotstopper.data.anki

import dev.tinow.brainrotstopper.domain.anki.AnkiCapabilities
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiParsing
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.CardSource
import dev.tinow.brainrotstopper.domain.anki.DeckCounts
import dev.tinow.brainrotstopper.domain.anki.DeckInfo
import dev.tinow.brainrotstopper.domain.anki.NextCard
import dev.tinow.brainrotstopper.domain.anki.RenderedCard
import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.quiz.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * [CardSource] over the AnkiDroid content provider (architecture.md 5).
 *
 * Every call is serialised by one mutex (the `schedule` query selects a deck as a side effect and is not reset after an
 * exception), has a timeout (longer for the first call, AnkiDroid may have to start) and maps every exception to an
 * [AnkiError]. A null cursor is never "nothing due". There are no polling loops here.
 */
class AnkiContentProviderSource(
    private val gateway: ProviderGateway,
    private val capabilities: AnkiCapabilities = AnkiCapabilities(),
    private val firstTimeoutMs: Long = Constants.ANKI_FIRST_TIMEOUT_MS,
    private val timeoutMs: Long = Constants.ANKI_TIMEOUT_MS,
) : CardSource {
    private val mutex = Mutex()
    private var warm = false

    /** note type id -> CSS (kept for the process lifetime; a failed read is not cached). */
    private val cssByModel = HashMap<Long, String>()

    private suspend fun <T> guarded(block: suspend () -> AnkiResult<T>): AnkiResult<T> = mutex.withLock {
        val limit = if (warm) timeoutMs else firstTimeoutMs
        try {
            withTimeout(limit) { block() }.also { warm = true }
        } catch (e: TimeoutCancellationException) {
            AnkiResult.Err(AnkiError.Unreachable)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AnkiResult.Err(AnkiErrorMapper.map(t))
        }
    }

    private fun <T> err(error: AnkiError): AnkiResult<T> = AnkiResult.Err(error)

    override suspend fun probe(): AnkiResult<Unit> = guarded {
        if (!gateway.providerAvailable()) return@guarded err(AnkiError.NotInstalledOrApiOff)
        // a cheap real query: the provider exists but may answer with a null cursor when the API is switched off
        if (gateway.query(AnkiContract.DECKS) == null) err(AnkiError.NotInstalledOrApiOff) else AnkiResult.Ok(Unit)
    }

    override suspend fun listDecks(): AnkiResult<List<DeckInfo>> = guarded { decks() }

    private suspend fun decks(): AnkiResult<List<DeckInfo>> {
        val rows = gateway.query(AnkiContract.DECKS) ?: return err(AnkiError.NotInstalledOrApiOff)
        val decks = rows.map { row ->
            val id = row[AnkiContract.DECK_ID]?.toLongOrNull() ?: return err(AnkiError.BadData)
            val name = row[AnkiContract.DECK_NAME] ?: return err(AnkiError.BadData)
            val counts = AnkiParsing.deckCounts(row[AnkiContract.DECK_COUNT]) ?: return err(AnkiError.BadData)
            DeckInfo(id, name, counts, dynamic = isDynamic(row[AnkiContract.DECK_DYN]))
        }
        return AnkiResult.Ok(decks)
    }

    /** The provider returns `deck_dyn` as the text `false`/`true` (measured against v2.25.3); `0`/`1` is accepted as well. */
    private fun isDynamic(raw: String?) = raw == "true" || raw == "1"

    override suspend fun deckCounts(selection: DeckSelection): AnkiResult<DeckCounts> = guarded { counts(selection) }

    private suspend fun counts(selection: DeckSelection): AnkiResult<DeckCounts> = when (selection) {
        is DeckSelection.Single -> {
            val rows = gateway.query(AnkiContract.deck(selection.deckId))
            when {
                rows == null -> err(AnkiError.NotInstalledOrApiOff)
                rows.isEmpty() -> err(AnkiError.DeckMissing)
                else -> AnkiParsing.deckCounts(rows.first()[AnkiContract.DECK_COUNT])?.let { AnkiResult.Ok(it) } ?: err(AnkiError.BadData)
            }
        }
        // Top-level counts already include their sub decks, so only top-level decks are summed (no double counting).
        DeckSelection.All -> when (val d = topLevelDecks()) {
            is AnkiResult.Err -> d
            is AnkiResult.Ok -> AnkiResult.Ok(
                DeckCounts(d.value.sumOf { it.counts.learn }, d.value.sumOf { it.counts.review }, d.value.sumOf { it.counts.new }),
            )
        }
    }

    private suspend fun topLevelDecks(): AnkiResult<List<DeckInfo>> = when (val d = decks()) {
        is AnkiResult.Err -> d
        is AnkiResult.Ok -> AnkiResult.Ok(d.value.filter { !it.dynamic && !it.name.contains("::") })
    }

    override suspend fun nextCard(selection: DeckSelection): AnkiResult<NextCard?> = guarded {
        when (selection) {
            is DeckSelection.Single -> schedule(selection.deckId)
            DeckSelection.All -> when (val d = topLevelDecks()) {
                is AnkiResult.Err -> d
                is AnkiResult.Ok -> {
                    // Decks with due cards, the biggest first; the first non-empty cursor wins. All empty: the queue matrix decides.
                    var result: AnkiResult<NextCard?> = AnkiResult.Ok(null)
                    for (deck in d.value.filter { it.counts.total > 0 }.sortedByDescending { it.counts.total }) {
                        val r = schedule(deck.id)
                        if (r is AnkiResult.Err || (r as AnkiResult.Ok).value != null) {
                            result = r
                            break
                        }
                    }
                    result
                }
            }
        }
    }

    private suspend fun schedule(deckId: Long): AnkiResult<NextCard?> {
        val rows = gateway.query(AnkiContract.SCHEDULE, null, AnkiContract.SCHEDULE_SELECTION, listOf("1", deckId.toString()))
            ?: return err(AnkiError.NotInstalledOrApiOff) // null cursor: never "nothing due"
        val row = rows.firstOrNull() ?: return AnkiResult.Ok(null)
        val noteId = row[AnkiContract.NOTE_ID]?.toLongOrNull() ?: return err(AnkiError.BadData)
        val ord = row[AnkiContract.ORD]?.toIntOrNull() ?: return err(AnkiError.BadData)
        val buttons = row[AnkiContract.BUTTON_COUNT]?.toIntOrNull() ?: 4
        val labels = if (capabilities.intervalLabelsAvailable) AnkiParsing.intervalLabels(row[AnkiContract.NEXT_REVIEW_TIMES]) else null
        return AnkiResult.Ok(NextCard(CardRef(noteId, ord), buttons, labels, AnkiParsing.mediaFiles(row[AnkiContract.MEDIA_FILES])))
    }

    override suspend fun render(ref: CardRef): AnkiResult<RenderedCard> = guarded {
        val rows = gateway.query(
            AnkiContract.card(ref.noteId, ref.ord),
            listOf(AnkiContract.QUESTION, AnkiContract.ANSWER, AnkiContract.ANSWER_PURE),
        ) ?: return@guarded err(AnkiError.NotInstalledOrApiOff)
        val row = rows.firstOrNull() ?: return@guarded err(AnkiError.BadData) // the card vanished between schedule and render
        val question = row[AnkiContract.QUESTION]
        val answer = row[AnkiContract.ANSWER]
        val pure = row[AnkiContract.ANSWER_PURE]
        if (question == null && answer == null) return@guarded err(AnkiError.BadData)
        AnkiResult.Ok(RenderedCard(ref, question.orEmpty(), answer.orEmpty(), pure.orEmpty(), modelCss(ref.noteId)))
    }

    /** Best effort: no CSS is a plainer card, never an error and never a reason to keep the lock closed. */
    private suspend fun modelCss(noteId: Long): String? {
        if (!capabilities.modelCssAvailable) return null
        return try {
            val mid = gateway.query(AnkiContract.note(noteId), listOf(AnkiContract.MID))?.firstOrNull()?.get(AnkiContract.MID)?.toLongOrNull()
                ?: return null
            cssByModel[mid]
                ?: gateway.query(AnkiContract.model(mid), listOf(AnkiContract.CSS))?.firstOrNull()?.get(AnkiContract.CSS)
                    ?.also { cssByModel[mid] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun cardProgress(ref: CardRef): AnkiResult<Int> = guarded {
        val rows = gateway.query(AnkiContract.card(ref.noteId, ref.ord), listOf(AnkiContract.REPS))
            ?: return@guarded err(AnkiError.NotInstalledOrApiOff)
        rows.firstOrNull()?.get(AnkiContract.REPS)?.toIntOrNull()?.let { AnkiResult.Ok(it) } ?: err(AnkiError.BadData)
    }

    override suspend fun answer(ref: CardRef, ease: Ease, takenMs: Long): AnkiResult<Unit> = guarded {
        val updated = gateway.update(
            AnkiContract.SCHEDULE,
            mapOf(
                AnkiContract.NOTE_ID to ref.noteId,
                AnkiContract.ORD to ref.ord,
                AnkiContract.ANSWER_EASE to ease.value,
                AnkiContract.TIME_TAKEN to takenMs,
            ),
        )
        // The row count alone does not prove that the answer was applied; the engine verifies through `cardProgress`.
        if (updated >= 1) AnkiResult.Ok(Unit) else err(AnkiError.AnswerNotSaved)
    }
}
