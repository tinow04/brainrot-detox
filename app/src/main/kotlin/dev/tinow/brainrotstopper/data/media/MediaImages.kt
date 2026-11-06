package dev.tinow.brainrotstopper.data.media

import dev.tinow.brainrotstopper.data.store.SettingsRepository
import java.io.File

/**
 * What the quiz engine calls before it renders a card: names in, `data:` URIs out (missing = placeholder). Reads the media
 * settings on every call, so a changed URL or token applies to the next card without a restart.
 */
class MediaImages(
    private val settings: SettingsRepository,
    private val cacheDir: File,
    clock: () -> Long,
    private val cleartextHosts: Set<String>,
    private val log: (String) -> Unit,
) {
    private val cache = MediaCache(cacheDir, clock)

    suspend fun load(names: List<String>): Map<String, String> {
        if (names.isEmpty()) return emptyMap()
        val s = settings.current()
        val config = MediaConfig.parse(s.mediaBaseUrl, s.mediaToken, cleartextHosts)
        return MediaInliner(config?.let(::HttpMediaFetcher), cache, log).load(names)
    }

    /** Debug hook and a future "clear cache" button. */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
