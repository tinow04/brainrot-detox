package dev.tinow.brainrotstopper.domain.stats

import dev.tinow.brainrotstopper.domain.model.DayStats
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.quiz.Constants
import java.time.LocalDate

/**
 * Derived statistics (architecture.md 4). "Day" is the epoch day of the local calendar date, so time zones and DST need
 * no special handling here. Deliberately local midnight, not Anki's rollover hour (decisions.md).
 */
data class BarSpec(val dayOfWeekIndex: Int, val value: Int, val heightDp: Float, val isToday: Boolean)

data class RankEntry(val pkg: String, val count: Int, val fraction: Float)

data class StatsSummary(
    val blockedToday: Int,
    val blockedWeek: Int,
    val streakDays: Int,
    val cards: Int,
    val correct: Int,
    val savedMinutes: Int,
    /** Oldest day first, today last. */
    val bars: List<BarSpec>,
    val ranking: List<RankEntry>,
)

object StreakCalculator {
    private fun DayStats?.counts() = this != null && unlocks + freeUnlocks >= 1

    private fun run(days: Map<Long, DayStats>, from: Long): Int {
        var n = 0
        var d = from
        while (days[d].counts()) { n++; d-- }
        return n
    }

    /** Streak including today; an empty today does not break it while yesterday counted. */
    fun streak(stats: Stats, today: Long): Int =
        if (stats.days[today].counts()) run(stats.days, today) else run(stats.days, today - 1)

    /** Streak of the days before today (Intro screen). */
    fun streakBeforeToday(stats: Stats, today: Long): Int = run(stats.days, today - 1)
}

object StatsAggregator {
    const val WINDOW_DAYS = 7
    const val CHART_MAX_DP = 90f
    const val BAR_MIN_DP = 4f
    const val BAR_ZERO_DP = 2f
    const val RANKING_SIZE = 4

    fun barHeightDp(value: Int, max: Int): Float = when {
        value <= 0 || max <= 0 -> BAR_ZERO_DP
        else -> maxOf(BAR_MIN_DP, value * CHART_MAX_DP / max)
    }

    fun summarize(stats: Stats, today: Long): StatsSummary {
        val window = (today - (WINDOW_DAYS - 1)..today).toList()
        val per = window.map { stats.days[it] ?: DayStats() }
        val maxBlocks = per.maxOf { it.blocks }
        val bars = window.zip(per).map { (day, s) ->
            BarSpec(LocalDate.ofEpochDay(day).dayOfWeek.value - 1, s.blocks, barHeightDp(s.blocks, maxBlocks), day == today)
        }
        val perApp = per.flatMap { it.perApp.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
            .filterValues { it > 0 }
        val top = perApp.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(RANKING_SIZE)
        val topMax = top.firstOrNull()?.value ?: 1
        return StatsSummary(
            blockedToday = stats.days[today]?.blocks ?: 0,
            blockedWeek = per.sumOf { it.blocks },
            streakDays = StreakCalculator.streak(stats, today),
            cards = per.sumOf { it.answered },
            correct = per.sumOf { it.correct },
            savedMinutes = per.sumOf { minOf(it.aborted, it.blocks) } * Constants.ASSUMED_MINUTES_SAVED_PER_ABORT,
            bars = bars,
            ranking = top.map { RankEntry(it.key, it.value, it.value.toFloat() / topMax) },
        )
    }
}
