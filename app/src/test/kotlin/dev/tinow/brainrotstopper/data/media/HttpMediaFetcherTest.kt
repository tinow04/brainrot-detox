package dev.tinow.brainrotstopper.data.media

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real sockets against a local server in the test (no Android, no real server, no real token). */
class HttpMediaFetcherTest {
    private class Seen(val rawPath: String, val auth: String?, val ifNoneMatch: String?)

    private lateinit var server: HttpServer
    private val seen = CopyOnWriteArrayList<Seen>()
    private var handler: (HttpExchange) -> Unit = { it.send(200, "image/png", PNG) }
    private val token = "testtoken-123"

    private fun HttpExchange.send(code: Int, type: String?, body: ByteArray = ByteArray(0), headers: Map<String, String> = emptyMap()) {
        type?.let { responseHeaders.add("Content-Type", it) }
        headers.forEach { (k, v) -> responseHeaders.add(k, v) }
        sendResponseHeaders(code, if (code == 304 || body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.use { it.write(body) } else close()
    }

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { ex ->
            seen += Seen(ex.requestURI.rawPath, ex.requestHeaders.getFirst("Authorization"), ex.requestHeaders.getFirst("If-None-Match"))
            handler(ex)
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
    }

    @After fun stop() = server.stop(0)

    private fun fetcher(): HttpMediaFetcher {
        val url = "http://127.0.0.1:${server.address.port}"
        return HttpMediaFetcher(MediaConfig.parse(url, token, setOf("127.0.0.1"))!!)
    }

    private fun fetch(name: String, etag: String? = null) = runBlocking { fetcher().fetch(name, etag) }

    @Test fun okReturnsBytesTypeAndEtagAndSendsBearer() {
        handler = { it.send(200, "image/png", PNG, mapOf("ETag" to "\"abc\"")) }
        val r = fetch("a.png") as FetchResult.Image
        assertThat(r.bytes).isEqualTo(PNG)
        assertThat(r.contentType).isEqualTo("image/png")
        assertThat(r.etag).isEqualTo("\"abc\"")
        assertThat(seen.single().auth).isEqualTo("Bearer $token")
        assertThat(seen.single().rawPath).isEqualTo("/a.png")
    }

    @Test fun contentTypeParametersAndCaseAreIgnored() {
        handler = { it.send(200, "Image/JPEG; charset=binary", PNG) }
        assertThat((fetch("a.jpg") as FetchResult.Image).contentType).isEqualTo("image/jpeg")
    }

    @Test fun namesWithSpaceUmlautAndAmpersandArePercentEncoded() {
        fetch("bild ä 1.png")
        fetch("a&b+c.png")
        assertThat(seen.map { it.rawPath }).containsExactly("/bild%20%C3%A4%201.png", "/a%26b%2Bc.png").inOrder()
    }

    @Test fun ifNoneMatchIsSentAndNotModifiedIsReported() {
        handler = { it.send(304, null, headers = mapOf("ETag" to "\"abc\"")) }
        assertThat(fetch("a.png", "\"abc\"")).isEqualTo(FetchResult.NotModified)
        assertThat(seen.single().ifNoneMatch).isEqualTo("\"abc\"")
    }

    @Test fun statusMapping() {
        handler = { it.send(404, "text/plain", "nope".toByteArray()) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.NotFound)
        handler = { it.send(401, "text/plain", "no".toByteArray()) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Unauthorized)
        handler = { it.send(429, "text/plain", "slow".toByteArray()) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.RateLimited)
        handler = { it.send(500, "text/plain", "boom".toByteArray()) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.Status))
        handler = { it.send(400, "text/plain", "bad".toByteArray()) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.Status))
    }

    @Test fun redirectIsNeverFollowed() {
        handler = { ex ->
            if (ex.requestURI.rawPath == "/a.png") ex.send(302, null, headers = mapOf("Location" to "/elsewhere.png")) else ex.send(200, "image/png", PNG)
        }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.Redirect))
        assertThat(seen.map { it.rawPath }).containsExactly("/a.png") // the target was not requested, the token went nowhere else
    }

    @Test fun wrongContentTypeIsRefused() {
        handler = { it.send(200, "text/html", "<script>x</script>".toByteArray()) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.BadType))
        handler = { it.send(200, null, PNG) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.BadType))
        handler = { it.send(200, "image/svg+xml", PNG) }
        assertThat(fetch("a.svg")).isEqualTo(FetchResult.Failed(FetchResult.Reason.BadType))
    }

    @Test fun tooLargeByDeclaredLengthAndWhileStreaming() {
        val big = ByteArray(MediaLimits.MEDIA_MAX_BYTES + 1)
        handler = { it.send(200, "image/png", big) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.TooLarge))
        // chunked: no Content-Length, the cap must hold while reading
        handler = { ex ->
            ex.responseHeaders.add("Content-Type", "image/png")
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { out -> runCatching { repeat(5) { out.write(ByteArray(MediaLimits.MEDIA_MAX_BYTES / 2)) } } }
        }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.TooLarge))
    }

    @Test fun exactlyTheLimitIsAccepted() {
        val ok = ByteArray(MediaLimits.MEDIA_MAX_BYTES) { 1 }
        handler = { it.send(200, "image/png", ok) }
        assertThat((fetch("a.png") as FetchResult.Image).bytes.size).isEqualTo(MediaLimits.MEDIA_MAX_BYTES)
    }

    @Test fun emptyBodyIsNotAnImage() {
        handler = { it.send(200, "image/png", ByteArray(0)) }
        assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.Status))
    }

    @Test fun slowServerTimesOut() {
        val release = CountDownLatch(1)
        handler = { ex -> release.await(10, TimeUnit.SECONDS); ex.send(200, "image/png", PNG) }
        try {
            assertThat(fetch("a.png")).isEqualTo(FetchResult.Failed(FetchResult.Reason.Timeout))
        } finally {
            release.countDown()
        }
    }

    @Test fun connectionRefusedIsNetworkFailure() {
        val f = HttpMediaFetcher(MediaConfig.parse("http://127.0.0.1:1", token, setOf("127.0.0.1"))!!)
        assertThat(runBlocking { f.fetch("a.png", null) }).isEqualTo(FetchResult.Failed(FetchResult.Reason.Network))
    }

    @Test fun unsafeNamesNeverReachTheNetwork() {
        for (n in listOf("../x.png", "a/b.png", "..", "", "C:\\x.png", "http://x/y.png", "a\u0000b.png")) {
            assertThat(fetch(n)).isEqualTo(FetchResult.Failed(FetchResult.Reason.BadName))
        }
        assertThat(seen).isEmpty()
    }

    @Test fun cancellationReturnsPromptlyEvenWhenTheServerHangs() = runBlocking {
        val release = CountDownLatch(1)
        handler = { ex -> release.await(10, TimeUnit.SECONDS); ex.send(200, "image/png", PNG) }
        val start = System.nanoTime()
        val job: Job = async { fetcher().fetch("a.png", null) }
        delay(300)
        job.cancel()
        job.join()
        release.countDown()
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1_500)
    }

    @Test fun tokenIsNotInTheRequestPathOrQuery() {
        fetch("a.png")
        assertThat(seen.single().rawPath).doesNotContain(token)
    }

    @Test fun encodeKeepsUnreservedAndEncodesRest() {
        assertThat(HttpMediaFetcher.encode("A-z_0.9~")).isEqualTo("A-z_0.9~")
        assertThat(HttpMediaFetcher.encode("é €")).isEqualTo("%C3%A9%20%E2%82%AC")
    }

    companion object {
        /** Not a decodable image on purpose: the fetcher passes bytes through. */
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    }
}
