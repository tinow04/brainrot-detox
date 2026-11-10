package dev.tinow.brainrotstopper.lock.render

import androidx.compose.runtime.mutableStateOf

/**
 * Decides between WebView and plain text. A WebView that failed once (missing WebView package, creation exception, a
 * card that crashed or killed the renderer process) is not tried again in this process: the fallback renderer takes over
 * for the rest of the session. Snapshot state, so a card on screen switches to plain text as soon as it is marked.
 */
object RendererSelector {
    private val broken = mutableStateOf(false)

    fun webViewUsable(): Boolean = !broken.value

    fun markWebViewBroken() {
        broken.value = true
    }

    /** Tests only. */
    fun reset() {
        broken.value = false
    }
}
