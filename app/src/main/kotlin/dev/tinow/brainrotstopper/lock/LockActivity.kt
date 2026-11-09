package dev.tinow.brainrotstopper.lock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.tinow.brainrotstopper.BrainrotApp
import dev.tinow.brainrotstopper.domain.quiz.Phase
import dev.tinow.brainrotstopper.lock.quiz.ErrorTarget
import dev.tinow.brainrotstopper.lock.quiz.QuizCommand
import dev.tinow.brainrotstopper.lock.quiz.QuizRoot
import dev.tinow.brainrotstopper.lock.quiz.QuizViewModel
import dev.tinow.brainrotstopper.service.LockUiBeacon
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import kotlinx.coroutines.launch

/**
 * Lock shell. Started by the gate with `EXTRA_TARGET_PKG`. It reports itself through [LockUiBeacon] (launch ack) and
 * leaves through HOME on cancel, back and gesture. The quiz (resume check, intro A1, cards A2 to A5, error screens) is
 * the [QuizEngine] in the [QuizViewModel]; this class only forwards the intent, the lifecycle and the engine's commands.
 */
class LockActivity : ComponentActivity() {
    private val vm: QuizViewModel by viewModels {
        viewModelFactory { initializer { QuizViewModel(application as BrainrotApp) } }
    }
    private val engine get() = vm.engine
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
            override fun handleOnBackPressed() = engine.cancel()
        })
        setContent {
            BsTheme { QuizRoot(engine, ::openErrorTarget) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) { engine.commands.collect(::handle) }
        }
        // A recreated activity (configuration change) keeps the running engine; a new process or a fresh start attaches.
        if (engine.phase == Phase.Idle || engine.phase == Phase.Closed) attach(intent)
        else targetPkg = intent.getStringExtra(EXTRA_TARGET_PKG)
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
        engine.setVisible(true)
    }

    override fun onStop() {
        super.onStop()
        engine.setVisible(false)
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
        engine.attach(pkg)
    }

    private fun handle(command: QuizCommand) {
        when (command) {
            QuizCommand.Finish -> finishAndRemoveTask()
            QuizCommand.GoHome -> {
                // Never just finish: that would reveal the blocked app below (architecture.md 3B).
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finishAndRemoveTask()
            }
            is QuizCommand.StartTarget -> {
                packageManager.getLaunchIntentForPackage(command.pkg)?.let { startActivity(it) }
                finish()
            }
        }
    }

    private fun openErrorTarget(target: ErrorTarget) {
        val intent = when (target) {
            ErrorTarget.AppInfo ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            ErrorTarget.AnkiDroid -> packageManager.getLaunchIntentForPackage(ANKIDROID_PACKAGE)
            ErrorTarget.Settings -> packageManager.getLaunchIntentForPackage(packageName)
        } ?: return
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        const val EXTRA_TARGET_PKG = "target_pkg"
        private const val ANKIDROID_PACKAGE = "com.ichi2.anki"
    }
}
