package dev.tinow.brainrotstopper.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.data.system.SystemTimeSource
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemTimeSourceTest {
    private val resolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    /** `Settings.Global.BOOT_COUNT` is a non-negative counter, or -1 when it cannot be read (then no grant is ever valid). */
    @Test fun bootCountIsNonNegativeOrMinusOne() {
        val boot = SystemTimeSource(resolver).now().bootCount
        assertThat(boot == -1 || boot >= 0).isTrue()
        println("BSTest bootCount=$boot")
    }

    @Test fun elapsedIsMonotonicAndWallMatchesSystemClock() {
        val source = SystemTimeSource(resolver)
        val a = source.now()
        Thread.sleep(20)
        val b = source.now()
        assertThat(b.elapsedMs).isAtLeast(a.elapsedMs + 15)
        assertThat(b.bootCount).isEqualTo(a.bootCount)
        assertThat(Math.abs(b.wallMs - System.currentTimeMillis())).isLessThan(1_000)
    }

    @Test fun wallOffsetShiftsOnlyWallTime() {
        var offset = 0L
        val source = SystemTimeSource(resolver) { offset }
        val base = source.now()
        offset = 3_600_000
        val shifted = source.now()
        assertThat(shifted.wallMs - base.wallMs).isAtLeast(3_600_000)
        assertThat(shifted.elapsedMs - base.elapsedMs).isLessThan(1_000)
    }
}
