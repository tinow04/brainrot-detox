package dev.tinow.brainrotstopper.lock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.tinow.brainrotstopper.BrainrotApp
import dev.tinow.brainrotstopper.domain.gate.GateSnapshot
import dev.tinow.brainrotstopper.domain.runtime.RuntimeOps
import dev.tinow.brainrotstopper.lock.screens.IntroScreen
import dev.tinow.brainrotstopper.lock.screens.IntroState
import dev.tinow.brainrotstopper.lock.screens.LoadingScreen
import dev.tinow.brainrotstopper.service.LockUiBeacon
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Lock shell (M3-T3). Started by the gate with `EXTRA_TARGET_PKG`. It reports itself through [LockUiBeacon] (launch ack),
 * checks first whether the lock is still needed, shows the intro (A1) from stored state only (no AnkiDroid access) and
 * leaves through HOME on cancel, back and gesture. The quiz itself follows in M4 (`QuizEngine`): until then [start]
 * only shows the loading screen.
 */
class LockActivity : ComponentActivity() {
    private sealed interface Ui {
        /** Resume check running; shows only the background so nothing flashes. */
        data object Blank : Ui
        data class Intro(val state: IntroState) : Ui
        data class Working(val app: String) : Ui
    }

    private val container get() = (application as BrainrotApp).container
    private var ui: Ui by mutableStateOf(Ui.Blank)
    private var targetPkg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Full-screen like the mockups (they show no system bars); a swipe from the edge shows them briefly.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })
        setContent {
            BsTheme {
                when (val current = ui) {
                    Ui.Blank -> Box(Modifier.fillMaxSize().background(BsTheme.colors.bg))
                    is Ui.Intro -> IntroScreen(current.state, onStart = { ui = Ui.Working(current.state.app) }, onCancel = ::cancel)
                    is Ui.Working -> LoadingScreen(current.app, onCancel = ::cancel)
                }
            }
        }
        attach(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        attach(intent)
        targetPkg?.let { LockUiBeacon.resumed(it) }
    }

    override fun onResume() {
        super.onResume()
        Log.i("BS/Svc", "lock-resumed t=${SystemClock.uptimeMillis()}")
        targetPkg?.let { LockUiBeacon.resumed(it) }
    }

    override fun onStop() {
        super.onStop()
        LockUiBeacon.gone()
    }

    private fun attach(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_TARGET_PKG)
        if (pkg == null) {
            finishAndRemoveTask()
            return
        }
        targetPkg = pkg
        LockUiBeacon.resumed(pkg)
        ui = Ui.Blank
        lifecycleScope.launch {
            val settings = container.settings.current()
            val runtime = container.runtime.current()
            val time = container.timeSource.now()
            when (LockEntry.decide(pkg, GateSnapshot(settings.blockedPackages, runtime.grants), time)) {
                LockEntry.Dismiss -> finishAndRemoveTask()
                LockEntry.OpenTarget -> {
                    packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                    finish()
                }
                LockEntry.Show -> {
                    container.runtime.apply(time) { RuntimeOps.attach(it, pkg, settings, time) { UUID.randomUUID().toString() } }
                    val session = container.runtime.current().session ?: return@launch
                    ui = Ui.Intro(IntroStateFactory.build(labelOf(pkg), session, container.stats.current(), time))
                }
            }
        }
    }

    /** Cancel, back and gesture: the episode counts as aborted, the session stays, the user goes HOME (never just finish). */
    private fun cancel() {
        val time = container.timeSource.now()
        container.ioScope.launch { container.runtime.apply(time) { RuntimeOps.cancel(it) } }
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg.substringAfterLast('.')
    }

    companion object {
        const val EXTRA_TARGET_PKG = "target_pkg"
    }
}
