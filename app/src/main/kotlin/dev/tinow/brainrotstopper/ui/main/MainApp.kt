package dev.tinow.brainrotstopper.ui.main

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tinow.brainrotstopper.data.anki.AnkiContract
import dev.tinow.brainrotstopper.data.system.AppLog
import dev.tinow.brainrotstopper.data.system.PermissionStatusReader
import dev.tinow.brainrotstopper.di.AppContainer
import dev.tinow.brainrotstopper.domain.permission.Protection
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.banner.BannerState
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.setup.SetupAction
import dev.tinow.brainrotstopper.ui.setup.SetupActions
import dev.tinow.brainrotstopper.ui.setup.SetupModel
import dev.tinow.brainrotstopper.ui.setup.SetupRow
import dev.tinow.brainrotstopper.ui.setup.SetupScreen
import dev.tinow.brainrotstopper.ui.setup.SetupViewModel
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import kotlinx.coroutines.launch

private const val ROUTE_SETUP = "setup"
private const val ROUTE_APPS = "apps"

/**
 * Main shell: setup as start route on the first start while a mandatory step is open, otherwise the three tabs with the
 * protection banner on top (architecture.md 6). The tab contents are placeholders until M5.
 */
@Composable
fun MainApp(container: AppContainer, reader: PermissionStatusReader, setup: SetupViewModel, initialRoute: String? = null) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val runtime by container.runtime.flow.collectAsStateWithLifecycle(initialValue = null)
    val permissions by reader.observe().collectAsStateWithLifecycle(initialValue = reader.read())
    val probe by setup.probe.collectAsStateWithLifecycle()

    // Step 3 talks to AnkiDroid: on every resume of the main app and whenever steps 1-2 or the API switch change.
    val mayProbe = Protection.mayProbe(permissions)
    LifecycleResumeEffect(mayProbe) {
        if (mayProbe) setup.refresh() else setup.clear()
        onPauseOrDispose { }
    }

    // The system dialog reports only "denied"; whether it would still ask again is the rationale flag right after it.
    var deniedPermanently by rememberSaveable { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        deniedPermanently = !granted && activity?.shouldShowRequestPermissionRationale(AnkiContract.PERMISSION) == false
        AppLog.i("BS/Setup", "permission result granted=$granted permanent=$deniedPermanently")
    }

    // "" = not decided yet (stores still loading): show only the background, never a wrong screen.
    var route by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(settings != null) {
        val s = settings
        if (route == "" && s != null) {
            route = when {
                initialRoute == ROUTE_SETUP -> ROUTE_SETUP
                initialRoute == ROUTE_APPS -> MainTab.Apps.name
                Protection.startOnSetup(s.onboardingDismissed, permissions, probe) -> ROUTE_SETUP
                else -> MainTab.Apps.name
            }
        }
    }
    val leaveSetup = {
        scope.launch { container.settings.update { it.copy(onboardingDismissed = true) } }
        route = MainTab.Apps.name
    }

    val run: (SetupAction) -> Unit = { action ->
        when (action) {
            SetupAction.InstallAnki -> SetupActions.installAnkiDroid(context)
            SetupAction.RequestPermission -> requestPermission.launch(AnkiContract.PERMISSION)
            SetupAction.AppInfo -> SetupActions.openAppInfo(context)
            SetupAction.OpenAnki -> SetupActions.openAnkiDroid(context)
            SetupAction.RetryProbe -> setup.refresh()
            SetupAction.Accessibility -> SetupActions.openAccessibilitySettings(context)
            SetupAction.Battery -> SetupActions.openBatterySettings(context)
        }
    }

    when {
        route == "" || settings == null -> Box(Modifier.fillMaxSize().background(BsTheme.colors.bg))
        route == ROUTE_SETUP -> {
            BackHandler(onBack = leaveSetup)
            val items = SetupModel.items(permissions, probe, deniedPermanently)
            // one compact line per change, e.g. `rows ok,open:RequestPermission,open,ok,open:Battery` (scripts assert on it)
            LaunchedEffect(items) {
                AppLog.i("BS/Setup", "rows " + items.joinToString(",") { i ->
                    i.state.name.lowercase() + (i.action?.let { ":$it" } ?: "") + (i.action2?.let { "+$it" } ?: "")
                })
            }
            SetupScreen(
                rows = items.map { item ->
                    SetupRow(
                        label = item.label,
                        state = item.state,
                        action = item.action?.let(SetupModel::label),
                        onAction = { item.action?.let(run) },
                        action2 = item.action2?.let(SetupModel::label),
                        onAction2 = { item.action2?.let(run) },
                        detail = item.detail,
                    )
                },
                hints = SetupModel.hints(permissions),
                onDone = if (Protection.setupComplete(permissions, probe)) leaveSetup else null,
            )
        }
        else -> {
            val tab = MainTab.entries.firstOrNull { it.name == route } ?: MainTab.Apps
            val banner = BannerState.from(
                Protection.status(permissions),
                runtime?.diag?.settingsCorrupted == true,
                setupIncomplete = Protection.ankiSteps(permissions, probe).open,
            )
            LaunchedEffect(banner) { AppLog.i("BS/Setup", "banner ${banner?.name ?: "none"}") }
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
