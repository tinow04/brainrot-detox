package dev.tinow.brainrotstopper.ui.setup

import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.permission.AnkiInstallStep
import dev.tinow.brainrotstopper.domain.permission.AnkiPermissionStep
import dev.tinow.brainrotstopper.domain.permission.AnkiProbe
import dev.tinow.brainrotstopper.domain.permission.AnkiReadyStep
import dev.tinow.brainrotstopper.domain.permission.PermissionSnapshot
import dev.tinow.brainrotstopper.domain.permission.Protection
import dev.tinow.brainrotstopper.domain.permission.ProtectionStatus
import dev.tinow.brainrotstopper.lock.quiz.QuizEngine
import dev.tinow.brainrotstopper.ui.Strings

/** What a setup button does; the host binds each to an intent or a request (keeps this model free of Android). */
enum class SetupAction { InstallAnki, RequestPermission, AppInfo, OpenAnki, RetryProbe, Accessibility, Battery }

/** One setup row as data: tag, text, optional dim detail and up to two actions (the first is the main one). */
data class SetupItem(
    val label: String,
    val state: SetupState,
    val detail: String? = null,
    val action: SetupAction? = null,
    val action2: SetupAction? = null,
)

/** Builds the setup rows in the order of architecture.md 6: AnkiDroid installed, access, ready, accessibility, battery. */
object SetupModel {
    fun items(p: PermissionSnapshot, probe: AnkiProbe, deniedPermanently: Boolean): List<SetupItem> {
        val anki = Protection.ankiSteps(p, probe, deniedPermanently)
        return listOf(installed(anki.installed), permission(anki.permission), ready(anki.ready), accessibility(Protection.status(p)), battery(p))
    }

    fun hints(p: PermissionSnapshot): List<String> = buildList {
        if (Protection.status(p) != ProtectionStatus.Active) add(Strings.SETUP_HINT_RESTRICTED)
        add(Strings.SETUP_HINT_RESTART)
        add(Strings.SETUP_HINT_AUTOSYNC)
    }

    private fun installed(step: AnkiInstallStep) = when (step) {
        AnkiInstallStep.Ok -> SetupItem(Strings.SETUP_ANKI_INSTALLED, SetupState.Ok)
        AnkiInstallStep.Missing -> SetupItem(Strings.SETUP_ANKI_MISSING, SetupState.Open, Strings.SETUP_ANKI_MISSING_DETAIL, SetupAction.InstallAnki)
    }

    private fun permission(step: AnkiPermissionStep) = when (step) {
        AnkiPermissionStep.Ok -> SetupItem(Strings.SETUP_ANKI_PERMISSION, SetupState.Ok)
        AnkiPermissionStep.NeedsInstall -> SetupItem(Strings.SETUP_ANKI_PERMISSION_ASK, SetupState.Open, Strings.SETUP_ANKI_NEEDS_PREVIOUS)
        AnkiPermissionStep.Ask -> SetupItem(Strings.SETUP_ANKI_PERMISSION_ASK, SetupState.Open, action = SetupAction.RequestPermission)
        AnkiPermissionStep.Denied ->
            SetupItem(Strings.SETUP_ANKI_PERMISSION_DENIED, SetupState.Error, Strings.SETUP_ANKI_PERMISSION_DENIED_DETAIL, SetupAction.AppInfo)
    }

    private fun ready(step: AnkiReadyStep) = when (step) {
        is AnkiReadyStep.Ok -> SetupItem(Strings.SETUP_ANKI_READY, SetupState.Ok, Strings.ankiConnected(step.decks))
        AnkiReadyStep.NeedsPrevious -> SetupItem(Strings.SETUP_ANKI_READY, SetupState.Open, Strings.SETUP_ANKI_NEEDS_PREVIOUS)
        AnkiReadyStep.Checking -> SetupItem(Strings.SETUP_ANKI_READY, SetupState.Open, Strings.SETUP_ANKI_CHECKING)
        AnkiReadyStep.ApiOff -> SetupItem(Strings.SETUP_ANKI_API_OFF, SetupState.Error, Strings.SETUP_ANKI_API_OFF_DETAIL, SetupAction.OpenAnki)
        AnkiReadyStep.NoData -> SetupItem(Strings.SETUP_ANKI_FAILED, SetupState.Error, Strings.SETUP_ANKI_NO_DATA_DETAIL, SetupAction.OpenAnki, SetupAction.RetryProbe)
        is AnkiReadyStep.Failed -> SetupItem(
            Strings.SETUP_ANKI_FAILED,
            SetupState.Error,
            QuizEngine.errorTexts(step.error).text,
            when (step.error) {
                AnkiError.StorageNotConfigured -> SetupAction.OpenAnki
                AnkiError.PermissionMissing -> SetupAction.AppInfo
                else -> SetupAction.RetryProbe
            },
        )
    }

    private fun accessibility(status: ProtectionStatus) = when (status) {
        ProtectionStatus.Active -> SetupItem(Strings.SETUP_ACCESSIBILITY, SetupState.Ok)
        ProtectionStatus.NotEnabled -> SetupItem(Strings.SETUP_ACCESSIBILITY, SetupState.Open, action = SetupAction.Accessibility, action2 = SetupAction.AppInfo)
        ProtectionStatus.EnabledNotBound -> SetupItem(Strings.SETUP_ACCESSIBILITY_UNBOUND, SetupState.Error, action = SetupAction.Accessibility)
        ProtectionStatus.Restricted ->
            SetupItem(Strings.SETUP_ACCESSIBILITY_RESTRICTED, SetupState.Error, action = SetupAction.AppInfo, action2 = SetupAction.Accessibility)
    }

    private fun battery(p: PermissionSnapshot) =
        if (p.batteryUnrestricted) SetupItem(Strings.SETUP_BATTERY, SetupState.Ok) else SetupItem(Strings.SETUP_BATTERY, SetupState.Open, action = SetupAction.Battery)

    fun label(action: SetupAction): String = when (action) {
        SetupAction.InstallAnki -> Strings.BTN_INSTALL_ANKIDROID
        SetupAction.RequestPermission -> Strings.BTN_ALLOW
        SetupAction.AppInfo -> Strings.BTN_APP_INFO
        SetupAction.OpenAnki -> Strings.BTN_OPEN_ANKIDROID
        SetupAction.RetryProbe -> Strings.BTN_RETRY
        SetupAction.Accessibility -> Strings.BTN_ACCESSIBILITY
        SetupAction.Battery -> Strings.BTN_BATTERY
    }
}
