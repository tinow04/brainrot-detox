package dev.tinow.brainrotstopper.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tinow.brainrotstopper.data.system.PermissionStatusReader
import dev.tinow.brainrotstopper.di.AppContainer
import dev.tinow.brainrotstopper.domain.permission.PermissionSnapshot
import dev.tinow.brainrotstopper.domain.permission.Protection
import dev.tinow.brainrotstopper.domain.permission.ProtectionStatus
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.banner.BannerState
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.setup.SetupActions
import dev.tinow.brainrotstopper.ui.setup.SetupRow
import dev.tinow.brainrotstopper.ui.setup.SetupScreen
import dev.tinow.brainrotstopper.ui.setup.SetupState
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import kotlinx.coroutines.launch

private const val ROUTE_SETUP = "setup"

/**
 * Main shell: setup as start route on the first start while a mandatory step is open, otherwise the three tabs with the
 * protection banner on top (architecture.md 6). The tab contents are placeholders until M5.
 */
@Composable
fun MainApp(container: AppContainer, reader: PermissionStatusReader) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val runtime by container.runtime.flow.collectAsStateWithLifecycle(initialValue = null)
    val permissions by reader.observe().collectAsStateWithLifecycle(initialValue = reader.read())

    // "" = not decided yet (stores still loading): show only the background, never a wrong screen.
    var route by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(settings != null) {
        val s = settings
        if (route == "" && s != null) {
            route = if (Protection.startOnSetup(s.onboardingDismissed, permissions)) ROUTE_SETUP else MainTab.Apps.name
        }
    }
    val leaveSetup = {
        scope.launch { container.settings.update { it.copy(onboardingDismissed = true) } }
        route = MainTab.Apps.name
    }

    val status = Protection.status(permissions)
    when {
        route == "" || settings == null -> Box(Modifier.fillMaxSize().background(BsTheme.colors.bg))
        route == ROUTE_SETUP -> {
            BackHandler(onBack = leaveSetup)
            SetupScreen(
                rows = setupRows(permissions, status) { act -> act(context) },
                hints = buildList {
                    if (status != ProtectionStatus.Active) add(Strings.SETUP_HINT_RESTRICTED)
                    add(Strings.SETUP_HINT_RESTART)
                    add(Strings.SETUP_HINT_AUTOSYNC)
                },
                onDone = if (!Protection.mandatoryOpen(permissions)) leaveSetup else null,
            )
        }
        else -> {
            val tab = MainTab.entries.firstOrNull { it.name == route } ?: MainTab.Apps
            val banner = BannerState.from(status, runtime?.diag?.settingsCorrupted == true, setupIncomplete = false)
            MainScaffold(tab, { route = it.name }, banner, { route = ROUTE_SETUP }) {
                ScreenTitle(
                    when (tab) {
                        MainTab.Apps -> Strings.TITLE_APPS
                        MainTab.Config -> Strings.TITLE_CONFIG
                        MainTab.Stats -> Strings.TITLE_STATS
                    },
                )
                BsText(Strings.TAB_SOON, BsTheme.type.body, BsTheme.colors.dim)
            }
        }
    }
}

private fun setupRows(
    p: PermissionSnapshot,
    status: ProtectionStatus,
    run: ((android.content.Context) -> Unit) -> Unit,
): List<SetupRow> {
    val accessibility = when (status) {
        ProtectionStatus.Active -> SetupRow(Strings.SETUP_ACCESSIBILITY, SetupState.Ok, null, {})
        ProtectionStatus.NotEnabled -> SetupRow(
            Strings.SETUP_ACCESSIBILITY, SetupState.Open, Strings.BTN_ACCESSIBILITY, { run(SetupActions::openAccessibilitySettings) },
            Strings.BTN_APP_INFO, { run(SetupActions::openAppInfo) },
        )
        ProtectionStatus.EnabledNotBound -> SetupRow(
            Strings.SETUP_ACCESSIBILITY_UNBOUND, SetupState.Error, Strings.BTN_ACCESSIBILITY, { run(SetupActions::openAccessibilitySettings) },
        )
        ProtectionStatus.Restricted -> SetupRow(
            Strings.SETUP_ACCESSIBILITY_RESTRICTED, SetupState.Error, Strings.BTN_APP_INFO, { run(SetupActions::openAppInfo) },
            Strings.BTN_ACCESSIBILITY, { run(SetupActions::openAccessibilitySettings) },
        )
    }
    val battery = SetupRow(
        Strings.SETUP_BATTERY,
        if (p.batteryUnrestricted) SetupState.Ok else SetupState.Open,
        if (p.batteryUnrestricted) null else Strings.BTN_BATTERY,
        { run(SetupActions::openBatterySettings) },
    )
    return listOf(accessibility, battery)
}
