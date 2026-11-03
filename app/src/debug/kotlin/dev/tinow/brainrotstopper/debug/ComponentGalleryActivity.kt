package dev.tinow.brainrotstopper.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.tinow.brainrotstopper.ui.components.ComponentsShowcase
import dev.tinow.brainrotstopper.ui.components.LocalCursorBlink
import dev.tinow.brainrotstopper.ui.theme.BsTheme

/**
 * Debug gallery: `adb shell am start -n dev.tinow.brainrotstopper/.debug.ComponentGalleryActivity --es screen components [--ez blink false]`.
 * Theme follows the system (`cmd uimode night yes|no`). Screens are added to [render] as they are built.
 */
class ComponentGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "components"
        val blink = intent.getBooleanExtra("blink", false)
        setContent {
            CompositionLocalProvider(LocalCursorBlink provides blink) {
                BsTheme { render(screen) }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun render(screen: String) {
        when (screen) {
            else -> ComponentsShowcase()
        }
    }
}
