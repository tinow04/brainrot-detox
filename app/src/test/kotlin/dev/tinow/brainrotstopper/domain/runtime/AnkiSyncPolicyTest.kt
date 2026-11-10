package dev.tinow.brainrotstopper.domain.runtime

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.model.SyncStamp
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test

class AnkiSyncPolicyTest {
    private val interval = Constants.ANKI_SYNC_MIN_INTERVAL_MS
    private fun t(elapsed: Long, boot: Int = 3, wall: Long = 1_700_000_000_000 + elapsed) = TimeSnapshot(elapsed, wall, boot)
    private val last = SyncStamp(elapsedMs = 1_000_000, bootCount = 3, wallMs = 1_700_001_000_000)

    @Test fun neverSyncedIsDue() {
        assertThat(AnkiSyncPolicy.due(null, t(5))).isTrue()
    }

    @Test fun sameBootCountsOnlyTheMonotonicClock() {
        assertThat(AnkiSyncPolicy.due(last, t(1_000_000 + interval - 1, wall = Long.MAX_VALUE / 2))).isFalse()
        assertThat(AnkiSyncPolicy.due(last, t(1_000_000 + interval))).isTrue()
    }

    @Test fun clockBackwardsOnTheSameBootIsDue() {
        assertThat(AnkiSyncPolicy.due(last, t(500))).isTrue()
    }

    @Test fun afterARebootUptimeOrWallClockCounts() {
        // short uptime, wall clock says 1 h: not due
        assertThat(AnkiSyncPolicy.due(last, t(60_000, boot = 4, wall = last.wallMs + 3_600_000))).isFalse()
        // wall clock says 4 h: due
        assertThat(AnkiSyncPolicy.due(last, t(60_000, boot = 4, wall = last.wallMs + 4 * 3_600_000))).isTrue()
        // wall clock behind the stamp is ignored, uptime alone is enough
        assertThat(AnkiSyncPolicy.due(last, t(interval, boot = 4, wall = 0))).isTrue()
    }

    @Test fun stampTakesAllThreeClocks() {
        assertThat(AnkiSyncPolicy.stamp(TimeSnapshot(7, 8, 9))).isEqualTo(SyncStamp(7, 9, 8))
    }
}
