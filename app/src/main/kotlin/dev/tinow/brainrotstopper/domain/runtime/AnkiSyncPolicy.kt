package dev.tinow.brainrotstopper.domain.runtime

import dev.tinow.brainrotstopper.domain.model.SyncStamp
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot

/** When the next `DO_SYNC` to AnkiDroid is allowed (M6-T7). Never synced = due. */
object AnkiSyncPolicy {
    fun due(last: SyncStamp?, now: TimeSnapshot, intervalMs: Long = Constants.ANKI_SYNC_MIN_INTERVAL_MS): Boolean {
        if (last == null) return true
        val since = if (now.bootCount >= 0 && last.bootCount == now.bootCount) {
            now.elapsedMs - last.elapsedMs
        } else {
            // Other boot: the monotonic clock restarted; this boot's uptime has passed at least, the wall clock may say more.
            val wallSince = if (last.wallMs > 0 && now.wallMs >= last.wallMs) now.wallMs - last.wallMs else 0
            maxOf(now.elapsedMs, wallSince)
        }
        // A clock that went backwards on the same boot is implausible: sync rather than never again.
        return since < 0 || since >= intervalMs
    }

    fun stamp(now: TimeSnapshot) = SyncStamp(now.elapsedMs, now.bootCount, now.wallMs)
}
