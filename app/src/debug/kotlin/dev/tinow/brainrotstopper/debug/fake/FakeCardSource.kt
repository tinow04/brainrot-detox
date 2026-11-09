package dev.tinow.brainrotstopper.debug.fake

import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.CardSource
import dev.tinow.brainrotstopper.domain.anki.DeckCounts
import dev.tinow.brainrotstopper.domain.anki.DeckInfo
import dev.tinow.brainrotstopper.domain.anki.NextCard
import dev.tinow.brainrotstopper.domain.anki.RenderedCard
import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Ease

/**
 * Scripted [CardSource] with a tiny scheduler: Again and Hard put the card back at the end of the queue, Good and Easy
 * remove it. Every answer that is applied raises the card's review count (what `answerVerify` reads).
 * Used by the engine tests (JVM) and, through `SET_SOURCE --es source fake --es scenario <name>`, by the emulator checks.
 */
class FakeCardSource(cards: List<RenderedCard> = emptyList()) : CardSource {
    private val queue = ArrayDeque(cards)
    private val reps = HashMap<CardRef, Int>()

    /** Number of calls per method name (`probe`, `listDecks`, `deckCounts`, `nextCard`, `render`, `cardProgress`, `answer`). */
    val calls = LinkedHashMap<String, Int>()

    /** Every answer that reached the provider, in order (applied or not); a kill before the call leaves no entry. */
    val answers = mutableListOf<Pair<CardRef, Ease>>()

    /** Cards still waiting in the learning queue although the cursor is empty (queue matrix row 5). */
    var learning = 0

    /** A non-null value makes that method fail every time (until cleared). */
    val failing = HashMap<String, AnkiError>()

    /** When false, [answer] reports success but the card's review count stays (AnkiDroid ignored it). */
    var answerApplies = true

    /** When true, [answer] applies the rating and then reports an error (the worst case for double counting). */
    var answerAppliesButFails: AnkiError? = null

    /** Interval labels delivered with the cards, or null for none. */
    var intervals: List<String>? = listOf("<1 min", "<6 min", "1 d", "4 d")

    /** Runs before / after the provider call of [answer]; tests use them to kill the engine at an exact step. */
    var beforeAnswer: suspend (CardRef) -> Unit = {}
    var afterAnswer: suspend (CardRef) -> Unit = {}
    var onCall: suspend (String) -> Unit = {}

    val pending: Int get() = queue.size

    fun repsOf(ref: CardRef) = reps[ref] ?: 0

    private suspend fun <T> call(name: String, block: () -> AnkiResult<T>): AnkiResult<T> {
        calls[name] = (calls[name] ?: 0) + 1
        onCall(name)
        failing[name]?.let { return AnkiResult.Err(it) }
        return block()
    }

    val totalCalls: Int get() = calls.values.sum()

    override suspend fun probe(): AnkiResult<Unit> = call("probe") { AnkiResult.Ok(Unit) }

    override suspend fun listDecks(): AnkiResult<List<DeckInfo>> =
        call("listDecks") { AnkiResult.Ok(listOf(DeckInfo(1, "Test", counts(), dynamic = false))) }

    private fun counts() = DeckCounts(learn = learning, review = 0, new = queue.size)

    override suspend fun deckCounts(selection: DeckSelection): AnkiResult<DeckCounts> = call("deckCounts") { AnkiResult.Ok(counts()) }

    override suspend fun nextCard(selection: DeckSelection): AnkiResult<NextCard?> = call("nextCard") {
        val head = queue.firstOrNull()
        AnkiResult.Ok(head?.let { NextCard(it.ref, 4, intervals, emptyList()) })
    }

    override suspend fun render(ref: CardRef): AnkiResult<RenderedCard> = call("render") {
        queue.firstOrNull { it.ref == ref }?.let { AnkiResult.Ok(it) } ?: AnkiResult.Err(AnkiError.BadData)
    }

    override suspend fun cardProgress(ref: CardRef): AnkiResult<Int> = call("cardProgress") { AnkiResult.Ok(repsOf(ref)) }

    override suspend fun answer(ref: CardRef, ease: Ease, takenMs: Long): AnkiResult<Unit> {
        calls["answer"] = (calls["answer"] ?: 0) + 1
        onCall("answer")
        failing["answer"]?.let { return AnkiResult.Err(it) }
        beforeAnswer(ref)
        answers += ref to ease
        if (answerApplies) apply(ref, ease)
        afterAnswer(ref)
        answerAppliesButFails?.let { return AnkiResult.Err(it) }
        return AnkiResult.Ok(Unit)
    }

    private fun apply(ref: CardRef, ease: Ease) {
        reps[ref] = repsOf(ref) + 1
        val card = queue.firstOrNull { it.ref == ref } ?: return
        queue.remove(card)
        if (!ease.counts) queue.addLast(card)
    }

    companion object {
        /** [n] distinct basic cards (note ids 100 up) built from the fixtures. */
        fun cards(n: Int): List<RenderedCard> {
            val pool = listOf("html", "cloze", "type", "image", "audio", "colors")
            return (0 until n).map { i ->
                val base = CardFixtures.all.getValue(pool[i % pool.size])
                val q = "Karte ${i + 1}: " + base.question
                base.copy(ref = CardRef(100L + i, 0), question = q, answer = q + base.answer.removePrefix(base.question), answerPure = base.answerPure)
            }
        }

        /** Scenario names for `SET_SOURCE --es scenario`; unknown names behave like `normal`. */
        fun scenario(name: String): FakeCardSource = when (name) {
            "empty" -> FakeCardSource()
            "waiting" -> FakeCardSource().also { it.learning = 1 }
            "error-permission" -> failing(AnkiError.PermissionMissing)
            "error-notinstalled" -> failing(AnkiError.NotInstalledOrApiOff)
            "error-storage" -> failing(AnkiError.StorageNotConfigured)
            "error-unreachable" -> failing(AnkiError.Unreachable)
            "error-deck" -> failing(AnkiError.DeckMissing)
            "error-bad" -> failing(AnkiError.BadData)
            "answer-lost" -> FakeCardSource(cards(30)).also { it.answerApplies = false }
            "no-intervals" -> FakeCardSource(cards(30)).also { it.intervals = null }
            else -> FakeCardSource(cards(30))
        }

        private fun failing(error: AnkiError) = FakeCardSource(cards(30)).also { it.failing["nextCard"] = error }
    }
}
