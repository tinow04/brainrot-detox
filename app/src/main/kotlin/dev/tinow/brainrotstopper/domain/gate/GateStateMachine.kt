package dev.tinow.brainrotstopper.domain.gate

import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot

sealed interface GateState {
    data object Unknown : GateState
    data object Free : GateState
    data class LockRequested(val pkg: String, val since: Long, val retried: Boolean, val ackDeadline: Long) : GateState
    data class LockVisible(val pkg: String) : GateState
    data class CoolDown(val pkg: String, val until: Long, val breaker: Boolean, val lastGoHome: Long = Long.MIN_VALUE) : GateState
}

sealed interface GateInput {
    /** First window event of a (non-ignored) foreground package. */
    data class Event(val pkg: String, val cls: String? = null) : GateInput
    /** The single re-evaluate timer fired (ack deadline, throttle end, grant expiry or breaker interval). */
    data object Timer : GateInput
    data object ScreenOn : GateInput
    data object SettingsChanged : GateInput
    /** `LockActivity.onResume` reported itself. */
    data object Ack : GateInput
    /** The lock UI appeared for [pkg] or disappeared (null). */
    data class LockVisibleChanged(val pkg: String?) : GateInput
    /** Accessibility service (re)connected; the foreground is unknown until the next event. */
    data object Connected : GateInput
}

enum class GateDiag { Breaker, LaunchFailed }

sealed interface GateEffect {
    data class StartLock(val pkg: String, val retry: Boolean) : GateEffect
    data object GoHome : GateEffect
    /** Replaces any earlier scheduled re-evaluate (one timer at a time). */
    data class ScheduleReeval(val atElapsedMs: Long) : GateEffect
    data object CancelTimers : GateEffect
    data class Diag(val kind: GateDiag) : GateEffect
}

/** Inputs of one step that are not events: clock, last known foreground package and what the gate may see. */
data class GateEnv(
    val now: Long,
    val foreground: String?,
    val snapshot: GateSnapshot,
    val time: TimeSnapshot,
    /** Package the lock UI currently shows for (null = no lock UI). */
    val lockVisible: String? = null,
)

data class GateModel(val state: GateState = GateState.Unknown, val throttle: LaunchThrottle = LaunchThrottle())

/**
 * Pure gate logic in virtual time (architecture.md 3A). Invariant: a lock decision that is delayed by the relaunch
 * throttle or the breaker always schedules a re-evaluate for the last known foreground package, and neither ever
 * lets the app through.
 */
object GateStateMachine {
    fun step(model: GateModel, input: GateInput, env: GateEnv): Pair<GateModel, List<GateEffect>> {
        val state = model.state
        return when (input) {
            GateInput.Connected -> if (state is GateState.Unknown) model to listOf(GateEffect.CancelTimers) else model to emptyList()
            GateInput.Ack -> when (state) {
                is GateState.LockRequested -> GateModel(GateState.LockVisible(state.pkg), model.throttle) to listOf(GateEffect.CancelTimers)
                else -> model to emptyList()
            }
            is GateInput.LockVisibleChanged -> lockVisibleChanged(model, input.pkg, env)
            is GateInput.Event -> evaluate(model, input.pkg, env, fromTimer = false)
            GateInput.Timer -> evaluate(model, env.foreground, env, fromTimer = true)
            GateInput.ScreenOn, GateInput.SettingsChanged -> evaluate(model, env.foreground, env, fromTimer = false)
        }
    }

    private fun lockVisibleChanged(model: GateModel, pkg: String?, env: GateEnv): Pair<GateModel, List<GateEffect>> {
        val state = model.state
        if (pkg != null && state is GateState.LockRequested && state.pkg == pkg) {
            return GateModel(GateState.LockVisible(pkg), model.throttle) to listOf(GateEffect.CancelTimers)
        }
        // The lock UI went away: look at the foreground again (fail-closed, it may still be a blocked app).
        return if (pkg == null) evaluate(model, env.foreground, env, fromTimer = false) else model to emptyList()
    }

    private fun evaluate(model: GateModel, fg: String?, env: GateEnv, fromTimer: Boolean): Pair<GateModel, List<GateEffect>> {
        val state = model.state
        when (val decision = GateEvaluator.evaluate(fg, env.snapshot, env.time)) {
            is GateDecision.Allow -> {
                val effects = mutableListOf<GateEffect>(GateEffect.CancelTimers)
                if (decision is GateDecision.Granted) effects += GateEffect.ScheduleReeval(decision.untilElapsedMs)
                return GateModel(GateState.Free, model.throttle) to effects
            }
            is GateDecision.Lock -> return lock(model, state, decision.pkg, env, fromTimer)
        }
    }

    private fun lock(model: GateModel, state: GateState, pkg: String, env: GateEnv, fromTimer: Boolean): Pair<GateModel, List<GateEffect>> {
        val now = env.now
        // Lock UI already visible for this package: nothing to start.
        if (env.lockVisible == pkg) return GateModel(GateState.LockVisible(pkg), model.throttle) to emptyList()

        if (state is GateState.LockRequested) {
            if (state.pkg == pkg && !fromTimer) return model to emptyList() // still waiting for the ack
            if (state.pkg == pkg && fromTimer) return ackDeadline(model, state, env)
        }

        if (state is GateState.CoolDown && state.pkg == pkg && state.breaker && now < state.until) {
            // Breaker cooldown: no lock starts, but the app must not stay usable. Send it home at most once per interval.
            val goHome = now - state.lastGoHome >= Constants.BREAKER_MIN_INTERVAL_MS
            val next = if (goHome) state.copy(lastGoHome = now) else state
            val effects = buildList {
                if (goHome) add(GateEffect.GoHome)
                add(GateEffect.ScheduleReeval(minOf(state.until, (if (goHome) now else state.lastGoHome) + Constants.BREAKER_MIN_INTERVAL_MS)))
            }
            return GateModel(next, model.throttle) to effects
        }

        if (model.throttle.breakerTripped(now)) {
            val cooldown = GateState.CoolDown(pkg, now + Constants.BREAKER_COOLDOWN_MS, breaker = true, lastGoHome = now)
            return GateModel(cooldown, model.throttle) to listOf(
                GateEffect.GoHome,
                GateEffect.Diag(GateDiag.Breaker),
                GateEffect.ScheduleReeval(now + Constants.BREAKER_MIN_INTERVAL_MS),
            )
        }

        val blockedUntil = model.throttle.blockedUntil(pkg, now)
        if (blockedUntil != null) {
            return GateModel(GateState.CoolDown(pkg, blockedUntil, breaker = false), model.throttle) to
                listOf(GateEffect.ScheduleReeval(blockedUntil + Constants.REEVAL_TOLERANCE_MS))
        }

        val requested = GateState.LockRequested(pkg, now, retried = false, ackDeadline = now + Constants.LAUNCH_ACK_MS)
        return GateModel(requested, model.throttle.record(pkg, now)) to listOf(
            GateEffect.StartLock(pkg, retry = false),
            GateEffect.ScheduleReeval(requested.ackDeadline),
        )
    }

    /** The ack timer fired while the target is still in front: retry once, then go home. */
    private fun ackDeadline(model: GateModel, state: GateState.LockRequested, env: GateEnv): Pair<GateModel, List<GateEffect>> {
        val now = env.now
        if (now < state.ackDeadline) return model to listOf(GateEffect.ScheduleReeval(state.ackDeadline))
        if (!state.retried) {
            val retry = GateState.LockRequested(state.pkg, now, retried = true, ackDeadline = now + Constants.LAUNCH_ACK_MS)
            return GateModel(retry, model.throttle.record(state.pkg, now)) to listOf(
                GateEffect.StartLock(state.pkg, retry = true),
                GateEffect.ScheduleReeval(retry.ackDeadline),
            )
        }
        val cooldown = GateState.CoolDown(state.pkg, now + Constants.RELAUNCH_MIN_MS, breaker = false)
        return GateModel(cooldown, model.throttle) to listOf(
            GateEffect.GoHome,
            GateEffect.Diag(GateDiag.LaunchFailed),
            GateEffect.ScheduleReeval(cooldown.until + Constants.REEVAL_TOLERANCE_MS),
        )
    }
}
