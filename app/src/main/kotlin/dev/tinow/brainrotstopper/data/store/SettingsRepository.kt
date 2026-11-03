package dev.tinow.brainrotstopper.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.tinow.brainrotstopper.domain.model.BsJson
import dev.tinow.brainrotstopper.domain.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * `settings.json` with a mirror copy (`settings.mirror.json`, temp file + atomic rename) written after every successful
 * update. A corrupt main file is restored from the mirror; only if that is missing or corrupt too, defaults apply. Every
 * corruption case calls [onCorrupted] (sets `runtime.diag.settingsCorrupted`, red banner).
 */
class SettingsRepository(
    file: File,
    private val mirror: File,
    scope: CoroutineScope,
    /** Called on every corruption case (must not block; the handler is not suspending). */
    private val onCorrupted: () -> Unit = {},
) {
    private val store: DataStore<Settings> = DataStoreFactory.create(
        serializer = JsonSerializer(Settings.serializer(), Settings()),
        // DataStore writes the returned value back to the file itself.
        corruptionHandler = ReplaceFileCorruptionHandler {
            onCorrupted()
            readMirror() ?: Settings()
        },
        scope = scope,
        produceFile = { file },
    )

    /** Values outside the allowed ranges are clamped on read, so a hand-edited file cannot misbehave. */
    val flow: Flow<Settings> = store.data.map { it.sanitized() }

    suspend fun current(): Settings = flow.first()

    /** The only way to change settings; the mirror follows each successful write. */
    suspend fun update(transform: (Settings) -> Settings): Settings {
        val updated = store.updateData { transform(it).sanitized() }
        writeMirror(updated)
        return updated
    }

    internal fun readMirror(): Settings? =
        try {
            if (mirror.isFile) BsJson.decodeFromString<Settings>(mirror.readText()) else null
        } catch (_: Exception) {
            null
        }

    private fun writeMirror(settings: Settings) {
        try {
            mirror.parentFile?.mkdirs()
            val tmp = File(mirror.parentFile, mirror.name + ".tmp")
            tmp.writeText(BsJson.encodeToString(Settings.serializer(), settings))
            Files.move(tmp.toPath(), mirror.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.io.IOException) {
            // The mirror is a safety net; failing to write it must not fail the settings update.
        }
    }
}
