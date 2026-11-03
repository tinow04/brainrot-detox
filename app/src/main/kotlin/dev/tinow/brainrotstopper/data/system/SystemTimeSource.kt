package dev.tinow.brainrotstopper.data.system

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import dev.tinow.brainrotstopper.domain.time.TimeSource
import java.time.ZoneId

/**
 * Real clocks. `elapsedRealtime` keeps counting in deep sleep and only resets on reboot, which is why grants also store
 * the boot count. If `BOOT_COUNT` cannot be read the snapshot carries -1, which [dev.tinow.brainrotstopper.domain.gate.GrantPolicy]
 * never accepts (fail-closed).
 */
class SystemTimeSource(
    private val resolver: ContentResolver,
    /** Debug only: shifts the wall clock to test that nothing decides on it. */
    private val wallOffsetMs: () -> Long = { 0L },
) : TimeSource {
    override fun now(): TimeSnapshot = TimeSnapshot(
        elapsedMs = SystemClock.elapsedRealtime(),
        wallMs = System.currentTimeMillis() + wallOffsetMs(),
        bootCount = readBootCount(),
        zone = ZoneId.systemDefault(),
    )

    private fun readBootCount(): Int =
        try {
            Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: RuntimeException) {
            -1
        }
}
