package dev.tinow.brainrotstopper.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import dev.tinow.brainrotstopper.domain.runtime.EmergencyButton
import dev.tinow.brainrotstopper.lock.screens.AnswerScreen
import dev.tinow.brainrotstopper.lock.screens.ErrorScreen
import dev.tinow.brainrotstopper.lock.screens.FeedbackScreen
import dev.tinow.brainrotstopper.lock.screens.IntroScreen
import dev.tinow.brainrotstopper.lock.screens.IntroState
import dev.tinow.brainrotstopper.lock.screens.Intervals
import dev.tinow.brainrotstopper.lock.screens.LoadingScreen
import dev.tinow.brainrotstopper.lock.screens.LockHead
import dev.tinow.brainrotstopper.lock.screens.QuestionScreen
import dev.tinow.brainrotstopper.lock.screens.SessionStats
import dev.tinow.brainrotstopper.lock.screens.UnlockVariant
import dev.tinow.brainrotstopper.lock.screens.UnlockedScreen
import dev.tinow.brainrotstopper.lock.screens.WaitingScreen
import dev.tinow.brainrotstopper.ui.Span
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.apps.AppItem
import dev.tinow.brainrotstopper.ui.apps.AppsScreen
import dev.tinow.brainrotstopper.ui.banner.BannerState
import dev.tinow.brainrotstopper.ui.components.BsImagePlaceholder
import dev.tinow.brainrotstopper.ui.components.BsRichText
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.debug.seed.StatsSeedScreen
import dev.tinow.brainrotstopper.ui.components.RateKind
import dev.tinow.brainrotstopper.ui.config.ConfigScreen
import dev.tinow.brainrotstopper.ui.config.ConfigState
import dev.tinow.brainrotstopper.ui.config.DeckItem
import dev.tinow.brainrotstopper.ui.config.MediaCheck
import dev.tinow.brainrotstopper.ui.config.MediaConfig
import dev.tinow.brainrotstopper.ui.main.MainScaffold
import dev.tinow.brainrotstopper.ui.main.MainTab
import dev.tinow.brainrotstopper.ui.setup.SetupRow
import dev.tinow.brainrotstopper.ui.setup.SetupScreen
import dev.tinow.brainrotstopper.ui.setup.SetupState
import dev.tinow.brainrotstopper.ui.stats.StatsScreen
import dev.tinow.brainrotstopper.ui.stats.StatsState
import dev.tinow.brainrotstopper.ui.theme.BsTheme

/** Sample data from design/mockups, shared by the @Preview functions and the debug gallery (`--es screen <name>`). */
object ScreenSamples {
    val names = listOf(
        "a1", "a1-resume", "a2", "a3", "a4", "a4-hard", "a5", "a5-alldone", "a5-emergency", "waiting", "loading",
        "error-not-available", "error-permission", "error-storage", "error-unreachable", "error-deck", "error-bad-data", "error-answer",
        "error-emergency-wait", "error-emergency-cooldown", "b1", "b1-empty", "b2", "b2-media", "b3", "setup", "banner-degraded", "banner-disabled", "banner-unbound",
        "banner-restricted", "banner-corrupt", "components",
    )

    private val head0 = LockHead("instagram", "biologie", 0, 5)
    private val head1 = LockHead("instagram", "biologie", 1, 5)
    private val head5 = LockHead("instagram", "biologie", 5, 5)
    private const val QUESTION = "Welches Organell produziert den Großteil des ATP in der Zelle?"

    private val apps = listOf(
        AppItem("ig", "instagram", true), AppItem("tt", "tiktok", true), AppItem("yt", "youtube", true), AppItem("rd", "reddit", true),
        AppItem("cr", "chrome", false), AppItem("wa", "whatsapp", false), AppItem("sp", "spotify", false), AppItem("ma", "maps", false),
    )

    private val config = ConfigState(
        required = 5,
        durations = listOf(5, 15, 30, 60),
        durationIndex = 1,
        decks = listOf(DeckItem("Biologie::Zellbiologie", 124), DeckItem("Englisch::Vokabeln", 58), DeckItem(null, 182)),
        deckIndex = 0,
        ankiConnected = true,
    )

    /** B3 through the real path (seed -> StatsAggregator -> StatsModel) on a Monday, as in the mockup (today = mo). */
    private val stats: StatsState get() = StatsSeedScreen.state()

    @Composable
    private fun CardText(text: String) = BsText(text, BsTheme.type.card, BsTheme.colors.text)

    @Composable
    private fun Main(tab: MainTab, banner: BannerState? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        var current by remember { mutableStateOf(tab) }
        MainScaffold(current, { current = it }, banner, {}, content = content)
    }

    @Composable
    fun Screen(name: String) {
        if (CardGallery.handles(name)) return CardGallery.Screen(name)
        when (name) {
            "a1" -> IntroScreen(IntroState("instagram", "biologie", 12, 15, 0, 5), {}, {})
            "a1-resume" -> IntroScreen(IntroState("instagram", "biologie", 12, 15, 3, 5), {}, {})
            "a2" -> QuestionScreen(head1, 2, {}, {}) { CardText(QUESTION) }
            "a3" -> AnswerScreen(
                head1, Intervals("<1m", "6m", "10m", "4d"), {},
                question = { BsText(QUESTION, BsTheme.type.cardSmall, BsTheme.colors.muted) },
                answer = {
                    BsRichText(listOf(Span("Mitochondrium", true), Span(" (innere Membran: oxidative Phosphorylierung)")), BsTheme.type.card, BsTheme.colors.text)
                    BsImagePlaceholder(Strings.mediaPlaceholder("mitochondrium.png"))
                },
            )
            "a4" -> FeedbackScreen(head1, 2, RateKind.Again, {})
            "a4-hard" -> FeedbackScreen(head1, 2, RateKind.Hard, {})
            "a5" -> UnlockedScreen(head5, 15, SessionStats(7, 5, 262_000, 12), UnlockVariant.Unlocked, {}, {})
            "a5-alldone" -> UnlockedScreen(head5, 15, SessionStats(3, 3, 70_000, 12), UnlockVariant.AllDone, {}, {})
            "a5-emergency" -> UnlockedScreen(head0, 5, null, UnlockVariant.Emergency, {}, {})
            "waiting" -> WaitingScreen(head1, {})
            "loading" -> LoadingScreen("instagram", {})
            "error-not-available" -> ErrorScreen("instagram", Strings.ERR_NOT_AVAILABLE, EmergencyButton.Ready, {}, {}, {})
            "error-permission" -> ErrorScreen("instagram", Strings.ERR_PERMISSION, EmergencyButton.Ready, {}, {}, {})
            "error-storage" -> ErrorScreen("instagram", Strings.ERR_STORAGE, EmergencyButton.Ready, {}, {}, {})
            "error-unreachable" -> ErrorScreen("instagram", Strings.ERR_UNREACHABLE, EmergencyButton.Ready, {}, {}, {})
            "error-deck" -> ErrorScreen("instagram", Strings.ERR_DECK_MISSING, EmergencyButton.Ready, {}, {}, {})
            "error-bad-data" -> ErrorScreen("instagram", Strings.ERR_BAD_DATA, EmergencyButton.Ready, {}, {}, {})
            "error-answer" -> ErrorScreen("instagram", Strings.ERR_ANSWER_NOT_SAVED, EmergencyButton.Ready, {}, {}, {})
            "error-emergency-wait" -> ErrorScreen("instagram", Strings.ERR_UNREACHABLE, EmergencyButton.Wait(42), {}, {}, {})
            "error-emergency-cooldown" -> ErrorScreen("instagram", Strings.ERR_UNREACHABLE, EmergencyButton.Cooldown(4 * 3_600_000L + 12 * 60_000L), {}, {}, {})
            "b1" -> Main(MainTab.Apps) {
                var q by remember { mutableStateOf("") }
                AppsScreen(apps.filter { it.blocked }, apps.filter { !it.blocked }, q, { q = it }, { _, _ -> })
            }
            "b1-empty" -> Main(MainTab.Apps) { AppsScreen(emptyList(), apps.filter { !it.blocked }, "", {}, { _, _ -> }) }
            "b2" -> Main(MainTab.Config) { ConfigPreview(config) }
            "b2-media" -> Main(MainTab.Config) { ConfigPreview(config.copy(media = MediaConfig("https://anki-media.example.org", "secret-token", MediaCheck.Ok))) }
            "b3" -> Main(MainTab.Stats) { StatsScreen(stats) }
            "setup" -> SetupScreen(
                listOf(
                    SetupRow(Strings.SETUP_ANKI_INSTALLED, SetupState.Ok, null, {}),
                    SetupRow(Strings.SETUP_ANKI_PERMISSION, SetupState.Open, Strings.BTN_ALLOW, {}),
                    SetupRow(Strings.SETUP_ANKI_READY, SetupState.Error, Strings.BTN_RETRY, {}),
                    SetupRow(Strings.SETUP_ACCESSIBILITY, SetupState.Open, Strings.BTN_ACCESSIBILITY, {}),
                    SetupRow(Strings.SETUP_BATTERY, SetupState.Open, Strings.BTN_BATTERY, {}),
                ),
                listOf(Strings.SETUP_HINT_AUTOSYNC),
            )
            "banner-degraded" -> Main(MainTab.Apps, BannerState.Degraded) { AppsScreen(apps.filter { it.blocked }, apps.filter { !it.blocked }, "", {}, { _, _ -> }) }
            "banner-disabled" -> Main(MainTab.Config, BannerState.ProtectionOffDisabled) { ConfigPreview(config) }
            "banner-unbound" -> Main(MainTab.Stats, BannerState.ProtectionOffUnbound) { StatsScreen(stats) }
            "banner-restricted" -> Main(MainTab.Apps, BannerState.ProtectionOffRestricted) { AppsScreen(apps.filter { it.blocked }, apps.filter { !it.blocked }, "", {}, { _, _ -> }) }
            "banner-corrupt" -> Main(MainTab.Config, BannerState.SettingsCorrupt) { ConfigPreview(config) }
            else -> dev.tinow.brainrotstopper.ui.components.ComponentsShowcase()
        }
    }

    @Composable
    private fun ConfigPreview(initial: ConfigState) {
        var s by remember { mutableStateOf(initial) }
        ConfigScreen(
            s,
            { s = s.copy(required = it.coerceIn(1, 50)) },
            { s = s.copy(durationIndex = it) },
            { s = s.copy(deckIndex = it) },
            { u -> s = s.copy(media = s.media?.copy(url = u)) },
            { tk -> s = s.copy(media = s.media?.copy(token = tk)) },
        )
    }
}

@Preview(name = "a1 dark", widthDp = 412, heightDp = 915) @Composable private fun A1D() = BsTheme(true) { ScreenSamples.Screen("a1") }
@Preview(name = "a1 light", widthDp = 412, heightDp = 915) @Composable private fun A1L() = BsTheme(false) { ScreenSamples.Screen("a1") }
@Preview(name = "a2 dark", widthDp = 412, heightDp = 915) @Composable private fun A2D() = BsTheme(true) { ScreenSamples.Screen("a2") }
@Preview(name = "a2 light", widthDp = 412, heightDp = 915) @Composable private fun A2L() = BsTheme(false) { ScreenSamples.Screen("a2") }
@Preview(name = "a3 dark", widthDp = 412, heightDp = 915) @Composable private fun A3D() = BsTheme(true) { ScreenSamples.Screen("a3") }
@Preview(name = "a3 light", widthDp = 412, heightDp = 915) @Composable private fun A3L() = BsTheme(false) { ScreenSamples.Screen("a3") }
@Preview(name = "a4 dark", widthDp = 412, heightDp = 915) @Composable private fun A4D() = BsTheme(true) { ScreenSamples.Screen("a4") }
@Preview(name = "a4 light", widthDp = 412, heightDp = 915) @Composable private fun A4L() = BsTheme(false) { ScreenSamples.Screen("a4") }
@Preview(name = "a5 dark", widthDp = 412, heightDp = 915) @Composable private fun A5D() = BsTheme(true) { ScreenSamples.Screen("a5") }
@Preview(name = "a5 light", widthDp = 412, heightDp = 915) @Composable private fun A5L() = BsTheme(false) { ScreenSamples.Screen("a5") }
@Preview(name = "b1 dark", widthDp = 412, heightDp = 915) @Composable private fun B1D() = BsTheme(true) { ScreenSamples.Screen("b1") }
@Preview(name = "b1 light", widthDp = 412, heightDp = 915) @Composable private fun B1L() = BsTheme(false) { ScreenSamples.Screen("b1") }
@Preview(name = "b2 dark", widthDp = 412, heightDp = 915) @Composable private fun B2D() = BsTheme(true) { ScreenSamples.Screen("b2") }
@Preview(name = "b2 light", widthDp = 412, heightDp = 915) @Composable private fun B2L() = BsTheme(false) { ScreenSamples.Screen("b2") }
@Preview(name = "b3 dark", widthDp = 412, heightDp = 915) @Composable private fun B3D() = BsTheme(true) { ScreenSamples.Screen("b3") }
@Preview(name = "b3 light", widthDp = 412, heightDp = 915) @Composable private fun B3L() = BsTheme(false) { ScreenSamples.Screen("b3") }
@Preview(name = "setup dark", widthDp = 412, heightDp = 915) @Composable private fun SetupD() = BsTheme(true) { ScreenSamples.Screen("setup") }
@Preview(name = "setup light", widthDp = 412, heightDp = 915) @Composable private fun SetupL() = BsTheme(false) { ScreenSamples.Screen("setup") }
