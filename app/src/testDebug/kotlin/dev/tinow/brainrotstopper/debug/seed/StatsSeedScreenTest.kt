package dev.tinow.brainrotstopper.debug.seed

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.stats.StatsAggregator
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.stats.RankItem
import dev.tinow.brainrotstopper.ui.stats.StatsModel
import org.junit.Test

/** SEED_STATS preset week -> StatsModel gives exactly what the B3 mockup shows (14/87/12 tage/412/86 %/~3h 20m). */
class StatsSeedScreenTest {
    @Test fun weekSeedProducesTheMockupScreen() {
        val s = StatsSeedScreen.state()
        assertThat(s.blockedToday).isEqualTo(14)
        assertThat(s.blockedWeek).isEqualTo(87)
        assertThat(Strings.tage(s.streakDays)).isEqualTo("12 tage")
        assertThat(s.cards).isEqualTo(412)
        assertThat(Strings.quote(s.correct, s.cards)).isEqualTo("86 %")
        assertThat(Strings.timeSaved(s.savedMinutes)).isEqualTo("~3h 20m")
        assertThat(s.bars.map { it.label }).containsExactly("di", "mi", "do", "fr", "sa", "so", "mo").inOrder()
        assertThat(s.bars.map { it.value }).containsExactly(11, 9, 13, 15, 12, 13, 14).inOrder()
        // tallest bar 90 dp, the others relative to it
        assertThat(s.bars.map { it.heightDp }).containsExactly(66f, 54f, 78f, 90f, 72f, 78f, 84f).inOrder()
        assertThat(s.ranking).containsExactly(RankItem("tiktok", 31), RankItem("instagram", 28), RankItem("youtube", 17), RankItem("reddit", 11)).inOrder()
    }

    @Test fun emptyStatsShowDashAndFlatBars() {
        val s = StatsModel.state(StatsAggregator.summarize(Stats(), StatsSeedScreen.MONDAY)) { it }
        assertThat(listOf(s.blockedToday, s.blockedWeek, s.streakDays, s.cards, s.savedMinutes)).containsExactly(0, 0, 0, 0, 0)
        assertThat(Strings.quote(s.correct, s.cards)).isEqualTo("-")
        assertThat(Strings.timeSaved(s.savedMinutes)).isEqualTo("0 min")
        assertThat(s.bars.map { it.heightDp }.toSet()).containsExactly(StatsAggregator.BAR_ZERO_DP)
        assertThat(s.ranking).isEmpty()
    }

    @Test fun unknownPackageKeepsItsName() {
        val s = StatsModel.state(StatsAggregator.summarize(StatsSeed.week(StatsSeedScreen.MONDAY), StatsSeedScreen.MONDAY)) { it }
        assertThat(s.ranking.first().app).isEqualTo("com.zhiliaoapp.musically")
    }
}
