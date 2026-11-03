package dev.tinow.brainrotstopper.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.tinow.brainrotstopper.ui.components.LocalCursorBlink
import dev.tinow.brainrotstopper.ui.theme.BsTheme

/**
 * Debug gallery: `adb shell am start -n dev.tinow.brainrotstopper/.debug.ComponentGalleryActivity --es screen components [--ez blink false] [--ez bars true]`.
 * Theme follows the system (`cmd uimode night yes|no`). `screen` is one of [ScreenSamples.names].
 */
class ComponentGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "components"
        val blink = intent.getBooleanExtra("blink", false)
        // The mockups have no system bars; hide them so screenshots compare 1:1 (`--ez bars true` keeps them).
        if (!intent.getBooleanExtra("bars", false)) {
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            CompositionLocalProvider(LocalCursorBlink provides blink) {
                BsTheme { render(screen) }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun render(screen: String) {
        ScreenSamples.Screen(screen)
    }
}
