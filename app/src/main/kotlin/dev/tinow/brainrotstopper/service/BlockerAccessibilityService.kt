package dev.tinow.brainrotstopper.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.tinow.brainrotstopper.BrainrotApp
import dev.tinow.brainrotstopper.MainActivity
import dev.tinow.brainrotstopper.data.system.AppLog
import dev.tinow.brainrotstopper.lock.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Thin shell: forwards window events to the [GateController] and owns its lifecycle. The accessibility callback does
 * nothing but a non-blocking `trySend` inside a catch-all.
 */
class BlockerAccessibilityService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    private var controller: GateController? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        AppLog.i(AppLog.SVC, "onServiceConnected t=${SystemClock.uptimeMillis()}")
        val container = (application as BrainrotApp).container
        shutdown() // a reconnect without onDestroy must not leave a second controller behind
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gate = GateController(
            scope = serviceScope,
            timeSource = container.timeSource,
            settings = container.settings.flow,
            runtime = container.runtime.flow,
            protectedProvider = AndroidProtectedSetProvider(this),
            launcher = AndroidLockLauncher(this),
            diag = RuntimeDiagSink(container.runtime, container.ioScope),
            tracker = ForegroundTracker(LockActivity::class.java.name, MainActivity::class.java.name),
            flags = { container.debugFlags },
            log = { AppLog.i(AppLog.GATE, it) },
        )
        scope = serviceScope
        controller = gate
        container.gate = gate
        gate.start()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching { gate.onScreenOn() }
            }
        }
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        screenReceiver = receiver

        val wall = container.timeSource.now().wallMs
        container.ioScope.launch { container.runtime.update { it.copy(diag = it.diag.copy(serviceLastConnectedWall = wall)) } }
        gate.onConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val pkg = event.packageName?.toString() ?: return
            controller?.onAccessibilityEvent(pkg, event.className?.toString())
        } catch (_: Throwable) {
            // never let an exception leave the accessibility callback
        }
    }

    override fun onInterrupt() {
        AppLog.i(AppLog.SVC, "onInterrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        markUnbound()
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AppLog.i(AppLog.SVC, "onDestroy t=${SystemClock.uptimeMillis()}")
        shutdown()
        super.onDestroy()
    }

    private fun markUnbound() {
        runCatching {
            val container = (application as BrainrotApp).container
            val wall = container.timeSource.now().wallMs
            container.ioScope.launch { container.runtime.update { it.copy(diag = it.diag.copy(serviceLastUnboundWall = wall)) } }
        }
    }

    private fun shutdown() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        controller?.stop()
        runCatching { (application as BrainrotApp).container.gate = null }
        controller = null
        scope?.cancel()
        scope = null
    }
}
