package dev.tinow.brainrotstopper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.tinow.brainrotstopper.ui.Span
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

/** All terminal components on one page (previews and the debug component gallery). */
@Composable
fun ComponentsShowcase(modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    var checked by remember { mutableStateOf(true) }
    var steps by remember { mutableIntStateOf(5) }
    var seg by remember { mutableIntStateOf(1) }
    var deck by remember { mutableIntStateOf(0) }
    var nav by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = BsDims.Margin, vertical = BsDims.S24),
        verticalArrangement = Arrangement.spacedBy(BsDims.S16),
    ) {
        BsHeader("instagram · biologie", Strings.progress(1, 5), rightDescription = Strings.progressDescription(1, 5))
        Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S8)) {
            BsTag(Strings.TAG_UNLOCKED, BsTagKind.Ok)
            BsTag(Strings.TAG_FAIL, BsTagKind.Fail)
            BsTag(Strings.TAG_WARN, BsTagKind.Warn)
            BsTag(Strings.TAG_WAIT, BsTagKind.Info)
        }
        BsRichText(Strings.introText(5, false), BsTheme.type.card, c.text)
        BsButton(Strings.BTN_START, {}, cursor = true)
        BsButton(Strings.BTN_CANCEL, {}, style = BsButtonStyle.Ghost)
        BsBox(Strings.BOX_STATS) {
            BsKv(Strings.KV_STREAK, Strings.tage(12))
            BsKv(Strings.KV_TODAY, Strings.karten(15))
        }
        BsSep(Strings.sepBlocked(4))
        AppRow("instagram", blocked = true, onToggle = { checked = it })
        AppRow("chrome", blocked = false, onToggle = {})
        BsSearchField(query, { query = it }, "app suchen", Strings.SEARCH_PROMPT)
        BsBox(Strings.BOX_CARDS) {
            BsStepper(steps, { steps = (steps - 1).coerceAtLeast(1) }, { steps += 1 })
        }
        BsBox(Strings.BOX_DURATION) { BsSegments(listOf("5 min", "15 min", "30 min", "60 min"), seg, { seg = it }) }
        BsBox(Strings.BOX_DECK) {
            BsRadioOption(deck == 0, "Biologie::Zellbiologie", Strings.due(124), { deck = 0 })
            BsRadioOption(deck == 1, Strings.ALL_DECKS, Strings.due(182), { deck = 1 }, divider = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S12)) {
            RateButton(Strings.RATE_AGAIN, "<1m", RateKind.Again, {}, Modifier.weight(1f))
            RateButton(Strings.RATE_EASY, "4d", RateKind.Easy, {}, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S12)) {
            RateButton(Strings.RATE_HARD, "6m", RateKind.Hard, {}, Modifier.weight(1f))
            RateButton(Strings.RATE_GOOD, null, RateKind.Good, {}, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S16)) {
            AppIconInitials("instagram", large = true)
            AppIconInitials("Notizen")
        }
        BsImagePlaceholder(Strings.mediaPlaceholder("mitochondrium.png"))
        BsBanner(Strings.BANNER_DEGRADED, BsTagKind.Warn, {})
        Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S8)) {
            BsStatTile("14", Strings.STAT_BLOCKED_TODAY, Modifier.weight(1f))
            BsStatTile(Strings.timeSaved(200), Strings.STAT_SAVED, Modifier.weight(1f))
        }
        BsBarChart(listOf(11, 9, 13, 15, 12, 13, 14).zip(Strings.WEEKDAYS.drop(1) + Strings.WEEKDAYS.take(1)).map { ChartBar(it.second, it.first) })
        BsRankRow("tiktok", 1f, 31)
        BsRankRow("reddit", 0.35f, 11)
        BsNavBar(listOf(Strings.NAV_APPS, Strings.NAV_CONFIG, Strings.NAV_STATS), nav, { nav = it })
        BsRichText(listOf(Span("bold ", bold = true), Span("normal")), BsTheme.type.body, c.muted, Modifier.fillMaxWidth())
    }
}

@Preview(name = "components dark", widthDp = 412, heightDp = 2400, showBackground = true)
@Composable
private fun ComponentsDarkPreview() = BsTheme(dark = true) { ComponentsShowcase() }

@Preview(name = "components light", widthDp = 412, heightDp = 2400, showBackground = true)
@Composable
private fun ComponentsLightPreview() = BsTheme(dark = false) { ComponentsShowcase() }
