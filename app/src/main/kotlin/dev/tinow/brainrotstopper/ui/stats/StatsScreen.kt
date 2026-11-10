package dev.tinow.brainrotstopper.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.components.BsBarChart
import dev.tinow.brainrotstopper.ui.components.BsBox
import dev.tinow.brainrotstopper.ui.components.BsRankRow
import dev.tinow.brainrotstopper.ui.components.BsStatTile
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.components.ChartBar
import dev.tinow.brainrotstopper.ui.main.ScreenTitle
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

data class RankItem(val app: String, val count: Int)

data class StatsState(
    val blockedToday: Int,
    val blockedWeek: Int,
    val streakDays: Int,
    val cards: Int,
    val correct: Int,
    val savedMinutes: Int,
    val bars: List<ChartBar>,
    val ranking: List<RankItem>,
)

/** B3: statistics of the last 7 days (the host provides the data). */
@Composable
fun StatsScreen(state: StatsState, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(BsDims.S24)) {
        ScreenTitle(Strings.TITLE_STATS)
        Column(verticalArrangement = Arrangement.spacedBy(BsDims.S8)) {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(BsDims.S8)) {
                BsStatTile(state.blockedToday.toString(), Strings.STAT_BLOCKED_TODAY, Modifier.weight(1f).fillMaxHeight())
                BsStatTile(state.blockedWeek.toString(), Strings.STAT_WEEK, Modifier.weight(1f).fillMaxHeight())
                BsStatTile(Strings.tage(state.streakDays), Strings.STAT_STREAK, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(BsDims.S8)) {
                BsStatTile(state.cards.toString(), Strings.STAT_CARDS, Modifier.weight(1f).fillMaxHeight())
                BsStatTile(Strings.quote(state.correct, state.cards), Strings.STAT_QUOTE, Modifier.weight(1f).fillMaxHeight())
                BsStatTile(Strings.timeSaved(state.savedMinutes), Strings.STAT_SAVED, Modifier.weight(1f).fillMaxHeight())
            }
            BsText(Strings.STAT_FOOTNOTE, t.label, c.dim)
        }
        BsBox(Strings.BOX_CHART) { BsBarChart(state.bars) }
        BsBox(Strings.BOX_RANKING) {
            val max = state.ranking.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
            if (state.ranking.isEmpty()) BsText(Strings.RANKING_EMPTY, t.body, c.dim)
            state.ranking.forEach { BsRankRow(Strings.appLabel(it.app), it.count.toFloat() / max, it.count) }
        }
    }
}
