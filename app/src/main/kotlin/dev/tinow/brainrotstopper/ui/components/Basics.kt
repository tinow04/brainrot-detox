package dev.tinow.brainrotstopper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tinow.brainrotstopper.debug.debugBounds
import dev.tinow.brainrotstopper.ui.Span
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import kotlinx.coroutines.delay

/** Tests switch cursor blinking off (stable screenshots); production reads container.debugFlags. */
val LocalCursorBlink = compositionLocalOf { true }

private val Shape = RoundedCornerShape(BsDims.Radius)

@Composable
fun BsText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip,
    )
}

fun spansToAnnotated(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        if (s.bold) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(s.text)
        if (s.bold) pop()
    }
}

/** Running text with `<b>` emphasis from [Span]s. */
@Composable
fun BsRichText(spans: List<Span>, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(text = spansToAnnotated(spans), modifier = modifier, style = style.copy(color = color))
}

/** `.cur`: blinking block cursor, 0.6em x 1.15em, steps(1) 1.1 s. Hidden from accessibility. */
@Composable
fun BlinkCursor(color: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp) {
    var on by remember { mutableStateOf(true) }
    val blink = LocalCursorBlink.current
    if (blink) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(550)
                on = !on
            }
        }
    }
    val (w, h) = with(LocalDensity.current) { (fontSize * 0.6f).toDp() to (fontSize * 1.15f).toDp() }
    Box(
        modifier
            .padding(start = BsDims.CursorMargin)
            .size(w, h)
            .alpha(if (on) 1f else 0f)
            .drawBehind { drawRect(color) }
            .clearAndSetSemantics { },
    )
}

enum class BsButtonStyle { Primary, Ghost }

/** `.btn`, `.btn.primary`, `.btn.ghost`; min height 52/48 dp. */
@Composable
fun BsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BsButtonStyle = BsButtonStyle.Primary,
    cursor: Boolean = false,
    enabled: Boolean = true,
) {
    val c = BsTheme.colors
    val t = BsTheme.type
    val primary = style == BsButtonStyle.Primary
    val fg = if (primary) c.onAccent else if (style == BsButtonStyle.Ghost) c.muted else c.text
    Box(
        modifier
            .fillMaxWidth()
            .debugBounds(text)
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = if (primary) BsDims.BtnHeight else BsDims.GhostHeight)
            .background(if (primary) c.accent else Color.Transparent, Shape)
            .border(BsDims.Border, if (primary) c.accent else if (style == BsButtonStyle.Ghost) Color.Transparent else c.ctl, Shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = BsDims.S16),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BsDims.BtnGap)) {
            BsText(text, if (primary) t.button else t.ghost, fg)
            if (cursor) BlinkCursor(c.onAccent)
        }
    }
}

enum class BsTagKind { Ok, Fail, Warn, Info }

@Composable
fun BsTagColor(kind: BsTagKind): Color = when (kind) {
    BsTagKind.Ok -> BsTheme.colors.green
    BsTagKind.Fail -> BsTheme.colors.red
    BsTagKind.Warn -> BsTheme.colors.orange
    BsTagKind.Info -> BsTheme.colors.blue
}

/** `.tag` + colour class: `[ OK ]`, `[ FAIL ]`, `[ LOCK ]`, `[ WARN ]`, `[ WAIT ]`, `[ OFFEN ]`, `[ FEHLER ]`. */
@Composable
fun BsTag(text: String, kind: BsTagKind, modifier: Modifier = Modifier, style: TextStyle = BsTheme.type.tag) {
    BsText(text, style, BsTagColor(kind), modifier)
}

/** `.hdr`: left meta text, right text (progress or file name), bottom border. */
@Composable
fun BsHeader(left: String, right: String, modifier: Modifier = Modifier, rightDescription: String? = null) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BsDims.HdrMinHeight)
            .drawBehind { drawRect(c.line, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
            .padding(bottom = BsDims.HdrPaddingBottom),
        horizontalArrangement = Arrangement.spacedBy(BsDims.S8, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BsText(left, t.meta, c.dim, Modifier.weight(1f, fill = true), maxLines = 1)
        BsText(
            right,
            if (rightDescription != null) t.progress else t.meta,
            if (rightDescription != null) c.text else c.dim,
            Modifier.clearAndSetSemanticsIf(rightDescription),
            maxLines = 1,
        )
    }
}

private fun Modifier.clearAndSetSemanticsIf(description: String?): Modifier =
    if (description == null) this else this.clearAndSetSemantics { contentDescription = description }

/** `.box`: bordered box with the label notched into the top edge. */
@Composable
fun BsBox(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(BsDims.Border, c.line)
                .padding(top = BsDims.BoxPaddingTop, start = BsDims.BoxPaddingSides, end = BsDims.BoxPaddingSides, bottom = BsDims.BoxPaddingSides),
            content = content,
        )
        BsText(
            label,
            t.label.copy(lineHeight = 20.sp),
            c.dim,
            Modifier
                .offset(x = BsDims.BoxLabelIndent, y = -BsDims.BoxLabelOffset)
                .background(c.bg)
                .padding(horizontal = BsDims.BoxLabelPadding),
        )
    }
}

/** `.kv`: key (dim) left, value right. */
@Composable
fun BsKv(key: String, value: String, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Row(
        modifier.fillMaxWidth().heightIn(min = BsDims.KvMinHeight),
        horizontalArrangement = Arrangement.spacedBy(BsDims.KvGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BsText(key, t.body, c.dim)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { BsText(value, t.body, c.text) }
    }
}

/** `.sep`: centred dim label between two hairlines. */
@Composable
fun BsSep(text: String, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BsDims.SepGap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(BsDims.Border).background(c.line))
        BsText(text, BsTheme.type.label, c.dim)
        Box(Modifier.weight(1f).height(BsDims.Border).background(c.line))
    }
}
