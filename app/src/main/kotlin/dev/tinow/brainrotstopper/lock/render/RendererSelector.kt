package dev.tinow.brainrotstopper.lock.render

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decides between WebView and plain text. A WebView that failed once (missing WebView package, creation exception) is not
 * tried again in this process: the fallback renderer takes over for the rest of the session.
 */
object RendererSelector {
    private val broken = AtomicBoolean(false)

    fun webViewUsable(): Boolean = !broken.get()

    fun markWebViewBroken() {
        broken.set(true)
    }

    /** Tests only. */
    fun reset() {
        broken.set(false)
    }
}
