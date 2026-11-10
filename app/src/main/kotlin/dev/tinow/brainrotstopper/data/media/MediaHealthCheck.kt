package dev.tinow.brainrotstopper.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Result of `$ ./test` in the `bilder` box. */
enum class MediaHealth { Ok, Unreachable, TokenRejected }

/**
 * Two requests against the own media server (media-server.md): `GET /healthz` without token (reachable at all?), then a
 * GET for a file that does not exist WITH the token: 404 = token accepted, 401/403 = token rejected. No card content is
 * sent, the token stays in the header and never reaches a log.
 */
class MediaHealthCheck(
    private val fetcher: (MediaConfig) -> MediaFetcher = ::HttpMediaFetcher,
) {
    suspend fun check(config: MediaConfig): MediaHealth {
        if (!healthz(config.baseUrl)) return MediaHealth.Unreachable
        return when (fetcher(config).fetch(PROBE_NAME, null)) {
            FetchResult.NotFound, FetchResult.NotModified, is FetchResult.Image -> MediaHealth.Ok
            FetchResult.Unauthorized -> MediaHealth.TokenRejected
            else -> MediaHealth.Unreachable
        }
    }

    private suspend fun healthz(base: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$base/healthz").openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = MediaLimits.CONNECT_TIMEOUT_MS
            conn.readTimeout = MediaLimits.READ_TIMEOUT_MS
            conn.useCaches = false
            try {
                conn.responseCode == 200
            } finally {
                conn.disconnect()
            }
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        /** A name that never exists on the server; only used to see whether the token is accepted. */
        const val PROBE_NAME = "brainrot-stopper-token-check.png"
    }
}
