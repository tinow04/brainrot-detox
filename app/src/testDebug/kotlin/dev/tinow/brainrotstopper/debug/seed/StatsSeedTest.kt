package dev.tinow.brainrotstopper.debug.seed

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.stats.StatsAggregator
import dev.tinow.brainrotstopper.ui.Strings
import org.junit.Test

/** The seed must reproduce the numbers shown in the stats mockup (B3). */
class StatsSeedTest {
    private val today = 20_400L

    @Test fun weekSeedMatchesMockupNumbers() {
        val s = StatsAggregator.summarize(StatsSeed.week(today), today)
        assertThat(s.blockedToday).isEqualTo(14)
        assertThat(s.blockedWeek).isEqualTo(87)
        assertThat(s.streakDays).isEqualTo(12)
        assertThat(s.cards).isEqualTo(412)
        assertThat(Strings.quote(s.correct, s.cards)).isEqualTo("86 %")
        assertThat(s.savedMinutes).isEqualTo(200)
        assertThat(s.bars.map { it.value }).containsExactly(11, 9, 13, 15, 12, 13, 14).inOrder()
        assertThat(s.ranking).hasSize(4)
    }

    @Test fun emptySeedIsEmpty() {
        assertThat(StatsSeed.empty().days).isEmpty()
    }
}
