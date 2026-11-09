package dev.tinow.brainrotstopper.domain.runtime

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.model.EmergencyState
import dev.tinow.brainrotstopper.domain.quiz.Constants.EMERGENCY_COOLDOWN_MS
import dev.tinow.brainrotstopper.domain.quiz.Constants.EMERGENCY_WAIT_MS
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test

/** Cooldown across reboots and the button states of the emergency exit (M4-T6). */
class EmergencyPolicyTest {
    private val wall0 = 1_700_000_000_000
    private val hour = 3_600_000L
    private fun t(elapsed: Long, boot: Int = 1, wall: Long = wall0 + elapsed) = TimeSnapshot(elapsed, wall, boot)
    private val used = EmergencyState(lastUsedElapsed = 10 * hour, bootCount = 1, lastUsedWall = wall0 + 10 * hour)

    @Test fun neverUsedIsAvailable() {
        assertThat(EmergencyPolicy.remainingMs(null, t(0))).isEqualTo(0)
        assertThat(EmergencyPolicy.available(null, t(0))).isTrue()
    }

    @Test fun sameBootCountsTheMonotonicClockOnly() {
        // the wall clock jumping ahead does not shorten the cooldown on the same boot
        val now = t(11 * hour, wall = wall0 + 100 * hour)
        assertThat(EmergencyPolicy.remainingMs(used, now)).isEqualTo(5 * hour)
        assertThat(EmergencyPolicy.available(used, now)).isFalse()
        assertThat(EmergencyPolicy.available(used, t(16 * hour))).isTrue()
    }

    @Test fun rebootRightAfterTheUseKeepsTheCooldown() {
        // rebooted one hour after the use, five minutes up: 5 h left (wall says 1 h 5 min since the use)
        val now = t(5 * 60_000L, boot = 2, wall = used.lastUsedWall + hour + 5 * 60_000L)
        assertThat(EmergencyPolicy.remainingMs(used, now)).isEqualTo(5 * hour - 5 * 60_000L)
        assertThat(EmergencyPolicy.available(used, now)).isFalse()
    }

    @Test fun rebootWithTheWallClockSetBackFallsBackToTheUptime() {
        val now = t(2 * hour, boot = 2, wall = used.lastUsedWall - 50 * hour)
        assertThat(EmergencyPolicy.remainingMs(used, now)).isEqualTo(4 * hour)
        assertThat(EmergencyPolicy.available(used, t(6 * hour, boot = 2, wall = 0))).isTrue()
    }

    @Test fun rebootAfterTheCooldownEndedFreesIt() {
        // phone off over night: the wall clock shows 9 h since the use, the uptime only 10 minutes
        val now = t(10 * 60_000L, boot = 2, wall = used.lastUsedWall + 9 * hour)
        assertThat(EmergencyPolicy.available(used, now)).isTrue()
    }

    @Test fun stateWithoutWallTimeCountsTheUptimeAfterAReboot() {
        val old = EmergencyState(lastUsedElapsed = 10 * hour, bootCount = 1) // written before M4-T6
        assertThat(EmergencyPolicy.remainingMs(old, t(hour, boot = 2))).isEqualTo(5 * hour)
    }

    @Test fun unreadableBootCountIsNeverAvailable() {
        assertThat(EmergencyPolicy.available(null, t(0, boot = -1))).isFalse()
        assertThat(EmergencyPolicy.button(0, null, t(EMERGENCY_WAIT_MS, boot = -1))).isEqualTo(EmergencyButton.Hidden)
    }

    @Test fun buttonCountsDownSixtySecondsThenIsReady() {
        assertThat(EmergencyPolicy.button(1_000, null, t(1_000))).isEqualTo(EmergencyButton.Wait(60))
        assertThat(EmergencyPolicy.button(1_000, null, t(1_001))).isEqualTo(EmergencyButton.Wait(60))
        assertThat(EmergencyPolicy.button(1_000, null, t(1_000 + EMERGENCY_WAIT_MS - 1))).isEqualTo(EmergencyButton.Wait(1))
        assertThat(EmergencyPolicy.button(1_000, null, t(1_000 + EMERGENCY_WAIT_MS))).isEqualTo(EmergencyButton.Ready)
    }

    @Test fun buttonShowsTheCooldownWhenItLastsLonger() {
        val errorAt = 12 * hour
        assertThat(EmergencyPolicy.button(errorAt, used, t(errorAt))).isEqualTo(EmergencyButton.Cooldown(4 * hour))
        // 30 s of cooldown left but 60 s of waiting: the wait counts down
        val late = used.lastUsedElapsed + EMERGENCY_COOLDOWN_MS - 30_000
        assertThat(EmergencyPolicy.button(late, used, t(late))).isEqualTo(EmergencyButton.Wait(60))
    }

    @Test fun buttonHiddenWhenSwitchedOff() {
        assertThat(EmergencyPolicy.button(0, null, t(EMERGENCY_WAIT_MS), enabled = false)).isEqualTo(EmergencyButton.Hidden)
        assertThat(EmergencyPolicy.available(null, t(0), enabled = false)).isFalse()
    }
}
