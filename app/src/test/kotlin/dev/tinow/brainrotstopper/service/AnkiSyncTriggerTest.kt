package dev.tinow.brainrotstopper.service

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.debug.fake.FakeTimeSource
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.model.SyncStamp
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.runtime.Applied
import dev.tinow.brainrotstopper.domain.runtime.RuntimeStore
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnkiSyncTriggerTest {
    private class Store : RuntimeStore {
        var rt = Runtime()
        override suspend fun current() = rt
        override suspend fun <T> apply(now: TimeSnapshot, op: (Runtime) -> Applied<T>): T = op(rt).also { rt = it.runtime }.info
        override suspend fun update(transform: (Runtime) -> Runtime) = transform(rt).also { rt = it }
    }

    private class Rig(test: TestScope, var settings: Settings = Settings()) {
        val store = Store()
        val time = FakeTimeSource()
        var sent = 0
        var accept = true
        var gateRunning = true
        val logs = mutableListOf<String>()
        private val gate = AnkiSyncSender { sent++; accept }
        val trigger = AnkiSyncTrigger(test.backgroundScope, { settings }, store, time, { gate.takeIf { gateRunning } }, { logs += it })
    }

    private fun TestScope.settle() {
        advanceTimeBy(Constants.ANKI_SYNC_DELAY_MS + 1)
        runCurrent()
    }

    @Test fun `first grant sends once after the delay and stamps it`() = runTest {
        val r = Rig(this)
        r.trigger.request()
        advanceTimeBy(Constants.ANKI_SYNC_DELAY_MS - 1); runCurrent()
        assertThat(r.sent).isEqualTo(0)
        settle()
        assertThat(r.sent).isEqualTo(1)
        assertThat(r.store.rt.ankiSync).isEqualTo(SyncStamp(r.time.elapsedMs, r.time.bootCount, r.time.wallMs))
    }

    @Test fun `a second grant inside the interval sends nothing, after it again`() = runTest {
        val r = Rig(this)
        r.trigger.request(); settle()
        r.time.advance(Constants.ANKI_SYNC_MIN_INTERVAL_MS - 1)
        r.trigger.request(); settle()
        assertThat(r.sent).isEqualTo(1)
        assertThat(r.logs).contains("sync skipped: interval")
        r.time.advance(1)
        r.trigger.request(); settle()
        assertThat(r.sent).isEqualTo(2)
    }

    @Test fun `switched off sends nothing`() = runTest {
        val r = Rig(this, Settings(ankiSyncAfterUnlock = false))
        r.trigger.request(); settle()
        assertThat(r.sent).isEqualTo(0)
        assertThat(r.store.rt.ankiSync).isNull()
    }

    @Test fun `refused or without service nothing is stamped, the next grant tries again`() = runTest {
        val r = Rig(this)
        r.accept = false
        r.trigger.request(); settle()
        assertThat(r.sent).isEqualTo(1)
        assertThat(r.store.rt.ankiSync).isNull()
        r.gateRunning = false
        r.trigger.request(); settle()
        assertThat(r.sent).isEqualTo(1)
        assertThat(r.logs).contains("sync skipped: service off")
        r.gateRunning = true; r.accept = true
        r.trigger.request(); settle()
        assertThat(r.sent).isEqualTo(2)
        assertThat(r.store.rt.ankiSync).isNotNull()
    }

    @Test fun `two requests in a row send once`() = runTest {
        val r = Rig(this)
        r.trigger.request()
        r.trigger.request()
        settle()
        assertThat(r.sent).isEqualTo(1)
    }
}
