package dev.tinow.brainrotstopper.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.tinow.brainrotstopper.debug.ScreenSamples
import dev.tinow.brainrotstopper.ui.components.LocalCursorBlink
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import org.junit.Rule
import org.junit.Test

/** At font scale 2.0 the main action of every lock screen must stay visible and at least 48 dp high (T5 acceptance). */
class FontScaleTest {
    @get:Rule
    val rule = createComposeRule()

    private fun check(screen: String, button: String) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f), LocalCursorBlink provides false) {
                BsTheme(dark = true) { ScreenSamples.Screen(screen) }
            }
        }
        rule.onNodeWithText(button, substring = true).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test fun intro() = check("a1", Strings.BTN_START)
    @Test fun introResume() = check("a1-resume", Strings.BTN_CONTINUE)
    @Test fun question() = check("a2", Strings.BTN_SHOW_ANSWER)
    @Test fun answerGood() = check("a3", Strings.RATE_GOOD)
    @Test fun feedback() = check("a4", Strings.BTN_CONTINUE)
    @Test fun unlocked() = check("a5", Strings.openCommand("instagram"))
    @Test fun waiting() = check("waiting", Strings.BTN_CANCEL)
    @Test fun errorAction() = check("error-unreachable", Strings.ERR_UNREACHABLE.action)
    @Test fun setup() = check("setup", Strings.BTN_ALLOW)
}
