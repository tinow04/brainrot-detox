package dev.tinow.brainrotstopper.service

import dev.tinow.brainrotstopper.domain.gate.ProtectedPackages

/**
 * Pure foreground bookkeeping (architecture.md 2.2). The foreground is the package of the last event that is not
 * ignored. Only transient overlay packages are ignored (they show a window above another app and send no event for
 * the app below when they close, measured in M0-S2); every other package, also an unknown one, is a normal app.
 * Only the package counts, never the class: a cold start reports a starting-window FrameLayout of the target package.
 */
class ForegroundTracker(private val lockActivityClass: String) {
    sealed interface Result {
        /** The event says nothing about the app in front. */
        data object Ignored : Result
        data class Foreground(val pkg: String) : Result
    }

    var current: String? = null
        private set

    /** Enabled input methods; they float above apps like the other overlay packages. */
    @Volatile
    var ignoredDynamic: Set<String> = emptySet()

    /** True while the lock UI is requested or visible: window events of our own package then belong to the lock UI. */
    @Volatile
    var lockActive: Boolean = false

    fun onEvent(pkg: String, cls: String?): Result {
        if (pkg in IGNORED || pkg in ignoredDynamic) return Result.Ignored
        if (pkg == ProtectedPackages.OWN_PACKAGE && (cls == lockActivityClass || lockActive)) return Result.Ignored
        current = pkg
        return Result.Foreground(pkg)
    }

    companion object {
        /** Observed in M0-S2 (`com.android.systemui`, `android`) plus the permission and installer dialogs of the platform. */
        val IGNORED: Set<String> = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
        )
    }
}
