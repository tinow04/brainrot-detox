package dev.tinow.brainrotstopper.data.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns image names into `data:` URIs for `CardHtml.build(images = ...)`. Everything that fails (no server, 401, 404, 429,
 * timeout, too large, wrong type) is simply missing from the result: the card keeps the placeholder and stays rateable.
 * Media problems never influence unlock or counting. [log] gets short status lines without token and without file names.
 */
class MediaInliner(
    private val fetcher: MediaFetcher?,
    private val cache: MediaCache,
    private val log: (String) -> Unit = {},
    private val timeoutMs: Long = MediaLimits.MEDIA_TIMEOUT_MS,
) {
    suspend fun load(names: List<String>): Map<String, String> {
        val wanted = names.distinct()
        if (wanted.isEmpty()) return emptyMap()
        val found = ConcurrentHashMap<String, CacheEntry>()
        coroutineScope {
            val gate = Semaphore(MediaLimits.MEDIA_PARALLEL)
            val jobs = wanted.map { name ->
                launch {
                    gate.withPermit {
                        try {
                            one(name)?.let { found[name] = it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log("media ${tag(name)} error ${e.javaClass.simpleName}")
                        }
                    }
                }
            }
            if (withTimeoutOrNull(timeoutMs) { jobs.joinAll() } == null) {
                log("media deadline ${timeoutMs}ms, ${found.size}/${wanted.size} ready")
                jobs.forEach { it.cancelAndJoin() }
            }
        }
        // keep the document size in check: in card order until the budget is used up
        val out = LinkedHashMap<String, String>()
        var total = 0
        for (name in wanted) {
            val e = found[name] ?: continue
            if (total + e.bytes.size > MediaLimits.MEDIA_MAX_TOTAL_BYTES) continue
            total += e.bytes.size
            out[name] = "data:${e.contentType};base64,${Base64.getEncoder().encodeToString(e.bytes)}"
        }
        return out
    }

    private suspend fun one(name: String): CacheEntry? {
        val cached = cache.get(name)
        if (cached != null && cache.isFresh(cached)) return cached
        if (fetcher == null) return cached // feature off: whatever is cached is still fine, nothing is fetched
        if (cache.isMissing(name)) return null
        return when (val r = fetcher.fetch(name, cached?.etag)) {
            is FetchResult.Image -> {
                cache.put(name, r.bytes, r.contentType, r.etag)
                log("media ${tag(name)} 200 ${r.bytes.size}b")
                CacheEntry(r.bytes, r.contentType, r.etag, 0)
            }
            FetchResult.NotModified -> {
                log("media ${tag(name)} 304")
                cached?.also { cache.touch(name, it) }
            }
            FetchResult.NotFound -> {
                log("media ${tag(name)} 404")
                cache.markMissing(name)
                cache.remove(name) // gone on the server: do not keep showing a stale copy
                null
            }
            FetchResult.Unauthorized -> { log("media ${tag(name)} 401"); cached }
            FetchResult.RateLimited -> { log("media ${tag(name)} 429"); cached }
            is FetchResult.Failed -> { log("media ${tag(name)} failed ${r.reason}"); cached }
        }
    }

    /** First bytes of the name's hash: enough to correlate log lines, not enough to read the name. */
    private fun tag(name: String) = cache.key(name).take(8)
}
