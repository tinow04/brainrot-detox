package dev.tinow.brainrotstopper.data.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaConfigTest {
    @Test fun httpsUrlIsAccepted() {
        val c = MediaConfig.parse("https://anki-media.example.org", "tok")!!
        assertThat(c.baseUrl).isEqualTo("https://anki-media.example.org")
        assertThat(c.token).isEqualTo("tok")
    }

    @Test fun trailingSlashAndWhitespaceAreNormalised() {
        assertThat(MediaConfig.parse("  https://h.example:8443/ ", " tok ")!!.baseUrl).isEqualTo("https://h.example:8443")
    }

    @Test fun emptyMeansOff() {
        assertThat(MediaConfig.parse("", "tok")).isNull()
        assertThat(MediaConfig.parse("https://h.example", "")).isNull()
        assertThat(MediaConfig.parse("  ", "  ")).isNull()
    }

    @Test fun httpOnlyForListedCleartextHosts() {
        assertThat(MediaConfig.parse("http://h.example", "t")).isNull()
        assertThat(MediaConfig.parse("http://10.0.2.2:8099", "t")).isNull()
        assertThat(MediaConfig.parse("http://10.0.2.2:8099", "t", setOf("10.0.2.2"))!!.baseUrl).isEqualTo("http://10.0.2.2:8099")
        assertThat(MediaConfig.parse("http://other.example", "t", setOf("10.0.2.2"))).isNull()
    }

    @Test fun unusableUrlsAreRejected() {
        for (u in listOf("ftp://h.example", "file:///etc", "javascript:alert(1)", "h.example", "https://", "https://u:p@h.example",
            "https://h.example/path", "https://h.example?x=1", "https://h.example#f", "https://h .example", "not a url")) {
            assertThat(MediaConfig.parse(u, "t")).isNull()
        }
    }

    @Test fun tokenWithWhitespaceIsRejected() {
        assertThat(MediaConfig.parse("https://h.example", "a b")).isNull()
        assertThat(MediaConfig.parse("https://h.example", "a\nb")).isNull()
    }

    @Test fun toStringHidesTheToken() {
        assertThat(MediaConfig.parse("https://h.example", "geheim123")!!.toString()).doesNotContain("geheim123")
    }
}
