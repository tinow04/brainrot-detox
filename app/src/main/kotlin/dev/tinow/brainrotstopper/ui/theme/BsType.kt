package dev.tinow.brainrotstopper.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.tinow.brainrotstopper.R

val BsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * CSS gives every line box the full `line-height` (half-leading above the first and below the last line), also for a
 * single line. Compose's default `Trim.Both` cuts that off, so a one-line text is only as high as the font (verified:
 * a one-line 22 px stat measured 29 dp, `lineHeight = 60.sp` changed nothing). `Trim.None` + `Center` matches CSS.
 */
val ExactLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Int, lineHeightFactor: Float, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified) =
    TextStyle(
        fontFamily = BsMono,
        fontSize = size.sp,
        lineHeight = (size * lineHeightFactor).sp,
        lineHeightStyle = ExactLineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontWeight = weight,
        letterSpacing = letterSpacing,
    )

/** Type scale (sp) from theme.css: 12 label, 13 meta, 14 body, 16 button, 20 card, 22 title, 28 hero. */
@Immutable
class BsType(val tuning: Unit = Unit) {
    val hero = style(28, 1.25f, FontWeight.Bold) // .t-hero
    val heroSmall = style(20, 1.25f, FontWeight.Bold) // hero for required > 10
    val title = style(22, 1.3f, FontWeight.Bold) // .t-title
    val card = style(20, 1.45f) // .t-card
    val cardSmall = style(15, 1.5f) // dimmed question on the answer side (A3 inline 15px)
    val body = style(14, 1.5f)
    val button = style(16, 1.5f, FontWeight.Bold) // .btn
    val ghost = style(14, 1.5f, FontWeight.Medium) // .btn.ghost
    val meta = style(13, 1.5f) // .hdr, .nav a, .rk
    val navOn = style(13, 1.5f, FontWeight.Bold)
    val progress = style(14, 1.5f, FontWeight.Bold, 0.04.em) // .hdr .prog
    val label = style(12, 1.4f) // .t-sm, .lbl, .sep, .st, .iv
    val stat = style(22, 1.25f, FontWeight.Bold) // .stat .v
    val tag = style(14, 1.5f, FontWeight.Bold)
    val rateKey = style(16, 1.5f, FontWeight.Bold) // .rate .k
    val input = style(16, 1.5f)
}
