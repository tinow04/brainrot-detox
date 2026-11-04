package dev.tinow.brainrotstopper.domain.permission

/** One reading of the permission state the app can observe (architecture.md 6). */
data class PermissionSnapshot(
    /** Our service is listed in `enabled_accessibility_services`. */
    val a11yEnabled: Boolean = false,
    /** The system reports our service as running (bound). */
    val a11yBound: Boolean = false,
    /** The system blocks enabling it ("restricted settings"). Not detectable by a public API, see decisions.md. */
    val restricted: Boolean = false,
    /** The app is exempt from battery optimisation. */
    val batteryUnrestricted: Boolean = false,
)

/** Whether the lock mechanism actually works right now. */
enum class ProtectionStatus { Active, NotEnabled, EnabledNotBound, Restricted }

object Protection {
    fun status(s: PermissionSnapshot): ProtectionStatus = when {
        s.a11yBound -> ProtectionStatus.Active
        s.restricted -> ProtectionStatus.Restricted
        s.a11yEnabled -> ProtectionStatus.EnabledNotBound
        else -> ProtectionStatus.NotEnabled
    }

    /**
     * A mandatory setup step is open. In M3 only the accessibility service counts; the AnkiDroid steps (installed,
     * permission, ready) join in M4-T5.
     */
    fun mandatoryOpen(s: PermissionSnapshot): Boolean = status(s) != ProtectionStatus.Active

    /** First start: setup is the start route as long as a mandatory step is open and it was not dismissed before. */
    fun startOnSetup(onboardingDismissed: Boolean, s: PermissionSnapshot): Boolean = !onboardingDismissed && mandatoryOpen(s)
}
