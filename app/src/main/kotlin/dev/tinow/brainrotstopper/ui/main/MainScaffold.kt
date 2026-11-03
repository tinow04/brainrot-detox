package dev.tinow.brainrotstopper.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.banner.BannerHost
import dev.tinow.brainrotstopper.ui.banner.BannerState
import dev.tinow.brainrotstopper.ui.components.BsNavBar
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

enum class MainTab { Apps, Config, Stats }

/** Title line `$ name` (`.t-title` with accent prompt). */
@Composable
fun ScreenTitle(name: String, modifier: Modifier = Modifier) {
    val t = BsTheme.type
    Row(modifier) {
        BsText(Strings.TITLE_PROMPT + " ", t.title, BsTheme.colors.accent)
        BsText(name, t.title, BsTheme.colors.text)
    }
}

/**
 * `.main`: optional banner on top, [content] filling the rest (it scrolls itself) and the bottom navigation.
 * Insets: top = max(56 dp, status bar + 24 dp), the nav bar grows by the navigation-bar inset beyond its own 16 dp.
 */
@Composable
fun MainScaffold(
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    banner: BannerState?,
    onBanner: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier.fillMaxSize().background(BsTheme.colors.bg)) {
        val contentTop = if (banner != null) {
            BannerHost(banner, onBanner, Modifier.padding(start = BsDims.Margin, end = BsDims.Margin, top = status + BsDims.S8))
            BsDims.S24
        } else {
            maxOf(BsDims.ScrTop, status + 24.dp)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = BsDims.Margin, end = BsDims.Margin, top = contentTop, bottom = BsDims.MainBottom),
            verticalArrangement = Arrangement.spacedBy(BsDims.S16),
            content = content,
        )
        BsNavBar(
            listOf(Strings.NAV_APPS, Strings.NAV_CONFIG, Strings.NAV_STATS),
            tab.ordinal,
            { onTab(MainTab.entries[it]) },
            Modifier.padding(bottom = maxOf(0.dp, navInset + 8.dp - BsDims.NavBottom)),
            label = Strings.NAV_LABEL,
        )
    }
}
