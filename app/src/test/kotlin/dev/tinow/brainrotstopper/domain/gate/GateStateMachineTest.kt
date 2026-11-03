package dev.tinow.brainrotstopper.domain.gate

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test

/** Drives the pure machine in virtual time: one replaceable timer, effects are recorded, no real clock. */
private class Harness(blocked: Set<String> = setOf(INSTA, TIKTOK)) {
    var now = 100_000L
    var foreground: String? = null
    var lockVisible: String? = null
    var model = GateModel()
    var snapshot = GateSnapshot(blockedPackages = blocked)
    var timer: Long? = null
    val effects = mutableListOf<GateEffect>()

    private fun env() = GateEnv(now, foreground, snapshot, TimeSnapshot(now, 0, 1), lockVisible)

    fun send(input: GateInput): List<GateEffect> {
        val (m, e) = GateStateMachine.step(model, input, env())
        model = m
        for (effect in e) when (effect) {
            GateEffect.CancelTimers -> timer = null
            is GateEffect.ScheduleReeval -> timer = effect.atElapsedMs
            else -> Unit
        }
        effects += e
        return e
    }

    fun event(pkg: String) { foreground = pkg; send(GateInput.Event(pkg)) }

    /** Moves time forward and fires the timer whenever it comes due, like the controller would. */
    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val due = timer
            if (due == null || due > end) break
            now = maxOf(now, due)
            timer = null
            send(GateInput.Timer)
        }
        now = end
    }

    fun count(predicate: (GateEffect) -> Boolean) = effects.count(predicate)
    val starts get() = effects.filterIsInstance<GateEffect.StartLock>()
    val state get() = model.state
}

private const val INSTA = "com.insta"
private const val TIKTOK = "com.tiktok"
private const val MAPS = "com.maps"

class GateStateMachineTest {
    // --- table rows ----------------------------------------------------------------------------------------------

    @Test fun unknownConnectedStaysUnknownAndCancelsTimers() {
        val h = Harness()
        assertThat(h.send(GateInput.Connected)).containsExactly(GateEffect.CancelTimers)
        assertThat(h.state).isEqualTo(GateState.Unknown)
    }

    @Test fun allowedEventGoesFreeFromEveryState() {
        for (initial in listOf(
            GateState.Unknown, GateState.Free, GateState.LockVisible(INSTA),
            GateState.CoolDown(INSTA, 200_000, breaker = false), GateState.LockRequested(INSTA, 0, false, 1_500),
        )) {
            val h = Harness()
            h.model = GateModel(initial)
            h.event(MAPS)
            assertThat(h.state).isEqualTo(GateState.Free)
            assertThat(h.timer).isNull()
        }
    }

    @Test fun grantedEventSchedulesExpiryReevaluate() {
        val h = Harness()
        val grant = GrantPolicy.create(GrantKind.QUIZ, 60_000, TimeSnapshot(h.now, 0, 1))
        h.snapshot = h.snapshot.copy(grants = mapOf(INSTA to grant))
        h.event(INSTA)
        assertThat(h.state).isEqualTo(GateState.Free)
        assertThat(h.timer).isEqualTo(h.now + 60_000)
    }

    @Test fun lockDecisionStartsLockAndSchedulesAckDeadline() {
        val h = Harness()
        val before = h.now
        h.event(INSTA)
        assertThat(h.starts).containsExactly(GateEffect.StartLock(INSTA, retry = false))
        assertThat(h.state).isEqualTo(GateState.LockRequested(INSTA, before, false, before + Constants.LAUNCH_ACK_MS))
        assertThat(h.timer).isEqualTo(before + Constants.LAUNCH_ACK_MS)
    }

    @Test fun lockVisibleForSamePackageStartsNothing() {
        val h = Harness()
        h.lockVisible = INSTA
        h.event(INSTA)
        assertThat(h.state).isEqualTo(GateState.LockVisible(INSTA))
        assertThat(h.effects).isEmpty()
    }

    @Test fun ackMovesRequestedToVisibleAndCancelsTimers() {
        val h = Harness()
        h.event(INSTA)
        h.send(GateInput.Ack)
        assertThat(h.state).isEqualTo(GateState.LockVisible(INSTA))
        assertThat(h.timer).isNull()
    }

    @Test fun lockVisibleChangedToTargetCountsAsAck() {
        val h = Harness()
        h.event(INSTA)
        h.lockVisible = INSTA
        h.send(GateInput.LockVisibleChanged(INSTA))
        assertThat(h.state).isEqualTo(GateState.LockVisible(INSTA))
    }

    @Test fun ackWithoutRequestChangesNothing() {
        val h = Harness()
        assertThat(h.send(GateInput.Ack)).isEmpty()
        assertThat(h.state).isEqualTo(GateState.Unknown)
    }

    @Test fun lockVisibleThenOtherBlockedPackageRequestsNewLock() {
        val h = Harness()
        h.event(INSTA); h.lockVisible = INSTA; h.send(GateInput.Ack)
        h.advance(5_000)
        h.event(TIKTOK)
        assertThat(h.state).isInstanceOf(GateState.LockRequested::class.java)
        assertThat(h.starts.last()).isEqualTo(GateEffect.StartLock(TIKTOK, retry = false))
    }

    @Test fun lockVisibleThenAllowedEventGoesFree() {
        val h = Harness()
        h.event(INSTA); h.lockVisible = INSTA; h.send(GateInput.Ack)
        h.event(MAPS)
        assertThat(h.state).isEqualTo(GateState.Free)
    }

    @Test fun freeAfterGrantExpiryLocksOnTimer() {
        val h = Harness()
        val grant = GrantPolicy.create(GrantKind.QUIZ, 60_000, TimeSnapshot(h.now, 0, 1))
        h.snapshot = h.snapshot.copy(grants = mapOf(INSTA to grant))
        h.event(INSTA)
        h.advance(59_999)
        assertThat(h.starts).isEmpty()
        h.advance(2)
        assertThat(h.starts).containsExactly(GateEffect.StartLock(INSTA, retry = false))
        assertThat(h.state).isInstanceOf(GateState.LockRequested::class.java)
    }

    @Test fun screenOnReevaluatesTheKnownForeground() {
        val h = Harness()
        h.foreground = INSTA
        h.send(GateInput.ScreenOn)
        assertThat(h.starts).hasSize(1)
    }

    @Test fun settingsChangedLocksNewlyBlockedForeground() {
        val h = Harness(blocked = emptySet())
        h.event(INSTA)
        assertThat(h.state).isEqualTo(GateState.Free)
        h.snapshot = h.snapshot.copy(blockedPackages = setOf(INSTA))
        h.send(GateInput.SettingsChanged)
        assertThat(h.starts).hasSize(1)
    }

    @Test fun lockUiGoneWhileTargetStillInFrontLocksAgain() {
        val h = Harness()
        h.event(INSTA); h.lockVisible = INSTA; h.send(GateInput.Ack)
        h.advance(10_000)
        h.lockVisible = null
        h.send(GateInput.LockVisibleChanged(null))
        assertThat(h.starts).hasSize(2)
    }

    // --- ack frist ----------------------------------------------------------------------------------------------

    @Test fun missingAckRetriesOnceThenGoesHomeWithDiag() {
        val h = Harness()
        h.event(INSTA)
        h.advance(Constants.LAUNCH_ACK_MS)
        assertThat(h.starts.map { it.retry }).containsExactly(false, true).inOrder()
        assertThat((h.state as GateState.LockRequested).retried).isTrue()
        h.advance(Constants.LAUNCH_ACK_MS)
        assertThat(h.count { it == GateEffect.GoHome }).isEqualTo(1)
        assertThat(h.effects).contains(GateEffect.Diag(GateDiag.LaunchFailed))
        assertThat(h.state).isInstanceOf(GateState.CoolDown::class.java)
        assertThat(h.effects.filterIsInstance<GateEffect.Diag>().map { it.kind }).doesNotContain(GateDiag.Breaker)
    }

    @Test fun ackInsideTheDeadlineStopsTheRetry() {
        val h = Harness()
        h.event(INSTA)
        h.advance(1_000)
        h.send(GateInput.Ack)
        h.advance(10_000)
        assertThat(h.starts).hasSize(1)
        assertThat(h.state).isEqualTo(GateState.LockVisible(INSTA))
    }

    @Test fun ackDeadlineButTargetLeftGoesFree() {
        val h = Harness()
        h.event(INSTA)
        h.foreground = MAPS
        h.advance(Constants.LAUNCH_ACK_MS)
        assertThat(h.starts).hasSize(1)
        assertThat(h.state).isEqualTo(GateState.Free)
    }

    @Test fun repeatedEventWhileWaitingForAckDoesNotStartAgain() {
        val h = Harness()
        h.event(INSTA)
        h.advance(200)
        h.event(INSTA)
        assertThat(h.starts).hasSize(1)
    }

    // --- throttle / breaker invariants ---------------------------------------------------------------------------

    @Test fun eventInsideThrottleWindowThenSilenceStillLocksAfterWindow() {
        val h = Harness()
        h.event(INSTA); h.lockVisible = INSTA; h.send(GateInput.Ack)
        h.lockVisible = null; h.foreground = MAPS; h.event(MAPS)
        h.advance(300)
        h.event(INSTA) // 300 ms after the last start: inside RELAUNCH_MIN_MS
        assertThat(h.state).isInstanceOf(GateState.CoolDown::class.java)
        assertThat(h.starts).hasSize(1)
        val windowEnd = (h.state as GateState.CoolDown).until
        h.advance(windowEnd + Constants.REEVAL_TOLERANCE_MS - h.now) // no further event at all
        assertThat(h.starts).hasSize(2)
        assertThat(h.now).isAtMost(windowEnd + Constants.REEVAL_TOLERANCE_MS)
    }

    @Test fun sevenStartsInTenSecondsTripBreakerWithGoHomeAndNeverAllow() {
        val h = Harness()
        var attempts = 0
        // Reopen the blocked app every time the 1.5 s relaunch window allows it (lock never acks, so each is a start).
        while (h.effects.none { it == GateEffect.Diag(GateDiag.Breaker) } && attempts < 40) {
            h.event(INSTA)
            h.send(GateInput.Ack) // lock appears, user closes it again and reopens the app
            h.lockVisible = null
            h.advance(Constants.RELAUNCH_MIN_MS)
            attempts++
        }
        assertThat(h.starts).hasSize(Constants.BREAKER_STARTS)
        assertThat(h.effects).contains(GateEffect.Diag(GateDiag.Breaker))
        assertThat(h.count { it == GateEffect.GoHome }).isAtLeast(1)
        assertThat(h.state).isInstanceOf(GateState.CoolDown::class.java)
        assertThat((h.state as GateState.CoolDown).breaker).isTrue()
    }

    @Test fun breakerCooldownReevaluatesInIntervalsUntilLockAppearsAfter30s() {
        val h = Harness()
        // Provoke the breaker with a flood of reopen events spaced by the relaunch window.
        repeat(8) { h.event(INSTA); h.advance(Constants.RELAUNCH_MIN_MS) }
        val state = h.state as GateState.CoolDown
        assertThat(state.breaker).isTrue()
        val startsAtTrip = h.starts.size
        val cooldownEnd = state.until
        // App stays in front the whole time and no further event arrives: re-evaluate must keep checking.
        val reevalTimes = mutableListOf<Long>()
        var guard = 0
        while (h.starts.size == startsAtTrip && guard++ < 100) {
            val t = h.timer ?: error("no re-evaluate scheduled during breaker cooldown")
            reevalTimes += t
            h.advance(t - h.now)
        }
        assertThat(h.starts.size).isEqualTo(startsAtTrip + 1) // lock appears
        assertThat(h.now).isAtLeast(cooldownEnd)
        assertThat(h.now).isAtMost(cooldownEnd + Constants.BREAKER_MIN_INTERVAL_MS)
        reevalTimes.zipWithNext().dropLast(1).forEach { (a, b) -> assertThat(b - a).isAtMost(Constants.BREAKER_MIN_INTERVAL_MS) }
        assertThat(h.effects.filterIsInstance<GateEffect.GoHome>()).isNotEmpty()
    }

    @Test fun userGoesHomeInsideBreakerCooldownNoLockStarts() {
        val h = Harness()
        repeat(8) { h.event(INSTA); h.advance(Constants.RELAUNCH_MIN_MS) }
        assertThat((h.state as GateState.CoolDown).breaker).isTrue()
        val startsAtTrip = h.starts.size
        h.foreground = MAPS // launcher / other app now in front
        h.advance(60_000)
        assertThat(h.starts.size).isEqualTo(startsAtTrip)
        assertThat(h.state).isEqualTo(GateState.Free)
    }

    @Test fun userGoesHomeInsideThrottleWindowNoStartLock() {
        val h = Harness()
        h.event(INSTA); h.send(GateInput.Ack)
        h.advance(100)
        h.event(INSTA)
        assertThat(h.state).isInstanceOf(GateState.CoolDown::class.java)
        h.foreground = MAPS
        h.advance(5_000)
        assertThat(h.starts).hasSize(1)
        assertThat(h.state).isEqualTo(GateState.Free)
    }

    @Test fun breakerNeverAllowsWhileBlockedAppStaysInFront() {
        val h = Harness()
        repeat(8) { h.event(INSTA); h.advance(Constants.RELAUNCH_MIN_MS) }
        // From the trip until the lock shows up there must be no state that lets the app through.
        var sawFree = false
        var guard = 0
        val startsAtTrip = h.starts.size
        while (h.starts.size == startsAtTrip && guard++ < 100) {
            h.advance((h.timer ?: error("stalled")) - h.now)
            if (h.state == GateState.Free) sawFree = true
        }
        assertThat(sawFree).isFalse()
    }
}
