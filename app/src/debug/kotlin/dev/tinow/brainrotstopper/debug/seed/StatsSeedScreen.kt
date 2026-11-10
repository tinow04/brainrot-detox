package dev.tinow.brainrotstopper.debug.seed

import dev.tinow.brainrotstopper.domain.stats.StatsAggregator
import dev.tinow.brainrotstopper.ui.stats.StatsModel
import dev.tinow.brainrotstopper.ui.stats.StatsState

/** The B3 mockup state, produced by the same path as the real tab: [StatsSeed] -> StatsAggregator -> StatsModel. */
object StatsSeedScreen {
    /** 2026-11-09, a Monday: the mockup's last bar is `mo`. */
    const val MONDAY = 20_766L

    /** Labels of the seeded packages as the mockup shows them (on a device: launcher labels). */
    val LABELS = mapOf(
        "com.zhiliaoapp.musically" to "tiktok",
        "com.instagram.android" to "instagram",
        "com.google.android.youtube" to "youtube",
        "com.reddit.frontpage" to "reddit",
    )

    fun state(today: Long = MONDAY): StatsState =
        StatsModel.state(StatsAggregator.summarize(StatsSeed.week(today), today)) { LABELS[it] ?: it }
}
