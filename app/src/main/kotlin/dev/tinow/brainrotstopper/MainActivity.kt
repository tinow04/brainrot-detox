package dev.tinow.brainrotstopper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.tinow.brainrotstopper.data.system.AppLog
import dev.tinow.brainrotstopper.data.system.PackageCatalog
import dev.tinow.brainrotstopper.data.system.PermissionStatusReader
import dev.tinow.brainrotstopper.service.AndroidProtectedSetProvider
import dev.tinow.brainrotstopper.ui.apps.AppsViewModel
import android.content.pm.ApplicationInfo
import dev.tinow.brainrotstopper.data.media.MediaHealthCheck
import dev.tinow.brainrotstopper.ui.config.ConfigViewModel
import dev.tinow.brainrotstopper.ui.main.MainApp
import dev.tinow.brainrotstopper.ui.setup.SetupViewModel
import dev.tinow.brainrotstopper.ui.theme.BsTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as BrainrotApp).container

    // the card source is read per check, so SET_SOURCE (debug) takes effect without a restart
    private val setup: SetupViewModel by viewModels {
        viewModelFactory { initializer { SetupViewModel { container.cardSource() } } }
    }

    private val apps: AppsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val catalog = PackageCatalog(application)
                AppsViewModel(catalog::launchable, AndroidProtectedSetProvider(application), container.settings) { AppLog.i("BS/Apps", it) }
            }
        }
    }

    // the card source is read per call (SET_SOURCE); cleartext only for the emulator host in debuggable builds (as MediaImages)
    private val config: ConfigViewModel by viewModels {
        viewModelFactory {
            initializer {
                val debuggable = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                val health = MediaHealthCheck()
                ConfigViewModel(
                    settings = container.settings,
                    source = { container.cardSource() },
                    mediaCheck = health::check,
                    cleartextHosts = if (debuggable) setOf("10.0.2.2") else emptySet(),
                ) { AppLog.i("BS/Config", it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reader = PermissionStatusReader(this) { container.debugFlags }
        val route = intent?.getStringExtra(EXTRA_ROUTE)
        setContent { BsTheme { MainApp(container, reader, setup, apps, config, route) } }
    }

    companion object {
        /** `--es route setup|apps|config` picks the first screen (scripts, and the lock screen's "deck missing" error). */
        const val EXTRA_ROUTE = "route"
    }
}
