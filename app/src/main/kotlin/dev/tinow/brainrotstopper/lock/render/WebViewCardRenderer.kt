package dev.tinow.brainrotstopper.lock.render

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.tinow.brainrotstopper.data.anki.CardDocument
import dev.tinow.brainrotstopper.data.system.AppLog
import java.io.ByteArrayInputStream

/**
 * Card HTML in a locked-down WebView: no JavaScript, no network, no file or content access, links ignored, every request
 * except the document itself answered with an empty response. The document also carries a CSP (`CardHtml`).
 * The view is invisible until the first frame is drawn and then sized to its content, so the card scrolls with its
 * parent instead of inside the WebView.
 */
@Composable
fun WebViewCardRenderer(doc: CardDocument, modifier: Modifier = Modifier, onFailure: () -> Unit = {}) {
    val density = LocalDensity.current.density
    var heightPx by remember { mutableIntStateOf(0) }
    val minHeight = 24.dp
    AndroidView(
        modifier = modifier.fillMaxWidth().height(maxOf(minHeight, with(LocalDensity.current) { heightPx.toDp() })).testTag("card-web"),
        factory = { ctx ->
            try {
                createLockedWebView(ctx)
            } catch (e: Throwable) { // no WebView package, creation failure: fall back to plain text
                AppLog.w("Render", "webview unavailable: ${e.javaClass.simpleName}")
                onFailure()
                View(ctx)
            }
        },
        update = { v ->
            val web = v as? WebView ?: return@AndroidView
            if (web.tag == doc.html) return@AndroidView // same document: no reload
            web.tag = doc.html
            heightPx = 0 // contentHeight never reports less than the view, so a shorter next card needs a fresh start
            web.visibility = View.INVISIBLE
            val client = object : LockedClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    // the visual state callback fires after the content was drawn, so contentHeight is final
                    view.postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            view.visibility = View.VISIBLE
                            // the first value can still be too small (layout not finished): measure again a few times
                            fun measure(attempt: Int) {
                                if (view.tag != doc.html) return
                                val h = (view.contentHeight * density).toInt() + 1
                                if (h != heightPx) heightPx = h
                                if (attempt < MEASURE_ATTEMPTS) view.postDelayed({ measure(attempt + 1) }, MEASURE_INTERVAL_MS)
                            }
                            measure(0)
                        }
                    })
                }
            }
            web.webViewClient = client
            web.loadDataWithBaseURL(null, doc.html, "text/html", "utf-8", null)
        },
        onRelease = { v -> (v as? WebView)?.let { it.stopLoading(); it.destroy() } },
    )
}

private const val MEASURE_ATTEMPTS = 10
private const val MEASURE_INTERVAL_MS = 100L

private open class LockedClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true // links do nothing

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        // the document comes through loadData (no request); anything else is empty, also data: images are decoded inline
        if (request.url.scheme == "data") null else WebResourceResponse("text/plain", "utf-8", 404, "blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit
}

@SuppressLint("SetJavaScriptEnabled")
internal fun createLockedWebView(ctx: android.content.Context): WebView = WebView(ctx).apply {
    setBackgroundColor(0)
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    isLongClickable = false
    setOnLongClickListener { true }
    isHapticFeedbackEnabled = false
    settings.apply {
        javaScriptEnabled = false
        blockNetworkLoads = true
        blockNetworkImage = false // data: images are not network
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = false
        setGeolocationEnabled(false)
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        safeBrowsingEnabled = false // no lookups of loaded URLs at Google
        cacheMode = WebSettings.LOAD_NO_CACHE
        mediaPlaybackRequiresUserGesture = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
}
