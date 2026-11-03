package dev.tinow.brainrotstopper.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.stats.StatsDelta
import dev.tinow.brainrotstopper.domain.stats.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

/** `stats.json`. A corrupt file only costs the statistics, never the gate: it restarts empty. */
class StatsRepository(file: File, scope: CoroutineScope) {
    private val store: DataStore<Stats> = DataStoreFactory.create(
        serializer = JsonSerializer(Stats.serializer(), Stats()),
        corruptionHandler = ReplaceFileCorruptionHandler { Stats() },
        scope = scope,
        produceFile = { file },
    )

    val flow: Flow<Stats> get() = store.data

    suspend fun current(): Stats = store.data.first()

    suspend fun add(epochDay: Long, delta: StatsDelta) {
        if (delta.isEmpty) return
        store.updateData { it.plus(epochDay, delta) }
    }

    suspend fun update(transform: (Stats) -> Stats): Stats = store.updateData(transform)
}
