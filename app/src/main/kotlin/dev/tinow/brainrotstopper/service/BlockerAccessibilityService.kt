package dev.tinow.brainrotstopper.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.tinow.brainrotstopper.lock.LockActivity

/** M0-S2/S3 spike service: logs every window-state event and locks the dummy app. */
class BlockerAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        Log.i("BS/Svc", "onServiceConnected t=${SystemClock.uptimeMillis()}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString()
        Log.i("BS/Svc", "event pkg=$pkg cls=$cls eventTime=${event.eventTime} now=${SystemClock.uptimeMillis()}")
        if (pkg == BLOCKED_SPIKE_PKG) {
            startActivity(
                Intent(this, LockActivity::class.java)
                    .putExtra(LockActivity.EXTRA_TARGET_PKG, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
            Log.i("BS/Svc", "lock-start-called t=${SystemClock.uptimeMillis()}")
        }
    }

    override fun onInterrupt() {
        Log.i("BS/Svc", "onInterrupt")
    }

    override fun onDestroy() {
        Log.i("BS/Svc", "onDestroy t=${SystemClock.uptimeMillis()}")
        super.onDestroy()
    }

    companion object {
        const val BLOCKED_SPIKE_PKG = "dev.tinow.brainrotstopper.dummy"
    }
}
