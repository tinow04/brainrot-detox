package dev.tinow.brainrotstopper.lock

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.data.anki.CardDocument
import dev.tinow.brainrotstopper.data.anki.CardHtml
import dev.tinow.brainrotstopper.data.anki.CardSide
import dev.tinow.brainrotstopper.data.anki.CardStyle
import dev.tinow.brainrotstopper.debug.fake.CardFixtures
import dev.tinow.brainrotstopper.lock.render.CardView
import dev.tinow.brainrotstopper.lock.render.RendererSelector
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * M4-T3: the CSP really blocks (script, external image) in a WebView that has JavaScript and network switched ON, the
 * rendered text is the card text, colours are readable in both modes, and the renderers show the card.
 * JavaScript is only used here to look at the result; the production WebView has it off.
 */
class CardRenderingTest {
    @get:Rule
    val rule = createComposeRule()

    private val dark = CardStyle(true, "#cdd6f4", "#9399b2", "#cba6f7", "#45475a", "#313244")
    private val light = CardStyle(false, "#4c4f69", "#646880", "#8839ea", "#bcc0cc", "#dce0e8")

    private class Result(val values: Map<String, String>, val requests: List<String>)

    /** Loads [html] in a permissive WebView (JS on, network allowed) and evaluates each JS expression afterwards. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun inspect(html: String, vararg exprs: String): Result {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val requests = CopyOnWriteArrayList<String>()
        val out = HashMap<String, String>()
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val web = WebView(ctx)
            web.settings.javaScriptEnabled = true
            web.settings.blockNetworkLoads = false
            web.settings.blockNetworkImage = false
            web.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    requests += request.url.toString()
                    return null
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    val left = CountDownLatch(exprs.size)
                    exprs.forEach { e ->
                        view.evaluateJavascript(e) { v -> synchronized(out) { out[e] = v }; left.countDown() }
                    }
                    Thread { left.await(10, TimeUnit.SECONDS); done.countDown() }.start()
                }
            }
            web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue()
        return Result(out, requests)
    }

    private fun doc(key: String, style: CardStyle, side: CardSide = CardSide.Question) =
        CardHtml.build(CardFixtures.all.getValue(key), side, 0, style)

    @Test fun cspBlocksScriptAndExternalImageEvenWhenJsAndNetworkAreOn() {
        // the sanitiser would already strip these, so they are put in after it: this tests the CSP alone
        val html = doc("type", dark).html.replace(
            "<div id=\"bs\">",
            "<div id=\"bs\"><script>document.title='ran'</script><img id=ext src=\"http://example.invalid/x.png\" onerror=\"document.title='err'\">",
        )
        val r = inspect(html, "document.title", "document.getElementById('ext') != null")
        assertThat(r.values["document.title"]).isEqualTo("\"\"") // neither the script nor onerror ran
        assertThat(r.requests.filter { it.startsWith("http") }).isEmpty()
    }

    @Test fun controlWithoutCspTheScriptRuns() {
        // proves the test setup: with the CSP meta removed the same injection does run
        val html = doc("type", dark).html
            .replace(Regex("<meta http-equiv=\"Content-Security-Policy\"[^>]*>"), "")
            .replace("<div id=\"bs\">", "<div id=\"bs\"><script>document.title='ran'</script>")
        assertThat(inspect(html, "document.title").values["document.title"]).isEqualTo("\"ran\"")
    }

    @Test fun hostileFixtureNeverRunsOrLoads() {
        val r = inspect(doc("hostile", dark).html, "document.title", "document.body.innerText.indexOf('SICHTBAR')")
        assertThat(r.values["document.title"]).isEqualTo("\"\"")
        assertThat(r.values["document.body.innerText.indexOf('SICHTBAR')"]).isNotEqualTo("-1")
        assertThat(r.requests.filter { it.startsWith("http") }).isEmpty()
    }

    @Test fun renderedTextIsTheCardTextInEveryFixture() {
        for ((key, card) in CardFixtures.all) {
            for (side in CardSide.entries) {
                val d = CardHtml.build(card, side, 0, dark)
                val r = inspect(d.html, "document.getElementById('bs').innerText")
                val text = r.values.getValue("document.getElementById('bs').innerText").trim('"')
                    .replace("\\n", "\n").replace("\\\"", "\"").replace("\\u003C", "<")
                // every non-empty plain-text line of the card must be on the page (lists: marker "- " is ours, not the page's)
                d.plainText.lines().map { it.removePrefix("- ").trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    assertThat(text.replace(Regex("\\s+"), " ")).contains(line.replace(Regex("\\s+"), " ").take(40))
                }
                assertThat(r.requests.filter { it.startsWith("http") }).isEmpty()
            }
        }
    }

    @Test fun hardCodedCardColoursDoNotBreakDarkOrLightMode() {
        for ((style, expectText) in listOf(dark to "rgb(205, 214, 244)", light to "rgb(76, 79, 105)")) {
            val d = doc("colors", style)
            val expr = "getComputedStyle(document.querySelector('#bs font')).color"
            val bg = "getComputedStyle(document.querySelector('#bs span')).backgroundColor"
            val r = inspect(d.html, expr, bg, "getComputedStyle(document.body).backgroundColor")
            assertThat(r.values[expr]).isEqualTo("\"$expectText\"")
            assertThat(r.values[bg]).isEqualTo("\"rgba(0, 0, 0, 0)\"") // no white patch on the dark background
            assertThat(r.values["getComputedStyle(document.body).backgroundColor"]).isEqualTo("\"rgba(0, 0, 0, 0)\"")
        }
    }

    @Test fun clozeIsHighlightedWithAccentColour() {
        val r = inspect(doc("cloze", dark).html, "getComputedStyle(document.querySelector('.cloze')).color")
        assertThat(r.values.values.single()).isEqualTo("\"rgb(203, 166, 247)\"")
    }

    @Test fun inlinedImageIsShownAsImage() {
        val d = CardHtml.build(CardFixtures.all.getValue("inline"), CardSide.Question, 0, dark, mapOf("rot.png" to CardFixtures.RED_PNG))
        val r = inspect(d.html, "document.images.length", "document.images[0].naturalWidth")
        assertThat(r.values["document.images.length"]).isEqualTo("1")
        assertThat(r.values["document.images[0].naturalWidth"]).isEqualTo("8")
    }

    private fun show(d: CardDocument, plain: Boolean) {
        rule.setContent { BsTheme(dark = true) { CardView(d, forcePlain = plain) } }
    }

    @Test fun webViewRendererShowsTheCard() {
        RendererSelector.reset()
        show(doc("html", dark), plain = false)
        rule.onNodeWithTag("card-web").assertIsDisplayed()
    }

    @Test fun webViewGrowsToItsContentAndShrinksForTheNextCard() {
        RendererSelector.reset()
        val long = doc("long", dark)
        val short = doc("html", dark)
        val current = androidx.compose.runtime.mutableStateOf(long)
        rule.setContent { BsTheme(dark = true) { CardView(current.value) } }
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("card-web")).fetchSemanticsNodes().firstOrNull()?.size?.height?.let { it > 2000 } == true }
        current.value = short
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("card-web")).fetchSemanticsNodes().firstOrNull()?.size?.height?.let { it < 400 } == true }
    }

    @Test fun plainRendererShowsTheCard() {
        show(doc("html", dark), plain = true)
        rule.onNodeWithTag("card-plain").assertIsDisplayed()
    }

    @Test fun brokenWebViewFallsBackToPlainText() {
        RendererSelector.markWebViewBroken()
        try {
            show(doc("html", dark), plain = false)
            rule.onNodeWithTag("card-plain").assertIsDisplayed()
        } finally {
            RendererSelector.reset()
        }
    }
}
