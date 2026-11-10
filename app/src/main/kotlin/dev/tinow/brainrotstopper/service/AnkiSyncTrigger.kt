package dev.tinow.brainrotstopper.service

import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.runtime.AnkiSyncPolicy
import dev.tinow.brainrotstopper.domain.runtime.RuntimeStore
import dev.tinow.brainrotstopper.domain.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Who can send the sync request: the running gate (its service context may start activities from the background). */
fun interface AnkiSyncSender {
    /** false = not sent (lock in front, AnkiDroid missing). */
    fun sendAnkiSync(): Boolean
}

/**
 * Optional AnkiDroid sync after a grant (M6-T7): the lock calls [request] when the unlocked screen is left (target opened
 * or closed). If switched on and the last sync is [Constants.ANKI_SYNC_MIN_INTERVAL_MS] ago, `DO_SYNC` goes out
 * [Constants.ANKI_SYNC_DELAY_MS] later through the gate. Best effort: AnkiDroid drops it when offline, not logged in or
 * synced < 2 min ago (research.md E2); a skipped or failed attempt is only logged.
 */
class AnkiSyncTrigger(
    private val scope: CoroutineScope,
    private val settings: suspend () -> Settings,
    private val runtime: RuntimeStore,
    private val time: TimeSource,
    private val sender: () -> AnkiSyncSender?,
    private val log: (String) -> Unit = {},
) {
    private var job: Job? = null

    @Synchronized
    fun request() {
        job?.cancel()
        job = scope.launch {
            try {
                run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("sync failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun run() {
        if (!settings().ankiSyncAfterUnlock) return log("sync off")
        if (!AnkiSyncPolicy.due(runtime.current().ankiSync, time.now())) return log("sync skipped: interval")
        delay(Constants.ANKI_SYNC_DELAY_MS)
        val gate = sender() ?: return log("sync skipped: service off")
        if (!gate.sendAnkiSync()) return log("sync skipped: lock in front or no ankidroid")
        val stamp = AnkiSyncPolicy.stamp(time.now())
        runtime.update { it.copy(ankiSync = stamp) }
        log("sync sent")
    }
}
