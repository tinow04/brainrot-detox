package dev.tinow.brainrotstopper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.tinow.brainrotstopper.data.system.PermissionStatusReader
import dev.tinow.brainrotstopper.ui.main.MainApp
import dev.tinow.brainrotstopper.ui.theme.BsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BrainrotApp).container
        val reader = PermissionStatusReader(this) { container.debugFlags }
        setContent { BsTheme { MainApp(container, reader) } }
    }
}
