package dev.tinow.brainrotstopper.ui.stats

import dev.tinow.brainrotstopper.domain.stats.StatsSummary
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.components.ChartBar

/**
 * StatsAggregator output -> B3 (android-free): weekday labels `mo..so` (oldest left, today right and highlighted by the
 * chart), bar heights as computed by the aggregator (2 dp for 0), ranking with app labels ([labelOf] falls back to the
 * package name for apps that are not installed any more).
 */
object StatsModel {
    fun state(summary: StatsSummary, labelOf: (String) -> String): StatsState = StatsState(
        blockedToday = summary.blockedToday,
        blockedWeek = summary.blockedWeek,
        streakDays = summary.streakDays,
        cards = summary.cards,
        correct = summary.correct,
        savedMinutes = summary.savedMinutes,
        bars = summary.bars.map { ChartBar(Strings.WEEKDAYS[it.dayOfWeekIndex], it.value, it.heightDp) },
        ranking = summary.ranking.map { RankItem(labelOf(it.pkg), it.count) },
    )
}
