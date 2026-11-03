package dev.tinow.brainrotstopper.domain.stats

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.model.DayStats
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsAggregatorTest {
    private val today = LocalDate.of(2026, 11, 4).toEpochDay() // a Wednesday
    private fun stats(vararg d: Pair<Int, DayStats>) = Stats(days = d.associate { (off, s) -> today - off to s })
    private fun unlocked(n: Int = 1) = DayStats(blocks = n, unlocks = n)

    @Test fun emptyDataGivesZerosAndDashQuote() {
        val s = StatsAggregator.summarize(Stats(), today)
        assertThat(s.blockedToday).isEqualTo(0)
        assertThat(s.cards).isEqualTo(0)
        assertThat(dev.tinow.brainrotstopper.ui.Strings.quote(s.correct, s.cards)).isEqualTo("-")
        assertThat(s.bars).hasSize(7)
        assertThat(s.bars.map { it.heightDp }.distinct()).containsExactly(2f)
        assertThat(s.ranking).isEmpty()
        assertThat(s.streakDays).isEqualTo(0)
    }

    @Test fun weekWindowIsSevenDaysIncludingTodayWithGapsAsZero() {
        val s = StatsAggregator.summarize(stats(0 to DayStats(blocks = 2), 3 to DayStats(blocks = 4), 6 to DayStats(blocks = 1), 7 to DayStats(blocks = 100)), today)
        assertThat(s.blockedToday).isEqualTo(2)
        assertThat(s.blockedWeek).isEqualTo(7) // day -7 is outside
        assertThat(s.bars.map { it.value }).containsExactly(1, 0, 0, 4, 0, 0, 2).inOrder()
        assertThat(s.bars.last().isToday).isTrue()
        assertThat(s.bars.dropLast(1).none { it.isToday }).isTrue()
    }

    @Test fun weekdayIndexStartsAtMonday() {
        val s = StatsAggregator.summarize(Stats(), today) // today = Wednesday
        assertThat(s.bars.map { it.dayOfWeekIndex }).containsExactly(3, 4, 5, 6, 0, 1, 2).inOrder()
    }

    @Test fun cardsAndQuoteSumOverTheWeek() {
        val s = StatsAggregator.summarize(stats(0 to DayStats(answered = 10, correct = 8), 2 to DayStats(answered = 4, correct = 4)), today)
        assertThat(s.cards).isEqualTo(14)
        assertThat(s.correct).isEqualTo(12)
        assertThat(dev.tinow.brainrotstopper.ui.Strings.quote(s.correct, s.cards)).isEqualTo("86 %")
    }

    @Test fun barHeightRules() {
        assertThat(StatsAggregator.barHeightDp(1, 15)).isEqualTo(6f) // exact value, above the floor
        assertThat(StatsAggregator.barHeightDp(15, 15)).isEqualTo(90f)
        assertThat(StatsAggregator.barHeightDp(0, 15)).isEqualTo(2f)
        assertThat(StatsAggregator.barHeightDp(0, 0)).isEqualTo(2f)
        assertThat(StatsAggregator.barHeightDp(1, 100)).isEqualTo(4f) // 0.9 dp raised to the minimum
        assertThat(StatsAggregator.barHeightDp(1, 23)).isEqualTo(4f) // 3.9 dp raised to the minimum
        assertThat(StatsAggregator.barHeightDp(5, 10)).isEqualTo(45f)
    }

    @Test fun rankingTopFourRelativeToMaximumWithStableTieBreak() {
        val s = StatsAggregator.summarize(
            stats(
                0 to DayStats(perApp = mapOf("a" to 10, "b" to 5, "c" to 5, "d" to 2, "e" to 1)),
                1 to DayStats(perApp = mapOf("a" to 10, "e" to 1)),
            ),
            today,
        )
        assertThat(s.ranking.map { it.pkg }).containsExactly("a", "b", "c", "d").inOrder() // d and e tie at 2: alphabetical
    }

    @Test fun rankingFractionsAreRelativeToTheLargestApp() {
        val s = StatsAggregator.summarize(stats(0 to DayStats(perApp = mapOf("a" to 8, "b" to 4, "c" to 2))), today)
        assertThat(s.ranking.map { it.fraction }).containsExactly(1f, 0.5f, 0.25f).inOrder()
    }

    @Test fun abortedAboveBlocksIsClampedPerDay() {
        val s = StatsAggregator.summarize(stats(0 to DayStats(blocks = 2, aborted = 9), 1 to DayStats(blocks = 3, aborted = 1)), today)
        assertThat(s.savedMinutes).isEqualTo((2 + 1) * 5)
    }

    // --- streak ---------------------------------------------------------------------------------------------------

    @Test fun streakCountsBackwardsFromToday() {
        val st = stats(0 to unlocked(), 1 to unlocked(), 2 to unlocked(), 4 to unlocked())
        assertThat(StreakCalculator.streak(st, today)).isEqualTo(3)
    }

    @Test fun emptyTodayDoesNotBreakWhenYesterdayCounted() {
        val st = stats(1 to unlocked(), 2 to unlocked())
        assertThat(StreakCalculator.streak(st, today)).isEqualTo(2)
        assertThat(StreakCalculator.streakBeforeToday(st, today)).isEqualTo(2)
    }

    @Test fun missingYesterdayBreaksTheStreak() {
        val st = stats(2 to unlocked(), 3 to unlocked())
        assertThat(StreakCalculator.streak(st, today)).isEqualTo(0)
    }

    @Test fun blocksWithoutUnlockDoNotCount() {
        val st = stats(0 to DayStats(blocks = 5, aborted = 5), 1 to unlocked())
        assertThat(StreakCalculator.streak(st, today)).isEqualTo(1)
    }

    @Test fun freeUnlockDayKeepsTheStreak() {
        val st = stats(0 to unlocked(), 1 to DayStats(blocks = 1, freeUnlocks = 1), 2 to unlocked())
        assertThat(StreakCalculator.streak(st, today)).isEqualTo(3)
    }

    @Test fun emergenciesDoNotKeepTheStreak() {
        val st = stats(0 to unlocked(), 1 to DayStats(blocks = 1, emergencies = 1))
        assertThat(StreakCalculator.streak(st, today)).isEqualTo(1)
    }

    @Test fun introStreakExcludesTodayAndResultScreenIncludesIt() {
        val before = stats(1 to unlocked(), 2 to unlocked())
        val after = stats(0 to unlocked(), 1 to unlocked(), 2 to unlocked())
        assertThat(StreakCalculator.streakBeforeToday(before, today)).isEqualTo(2)
        assertThat(StreakCalculator.streak(after, today)).isEqualTo(3)
        assertThat(StreakCalculator.streakBeforeToday(after, today)).isEqualTo(2)
    }

    // --- day change, zone and DST ---------------------------------------------------------------------------------

    @Test fun epochDayUsesTheLocalZoneAroundMidnight() {
        val berlin = ZoneId.of("Europe/Berlin")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val wall = java.time.ZonedDateTime.of(2026, 11, 3, 23, 30, 0, 0, berlin).toInstant().toEpochMilli()
        assertThat(TimeSnapshot(0, wall, 1, berlin).epochDay).isEqualTo(LocalDate.of(2026, 11, 3).toEpochDay())
        assertThat(TimeSnapshot(0, wall + 31 * 60_000, 1, berlin).epochDay).isEqualTo(LocalDate.of(2026, 11, 4).toEpochDay())
        assertThat(TimeSnapshot(0, wall, 1, tokyo).epochDay).isEqualTo(LocalDate.of(2026, 11, 4).toEpochDay())
    }

    @Test fun dstTransitionDaysAreEachOneDay() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 has 23 hours, 2026-10-25 has 25 hours; consecutive epoch days regardless.
        for ((y, m, d) in listOf(Triple(2026, 3, 29), Triple(2026, 10, 25))) {
            val start = LocalDate.of(y, m, d).atStartOfDay(berlin).toInstant().toEpochMilli()
            val end = LocalDate.of(y, m, d).plusDays(1).atStartOfDay(berlin).toInstant().toEpochMilli()
            val day = LocalDate.of(y, m, d).toEpochDay()
            assertThat(TimeSnapshot(0, start, 1, berlin).epochDay).isEqualTo(day)
            assertThat(TimeSnapshot(0, end - 1, 1, berlin).epochDay).isEqualTo(day)
            assertThat(TimeSnapshot(0, end, 1, berlin).epochDay).isEqualTo(day + 1)
        }
    }

    @Test fun statsPlusAddsToTodayAndPrunesOldDays() {
        val old = today - 401
        val base = Stats(days = mapOf(old to DayStats(blocks = 9), today - 10 to DayStats(blocks = 1)))
        val next = base.plus(today, StatsDelta(blocks = 1, perApp = mapOf("a" to 1)))
        assertThat(next.days.keys).containsExactly(today - 10, today)
        assertThat(next.days.getValue(today).perApp).containsExactly("a", 1)
        val again = next.plus(today, StatsDelta(blocks = 1, perApp = mapOf("a" to 1, "b" to 1)))
        assertThat(again.days.getValue(today).blocks).isEqualTo(2)
        assertThat(again.days.getValue(today).perApp).containsExactly("a", 2, "b", 1)
    }
}
