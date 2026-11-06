package dev.tinow.brainrotstopper.data.media

import java.io.File
import java.security.MessageDigest

class CacheEntry(val bytes: ByteArray, val contentType: String, val etag: String?, val storedAtMs: Long)

/**
 * Disk cache `<dir>/<sha256(name)>` with a sidecar `<hash>.meta` (content type, etag, stored-at). 200 answers live here;
 * 404 never does (only [markMissing] in memory, so a card whose image is not synced yet does not hammer the server).
 * Corrupt or half-written entries count as a miss. [clock] is wall time in ms (injected for tests).
 */
class MediaCache(
    private val dir: File,
    private val clock: () -> Long,
    private val maxBytes: Long = MediaLimits.CACHE_MAX_BYTES,
) {
    private val missing = HashMap<String, Long>()

    fun key(name: String): String =
        MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Synchronized
    fun get(name: String): CacheEntry? {
        val k = key(name)
        val data = File(dir, k)
        val meta = File(dir, "$k.meta")
        return try {
            if (!data.isFile || !meta.isFile) return null
            val lines = meta.readLines()
            if (lines.size < 3) return null
            val type = lines[0].takeIf { it in MediaLimits.IMAGE_TYPES } ?: return null
            val stored = lines[2].toLongOrNull() ?: return null
            val bytes = data.readBytes()
            if (bytes.isEmpty() || bytes.size > MediaLimits.MEDIA_MAX_BYTES) return null
            CacheEntry(bytes, type, lines[1].ifEmpty { null }, stored)
        } catch (_: Exception) {
            null
        }
    }

    fun isFresh(e: CacheEntry): Boolean {
        val age = clock() - e.storedAtMs
        return age in 0 until MediaLimits.CACHE_FRESH_MS
    }

    @Synchronized
    fun put(name: String, bytes: ByteArray, contentType: String, etag: String?) {
        try {
            dir.mkdirs()
            val k = key(name)
            writeAtomic(File(dir, k), bytes)
            writeAtomic(File(dir, "$k.meta"), "$contentType\n${etag.orEmpty().replace('\n', ' ')}\n${clock()}\n".toByteArray())
            missing.remove(name)
            trim()
        } catch (_: Exception) {
            // a cache that cannot write is just slower
        }
    }

    /** 304: the entry is confirmed, its freshness starts again. */
    @Synchronized
    fun touch(name: String, entry: CacheEntry) = put(name, entry.bytes, entry.contentType, entry.etag)

    @Synchronized
    fun remove(name: String) {
        val k = key(name)
        File(dir, k).delete()
        File(dir, "$k.meta").delete()
    }

    @Synchronized
    fun markMissing(name: String) {
        missing[name] = clock()
        if (missing.size > 500) missing.entries.removeIf { clock() - it.value >= MediaLimits.NEGATIVE_TTL_MS }
    }

    @Synchronized
    fun isMissing(name: String): Boolean {
        val at = missing[name] ?: return false
        val age = clock() - at
        if (age in 0 until MediaLimits.NEGATIVE_TTL_MS) return true
        missing.remove(name)
        return false
    }

    /** Deletes the oldest entries until the data files fit into [maxBytes]. */
    @Synchronized
    fun trim() {
        val files = dir.listFiles { f -> f.isFile && !f.name.endsWith(".meta") && !f.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
            File(dir, "${f.name}.meta").delete()
        }
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("rename failed")
        }
    }
}
