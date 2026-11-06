package dev.tinow.brainrotstopper.domain.anki

import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Ease

/** Error model of the AnkiDroid integration (architecture.md 5). Every error keeps the lock closed. */
sealed interface AnkiError {
    data object NotInstalledOrApiOff : AnkiError
    data object PermissionMissing : AnkiError
    data object StorageNotConfigured : AnkiError
    data object Unreachable : AnkiError
    data object DeckMissing : AnkiError
    data object BadData : AnkiError
    data object AnswerNotSaved : AnkiError
    data class Unexpected(val msg: String) : AnkiError
}

sealed interface AnkiResult<out T> {
    data class Ok<T>(val value: T) : AnkiResult<T>
    data class Err(val error: AnkiError) : AnkiResult<Nothing>
}

/** `[learn, review, new]` of a deck including sub decks. */
data class DeckCounts(val learn: Int, val review: Int, val new: Int) {
    val total: Int get() = learn + review + new
}

/** What the device can do, measured by the M0 spikes (architecture.md 11); injected, never global. */
data class AnkiCapabilities(
    /** `reps` is readable per projection and rises after an answer (M0-S1a). */
    val answerVerify: Boolean = true,
    /** The `models` query returns the note type CSS. */
    val modelCssAvailable: Boolean = true,
    /** `next_review_times` is a JSON array of four strings. */
    val intervalLabelsAvailable: Boolean = true,
)

data class DeckInfo(val id: Long, val name: String, val counts: DeckCounts, val dynamic: Boolean)

/**
 * What the scheduler returned for the next card. [intervalLabels] are the four button labels (again, hard, good, easy) or
 * null when the provider did not deliver exactly four parsable strings; [mediaFiles] are file names only (a hint).
 */
data class NextCard(
    val ref: CardRef,
    val buttonCount: Int,
    val intervalLabels: List<String>?,
    val mediaFiles: List<String>,
)

/** Raw card sides as AnkiDroid renders them; HTML building and sanitising is `CardHtml` (M4-T3). */
data class RenderedCard(
    val ref: CardRef,
    val question: String,
    /** Contains the question, a `<hr id=answer>` and the back side (cloze cards have no `<hr id=answer>`). */
    val answer: String,
    /** Only the back side. */
    val answerPure: String,
    /** CSS of the note type, null when it could not be read (never an error). */
    val css: String?,
)

/**
 * Domain view of AnkiDroid. Implementations map every RuntimeException to [AnkiError], serialise access and apply
 * timeouts; a null cursor is [AnkiError.NotInstalledOrApiOff], never "nothing due".
 */
interface CardSource {
    suspend fun probe(): AnkiResult<Unit>
    suspend fun listDecks(): AnkiResult<List<DeckInfo>>
    suspend fun deckCounts(selection: DeckSelection): AnkiResult<DeckCounts>

    /** `Ok(null)` = the scheduler cursor is empty (the queue matrix then decides what that means). */
    suspend fun nextCard(selection: DeckSelection): AnkiResult<NextCard?>

    /** The raw question and answer of [ref]. */
    suspend fun render(ref: CardRef): AnkiResult<RenderedCard>

    /** Review count of the card, only when [AnkiCapabilities.answerVerify]. */
    suspend fun cardProgress(ref: CardRef): AnkiResult<Int>
    suspend fun answer(ref: CardRef, ease: Ease, takenMs: Long): AnkiResult<Unit>
}
