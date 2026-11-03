package dev.tinow.brainrotstopper.domain.gate

/**
 * Packages that are never locked, so the user cannot lock themselves out (architecture.md 7). The dynamic part
 * (home launchers, enabled input methods, default dialer) is collected on Android and passed in.
 */
object ProtectedPackages {
    const val OWN_PACKAGE = "dev.tinow.brainrotstopper"
    const val ANKIDROID = "com.ichi2.anki"

    val STATIC: Set<String> = setOf(
        OWN_PACKAGE,
        ANKIDROID,
        "com.android.settings",
        "com.android.systemui",
        "android",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.emergency",
    )

    fun isProtected(pkg: String, dynamic: Set<String> = emptySet()): Boolean = pkg in STATIC || pkg in dynamic
}
