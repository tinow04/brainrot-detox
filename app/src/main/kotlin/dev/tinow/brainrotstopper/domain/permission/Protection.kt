package dev.tinow.brainrotstopper.domain.permission

import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.CardSource

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
    /** The AnkiDroid package is installed and enabled (step 1). */
    val ankiInstalled: Boolean = false,
    /** Its card provider resolves; false while installed = the API is switched off in AnkiDroid (M0-S1b c). */
    val ankiProvider: Boolean = false,
    /** `READ_WRITE_DATABASE` is granted (step 2). */
    val ankiPermission: Boolean = false,
)

/** Whether the lock mechanism actually works right now. */
enum class ProtectionStatus { Active, NotEnabled, EnabledNotBound, Restricted }

/** Result of the provider check of step 3; it only runs while steps 1 and 2 are done and the provider resolves. */
sealed interface AnkiProbe {
    /** Not checked (yet) or reset because an earlier step is open. */
    data object Unknown : AnkiProbe
    data class Ready(val decks: Int) : AnkiProbe
    data class Failed(val error: AnkiError) : AnkiProbe
}

enum class AnkiInstallStep { Ok, Missing }

enum class AnkiPermissionStep {
    Ok,

    /** AnkiDroid is missing, there is nothing to ask for yet. */
    NeedsInstall,

    /** Can be requested with the system dialog. */
    Ask,

    /** The system no longer shows the dialog (denied twice or "don't ask again"); only the app info helps. */
    Denied,
}

sealed interface AnkiReadyStep {
    data class Ok(val decks: Int) : AnkiReadyStep

    /** Step 1 or 2 is open. */
    data object NeedsPrevious : AnkiReadyStep

    /** Installed and allowed, but the provider is gone: the API switch in AnkiDroid is off. */
    data object ApiOff : AnkiReadyStep

    /**
     * The provider resolves but answers with a null cursor. Not the API switch (that removes the provider): measured
     * when AnkiDroid itself cannot read its folder (`StorageAccessException` inside AnkiDroid, see gotchas.md).
     */
    data object NoData : AnkiReadyStep
    data object Checking : AnkiReadyStep
    data class Failed(val error: AnkiError) : AnkiReadyStep
}

/** The three AnkiDroid steps of the setup (architecture.md 6, steps 1-3). */
data class AnkiSteps(val installed: AnkiInstallStep, val permission: AnkiPermissionStep, val ready: AnkiReadyStep) {
    /** A step needs the user. A check that is still running does not count (no warning before anything is known). */
    val open: Boolean
        get() = installed != AnkiInstallStep.Ok || permission != AnkiPermissionStep.Ok ||
            ready is AnkiReadyStep.ApiOff || ready is AnkiReadyStep.NoData || ready is AnkiReadyStep.Failed

    val complete: Boolean get() = !open && ready is AnkiReadyStep.Ok
}

object Protection {
    fun status(s: PermissionSnapshot): ProtectionStatus = when {
        s.a11yBound -> ProtectionStatus.Active
        s.restricted -> ProtectionStatus.Restricted
        s.a11yEnabled -> ProtectionStatus.EnabledNotBound
        else -> ProtectionStatus.NotEnabled
    }

    /** [deniedPermanently] comes from the UI: the request came back denied and the system would not ask again. */
    fun ankiSteps(s: PermissionSnapshot, probe: AnkiProbe, deniedPermanently: Boolean = false): AnkiSteps {
        val installed = if (s.ankiInstalled) AnkiInstallStep.Ok else AnkiInstallStep.Missing
        val permission = when {
            !s.ankiInstalled -> AnkiPermissionStep.NeedsInstall
            s.ankiPermission -> AnkiPermissionStep.Ok
            deniedPermanently -> AnkiPermissionStep.Denied
            else -> AnkiPermissionStep.Ask
        }
        val ready = when {
            installed != AnkiInstallStep.Ok || permission != AnkiPermissionStep.Ok -> AnkiReadyStep.NeedsPrevious
            !s.ankiProvider -> AnkiReadyStep.ApiOff
            probe is AnkiProbe.Ready -> AnkiReadyStep.Ok(probe.decks)
            probe is AnkiProbe.Failed && probe.error == AnkiError.NotInstalledOrApiOff -> AnkiReadyStep.NoData
            probe is AnkiProbe.Failed -> AnkiReadyStep.Failed(probe.error)
            else -> AnkiReadyStep.Checking
        }
        return AnkiSteps(installed, permission, ready)
    }

    /** Whether the provider check of step 3 may run: everything it depends on is in place. */
    fun mayProbe(s: PermissionSnapshot): Boolean = s.ankiInstalled && s.ankiPermission && s.ankiProvider

    /** A mandatory step (1-4) is open. The battery step is only recommended. */
    fun mandatoryOpen(s: PermissionSnapshot, probe: AnkiProbe = AnkiProbe.Unknown): Boolean =
        status(s) != ProtectionStatus.Active || ankiSteps(s, probe).open

    /** Every mandatory step is done, including a successful provider check. */
    fun setupComplete(s: PermissionSnapshot, probe: AnkiProbe): Boolean =
        status(s) == ProtectionStatus.Active && ankiSteps(s, probe).complete

    /** First start: setup is the start route as long as a mandatory step is open and it was not dismissed before. */
    fun startOnSetup(onboardingDismissed: Boolean, s: PermissionSnapshot, probe: AnkiProbe = AnkiProbe.Unknown): Boolean =
        !onboardingDismissed && mandatoryOpen(s, probe)

    /** Step 3: the provider answers and lists the decks; the number shown counts the normal (non-filtered) decks. */
    suspend fun probe(source: CardSource): AnkiProbe {
        when (val p = source.probe()) {
            is AnkiResult.Err -> return AnkiProbe.Failed(p.error)
            is AnkiResult.Ok -> Unit
        }
        return when (val d = source.listDecks()) {
            is AnkiResult.Err -> AnkiProbe.Failed(d.error)
            is AnkiResult.Ok -> AnkiProbe.Ready(d.value.count { !it.dynamic })
        }
    }
}
