package dev.tinow.brainrotstopper.domain.gate

import dev.tinow.brainrotstopper.domain.model.UnlockGrant
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot

/** What the gate needs to decide; refreshed from the stores by the Android shell. */
data class GateSnapshot(
    val blockedPackages: Set<String> = emptySet(),
    val grants: Map<String, UnlockGrant> = emptyMap(),
    val dynamicProtected: Set<String> = emptySet(),
)

sealed interface GateDecision {
    sealed interface Allow : GateDecision
    data object NotBlocked : Allow
    data object Protected : Allow
    data class Granted(val remainingMs: Long, val untilElapsedMs: Long) : Allow
    data class Lock(val pkg: String) : GateDecision
}

object GateEvaluator {
    /** Protected packages first (even when listed as blocked), then the block list, then the grant. */
    fun evaluate(foreground: String?, snapshot: GateSnapshot, now: TimeSnapshot): GateDecision {
        if (foreground == null) return GateDecision.NotBlocked
        if (ProtectedPackages.isProtected(foreground, snapshot.dynamicProtected)) return GateDecision.Protected
        if (foreground !in snapshot.blockedPackages) return GateDecision.NotBlocked
        val grant = snapshot.grants[foreground]
        if (GrantPolicy.isValid(grant, now)) {
            return GateDecision.Granted(grant!!.untilElapsedMs - now.elapsedMs, grant.untilElapsedMs)
        }
        return GateDecision.Lock(foreground)
    }
}
