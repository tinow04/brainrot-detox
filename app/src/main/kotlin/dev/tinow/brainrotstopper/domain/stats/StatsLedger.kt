package dev.tinow.brainrotstopper.domain.stats

import dev.tinow.brainrotstopper.domain.model.DayStats
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.quiz.Constants

/** Counter changes of one runtime operation, applied to the stats file of the current day (architecture.md 4). */
data class StatsDelta(
    val blocks: Int = 0,
    val unlocks: Int = 0,
    val freeUnlocks: Int = 0,
    val emergencies: Int = 0,
    val aborted: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
    val perApp: Map<String, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = this == StatsDelta()

    operator fun plus(o: StatsDelta) = StatsDelta(
        blocks + o.blocks, unlocks + o.unlocks, freeUnlocks + o.freeUnlocks, emergencies + o.emergencies,
        aborted + o.aborted, answered + o.answered, correct + o.correct,
        (perApp.keys + o.perApp.keys).associateWith { (perApp[it] ?: 0) + (o.perApp[it] ?: 0) },
    )
}

/** Applies [delta] to [day] and drops days older than the retention window. */
fun Stats.plus(day: Long, delta: StatsDelta): Stats {
    val current = days[day] ?: DayStats()
    val updated = current.copy(
        blocks = current.blocks + delta.blocks,
        unlocks = current.unlocks + delta.unlocks,
        freeUnlocks = current.freeUnlocks + delta.freeUnlocks,
        emergencies = current.emergencies + delta.emergencies,
        aborted = current.aborted + delta.aborted,
        answered = current.answered + delta.answered,
        correct = current.correct + delta.correct,
        perApp = (current.perApp.keys + delta.perApp.keys).associateWith { (current.perApp[it] ?: 0) + (delta.perApp[it] ?: 0) },
    )
    val cutoff = day - Constants.STATS_RETENTION_DAYS
    return copy(days = (days + (day to updated)).filterKeys { it > cutoff })
}
