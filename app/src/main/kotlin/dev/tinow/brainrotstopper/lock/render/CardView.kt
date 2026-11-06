package dev.tinow.brainrotstopper.lock.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.tinow.brainrotstopper.data.anki.CardDocument
import dev.tinow.brainrotstopper.data.anki.CardStyle
import dev.tinow.brainrotstopper.ui.theme.BsColors
import dev.tinow.brainrotstopper.ui.theme.BsTheme

/** CSS colours of the current theme for `CardHtml.build`. */
@Composable
fun rememberCardStyle(): CardStyle {
    val c = BsTheme.colors
    return CardStyle(
        isDark = c.bg == BsColors.Dark.bg,
        text = c.text.css(), dim = c.dim.css(), accent = c.accent.css(), line = c.line.css(), fill = c.fill.css(),
    )
}

private fun Color.css(): String = "#%06x".format(toArgb() and 0xFFFFFF)

/**
 * Shows one card side: WebView for the HTML document, or the plain-text renderer when the WebView is unavailable or
 * [forcePlain] is set. Never throws; the card stays rateable either way (architecture.md 5).
 *
 * @param dimmed the question above the answer (A3) is smaller and dim; only the plain-text renderer needs to know, the
 * HTML document carries it already.
 */
@Composable
fun CardView(doc: CardDocument, modifier: Modifier = Modifier, dimmed: Boolean = false, forcePlain: Boolean = false) {
    if (forcePlain || !RendererSelector.webViewUsable()) {
        PlainTextCardRenderer(doc, modifier, dimmed)
    } else {
        WebViewCardRenderer(doc, modifier, onFailure = RendererSelector::markWebViewBroken)
    }
}
