package dev.tinow.brainrotstopper.lock.render

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.tinow.brainrotstopper.data.anki.CardDocument
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.theme.BsTheme

/** Fallback and second renderer: the card as plain text in the terminal font (no styling, no images). */
@Composable
fun PlainTextCardRenderer(doc: CardDocument, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val c = BsTheme.colors
    val t = BsTheme.type
    BsText(
        doc.plainText,
        if (dimmed) t.cardSmall else t.card,
        if (dimmed) c.muted else c.text,
        modifier.fillMaxWidth().testTag("card-plain"),
    )
}
