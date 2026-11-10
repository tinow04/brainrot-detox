package dev.tinow.brainrotstopper.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.tinow.brainrotstopper.domain.gate.GrantPolicy
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.runtime.Applied
import dev.tinow.brainrotstopper.domain.runtime.RuntimeStore
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * `runtime.json`: grants, session and episode in one file, so each operation is a single `updateData` and a process
 * kill can never leave a half-applied state. Corruption restarts empty, which is fail-closed (no grants).
 *
 * Statistics live in their own file and are written after the runtime commit; if the process dies in between, one
 * counter update is lost, never a grant or an answer (documented in architecture.md 4).
 */
class RuntimeRepository(
    file: File,
    scope: CoroutineScope,
    private val stats: StatsRepository,
    private val onStatsError: (Exception) -> Unit = {},
) : RuntimeStore {
    private val store: DataStore<Runtime> = DataStoreFactory.create(
        serializer = JsonSerializer(Runtime.serializer(), Runtime()),
        corruptionHandler = ReplaceFileCorruptionHandler { Runtime() },
        scope = scope,
        produceFile = { file },
    )

    val flow: Flow<Runtime> get() = store.data

    override suspend fun current(): Runtime = store.data.first()

    /** Applies a pure runtime operation (see RuntimeOps) in one `updateData`, then records its statistics for the local day of [now]. */
    override suspend fun <T> apply(now: TimeSnapshot, op: (Runtime) -> Applied<T>): T {
        var result: Applied<T>? = null
        store.updateData { current -> op(current).also { result = it }.runtime }
        val applied = checkNotNull(result)
        // The runtime commit is done: a failing statistics write loses one counter update, never the operation itself.
        try {
            stats.add(now.epochDay, applied.delta)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onStatsError(e)
        }
        return applied.info
    }

    /** Plain runtime change without statistics (write-ahead pending, diag counters). */
    override suspend fun update(transform: (Runtime) -> Runtime): Runtime = store.updateData(transform)

    suspend fun pruneGrants(now: TimeSnapshot) {
        store.updateData { it.copy(grants = GrantPolicy.prune(it.grants, now)) }
    }

    suspend fun markSettingsCorrupted() {
        store.updateData { it.copy(diag = it.diag.copy(settingsCorrupted = true)) }
    }

    suspend fun clearSettingsCorrupted() {
        store.updateData { it.copy(diag = it.diag.copy(settingsCorrupted = false)) }
    }
}
