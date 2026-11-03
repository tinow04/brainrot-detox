package dev.tinow.brainrotstopper.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Every colour token must equal the hex value in design/mockups/theme.css. */
class BsColorsTest {
    private fun tokens(block: String): Map<String, Long> {
        val css = File("../design/mockups/theme.css").readText()
        val body = Regex("""\.bs\.$block\s*\{([^}]*)\}""").find(css)!!.groupValues[1]
        return Regex("""--([a-z0-9-]+):\s*#([0-9a-fA-F]{6})""").findAll(body).associate { it.groupValues[1] to (0xFF000000 or it.groupValues[2].toLong(16)) }
    }

    private fun check(css: Map<String, Long>, c: BsColors) {
        val actual = mapOf(
            "bg" to c.bg, "bg2" to c.bg2, "fill" to c.fill, "line" to c.line, "ctl" to c.ctl, "text" to c.text,
            "muted" to c.muted, "dim" to c.dim, "accent" to c.accent, "on-accent" to c.onAccent,
            "red" to c.red, "orange" to c.orange, "green" to c.green, "blue" to c.blue,
        )
        assertThat(css.keys).containsExactlyElementsIn(actual.keys)
        for ((k, v) in actual) assertThat(v).isEqualTo(Color(css.getValue(k).toInt()))
    }

    @Test fun dark() = check(tokens("dark"), BsColors.Dark)

    @Test fun light() = check(tokens("light"), BsColors.Light)
}
