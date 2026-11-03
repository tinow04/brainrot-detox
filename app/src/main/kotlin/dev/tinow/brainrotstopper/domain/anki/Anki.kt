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

/** What the device can do, measured by the M0 spikes; injected, never global. */
data class AnkiCapabilities(val answerVerify: Boolean = true)

data class DeckInfo(val id: Long, val name: String, val counts: DeckCounts, val dynamic: Boolean)

/**
 * Domain view of AnkiDroid. Implementations map every RuntimeException to [AnkiError], serialise access and apply
 * timeouts; a null cursor is [AnkiError.NotInstalledOrApiOff], never "nothing due".
 */
interface CardSource {
    suspend fun probe(): AnkiResult<Unit>
    suspend fun listDecks(): AnkiResult<List<DeckInfo>>
    suspend fun deckCounts(selection: DeckSelection): AnkiResult<DeckCounts>

    /** `Ok(null)` = the scheduler cursor is empty (the queue matrix then decides what that means). */
    suspend fun nextCard(selection: DeckSelection): AnkiResult<CardRef?>

    /** Review count of the card, only when [AnkiCapabilities.answerVerify]. */
    suspend fun cardProgress(ref: CardRef): AnkiResult<Int>
    suspend fun answer(ref: CardRef, ease: Ease, takenMs: Long): AnkiResult<Unit>
}
