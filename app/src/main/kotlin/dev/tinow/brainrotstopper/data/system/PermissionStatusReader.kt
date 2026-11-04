package dev.tinow.brainrotstopper.data.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dev.tinow.brainrotstopper.domain.permission.PermissionSnapshot
import dev.tinow.brainrotstopper.hooks.DebugFlags
import dev.tinow.brainrotstopper.hooks.NoDebugFlags
import dev.tinow.brainrotstopper.service.BlockerAccessibilityService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Reads what the app can observe about its permissions. "Bound" is what the system reports
 * (`getEnabledAccessibilityServiceList` lists running services), "enabled" is the settings entry; the difference is
 * the "enabled but not bound" state of architecture.md 6. [observe] pushes on every change and polls once a second
 * as a safety net, only while collected (the UI collects it while resumed).
 */
class PermissionStatusReader(context: Context, private val flags: () -> DebugFlags = { NoDebugFlags }) {
    private val app = context.applicationContext
    private val ours = ComponentName(app, BlockerAccessibilityService::class.java)

    fun read(): PermissionSnapshot {
        flags().permissionOverride?.let { return override(it) }
        val enabledSetting = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val enabled = enabledSetting.split(':').any { ComponentName.unflattenFromString(it) == ours }
        val manager = app.getSystemService(AccessibilityManager::class.java)
        val bound = manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty().any {
            it.resolveInfo?.serviceInfo?.let { si -> si.packageName == ours.packageName && si.name == ours.className } == true
        }
        val battery = app.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(app.packageName) == true
        return PermissionSnapshot(a11yEnabled = enabled, a11yBound = bound, batteryUnrestricted = battery)
    }

    private fun override(kind: String): PermissionSnapshot = when (kind) {
        "off" -> PermissionSnapshot()
        "unbound" -> PermissionSnapshot(a11yEnabled = true)
        "restricted" -> PermissionSnapshot(restricted = true)
        "ok" -> PermissionSnapshot(a11yEnabled = true, a11yBound = true, batteryUnrestricted = true)
        else -> PermissionSnapshot()
    }

    fun observe(): Flow<PermissionSnapshot> = callbackFlow {
        val manager = app.getSystemService(AccessibilityManager::class.java)
        val push = { trySend(read()) }
        val listener = AccessibilityManager.AccessibilityServicesStateChangeListener { push() }
        manager?.addAccessibilityServicesStateChangeListener(listener)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { push() }
        }
        app.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer)
        push()
        val ticker = launch {
            while (true) {
                delay(POLL_MS)
                push()
            }
        }
        awaitClose {
            ticker.cancel()
            manager?.removeAccessibilityServicesStateChangeListener(listener)
            app.contentResolver.unregisterContentObserver(observer)
        }
    }.distinctUntilChanged()

    private companion object {
        const val POLL_MS = 1_000L
    }
}
