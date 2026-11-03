package dev.tinow.brainrotstopper.debug.seed

import dev.tinow.brainrotstopper.domain.model.DayStats
import dev.tinow.brainrotstopper.domain.model.Stats

/** Sample statistics that reproduce the numbers of the stats mockup (B3) when read on [today]. */
object StatsSeed {
    private val blocks = listOf(11, 9, 13, 15, 12, 13, 14) // oldest first, today last
    private val aborted = listOf(6, 6, 6, 6, 6, 5, 5)
    private val answered = listOf(59, 59, 59, 59, 59, 59, 58)
    private val correct = listOf(51, 51, 51, 51, 50, 50, 50)
    private val shares = listOf("com.zhiliaoapp.musically" to 31, "com.instagram.android" to 28, "com.google.android.youtube" to 17, "com.reddit.frontpage" to 11)

    fun week(today: Long): Stats {
        val days = mutableMapOf<Long, DayStats>()
        for (i in blocks.indices) {
            val b = blocks[i]
            val perApp = shares.map { (pkg, share) -> pkg to Math.round(b * share / 87.0).toInt() }.toMutableList()
            perApp[3] = perApp[3].copy(second = b - perApp.take(3).sumOf { it.second }) // remainder to the last app
            days[today - (6 - i)] = DayStats(
                blocks = b, unlocks = 2, aborted = aborted[i], answered = answered[i], correct = correct[i],
                perApp = perApp.filter { it.second > 0 }.toMap(),
            )
        }
        // Streak of 12 days: five older days with one unlock each.
        for (back in 7..11) days[today - back] = DayStats(blocks = 1, unlocks = 1)
        return Stats(days = days)
    }

    fun empty() = Stats()
}
