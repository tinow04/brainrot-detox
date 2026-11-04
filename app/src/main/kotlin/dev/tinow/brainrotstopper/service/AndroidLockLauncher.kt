package dev.tinow.brainrotstopper.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import dev.tinow.brainrotstopper.data.system.AppLog
import dev.tinow.brainrotstopper.lock.LockActivity

/**
 * Starts the lock directly from the accessibility service (no PendingIntent): measured in M0-S2 as not blocked by the
 * background-activity-start rules (20/20). `BS/Svc lock-start-called` is the marker `scripts/spike/measure-lock.sh` reads.
 */
class AndroidLockLauncher(private val service: AccessibilityService) : LockLauncher {
    override fun start(pkg: String, retry: Boolean) {
        service.startActivity(
            Intent(service, LockActivity::class.java)
                .putExtra(LockActivity.EXTRA_TARGET_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        AppLog.i(AppLog.SVC, "lock-start-called t=${SystemClock.uptimeMillis()} retry=$retry")
    }

    override fun goHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}
