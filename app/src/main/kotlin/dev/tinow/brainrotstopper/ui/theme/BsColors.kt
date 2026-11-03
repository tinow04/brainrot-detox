package dev.tinow.brainrotstopper.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour tokens of the terminal theme (design/mockups/theme.css `.bs.dark` / `.bs.light`; BsColorsTest compares them). */
@Immutable
data class BsColors(
    val bg: Color,
    val bg2: Color,
    val fill: Color,
    val line: Color,
    val ctl: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    val red: Color,
    val orange: Color,
    val green: Color,
    val blue: Color,
) {
    companion object {
        /** Catppuccin Mocha. */
        val Dark = BsColors(
            bg = Color(0xFF1E1E2E), bg2 = Color(0xFF181825), fill = Color(0xFF313244), line = Color(0xFF45475A),
            ctl = Color(0xFF7F849C), text = Color(0xFFCDD6F4), muted = Color(0xFFBAC2DE), dim = Color(0xFF9399B2),
            accent = Color(0xFFCBA6F7), onAccent = Color(0xFF11111B),
            red = Color(0xFFF38BA8), orange = Color(0xFFFAB387), green = Color(0xFFA6E3A1), blue = Color(0xFF89B4FA),
        )

        /** Catppuccin Latte with darkened text/semantic colours for contrast. */
        val Light = BsColors(
            bg = Color(0xFFEFF1F5), bg2 = Color(0xFFE6E9EF), fill = Color(0xFFDCE0E8), line = Color(0xFFBCC0CC),
            ctl = Color(0xFF7C7F93), text = Color(0xFF4C4F69), muted = Color(0xFF5C5F77), dim = Color(0xFF646880),
            accent = Color(0xFF8839EF), onAccent = Color(0xFFEFF1F5),
            red = Color(0xFFD20F39), orange = Color(0xFFB04A00), green = Color(0xFF2A7A16), blue = Color(0xFF1A56D6),
        )
    }
}
