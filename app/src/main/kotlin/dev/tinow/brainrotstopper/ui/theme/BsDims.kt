package dev.tinow.brainrotstopper.ui.theme

import androidx.compose.ui.unit.dp

/** Dimensions from design/mockups/theme.css (BsDimsTest compares them with theme-dims.json). CSS class in the comment. */
object BsDims {
    // spacing grid
    val S4 = 4.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val S24 = 24.dp
    val S32 = 32.dp
    val Margin = 24.dp
    val Radius = 4.dp
    val Border = 1.dp
    val TouchMin = 48.dp

    // .scr / .main padding: top 56, sides 24, bottom 32 (scr) / 16 (main)
    val ScrTop = 56.dp
    val ScrBottom = 32.dp
    val MainBottom = 16.dp

    val HdrMinHeight = 36.dp // .hdr
    val HdrPaddingBottom = 12.dp
    val BoxPaddingTop = 20.dp // .box padding: 20 16 16
    val BoxPaddingSides = 16.dp
    val BoxLabelOffset = 11.dp // .box > .lbl top: -11
    val BoxLabelIndent = 12.dp
    val BoxLabelPadding = 8.dp
    val KvMinHeight = 30.dp // .kv
    val BtnHeight = 52.dp // .btn
    val GhostHeight = 48.dp // .btn.ghost
    val RateHeight = 84.dp // .rate
    val RatePadV = 12.dp
    val RatePadH = 16.dp
    val RowHeight = 60.dp // .row
    val IconSize = 40.dp // .ico
    val IconLarge = 64.dp // .ico.lg
    val ImgPlaceholder = 96.dp // .img
    val NavHeight = 56.dp // .nav a
    val NavBottom = 16.dp // .nav padding-bottom
    val SwitchWidth = 48.dp // .sw
    val SwitchHeight = 28.dp
    val SwitchKnob = 16.dp
    val SearchHeight = 48.dp // .search
    val StepHeight = 48.dp // .step button
    val SegsHeight = 48.dp // .segs button
    val OptHeight = 48.dp // .opt
    val ChartHeight = 150.dp // .chart
    val RankHeight = 34.dp // .rk
    val RankNameWidth = 84.dp
    val RankBarHeight = 10.dp
    val RankCountWidth = 24.dp
    val ChartBarMin = 4.dp
    val SwitchKnobInset = 5.dp // .sw span top/left
    val SwitchKnobOnLeft = 27.dp // .sw input:checked + span
    val StatPadV = 10.dp // .stat padding 10 12
    val StatPadH = 12.dp
    val SepGap = 10.dp // .sep
    val KvGap = 12.dp // .kv, .row
    val BtnGap = 8.dp // .btn
    val ChartGap = 8.dp // .chart
    val RankGap = 10.dp // .rk
    val CursorMargin = 3.dp // .cur
}
