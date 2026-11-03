package dev.tinow.brainrotstopper.domain.quiz

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.DeckCounts
import dev.tinow.brainrotstopper.domain.model.CardRef
import org.junit.Test

/** One test per row of the queue matrix (architecture.md 3B). */
class QueuePolicyTest {
    private val card = CardRef(1, 0)
    private fun ok(l: Int, r: Int, n: Int): () -> AnkiResult<DeckCounts> = { AnkiResult.Ok(DeckCounts(l, r, n)) }
    private fun decide(
        cursor: AnkiResult<CardRef?>, counts: () -> AnkiResult<DeckCounts>, lastWrong: Long? = null, now: Long = 1_000_000, includes: Boolean = true,
    ) = QueuePolicy.decide(cursor, counts, lastWrong, now, includes)

    @Test fun row1CursorNotEmptyIsCardAndCountsAreNotEvenRead() {
        val d = decide(AnkiResult.Ok(card), { error("counts must not be read") })
        assertThat(d).isEqualTo(QueueDecision.Card(card))
    }

    @Test fun row2NullCursorIsNotInstalledNeverAllDone() {
        val d = decide(AnkiResult.Err(AnkiError.NotInstalledOrApiOff), ok(0, 0, 0))
        assertThat(d).isEqualTo(QueueDecision.Error(AnkiError.NotInstalledOrApiOff))
    }

    @Test fun row3CursorErrorStaysError() {
        assertThat(decide(AnkiResult.Err(AnkiError.Unreachable), ok(0, 0, 0))).isEqualTo(QueueDecision.Error(AnkiError.Unreachable))
    }

    @Test fun row3EmptyCursorAndUnreadableCountsIsErrorNeverAllDone() {
        val d = decide(AnkiResult.Ok(null), { AnkiResult.Err(AnkiError.BadData) })
        assertThat(d).isEqualTo(QueueDecision.Error(AnkiError.BadData))
    }

    @Test fun row4NothingDueIsAllDoneIndependentOfProgress() {
        assertThat(decide(AnkiResult.Ok(null), ok(0, 0, 0))).isEqualTo(QueueDecision.AllDone)
    }

    @Test fun row5LearnPendingWaits() {
        assertThat(decide(AnkiResult.Ok(null), ok(2, 0, 0))).isEqualTo(QueueDecision.WaitingForLearning)
        assertThat(decide(AnkiResult.Ok(null), ok(1, 5, 5))).isEqualTo(QueueDecision.WaitingForLearning)
    }

    @Test fun row6ReviewOrNewWithoutCursorIsBadData() {
        assertThat(decide(AnkiResult.Ok(null), ok(0, 3, 0))).isEqualTo(QueueDecision.Error(AnkiError.BadData))
        assertThat(decide(AnkiResult.Ok(null), ok(0, 0, 4))).isEqualTo(QueueDecision.Error(AnkiError.BadData))
    }

    // Row 4a only applies when the learn count does not include cards waiting in the intraday queue.
    @Test fun row4aRecentWrongAnswerWaitsWhenLearnCountExcludesPending() {
        val d = decide(AnkiResult.Ok(null), ok(0, 0, 0), lastWrong = 1_000_000 - 60_000, includes = false)
        assertThat(d).isEqualTo(QueueDecision.WaitingForLearning)
    }

    @Test fun row4aOldWrongAnswerIsAllDone() {
        val d = decide(AnkiResult.Ok(null), ok(0, 0, 0), lastWrong = 1_000_000 - Constants.LEARN_PENDING_WAIT_MS, includes = false)
        assertThat(d).isEqualTo(QueueDecision.AllDone)
    }

    @Test fun row4aWithoutWrongAnswerIsAllDone() {
        assertThat(decide(AnkiResult.Ok(null), ok(0, 0, 0), lastWrong = null, includes = false)).isEqualTo(QueueDecision.AllDone)
    }

    @Test fun row4aIsIgnoredWhenCountIncludesPending() {
        val d = decide(AnkiResult.Ok(null), ok(0, 0, 0), lastWrong = 1_000_000 - 60_000, includes = true)
        assertThat(d).isEqualTo(QueueDecision.AllDone)
    }

    @Test fun defaultFollowsMeasuredConstant() {
        assertThat(Constants.LEARN_COUNT_INCLUDES_PENDING).isTrue()
        val d = QueuePolicy.decide(AnkiResult.Ok(null), ok(0, 0, 0), lastWrongElapsed = 999_000, nowElapsed = 1_000_000)
        assertThat(d).isEqualTo(QueueDecision.AllDone)
    }
}
