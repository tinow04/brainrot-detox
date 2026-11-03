package dev.tinow.brainrotstopper.domain.time

import java.time.Instant
import java.time.ZoneId

/**
 * One reading of all clocks. Decisions use [elapsedMs] (monotonic) and [bootCount] only; [wallMs] is for display and
 * the day boundary of the statistics (local midnight, deliberately not Anki's rollover hour).
 */
data class TimeSnapshot(
    val elapsedMs: Long,
    val wallMs: Long,
    /** `Settings.Global.BOOT_COUNT`, -1 when unreadable (a grant is then never valid). */
    val bootCount: Int,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val epochDay: Long get() = Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate().toEpochDay()
}

interface TimeSource {
    fun now(): TimeSnapshot
}
