package dev.tinow.brainrotstopper.data.media

import java.net.URI

/** Limits of the image download (architecture.md 5a); named constants, no magic numbers in the code. */
object MediaLimits {
    /** Whole-card deadline for all images; what is not there by then stays a placeholder. */
    const val MEDIA_TIMEOUT_MS = 2_500L
    const val CONNECT_TIMEOUT_MS = 2_000
    const val READ_TIMEOUT_MS = 2_000
    const val MEDIA_MAX_BYTES = 2 * 1024 * 1024
    /** All inlined images of one card together (the HTML string grows by a third through base64). */
    const val MEDIA_MAX_TOTAL_BYTES = 6 * 1024 * 1024
    const val MEDIA_PARALLEL = 4
    const val CACHE_FRESH_MS = 24 * 60 * 60 * 1000L
    const val NEGATIVE_TTL_MS = 60_000L
    const val CACHE_MAX_BYTES = 50L * 1024 * 1024
    val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
}

/** Base URL and bearer token of the own media server. The token never reaches a log or an exception message. */
class MediaConfig private constructor(val baseUrl: String, val token: String) {
    override fun toString() = "MediaConfig($baseUrl, token=***)"

    companion object {
        /**
         * Null = feature off or unusable (empty, malformed, wrong scheme, user info, query, fragment or a path).
         * https only; http is accepted for the hosts in [cleartextHosts] (the emulator host in debug builds). In release
         * builds the network security config blocks cleartext anyway.
         */
        fun parse(baseUrl: String, token: String, cleartextHosts: Set<String> = emptySet()): MediaConfig? {
            val url = baseUrl.trim().trimEnd('/')
            val tok = token.trim()
            if (url.isEmpty() || tok.isEmpty() || tok.any { it.isWhitespace() || it.isISOControl() }) return null
            val uri = try { URI(url) } catch (_: Exception) { return null }
            val host = uri.host ?: return null
            val ok = when (uri.scheme?.lowercase()) {
                "https" -> true
                "http" -> host in cleartextHosts
                else -> false
            }
            if (!ok || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
            if (!uri.rawPath.isNullOrEmpty()) return null
            return MediaConfig(url, tok)
        }
    }
}
