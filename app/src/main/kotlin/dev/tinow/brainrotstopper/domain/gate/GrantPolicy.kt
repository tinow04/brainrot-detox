package dev.tinow.brainrotstopper.domain.gate

import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.UnlockGrant
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot

/** Strict grant invariant (architecture.md 3C). The wall clock is never consulted. */
object GrantPolicy {
    fun isValid(grant: UnlockGrant?, now: TimeSnapshot): Boolean {
        if (grant == null) return false
        if (now.bootCount < 0) return false // BOOT_COUNT unreadable: never valid
        if (grant.bootCount != now.bootCount) return false // reboot invalidates
        if (grant.grantedAtElapsedMs > now.elapsedMs) return false // clock went backwards: implausible
        if (now.elapsedMs >= grant.untilElapsedMs) return false // exact boundary is expired
        return grant.untilElapsedMs - now.elapsedMs <= grant.durationMs + Constants.GRANT_PLAUSIBILITY_SLACK_MS
    }

    /** Remaining time of a valid grant, 0 otherwise. */
    fun remainingMs(grant: UnlockGrant?, now: TimeSnapshot): Long =
        if (isValid(grant, now)) grant!!.untilElapsedMs - now.elapsedMs else 0L

    fun create(kind: GrantKind, durationMs: Long, now: TimeSnapshot): UnlockGrant = UnlockGrant(
        grantedAtElapsedMs = now.elapsedMs,
        untilElapsedMs = now.elapsedMs + durationMs,
        durationMs = durationMs,
        bootCount = now.bootCount,
        grantedAtWallMs = now.wallMs,
        kind = kind,
    )

    /** Expired or implausible grants are dropped on every write. */
    fun prune(grants: Map<String, UnlockGrant>, now: TimeSnapshot): Map<String, UnlockGrant> =
        grants.filterValues { isValid(it, now) }
}
