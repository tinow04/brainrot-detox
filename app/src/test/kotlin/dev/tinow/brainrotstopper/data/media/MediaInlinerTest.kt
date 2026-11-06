package dev.tinow.brainrotstopper.data.media

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MediaInlinerTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 5_000_000L
    private val logs = ArrayList<String>()
    private fun cache() = MediaCache(File(tmp.root, "media"), { now })

    private class FakeFetcher(var answer: suspend (String, String?) -> FetchResult) : MediaFetcher {
        val calls = ConcurrentHashMap<String, Int>()
        val etags = ConcurrentHashMap<String, String>() // "" = no If-None-Match sent
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        override suspend fun fetch(name: String, etag: String?): FetchResult {
            calls.merge(name, 1, Int::plus)
            etags[name] = etag.orEmpty()
            val n = running.incrementAndGet()
            maxRunning.updateAndGet { maxOf(it, n) }
            try { return answer(name, etag) } finally { running.decrementAndGet() }
        }
    }

    private fun img(vararg b: Int, type: String = "image/png", etag: String? = "\"e\"") =
        FetchResult.Image(ByteArray(b.size) { b[it].toByte() }, type, etag)

    private fun uri(type: String, vararg b: Int) = "data:$type;base64," + Base64.getEncoder().encodeToString(ByteArray(b.size) { b[it].toByte() })

    private fun inliner(f: MediaFetcher?, c: MediaCache = cache(), timeoutMs: Long = MediaLimits.MEDIA_TIMEOUT_MS) =
        MediaInliner(f, c, { logs += it }, timeoutMs)

    @Test fun successBecomesDataUriAndIsCached() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> img(1, 2, 3) }
        val c = cache()
        val out = inliner(f, c).load(listOf("a.png"))
        assertThat(out).containsExactly("a.png", uri("image/png", 1, 2, 3))
        assertThat(c.get("a.png")!!.etag).isEqualTo("\"e\"")
    }

    @Test fun freshCacheHitDoesNotTouchTheNetwork() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> img(1) }
        val c = cache()
        inliner(f, c).load(listOf("a.png"))
        now += 1_000
        val out = inliner(f, c).load(listOf("a.png"))
        assertThat(f.calls["a.png"]).isEqualTo(1)
        assertThat(out).hasSize(1)
    }

    @Test fun staleEntryIsRevalidatedWithEtagAndKeptOn304() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> img(1, 2, etag = "\"v1\"") }
        val c = cache()
        inliner(f, c).load(listOf("a.png"))
        now += MediaLimits.CACHE_FRESH_MS + 1
        f.answer = { _, _ -> FetchResult.NotModified }
        val out = inliner(f, c).load(listOf("a.png"))
        assertThat(f.etags["a.png"]).isEqualTo("\"v1\"")
        assertThat(out["a.png"]).isEqualTo(uri("image/png", 1, 2))
        assertThat(c.isFresh(c.get("a.png")!!)).isTrue() // 304 renewed the freshness
    }

    @Test fun staleEntryIsReplacedOn200() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> img(1, etag = "\"v1\"") }
        val c = cache()
        inliner(f, c).load(listOf("a.png"))
        now += MediaLimits.CACHE_FRESH_MS + 1
        f.answer = { _, _ -> img(9, 9, etag = "\"v2\"") }
        assertThat(inliner(f, c).load(listOf("a.png"))["a.png"]).isEqualTo(uri("image/png", 9, 9))
    }

    @Test fun notFoundIsPlaceholderNotCachedOnDiskAndNotRetriedFor60s() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> FetchResult.NotFound }
        val c = cache()
        val i = inliner(f, c)
        assertThat(i.load(listOf("a.png"))).isEmpty()
        assertThat(i.load(listOf("a.png"))).isEmpty()
        assertThat(f.calls["a.png"]).isEqualTo(1) // the second card view did not ask again
        assertThat(File(tmp.root, "media").exists()).isFalse()
        now += MediaLimits.NEGATIVE_TTL_MS
        i.load(listOf("a.png"))
        assertThat(f.calls["a.png"]).isEqualTo(2)
    }

    @Test fun imageDeletedOnServerIsNotShownFromStaleCache() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> img(1) }
        val c = cache()
        inliner(f, c).load(listOf("a.png"))
        now += MediaLimits.CACHE_FRESH_MS + 1
        f.answer = { _, _ -> FetchResult.NotFound }
        assertThat(inliner(f, c).load(listOf("a.png"))).isEmpty()
        assertThat(c.get("a.png")).isNull()
    }

    @Test fun everyFailureLeavesAPlaceholder() = runBlocking<Unit> {
        val failures = listOf(
            FetchResult.Unauthorized, FetchResult.RateLimited, FetchResult.NotModified,
            *FetchResult.Reason.entries.map { FetchResult.Failed(it) }.toTypedArray(),
        )
        for (r in failures) {
            val out = inliner(FakeFetcher { _, _ -> r }, MediaCache(File(tmp.newFolder(), "m"), { now })).load(listOf("a.png"))
            assertThat(out).isEmpty()
        }
    }

    @Test fun staleCopyIsBetterThanNothingWhenTheServerFails() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> img(7) }
        val c = cache()
        inliner(f, c).load(listOf("a.png"))
        now += MediaLimits.CACHE_FRESH_MS + 1
        for (r in listOf(FetchResult.Unauthorized, FetchResult.RateLimited, FetchResult.Failed(FetchResult.Reason.Network), FetchResult.Failed(FetchResult.Reason.Timeout))) {
            f.answer = { _, _ -> r }
            assertThat(inliner(f, c).load(listOf("a.png"))["a.png"]).isEqualTo(uri("image/png", 7))
        }
    }

    @Test fun featureOffFetchesNothingButStillUsesTheCache() = runBlocking<Unit> {
        val c = cache()
        c.put("a.png", byteArrayOf(5), "image/png", null)
        now += MediaLimits.CACHE_FRESH_MS + 1
        val out = inliner(null, c).load(listOf("a.png", "b.png"))
        assertThat(out).containsExactly("a.png", uri("image/png", 5))
    }

    @Test fun emptyListAndDuplicatesAreCheap() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> img(1) }
        assertThat(inliner(f).load(emptyList())).isEmpty()
        assertThat(inliner(f).load(listOf("a.png", "a.png", "a.png"))).hasSize(1)
        assertThat(f.calls["a.png"]).isEqualTo(1)
    }

    @Test fun atMostFourDownloadsRunAtOnce() = runBlocking<Unit> {
        val f = FakeFetcher { _, _ -> delay(50); img(1) }
        val names = (1..12).map { "i$it.png" }
        assertThat(inliner(f).load(names)).hasSize(12)
        assertThat(f.maxRunning.get()).isEqualTo(MediaLimits.MEDIA_PARALLEL)
    }

    @Test fun deadlineKeepsWhatIsReadyAndDropsTheRest() = runBlocking<Unit> {
        val never = CompletableDeferred<Unit>()
        val f = FakeFetcher { n, _ -> if (n == "slow.png") { never.await(); img(2) } else img(1) }
        val start = System.nanoTime()
        val out = inliner(f, timeoutMs = 300).load(listOf("fast.png", "slow.png"))
        assertThat(out.keys).containsExactly("fast.png")
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000)
        assertThat(logs.any { it.startsWith("media deadline") }).isTrue()
    }

    @Test fun anExceptionInOneFetchOnlyLosesThatImage() = runBlocking<Unit> {
        val f = FakeFetcher { n, _ -> if (n == "bad.png") error("boom") else img(1) }
        assertThat(inliner(f).load(listOf("bad.png", "ok.png")).keys).containsExactly("ok.png")
    }

    @Test fun totalBudgetPerCardIsKeptInCardOrder() = runBlocking<Unit> {
        val half = ByteArray(MediaLimits.MEDIA_MAX_BYTES) { 1 }
        val f = FakeFetcher { _, _ -> FetchResult.Image(half, "image/png", null) }
        val names = listOf("1.png", "2.png", "3.png", "4.png")
        val out = inliner(f).load(names)
        assertThat(out.keys.toList()).containsExactly("1.png", "2.png", "3.png").inOrder() // 3 x 2 MiB = 6 MiB, the 4th does not fit
    }

    @Test fun logsHaveNeitherNamesNorTokens() = runBlocking<Unit> {
        val f = FakeFetcher { n, _ -> if (n.startsWith("a")) img(1) else FetchResult.NotFound }
        inliner(f).load(listOf("a-geheim.png", "b-geheim.png"))
        assertThat(logs).isNotEmpty()
        assertThat(logs.joinToString()).doesNotContain("geheim")
    }
}
