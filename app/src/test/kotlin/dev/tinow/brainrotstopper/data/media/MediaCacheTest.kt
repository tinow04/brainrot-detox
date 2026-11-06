package dev.tinow.brainrotstopper.data.media

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000_000L
    private fun cache(max: Long = MediaLimits.CACHE_MAX_BYTES) = MediaCache(File(tmp.root, "media"), { now }, max)

    @Test fun roundTripKeepsBytesTypeAndEtag() {
        val c = cache()
        c.put("a ä.png", byteArrayOf(1, 2, 3), "image/png", "\"e1\"")
        val e = c.get("a ä.png")!!
        assertThat(e.bytes).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(e.contentType).isEqualTo("image/png")
        assertThat(e.etag).isEqualTo("\"e1\"")
        assertThat(c.get("other.png")).isNull()
    }

    @Test fun fileNameIsAHashNotTheName() {
        val c = cache()
        c.put("geheim.png", byteArrayOf(1), "image/png", null)
        val names = File(tmp.root, "media").list()!!.toList()
        assertThat(names.none { it.contains("geheim") }).isTrue()
        assertThat(c.key("geheim.png")).hasLength(64)
    }

    @Test fun freshFor24HoursThenStale() {
        val c = cache()
        c.put("a.png", byteArrayOf(1), "image/png", null)
        now += MediaLimits.CACHE_FRESH_MS - 1
        assertThat(c.isFresh(c.get("a.png")!!)).isTrue()
        now += 1 // exactly 24 h: stale
        assertThat(c.isFresh(c.get("a.png")!!)).isFalse()
    }

    @Test fun clockGoingBackwardsIsNotFresh() {
        val c = cache()
        c.put("a.png", byteArrayOf(1), "image/png", null)
        now -= 5_000
        assertThat(c.isFresh(c.get("a.png")!!)).isFalse()
    }

    @Test fun touchRestartsFreshness() {
        val c = cache()
        c.put("a.png", byteArrayOf(1), "image/png", "\"e\"")
        now += MediaLimits.CACHE_FRESH_MS + 10
        val stale = c.get("a.png")!!
        assertThat(c.isFresh(stale)).isFalse()
        c.touch("a.png", stale)
        assertThat(c.isFresh(c.get("a.png")!!)).isTrue()
        assertThat(c.get("a.png")!!.etag).isEqualTo("\"e\"")
    }

    @Test fun missingIsRememberedOnlyInMemoryFor60Seconds() {
        val c = cache()
        c.markMissing("a.png")
        assertThat(c.isMissing("a.png")).isTrue()
        assertThat(File(tmp.root, "media").exists()).isFalse() // nothing on disk
        now += MediaLimits.NEGATIVE_TTL_MS - 1
        assertThat(c.isMissing("a.png")).isTrue()
        now += 1
        assertThat(c.isMissing("a.png")).isFalse()
        // a new instance (process restart) does not know it either
        c.markMissing("b.png")
        assertThat(cache().isMissing("b.png")).isFalse()
    }

    @Test fun putClearsMissing() {
        val c = cache()
        c.markMissing("a.png")
        c.put("a.png", byteArrayOf(1), "image/png", null)
        assertThat(c.isMissing("a.png")).isFalse()
    }

    @Test fun corruptEntriesAreMisses() {
        val c = cache()
        c.put("a.png", byteArrayOf(1, 2), "image/png", "\"e\"")
        val dir = File(tmp.root, "media")
        val meta = File(dir, c.key("a.png") + ".meta")
        meta.writeText("garbage")
        assertThat(c.get("a.png")).isNull()
        meta.writeText("text/html\n\n5\n") // not an image type
        assertThat(c.get("a.png")).isNull()
        meta.writeText("image/png\n\nnotanumber\n")
        assertThat(c.get("a.png")).isNull()
        meta.delete()
        assertThat(c.get("a.png")).isNull()
        File(dir, c.key("a.png")).writeBytes(ByteArray(0)) // empty data
        meta.writeText("image/png\n\n5\n")
        assertThat(c.get("a.png")).isNull()
    }

    @Test fun removeDeletesBothFiles() {
        val c = cache()
        c.put("a.png", byteArrayOf(1), "image/png", null)
        c.remove("a.png")
        assertThat(c.get("a.png")).isNull()
        assertThat(File(tmp.root, "media").list()!!.toList()).isEmpty()
    }

    @Test fun trimDeletesOldestFirstUntilItFits() {
        val c = cache(max = 250)
        val dir = File(tmp.root, "media")
        for ((i, n) in listOf("old.png", "mid.png", "new.png").withIndex()) {
            c.put(n, ByteArray(100) { 1 }, "image/png", null)
            File(dir, c.key(n)).setLastModified(1_000_000L + i * 10_000)
        }
        c.trim()
        assertThat(c.get("old.png")).isNull()
        assertThat(c.get("mid.png")).isNotNull()
        assertThat(c.get("new.png")).isNotNull()
        assertThat(File(dir, c.key("old.png") + ".meta").exists()).isFalse()
    }

    @Test fun unwritableCacheDirDoesNotThrow() {
        val blocker = File(tmp.root, "media").also { it.writeText("a file where the directory should be") }
        val c = MediaCache(blocker, { now })
        c.put("a.png", byteArrayOf(1), "image/png", null) // must not throw
        assertThat(c.get("a.png")).isNull()
    }
}
