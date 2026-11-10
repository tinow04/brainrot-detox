package dev.tinow.brainrotstopper.debug.seed

import dev.tinow.brainrotstopper.domain.model.DayStats
import dev.tinow.brainrotstopper.domain.model.Stats

/** Sample statistics that reproduce the numbers of the stats mockup (B3) when read on [today]. */
object StatsSeed {
    private val blocks = listOf(11, 9, 13, 15, 12, 13, 14) // oldest first, today last
    private val aborted = listOf(6, 6, 6, 6, 6, 5, 5)
    private val answered = listOf(59, 59, 59, 59, 59, 59, 58)
    private val correct = listOf(51, 51, 51, 51, 50, 50, 50)
    private val apps = listOf("com.zhiliaoapp.musically", "com.instagram.android", "com.google.android.youtube", "com.reddit.frontpage")
    // per day (oldest first) the blocks of tiktok, instagram, youtube, reddit: rows sum to [blocks], columns to the mockup
    // ranking 31 / 28 / 17 / 11 (rounding shares per day did not add up)
    private val perApp = listOf(
        listOf(4, 4, 2, 1), listOf(3, 3, 2, 1), listOf(5, 4, 2, 2), listOf(5, 5, 3, 2),
        listOf(4, 4, 2, 2), listOf(5, 4, 3, 1), listOf(5, 4, 3, 2),
    )

    fun week(today: Long): Stats {
        val days = mutableMapOf<Long, DayStats>()
        for (i in blocks.indices) {
            days[today - (6 - i)] = DayStats(
                blocks = blocks[i], unlocks = 2, aborted = aborted[i], answered = answered[i], correct = correct[i],
                perApp = apps.zip(perApp[i]).filter { it.second > 0 }.toMap(),
            )
        }
        // Streak of 12 days: five older days with one unlock each.
        for (back in 7..11) days[today - back] = DayStats(blocks = 1, unlocks = 1)
        return Stats(days = days)
    }

    fun empty() = Stats()
}
