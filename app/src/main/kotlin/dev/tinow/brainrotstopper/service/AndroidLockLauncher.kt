package dev.tinow.brainrotstopper.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
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

    /**
     * From the service context like the lock itself: the app may be in the background by now (the lock closed). AnkiDroid
     * comes up for a moment and closes its deck list again (research.md E1, measured in M6-T7).
     */
    override fun startAnkiSync(): Boolean = try {
        service.startActivity(Intent(ANKI_SYNC_ACTION).setPackage(ANKIDROID).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val ANKI_SYNC_ACTION = "com.ichi2.anki.DO_SYNC"
        const val ANKIDROID = "com.ichi2.anki"
    }
}
