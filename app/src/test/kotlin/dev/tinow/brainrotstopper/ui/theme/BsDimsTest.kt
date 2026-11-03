package dev.tinow.brainrotstopper.ui.theme

import androidx.compose.ui.unit.Dp
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** BsDims must match theme-dims.json (generated from design/mockups/theme.css by tools/mockup/extract-dims.mjs). */
class BsDimsTest {
    private val dims: JsonObject = Json.parseToJsonElement(File("src/test/resources/theme-dims.json").readText()).jsonObject

    private fun px(selector: String, prop: String): Float =
        dims.getValue(selector).jsonObject.getValue(prop).jsonPrimitive.content.removeSuffix("px").toFloat()

    private fun eq(d: Dp, selector: String, prop: String) = assertThat(d.value).isEqualTo(px(selector, prop))

    @Test fun header() {
        eq(BsDims.HdrMinHeight, ".bs .hdr", "min-height")
        eq(BsDims.HdrPaddingBottom, ".bs .hdr", "padding-bottom")
    }

    @Test fun boxLabelOffsetIsNegative() = assertThat(-BsDims.BoxLabelOffset.value).isEqualTo(px(".bs .box > .lbl", "top"))

    @Test fun controls() {
        eq(BsDims.KvMinHeight, ".bs .kv", "min-height")
        eq(BsDims.BtnHeight, ".bs .btn", "min-height")
        eq(BsDims.GhostHeight, ".bs .btn.ghost", "min-height")
        eq(BsDims.RateHeight, ".bs .rate", "min-height")
        eq(BsDims.RowHeight, ".bs .row", "min-height")
        eq(BsDims.IconSize, ".bs .ico", "width")
        eq(BsDims.IconLarge, ".bs .ico.lg", "width")
        eq(BsDims.ImgPlaceholder, ".bs .img", "height")
        eq(BsDims.NavHeight, ".bs .nav a", "min-height")
        eq(BsDims.NavBottom, ".bs .nav", "padding-bottom")
        eq(BsDims.SwitchWidth, ".bs .sw", "width")
        eq(BsDims.SwitchHeight, ".bs .sw", "height")
        eq(BsDims.SwitchKnob, ".bs .sw span", "width")
        eq(BsDims.SwitchKnobInset, ".bs .sw span", "top")
        eq(BsDims.SwitchKnobOnLeft, ".bs .sw input:checked + span", "left")
        eq(BsDims.SearchHeight, ".bs .search", "min-height")
        eq(BsDims.StepHeight, ".bs .step button", "height")
        eq(BsDims.SegsHeight, ".bs .segs button", "min-height")
        eq(BsDims.OptHeight, ".bs .opt", "min-height")
        eq(BsDims.ChartHeight, ".bs .chart", "height")
        eq(BsDims.ChartGap, ".bs .chart", "gap")
        eq(BsDims.RankHeight, ".bs .rk", "min-height")
        eq(BsDims.RankNameWidth, ".bs .rk .n", "width")
        eq(BsDims.RankBarHeight, ".bs .rk .tr", "height")
        eq(BsDims.RankCountWidth, ".bs .rk .c", "width")
        eq(BsDims.SepGap, ".bs .sep", "gap")
        eq(BsDims.KvGap, ".bs .kv", "gap")
        eq(BsDims.BtnGap, ".bs .btn", "gap")
        eq(BsDims.RankGap, ".bs .rk", "gap")
        eq(BsDims.CursorMargin, ".bs .cur", "margin-left")
    }

    @Test fun paddings() {
        assertThat(dims.getValue(".bs .scr").jsonObject.getValue("padding").jsonPrimitive.content).isEqualTo("56px 24px 32px")
        assertThat(dims.getValue(".bs .main").jsonObject.getValue("padding").jsonPrimitive.content).isEqualTo("56px 24px 16px")
        assertThat(dims.getValue(".bs .box").jsonObject.getValue("padding").jsonPrimitive.content).isEqualTo("20px 16px 16px")
        assertThat(dims.getValue(".bs .stat").jsonObject.getValue("padding").jsonPrimitive.content).isEqualTo("10px 12px")
        assertThat(BsDims.ScrTop.value).isEqualTo(56f)
        assertThat(BsDims.ScrBottom.value).isEqualTo(32f)
        assertThat(BsDims.MainBottom.value).isEqualTo(16f)
        assertThat(BsDims.Margin.value).isEqualTo(24f)
        assertThat(BsDims.BoxPaddingTop.value).isEqualTo(20f)
        assertThat(BsDims.BoxPaddingSides.value).isEqualTo(16f)
        assertThat(BsDims.StatPadV.value).isEqualTo(10f)
        assertThat(BsDims.StatPadH.value).isEqualTo(12f)
    }
}
