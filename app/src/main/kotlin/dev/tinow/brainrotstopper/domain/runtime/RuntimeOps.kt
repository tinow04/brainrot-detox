package dev.tinow.brainrotstopper.domain.runtime

import dev.tinow.brainrotstopper.domain.gate.GrantPolicy
import dev.tinow.brainrotstopper.domain.model.EmergencyState
import dev.tinow.brainrotstopper.domain.model.Episode
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.PendingAnswer
import dev.tinow.brainrotstopper.domain.model.QuizSession
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.quiz.SessionPolicy
import dev.tinow.brainrotstopper.domain.quiz.SessionResolution
import dev.tinow.brainrotstopper.domain.stats.StatsDelta
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot

/** Runtime after an operation plus the statistics it caused. Each operation is applied in a single `updateData`. */
data class Applied<T>(val runtime: Runtime, val delta: StatsDelta, val info: T)

data class AttachInfo(val resolution: SessionResolution?, val grantKind: GrantKind?, val newEpisode: Boolean)

data class CommitInfo(val unlocked: Boolean, val session: QuizSession?)

/** Pure state transitions of `runtime.json` (architecture.md 3B/3C/4). */
object RuntimeOps {
    private fun Runtime.pruned(now: TimeSnapshot) = copy(grants = GrantPolicy.prune(grants, now))

    /**
     * The lock UI came up for [pkg]. A valid grant means nothing is blocked (no episode, no block counted). Otherwise the
     * episode is opened or kept (debounce [Constants.BLOCK_DEBOUNCE_MS]) and the session resolved.
     */
    fun attach(
        rt: Runtime, pkg: String, settings: Settings, now: TimeSnapshot,
        sessionTtlMs: Long = Constants.SESSION_TTL_MS,
        newId: () -> String,
    ): Applied<AttachInfo> {
        val base = rt.pruned(now)
        val grant = base.grants[pkg]
        if (GrantPolicy.isValid(grant, now)) return Applied(base, StatsDelta(), AttachInfo(null, grant!!.kind, false))

        var delta = StatsDelta()
        val ep = base.episode
        val sameEpisode = ep != null && ep.pkg == pkg && ep.bootCount == now.bootCount && now.bootCount >= 0 &&
            now.elapsedMs - ep.lastAttachElapsed in 0 until Constants.BLOCK_DEBOUNCE_MS
        val episode = if (sameEpisode) {
            ep.copy(lastAttachElapsed = now.elapsedMs)
        } else {
            if (ep != null) delta += StatsDelta(aborted = 1) // an earlier episode ended without unlock or cancel
            delta += StatsDelta(blocks = 1, perApp = mapOf(pkg to 1))
            Episode(pkg, now.elapsedMs, now.elapsedMs, now.bootCount)
        }
        val resolution = SessionPolicy.resolve(base.session, pkg, settings, now, sessionTtlMs, newId)
        return Applied(base.copy(episode = episode, session = resolution.session), delta, AttachInfo(resolution, null, !sameEpisode))
    }

    /** Cancel / back / gesture outside the unlocked screen: the episode counts as aborted, the session stays. */
    fun cancel(rt: Runtime): Applied<Unit> =
        if (rt.episode == null) Applied(rt, StatsDelta(), Unit)
        else Applied(rt.copy(episode = null), StatsDelta(aborted = 1), Unit)

    /** Write-ahead: remember the rating before it is sent to Anki. */
    fun setPending(rt: Runtime, pending: PendingAnswer, now: TimeSnapshot): Runtime =
        rt.copy(session = rt.session?.copy(pending = pending, lastActiveElapsed = now.elapsedMs))

    /** Drops a pending answer without counting it (no `answerVerify`, or the user retried). */
    fun discardPending(rt: Runtime): Runtime = rt.copy(session = rt.session?.copy(pending = null))

    /**
     * Commit step of the answer pipeline: clears `pending`, counts per the stats table and, if the required number of
     * good answers is reached, creates the grant, deletes the session and closes the episode - all in one result.
     */
    fun commitAnswer(rt: Runtime, now: TimeSnapshot, unlockMsOverride: Long? = null): Applied<CommitInfo> {
        val session = rt.session
        val pending = session?.pending ?: return Applied(rt, StatsDelta(), CommitInfo(false, session))
        val ease = pending.ease
        val counted = session.copy(
            pending = null,
            answered = session.answered + 1,
            correct = session.correct + if (ease.counts) 1 else 0,
            wrong = session.wrong + if (ease.counts) 0 else 1,
            lastWrongElapsed = if (ease.counts) session.lastWrongElapsed else now.elapsedMs,
            activeMs = session.activeMs + pending.takenMs,
            lastActiveElapsed = now.elapsedMs,
        )
        var delta = StatsDelta(answered = 1, correct = if (ease.counts) 1 else 0)
        if (ease.counts && counted.correct >= counted.required) {
            val grant = GrantPolicy.create(GrantKind.QUIZ, unlockMsOverride ?: (counted.unlockMinutes * 60_000L), now)
            val next = rt.pruned(now).copy(grants = rt.pruned(now).grants + (counted.targetPkg to grant), session = null, episode = null)
            delta += StatsDelta(unlocks = 1)
            return Applied(next, delta, CommitInfo(true, null))
        }
        return Applied(rt.copy(session = counted), delta, CommitInfo(false, counted))
    }

    /** Deck really empty: the target is released for the normal duration without a quiz. Counts `freeUnlocks`. */
    fun commitNothingDue(rt: Runtime, now: TimeSnapshot, unlockMsOverride: Long? = null): Applied<Boolean> {
        val session = rt.session ?: return Applied(rt, StatsDelta(), false)
        val grant = GrantPolicy.create(GrantKind.NOTHING_DUE, unlockMsOverride ?: (session.unlockMinutes * 60_000L), now)
        val pruned = rt.pruned(now)
        return Applied(pruned.copy(grants = pruned.grants + (session.targetPkg to grant), session = null, episode = null), StatsDelta(freeUnlocks = 1), true)
    }

    /** `false` = still on cooldown (or the target is unknown) and nothing changes. */
    fun commitEmergency(rt: Runtime, target: String, now: TimeSnapshot): Applied<Boolean> {
        if (!EmergencyPolicy.available(rt.emergency, now)) return Applied(rt, StatsDelta(), false)
        val grant = GrantPolicy.create(GrantKind.EMERGENCY, Constants.EMERGENCY_GRANT_MIN * 60_000L, now)
        val pruned = rt.pruned(now)
        val next = pruned.copy(
            grants = pruned.grants + (target to grant),
            episode = null,
            emergency = EmergencyState(now.elapsedMs, now.bootCount, now.wallMs),
        )
        return Applied(next, StatsDelta(emergencies = 1), true)
    }
}

/** What the emergency button of an error screen shows (architecture.md 5, Notausgang). */
sealed interface EmergencyButton {
    /** Switched off (or the boot count is unreadable): no button. */
    data object Hidden : EmergencyButton

    /** The error screen has not been up for [Constants.EMERGENCY_WAIT_MS] yet: disabled, counting down. */
    data class Wait(val seconds: Int) : EmergencyButton

    data object Ready : EmergencyButton

    /** Used less than [Constants.EMERGENCY_COOLDOWN_MS] ago: disabled with the time left. */
    data class Cooldown(val remainingMs: Long) : EmergencyButton
}

object EmergencyPolicy {
    /** At most once per [Constants.EMERGENCY_COOLDOWN_MS]; a reboot does not end the cooldown ([remainingMs]). */
    fun available(state: EmergencyState?, now: TimeSnapshot, enabled: Boolean = Constants.EMERGENCY_EXIT_ENABLED): Boolean =
        enabled && now.bootCount >= 0 && remainingMs(state, now) == 0L

    /**
     * Cooldown left, between 0 and [Constants.EMERGENCY_COOLDOWN_MS]. On the same boot only the monotonic clock counts.
     * After a reboot the monotonic clock restarted: at least this boot's uptime has passed since the use (trusted lower
     * bound); the wall clock may show more (phone was off) and then counts. A wall clock behind the use is ignored.
     */
    fun remainingMs(state: EmergencyState?, now: TimeSnapshot): Long {
        if (state == null) return 0
        val cooldown = Constants.EMERGENCY_COOLDOWN_MS
        val since = if (now.bootCount >= 0 && state.bootCount == now.bootCount) {
            now.elapsedMs - state.lastUsedElapsed
        } else {
            val wallSince = if (state.lastUsedWall > 0 && now.wallMs >= state.lastUsedWall) now.wallMs - state.lastUsedWall else 0
            maxOf(now.elapsedMs, wallSince)
        }
        return (cooldown - since).coerceIn(0, cooldown)
    }

    /** The button for an error screen shown since [errorSince] (elapsed): the longer of wait and cooldown wins. */
    fun button(errorSince: Long, state: EmergencyState?, now: TimeSnapshot, enabled: Boolean = Constants.EMERGENCY_EXIT_ENABLED): EmergencyButton {
        if (!enabled || now.bootCount < 0) return EmergencyButton.Hidden
        val waitMs = (Constants.EMERGENCY_WAIT_MS - (now.elapsedMs - errorSince)).coerceIn(0, Constants.EMERGENCY_WAIT_MS)
        val coolMs = remainingMs(state, now)
        return when {
            coolMs > 0 && coolMs >= waitMs -> EmergencyButton.Cooldown(coolMs)
            waitMs > 0 -> EmergencyButton.Wait(((waitMs + 999) / 1000).toInt())
            else -> EmergencyButton.Ready
        }
    }
}
