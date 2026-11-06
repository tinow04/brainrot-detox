package dev.tinow.brainrotstopper.data.media

import dev.tinow.brainrotstopper.data.anki.CardHtml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

sealed interface FetchResult {
    class Image(val bytes: ByteArray, val contentType: String, val etag: String?) : FetchResult
    data object NotModified : FetchResult
    data object NotFound : FetchResult
    data object Unauthorized : FetchResult
    data object RateLimited : FetchResult
    data class Failed(val reason: Reason) : FetchResult

    enum class Reason { BadName, Redirect, TooLarge, BadType, Timeout, Network, Status }
}

interface MediaFetcher {
    /** [etag] of a stale cache entry: sent as `If-None-Match`. Never throws except for cancellation. */
    suspend fun fetch(name: String, etag: String?): FetchResult
}

/**
 * GET `<base>/<percent-encoded name>` with the bearer token (media-server.md). No redirect is followed (a redirect could
 * carry the token elsewhere), the size is capped while reading, only image content types pass.
 */
class HttpMediaFetcher(private val config: MediaConfig) : MediaFetcher {
    override suspend fun fetch(name: String, etag: String?): FetchResult {
        if (CardHtml.safeName(name) == null) return FetchResult.Failed(FetchResult.Reason.BadName)
        return coroutineScope {
            val conn = try {
                open(name, etag)
            } catch (_: IOException) {
                return@coroutineScope FetchResult.Failed(FetchResult.Reason.Network)
            }
            val job = async(Dispatchers.IO) { run(conn) }
            try {
                job.await()
            } catch (e: CancellationException) {
                conn.disconnect() // unblocks the reading thread
                throw e
            }
        }
    }

    private fun open(name: String, etag: String?): HttpURLConnection {
        val conn = URL(config.baseUrl + "/" + encode(name)).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = MediaLimits.CONNECT_TIMEOUT_MS
        conn.readTimeout = MediaLimits.READ_TIMEOUT_MS
        conn.useCaches = false
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer ${config.token}")
        conn.setRequestProperty("Accept", MediaLimits.IMAGE_TYPES.joinToString(","))
        if (etag != null) conn.setRequestProperty("If-None-Match", etag)
        return conn
    }

    private fun run(conn: HttpURLConnection): FetchResult = try {
        when (conn.responseCode) {
            200 -> readImage(conn)
            304 -> FetchResult.NotModified
            404 -> FetchResult.NotFound
            401, 403 -> FetchResult.Unauthorized
            429 -> FetchResult.RateLimited
            in 300..399 -> FetchResult.Failed(FetchResult.Reason.Redirect)
            else -> FetchResult.Failed(FetchResult.Reason.Status)
        }
    } catch (_: SocketTimeoutException) {
        FetchResult.Failed(FetchResult.Reason.Timeout)
    } catch (_: IOException) {
        FetchResult.Failed(FetchResult.Reason.Network)
    } finally {
        conn.disconnect()
    }

    private fun readImage(conn: HttpURLConnection): FetchResult {
        val type = conn.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (type == null || type !in MediaLimits.IMAGE_TYPES) return FetchResult.Failed(FetchResult.Reason.BadType)
        val declared = conn.contentLengthLong
        if (declared > MediaLimits.MEDIA_MAX_BYTES) return FetchResult.Failed(FetchResult.Reason.TooLarge)
        val out = ByteArrayOutputStream(if (declared in 1..MediaLimits.MEDIA_MAX_BYTES.toLong()) declared.toInt() else 16 * 1024)
        conn.inputStream.use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (out.size() + n > MediaLimits.MEDIA_MAX_BYTES) return FetchResult.Failed(FetchResult.Reason.TooLarge)
                out.write(buf, 0, n)
            }
        }
        if (out.size() == 0) return FetchResult.Failed(FetchResult.Reason.Status)
        return FetchResult.Image(out.toByteArray(), type, conn.getHeaderField("ETag"))
    }

    companion object {
        /** UTF-8 percent encoding, space as `%20` (not `+`), only RFC 3986 unreserved characters stay. */
        fun encode(name: String): String {
            val sb = StringBuilder()
            for (b in name.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code) {
                    sb.append(c.toChar())
                } else {
                    sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
                }
            }
            return sb.toString()
        }
    }
}
