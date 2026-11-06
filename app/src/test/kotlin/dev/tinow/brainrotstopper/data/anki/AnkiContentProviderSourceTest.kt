package dev.tinow.brainrotstopper.data.anki

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.debug.fake.FakeProviderGateway
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.DeckCounts
import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.quiz.QueueDecision
import dev.tinow.brainrotstopper.domain.quiz.QueuePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnkiContentProviderSourceTest {
    private val gateway = FakeProviderGateway()
    private val source = AnkiContentProviderSource(gateway, firstTimeoutMs = 8_000, timeoutMs = 4_000)

    private fun deckRow(id: Long, name: String, l: Int, r: Int, n: Int, dyn: Int = 0): Row =
        mapOf("deck_id" to "$id", "deck_name" to name, "deck_count" to "[$l,$r,$n]", "deck_dyn" to "$dyn")

    private fun scheduleRow(note: Long, ord: Int = 0, times: String? = null, media: String = "[]"): Row =
        mapOf("note_id" to "$note", "ord" to "$ord", "button_count" to "4", "next_review_times" to times, "media_files" to media)

    private fun decks(vararg rows: Row) {
        val previous = gateway.queryHandler
        gateway.queryHandler = { if (it.path == "decks") rows.toList() else previous(it) }
    }

    private fun err(r: AnkiResult<*>): AnkiError = (r as AnkiResult.Err).error

    private fun <T> ok(r: AnkiResult<T>): T = (r as AnkiResult.Ok).value

    // --- error mapping through the source -----------------------------------------------------------------------

    @Test fun `provider not installed`() = runTest {
        gateway.available = false
        assertThat(err(source.probe())).isEqualTo(AnkiError.NotInstalledOrApiOff)
    }

    @Test fun `null cursor on probe means not installed or api off`() = runTest {
        gateway.queryHandler = { null }
        assertThat(err(source.probe())).isEqualTo(AnkiError.NotInstalledOrApiOff)
    }

    @Test fun `probe succeeds with a cursor, even an empty one`() = runTest {
        gateway.queryHandler = { emptyList() }
        assertThat(source.probe()).isEqualTo(AnkiResult.Ok(Unit))
    }

    @Test fun `exceptions of the platform map to errors`() = runTest {
        val cases = listOf(
            SecurityException("no") to AnkiError.PermissionMissing,
            IllegalStateException("AnkiDroid storage is not configured") to AnkiError.StorageNotConfigured,
            IllegalArgumentException("Unknown URI") to AnkiError.Unreachable,
            ProviderDeadException() to AnkiError.Unreachable,
        )
        for ((exception, expected) in cases) {
            gateway.queryHandler = { throw exception }
            assertThat(err(source.listDecks())).isEqualTo(expected)
        }
    }

    @Test fun `a slow first call times out as unreachable`() = runTest {
        gateway.delayMs = 9_000
        gateway.queryHandler = { emptyList() }
        val result = async { source.probe() }
        advanceTimeBy(8_100)
        runCurrent()
        assertThat(err(result.await())).isEqualTo(AnkiError.Unreachable)
    }

    @Test fun `the first call gets the long timeout, later calls the short one`() = runTest {
        gateway.delayMs = 5_000
        gateway.queryHandler = { emptyList() }
        val first = async { source.probe() }
        advanceTimeBy(5_100); runCurrent()
        assertThat(first.await()).isEqualTo(AnkiResult.Ok(Unit)) // 5 s < 8 s
        val second = async { source.probe() }
        advanceTimeBy(4_100); runCurrent()
        assertThat(err(second.await())).isEqualTo(AnkiError.Unreachable) // 5 s > 4 s
    }

    @Test fun `calls are serialised`() = runTest {
        gateway.delayMs = 100
        gateway.queryHandler = { emptyList() }
        val a = async { source.listDecks() }
        val b = async { source.listDecks() }
        val c = async { source.probe() }
        advanceTimeBy(1_000); runCurrent()
        a.await(); b.await(); c.await()
        assertThat(gateway.maxInFlight).isEqualTo(1)
    }

    @Test fun `cancellation is not turned into an error`() = runTest {
        gateway.delayMs = 3_000
        gateway.queryHandler = { emptyList() }
        val job = async { source.probe() }
        runCurrent()
        job.cancel()
        var thrown: Throwable? = null
        try {
            job.await()
        } catch (e: CancellationException) {
            thrown = e
        }
        assertThat(thrown).isNotNull() // not mapped to Err(Unreachable) or similar
    }

    // --- decks and counts ----------------------------------------------------------------------------------------

    @Test fun `list decks parses id, name, counts and the dynamic flag`() = runTest {
        decks(deckRow(1, "Default", 0, 0, 0), deckRow(7, "Medizin::Biologie", 1, 2, 3), deckRow(9, "Filter", 0, 1, 0, dyn = 1))
        val list = ok(source.listDecks())
        assertThat(list.map { it.id }).containsExactly(1L, 7L, 9L).inOrder()
        assertThat(list[1].counts).isEqualTo(DeckCounts(1, 2, 3))
        assertThat(list.map { it.dynamic }).containsExactly(false, false, true).inOrder()
    }

    @Test fun `the dynamic flag is read from true and false as the real provider sends it`() = runTest {
        // regression found by the contract test: AnkiDroid 2.25.3 sends the text "false"/"true", not 0/1
        decks(
            mapOf("deck_id" to "1", "deck_name" to "A", "deck_count" to "[0,0,1]", "deck_dyn" to "false"),
            mapOf("deck_id" to "2", "deck_name" to "B", "deck_count" to "[0,0,1]", "deck_dyn" to "true"),
            mapOf("deck_id" to "3", "deck_name" to "C", "deck_count" to "[0,0,1]", "deck_dyn" to null),
        )
        assertThat(ok(source.listDecks()).map { it.dynamic }).containsExactly(false, true, false).inOrder()
        assertThat(ok(source.deckCounts(DeckSelection.All))).isEqualTo(DeckCounts(0, 0, 2))
    }

    @Test fun `a deck row with damaged counts is bad data`() = runTest {
        decks(mapOf("deck_id" to "1", "deck_name" to "x", "deck_count" to "oops", "deck_dyn" to "0"))
        assertThat(err(source.listDecks())).isEqualTo(AnkiError.BadData)
    }

    @Test fun `single deck counts, missing deck, damaged counts`() = runTest {
        gateway.queryHandler = { if (it.path == "decks/7") listOf(deckRow(7, "A", 1, 2, 3)) else emptyList() }
        assertThat(ok(source.deckCounts(DeckSelection.Single(7, "A")))).isEqualTo(DeckCounts(1, 2, 3))
        assertThat(err(source.deckCounts(DeckSelection.Single(8, "gone")))).isEqualTo(AnkiError.DeckMissing)
        gateway.queryHandler = { listOf(mapOf("deck_count" to "[1]")) }
        assertThat(err(source.deckCounts(DeckSelection.Single(7, "A")))).isEqualTo(AnkiError.BadData)
    }

    @Test fun `all decks sums only top level decks, no double counting of sub decks and no dynamic decks`() = runTest {
        decks(
            deckRow(1, "Default", 0, 0, 0),
            deckRow(2, "Medizin", 1, 2, 3), // already includes Medizin::Biologie
            deckRow(3, "Medizin::Biologie", 1, 1, 1),
            deckRow(4, "Sprachen", 0, 4, 0),
            deckRow(5, "Filter", 9, 9, 9, dyn = 1),
        )
        assertThat(ok(source.deckCounts(DeckSelection.All))).isEqualTo(DeckCounts(1, 6, 3))
    }

    // --- next card and the queue matrix --------------------------------------------------------------------------

    private val isolateTimes = "[\"⁨<1⁩m\",\"⁨ 6⁩m\",\"⁨ 10⁩m\",\"⁨ 4⁩d\"]"

    @Test fun `single deck next card with labels and media`() = runTest {
        gateway.queryHandler = { listOf(scheduleRow(55, 1, isolateTimes, "[\"a b.png\"]")) }
        val card = ok(source.nextCard(DeckSelection.Single(7, "A")))!!
        assertThat(card.ref).isEqualTo(CardRef(55, 1))
        assertThat(card.intervalLabels).containsExactly("<1m", "6m", "10m", "4d").inOrder()
        assertThat(card.mediaFiles).containsExactly("a b.png")
        val call = gateway.calls.single()
        assertThat(call.path).isEqualTo("schedule")
        assertThat(call.selection).isEqualTo("limit=?,deckID=?")
        assertThat(call.args).containsExactly("1", "7").inOrder()
    }

    @Test fun `unparsable interval times only remove the labels`() = runTest {
        gateway.queryHandler = { listOf(scheduleRow(55, 0, "garbage")) }
        val card = ok(source.nextCard(DeckSelection.Single(7, "A")))!!
        assertThat(card.intervalLabels).isNull()
    }

    @Test fun `schedule row without a note id is bad data`() = runTest {
        gateway.queryHandler = { listOf(mapOf("ord" to "0")) }
        assertThat(err(source.nextCard(DeckSelection.Single(7, "A")))).isEqualTo(AnkiError.BadData)
    }

    @Test fun `all decks ask the decks with due cards, the biggest first, and the first non-empty cursor wins`() = runTest {
        decks(deckRow(1, "Default", 0, 0, 0), deckRow(2, "A", 0, 0, 3), deckRow(3, "B", 0, 4, 3), deckRow(4, "C", 0, 0, 1))
        val previous = gateway.queryHandler
        gateway.queryHandler = { call ->
            when {
                call.path == "schedule" && call.args[1] == "3" -> emptyList()
                call.path == "schedule" && call.args[1] == "2" -> listOf(scheduleRow(21))
                else -> previous(call)
            }
        }
        val card = ok(source.nextCard(DeckSelection.All))!!
        assertThat(card.ref.noteId).isEqualTo(21)
        assertThat(gateway.calls.filter { it.path == "schedule" }.map { it.args[1] }).containsExactly("3", "2").inOrder() // never the empty Default or C
    }

    @Test fun `all decks with only empty cursors gives an empty result for the matrix`() = runTest {
        decks(deckRow(2, "A", 0, 0, 3))
        val previous = gateway.queryHandler
        gateway.queryHandler = { if (it.path == "schedule") emptyList() else previous(it) }
        assertThat(ok(source.nextCard(DeckSelection.All))).isNull()
    }

    private suspend fun decide(selection: DeckSelection): QueueDecision {
        val cursor = source.nextCard(selection).let { r ->
            when (r) {
                is AnkiResult.Ok -> AnkiResult.Ok(r.value?.ref)
                is AnkiResult.Err -> r
            }
        }
        val counts = source.deckCounts(selection)
        return QueuePolicy.decide(cursor, { counts }, lastWrongElapsed = null, nowElapsed = 0)
    }

    private val single = DeckSelection.Single(7, "A")

    private fun scripted(cursor: List<Row>?, countsRow: List<Row>) {
        gateway.queryHandler = { if (it.path == "schedule") cursor else countsRow }
    }

    @Test fun `matrix 1 - a card wins`() = runTest {
        scripted(listOf(scheduleRow(1)), listOf(deckRow(7, "A", 0, 0, 0)))
        assertThat(decide(single)).isEqualTo(QueueDecision.Card(CardRef(1, 0)))
    }

    @Test fun `matrix 2 - a null cursor is never nothing due`() = runTest {
        scripted(null, listOf(deckRow(7, "A", 0, 0, 0)))
        assertThat(decide(single)).isEqualTo(QueueDecision.Error(AnkiError.NotInstalledOrApiOff))
    }

    @Test fun `matrix 3 - unreadable counts are never nothing due`() = runTest {
        scripted(emptyList(), listOf(mapOf("deck_count" to "oops")))
        assertThat(decide(single)).isEqualTo(QueueDecision.Error(AnkiError.BadData))
        gateway.queryHandler = { if (it.path == "schedule") emptyList() else throw IllegalStateException("AnkiDroid storage is not configured") }
        assertThat(decide(single)).isEqualTo(QueueDecision.Error(AnkiError.StorageNotConfigured))
    }

    @Test fun `matrix 4 - empty cursor and no counts is all done`() = runTest {
        scripted(emptyList(), listOf(deckRow(7, "A", 0, 0, 0)))
        assertThat(decide(single)).isEqualTo(QueueDecision.AllDone)
    }

    @Test fun `matrix 5 - empty cursor with learning cards waits`() = runTest {
        scripted(emptyList(), listOf(deckRow(7, "A", 2, 0, 0)))
        assertThat(decide(single)).isEqualTo(QueueDecision.WaitingForLearning)
    }

    @Test fun `matrix 6 - empty cursor, due counts but no learning cards is bad data`() = runTest {
        scripted(emptyList(), listOf(deckRow(7, "A", 0, 3, 1)))
        assertThat(decide(single)).isEqualTo(QueueDecision.Error(AnkiError.BadData))
    }

    @Test fun `all decks with learning cards still due wait instead of finishing`() = runTest {
        decks(deckRow(2, "A", 1, 0, 0))
        val previous = gateway.queryHandler
        gateway.queryHandler = { if (it.path == "schedule") emptyList() else previous(it) }
        assertThat(decide(DeckSelection.All)).isEqualTo(QueueDecision.WaitingForLearning)
    }

    // --- render, progress, answer --------------------------------------------------------------------------------

    private fun cardRows(path: String): List<Row>? = when {
        path.startsWith("notes/5/cards/") -> listOf(mapOf("question" to "Q", "answer" to "Q<hr id=answer>A", "answer_pure" to "A"))
        path == "notes/5" -> listOf(mapOf("mid" to "99"))
        path == "models/99" -> listOf(mapOf("css" to ".card { color: red }"))
        else -> emptyList()
    }

    @Test fun `render returns the sides and the note type css, which is cached per note type`() = runTest {
        gateway.queryHandler = { cardRows(it.path) }
        val card = ok(source.render(CardRef(5, 0)))
        assertThat(card.question).isEqualTo("Q")
        assertThat(card.answer).isEqualTo("Q<hr id=answer>A")
        assertThat(card.answerPure).isEqualTo("A")
        assertThat(card.css).isEqualTo(".card { color: red }")
        source.render(CardRef(5, 0))
        assertThat(gateway.calls.count { it.path == "models/99" }).isEqualTo(1)
    }

    @Test fun `a failing css lookup never fails the card`() = runTest {
        gateway.queryHandler = { if (it.path.startsWith("notes/5/cards")) cardRows(it.path) else throw SecurityException("no") }
        val card = ok(source.render(CardRef(5, 0)))
        assertThat(card.css).isNull()
        assertThat(card.question).isEqualTo("Q")
    }

    @Test fun `render errors`() = runTest {
        gateway.queryHandler = { null }
        assertThat(err(source.render(CardRef(5, 0)))).isEqualTo(AnkiError.NotInstalledOrApiOff)
        gateway.queryHandler = { emptyList() }
        assertThat(err(source.render(CardRef(5, 0)))).isEqualTo(AnkiError.BadData)
        gateway.queryHandler = { listOf(mapOf("question" to null, "answer" to null)) }
        assertThat(err(source.render(CardRef(5, 0)))).isEqualTo(AnkiError.BadData)
    }

    @Test fun `card progress reads reps by projection`() = runTest {
        gateway.queryHandler = { listOf(mapOf("reps" to "3")) }
        assertThat(ok(source.cardProgress(CardRef(5, 0)))).isEqualTo(3)
        assertThat(gateway.calls.single().projection).containsExactly("reps")
        gateway.queryHandler = { listOf(mapOf("reps" to "x")) }
        assertThat(err(source.cardProgress(CardRef(5, 0)))).isEqualTo(AnkiError.BadData)
    }

    @Test fun `answer sends note, ord, ease and time`() = runTest {
        assertThat(source.answer(CardRef(5, 1), Ease.GOOD, 2500)).isEqualTo(AnkiResult.Ok(Unit))
        val (path, values) = gateway.updates.single()
        assertThat(path).isEqualTo("schedule")
        assertThat(values).containsExactly("note_id", 5L, "ord", 1, "answer_ease", 3, "time_taken", 2500L)
    }

    @Test fun `answer that updated nothing is not saved, exceptions are mapped`() = runTest {
        gateway.updateHandler = { _, _ -> 0 }
        assertThat(err(source.answer(CardRef(5, 0), Ease.AGAIN, 1000))).isEqualTo(AnkiError.AnswerNotSaved)
        gateway.updateHandler = { _, _ -> throw SecurityException("no") }
        assertThat(err(source.answer(CardRef(5, 0), Ease.AGAIN, 1000))).isEqualTo(AnkiError.PermissionMissing)
    }
}
