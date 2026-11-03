package dev.tinow.brainrotstopper.debug.fake

import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import dev.tinow.brainrotstopper.domain.time.TimeSource
import java.time.ZoneId

/** Virtual clocks for JVM and instrumented tests: elapsed and wall time move only when told to. */
class FakeTimeSource(
    var elapsedMs: Long = 1_000_000L,
    var wallMs: Long = 1_700_000_000_000L,
    var bootCount: Int = 7,
    var zone: ZoneId = ZoneId.of("Europe/Berlin"),
) : TimeSource {
    override fun now() = TimeSnapshot(elapsedMs, wallMs, bootCount, zone)

    /** Advances both clocks (the wall clock can be moved separately via [wallMs]). */
    fun advance(ms: Long) {
        elapsedMs += ms
        wallMs += ms
    }
}
