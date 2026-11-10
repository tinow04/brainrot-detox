package dev.tinow.brainrotstopper.ui.components

import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tinow.brainrotstopper.debug.debugBounds
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

private val Shape = RoundedCornerShape(BsDims.Radius)

enum class RateKind { Again, Hard, Good, Easy }

/** `.rate`: rating button, 84 dp high, border and 10 % tint in the semantic colour, optional next interval. */
@Composable
fun RateButton(text: String, interval: String?, kind: RateKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    val color = when (kind) {
        RateKind.Again -> c.red
        RateKind.Hard -> c.orange
        RateKind.Good -> c.green
        RateKind.Easy -> c.blue
    }
    Column(
        modifier
            .debugBounds(text)
            .heightIn(min = BsDims.RateHeight)
            .background(color.copy(alpha = 0.10f), Shape)
            .border(BsDims.Border, color, Shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = BsDims.RatePadH + BsDims.Border, vertical = BsDims.RatePadV + BsDims.Border),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        BsText(text, t.rateKey, color)
        if (!interval.isNullOrEmpty()) BsText(interval, t.label, c.dim)
    }
}

object AppInitials {
    private val known = mapOf(
        "instagram" to "ig", "tiktok" to "tt", "youtube" to "yt", "reddit" to "rd",
        "chrome" to "cr", "whatsapp" to "wa", "spotify" to "sp", "maps" to "ma",
    )

    /** Two letters for the icon placeholder: known apps from the mockups, otherwise the first two letters of the label. */
    fun of(label: String): String {
        val l = Strings.appLabel(label.trim())
        return known[l] ?: l.filter { it.isLetterOrDigit() }.take(2).ifEmpty { "??" }
    }
}

/** `.ico` / `.ico.lg`: bordered square with two letters. */
@Composable
fun AppIconInitials(label: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val c = BsTheme.colors
    val t = BsTheme.type
    val size: Dp = if (large) BsDims.IconLarge else BsDims.IconSize
    Box(
        modifier.size(size).border(BsDims.Border, c.ctl, Shape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        BsText(AppInitials.of(label), (if (large) t.card else t.meta).copy(fontWeight = FontWeight.Bold), c.muted)
    }
}

/** `.img`: dashed placeholder with 45 degree hatching, shown instead of a card image. */
@Composable
fun BsImagePlaceholder(text: String, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(BsDims.ImgPlaceholder)
            .drawBehind {
                val step = 9.dp.toPx()
                clipRect {
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(c.fill, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                        x += step
                    }
                }
                drawRect(c.ctl, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            }
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) { BsText(text, BsTheme.type.label, c.dim) }
}

/** Banner above the tabs: tappable one-liner in a status colour (border and text). */
@Composable
fun BsBanner(text: String, kind: BsTagKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = BsTagColor(kind)
    Box(
        modifier
            .fillMaxWidth()
            .debugBounds("banner")
            .heightIn(min = BsDims.TouchMin)
            .border(BsDims.Border, color, Shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = BsDims.S12 + BsDims.Border, vertical = BsDims.S8 + BsDims.Border),
        contentAlignment = Alignment.CenterStart,
    ) { BsText(text, BsTheme.type.meta.copy(fontWeight = FontWeight.Bold), color) }
}

/** `.row` (B1): app icon, label and switch as one touch target; blocked rows have the `fill` background. */
@Composable
fun AppRow(label: String, blocked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .debugBounds("row-$label")
            .heightIn(min = BsDims.RowHeight)
            .background(if (blocked) c.fill else Color.Transparent)
            .drawBehind { drawRect(c.line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
            .toggleable(value = blocked, role = Role.Switch, onValueChange = onToggle)
            .semantics { contentDescription = Strings.rowDescription(label, blocked) }
            .padding(horizontal = BsDims.S12, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(BsDims.KvGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconInitials(label)
        BsText(Strings.appLabel(label), t.button.copy(fontWeight = FontWeight.Normal), c.text, Modifier.weight(1f), maxLines = 1)
        BsSwitch(checked = blocked, onCheckedChange = null)
    }
}

/**
 * Compose never makes a first line smaller than the font (1.32 em), CSS does: value 22 px * 1.25 = 27.5 (Compose: 29 dp)
 * and label 12 px * 1.3 = 15.6 (Compose: 16 dp). Cuts [trim] off the text height and centres the glyphs in what is left.
 */
private fun Modifier.trimFirstLine(trim: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val t = trim.roundToPx()
    layout(placeable.width, (placeable.height - t).coerceAtLeast(0)) { placeable.place(0, -t / 2) }
}

/** `.stat`: bordered tile with a big value and a dim label. */
@Composable
fun BsStatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Column(modifier.border(BsDims.Border, c.line).padding(horizontal = BsDims.StatPadH + BsDims.Border, vertical = BsDims.StatPadV + BsDims.Border)) {
        BsText(value, t.stat, c.text, Modifier.trimFirstLine(1.5.dp))
        // `.stat .l` line-height 1.3. Compose does not break after "-" here ("richtig-quote" split mid-word), so offer a break point.
        BsText(label.replace("-", "-\u200B"), t.label.copy(lineHeight = 15.6.sp), c.dim, Modifier.trimFirstLine(0.75.dp)) // 0.4 dp overhang + rounding of the multi-line labels, tuned against the reference lines
    }
}

/** [heightDp] from StatsAggregator (2 dp for 0); null = computed here from the maximum (gallery samples). */
data class ChartBar(val label: String, val value: Int, val heightDp: Float? = null)

/** `.chart`: bar chart for the last days; the last bar (today) is emphasised. Bar height = value / max * 90 dp (min 4 dp for values > 0). */
@Composable
fun BsBarChart(bars: List<ChartBar>, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    val chartLabel = t.label.copy(lineHeight = 18.sp) // `.chart .c` has font-size 12 but no `.t-sm`: line-height 1.5 inherited
    val max = bars.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Row(
        modifier.fillMaxWidth().height(BsDims.ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(BsDims.ChartGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { i, bar ->
            val today = i == bars.lastIndex
            val h = bar.heightDp?.dp ?: if (bar.value <= 0) 0.dp else (bar.value.toFloat() / max * 90f).dp.coerceAtLeast(BsDims.ChartBarMin)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(BsDims.S4, Alignment.Bottom)) {
                BsText(bar.value.toString(), if (today) chartLabel.copy(fontWeight = FontWeight.Bold) else chartLabel.copy(fontWeight = FontWeight.Medium), if (today) c.text else c.muted)
                Box(Modifier.fillMaxWidth().height(h).background(c.accent.copy(alpha = if (today) 1f else 0.45f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                BsText(bar.label, if (today) chartLabel.copy(fontWeight = FontWeight.Bold) else chartLabel, if (today) c.text else c.dim)
            }
        }
    }
}

/** `.rk`: ranking row, bar width relative to the maximum (fraction 0..1). */
@Composable
fun BsRankRow(name: String, fraction: Float, count: Int, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Row(
        modifier.fillMaxWidth().heightIn(min = BsDims.RankHeight),
        horizontalArrangement = Arrangement.spacedBy(BsDims.RankGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BsText(name, t.meta, c.text, Modifier.width(BsDims.RankNameWidth), maxLines = 1)
        Box(Modifier.weight(1f).height(BsDims.RankBarHeight).background(c.fill)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(BsDims.RankBarHeight).background(c.accent))
        }
        BsText(count.toString(), t.meta.copy(textAlign = TextAlign.End), c.muted, Modifier.width(BsDims.RankCountWidth), maxLines = 1)
    }
}
