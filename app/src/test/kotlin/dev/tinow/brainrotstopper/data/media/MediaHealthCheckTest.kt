package dev.tinow.brainrotstopper.data.media

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** `$ ./test` against a local server in the test: healthz without token, then the token probe (404 = token ok). */
class MediaHealthCheckTest {
    private class Seen(val path: String, val auth: String?)

    private lateinit var server: HttpServer
    private val seen = CopyOnWriteArrayList<Seen>()
    private val token = "testtoken-123"
    private var healthz = 200
    private var probe: (String?) -> Int = { auth -> if (auth == "Bearer $token") 404 else 401 }

    private fun HttpExchange.status(code: Int) {
        sendResponseHeaders(code, -1)
        close()
    }

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { ex ->
            val auth = ex.requestHeaders.getFirst("Authorization")
            seen += Seen(ex.requestURI.rawPath, auth)
            ex.status(if (ex.requestURI.rawPath == "/healthz") healthz else probe(auth))
        }
        server.start()
    }

    @After fun stop() = server.stop(0)

    private fun check(tok: String = token) = runBlocking {
        MediaHealthCheck().check(MediaConfig.parse("http://127.0.0.1:${server.address.port}", tok, setOf("127.0.0.1"))!!)
    }

    @Test fun reachableAndTokenAccepted() {
        assertThat(check()).isEqualTo(MediaHealth.Ok)
        assertThat(seen.map { it.path }).containsExactly("/healthz", "/" + MediaHealthCheck.PROBE_NAME).inOrder()
        // healthz goes without the token, only the probe carries it
        assertThat(seen[0].auth).isNull()
        assertThat(seen[1].auth).isEqualTo("Bearer $token")
    }

    @Test fun wrongTokenIsRejected() {
        assertThat(check("wrong-token")).isEqualTo(MediaHealth.TokenRejected)
    }

    @Test fun forbiddenCountsAsRejected() {
        probe = { 403 }
        assertThat(check()).isEqualTo(MediaHealth.TokenRejected)
    }

    @Test fun healthzFailingMeansUnreachableWithoutSendingTheToken() {
        healthz = 502
        assertThat(check()).isEqualTo(MediaHealth.Unreachable)
        assertThat(seen.map { it.path }).containsExactly("/healthz")
    }

    @Test fun serverDownIsUnreachable() {
        val port = server.address.port
        server.stop(0)
        val r = runBlocking { MediaHealthCheck().check(MediaConfig.parse("http://127.0.0.1:$port", token, setOf("127.0.0.1"))!!) }
        assertThat(r).isEqualTo(MediaHealth.Unreachable)
    }

    @Test fun otherStatusOnProbeIsUnreachable() {
        probe = { 500 }
        assertThat(check()).isEqualTo(MediaHealth.Unreachable)
    }
}
