package dev.tinow.brainrotstopper.domain.quiz

import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.DeckCounts
import dev.tinow.brainrotstopper.domain.model.CardRef

sealed interface QueueDecision {
    data class Card(val ref: CardRef) : QueueDecision
    data object AllDone : QueueDecision
    data object WaitingForLearning : QueueDecision
    data class Error(val error: AnkiError) : QueueDecision
}

/** The queue matrix of architecture.md 3B (rows 1-6 plus 4a). The decision is never taken from the cursor alone. */
object QueuePolicy {
    fun decide(
        cursor: AnkiResult<CardRef?>,
        counts: () -> AnkiResult<DeckCounts>,
        lastWrongElapsed: Long?,
        nowElapsed: Long,
        learnCountIncludesPending: Boolean = Constants.LEARN_COUNT_INCLUDES_PENDING,
    ): QueueDecision {
        when (cursor) {
            is AnkiResult.Err -> return QueueDecision.Error(cursor.error) // rows 2 and 3 (cursor side)
            is AnkiResult.Ok -> cursor.value?.let { return QueueDecision.Card(it) } // row 1
        }
        val c = when (val r = counts()) {
            is AnkiResult.Err -> return QueueDecision.Error(r.error) // row 3: counts unreadable
            is AnkiResult.Ok -> r.value
        }
        return when {
            c.total == 0 -> {
                // Row 4a: a card answered wrong a moment ago can sit in the intraday learning queue without being counted yet.
                val pending = !learnCountIncludesPending && lastWrongElapsed != null &&
                    nowElapsed - lastWrongElapsed < Constants.LEARN_PENDING_WAIT_MS
                if (pending) QueueDecision.WaitingForLearning else QueueDecision.AllDone // row 4
            }
            c.learn > 0 -> QueueDecision.WaitingForLearning // row 5
            else -> QueueDecision.Error(AnkiError.BadData) // row 6: counts do not match the queue, fail-closed
        }
    }
}
