package dev.tinow.brainrotstopper.data

import androidx.datastore.dataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.data.store.RuntimeRepository
import dev.tinow.brainrotstopper.data.store.SettingsRepository
import dev.tinow.brainrotstopper.data.store.StatsRepository
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.PendingAnswer
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.runtime.CommitInfo
import dev.tinow.brainrotstopper.domain.runtime.RuntimeOps
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StoreRepositoriesTest {
    private lateinit var dir: File
    private val jobs = mutableListOf<Job>()
    private val settingsFile get() = File(dir, "settings.json")
    private val mirrorFile get() = File(dir, "settings.mirror.json")

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        dir = File(ctx.cacheDir, "store-test-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After fun tearDown() {
        runBlocking { jobs.forEach { it.cancelAndJoin() } }
        dir.deleteRecursively()
    }

    private fun scope() = Job().also { jobs += it }.let { CoroutineScope(it + Dispatchers.IO) }

    private fun settingsRepo(scope: CoroutineScope = scope(), onCorrupted: () -> Unit = {}) =
        SettingsRepository(settingsFile, mirrorFile, scope, onCorrupted)

    /** JUnit needs `void` test methods; `runBlocking` would return the value of the last expression. */
    private fun blocking(block: suspend CoroutineScope.() -> Unit) {
        runBlocking { block() }
    }

    private fun t(elapsed: Long, boot: Int = 1) = TimeSnapshot(elapsed, 1_700_000_000_000 + elapsed, boot)

    @Test fun datastoreFilesLiveInFilesDatastore() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val f = ctx.dataStoreFile("runtime.json")
        assertThat(f.parentFile?.name).isEqualTo("datastore")
        assertThat(f.parentFile?.parentFile).isEqualTo(ctx.filesDir)
    }

    @Test fun settingsRoundtripAcrossInstances() = blocking {
        val scope1 = Job().also { jobs += it }
        val first = settingsRepo(CoroutineScope(scope1 + Dispatchers.IO))
        first.update { it.copy(blockedPackages = setOf("a", "b"), requiredCorrect = 8, unlockMinutes = 30) }
        scope1.cancelAndJoin()
        val second = settingsRepo()
        val read = second.current()
        assertThat(read.blockedPackages).containsExactly("a", "b")
        assertThat(read.requiredCorrect).isEqualTo(8)
        assertThat(read.unlockMinutes).isEqualTo(30)
    }

    @Test fun updateWritesTheMirror() = blocking {
        settingsRepo().update { it.copy(requiredCorrect = 3) }
        assertThat(mirrorFile.readText()).contains("\"requiredCorrect\":3")
        assertThat(File(dir, "settings.mirror.json.tmp").exists()).isFalse()
    }

    @Test fun outOfRangeValuesAreClampedOnWrite() = blocking {
        val updated = settingsRepo().update { it.copy(requiredCorrect = 500, unlockMinutes = 7) }
        assertThat(updated.requiredCorrect).isEqualTo(20)
        assertThat(updated.unlockMinutes).isEqualTo(15)
    }

    @Test fun corruptSettingsAreRestoredFromMirrorAndFlagged() = blocking {
        val scope1 = Job().also { jobs += it }
        settingsRepo(CoroutineScope(scope1 + Dispatchers.IO)).update { it.copy(blockedPackages = setOf("keep.me"), requiredCorrect = 9) }
        scope1.cancelAndJoin()
        settingsFile.writeText("{ this is not json")
        var corrupted = 0
        val restored = settingsRepo(onCorrupted = { corrupted++ }).current()
        assertThat(restored.blockedPackages).containsExactly("keep.me")
        assertThat(restored.requiredCorrect).isEqualTo(9)
        assertThat(corrupted).isEqualTo(1)
    }

    @Test fun emptyFileCountsAsCorrupt() = blocking {
        val scope1 = Job().also { jobs += it }
        settingsRepo(CoroutineScope(scope1 + Dispatchers.IO)).update { it.copy(requiredCorrect = 4) }
        scope1.cancelAndJoin()
        settingsFile.writeText("")
        var corrupted = 0
        assertThat(settingsRepo(onCorrupted = { corrupted++ }).current().requiredCorrect).isEqualTo(4)
        assertThat(corrupted).isEqualTo(1)
    }

    @Test fun corruptSettingsAndCorruptMirrorGiveDefaultsAndFlag() = blocking {
        val scope1 = Job().also { jobs += it }
        settingsRepo(CoroutineScope(scope1 + Dispatchers.IO)).update { it.copy(requiredCorrect = 9) }
        scope1.cancelAndJoin()
        settingsFile.writeText("garbage")
        mirrorFile.writeText("also garbage")
        var corrupted = 0
        val read = settingsRepo(onCorrupted = { corrupted++ }).current()
        assertThat(read).isEqualTo(Settings())
        assertThat(corrupted).isEqualTo(1)
    }

    @Test fun corruptSettingsWithoutMirrorGiveDefaultsAndFlag() = blocking {
        settingsFile.writeText("{")
        var corrupted = 0
        assertThat(settingsRepo(onCorrupted = { corrupted++ }).current()).isEqualTo(Settings())
        assertThat(corrupted).isEqualTo(1)
    }

    @Test fun corruptionFlagLandsInRuntimeDiag() = blocking {
        val stats = StatsRepository(File(dir, "stats.json"), scope())
        val runtime = RuntimeRepository(File(dir, "runtime.json"), scope(), stats)
        settingsFile.writeText("{")
        val repo = settingsRepo(onCorrupted = { launchMark(runtime) })
        repo.current()
        withTimeout(5_000) { runtime.flow.first { it.diag.settingsCorrupted } }
    }

    private fun launchMark(runtime: RuntimeRepository) {
        CoroutineScope(Dispatchers.IO).launch { runtime.markSettingsCorrupted() }
    }

    @Test fun parallelUpdatesLoseNothing() = blocking {
        val repo = settingsRepo()
        (1..60).map { i -> async(Dispatchers.IO) { repo.update { it.copy(blockedPackages = it.blockedPackages + "pkg.$i") } } }.awaitAll()
        assertThat(repo.current().blockedPackages).hasSize(60)
    }

    @Test fun runtimeRoundtripAndParallelGrants() = blocking {
        val stats = StatsRepository(File(dir, "stats.json"), scope())
        val repo = RuntimeRepository(File(dir, "runtime.json"), scope(), stats)
        (1..40).map { i ->
            async(Dispatchers.IO) { repo.update { rt -> rt.copy(grants = rt.grants + ("p$i" to dev.tinow.brainrotstopper.domain.gate.GrantPolicy.create(GrantKind.QUIZ, 60_000, t(1_000)))) } }
        }.awaitAll()
        assertThat(repo.current().grants).hasSize(40)
    }

    @Test fun commitThatFailsInTheMiddleLeavesNoIntermediateState() = blocking {
        val stats = StatsRepository(File(dir, "stats.json"), scope())
        val repo = RuntimeRepository(File(dir, "runtime.json"), scope(), stats)
        val settings = Settings(requiredCorrect = 1)
        repo.apply(t(0)) { RuntimeOps.attach(it, "a", settings, t(0)) { "s" } }
        repo.update { RuntimeOps.setPending(it, PendingAnswer("x", 1, 0, Ease.GOOD, 100, null), t(10)) }
        val before = repo.current()
        runCatching {
            repo.apply<CommitInfo>(t(20)) { rt ->
                RuntimeOps.commitAnswer(rt, t(20)) // computed ...
                error("process dies before the write completes") // ... but never returned
            }
        }
        assertThat(repo.current()).isEqualTo(before)
        // Only the attach (one block) was recorded; the failed commit added no answer.
        val day = stats.current().days.values.single()
        assertThat(day.blocks).isEqualTo(1)
        assertThat(day.answered).isEqualTo(0)
    }

    @Test fun commitAnswerWithUnlockIsOneAtomicWriteAndRecordsStats() = blocking {
        val stats = StatsRepository(File(dir, "stats.json"), scope())
        val repo = RuntimeRepository(File(dir, "runtime.json"), scope(), stats)
        val settings = Settings(requiredCorrect = 1)
        repo.apply(t(0)) { RuntimeOps.attach(it, "a", settings, t(0)) { "s" } }
        repo.update { RuntimeOps.setPending(it, PendingAnswer("x", 1, 0, Ease.GOOD, 100, null), t(10)) }

        val seen = mutableListOf<Runtime>()
        val collector = scope().launch { repo.flow.collect { seen += it } }
        withTimeout(5_000) { while (seen.isEmpty()) kotlinx.coroutines.delay(10) }

        val info = repo.apply(t(50)) { RuntimeOps.commitAnswer(it, t(50)) }
        assertThat(info.unlocked).isTrue()
        withTimeout(5_000) { while (seen.none { it.grants.isNotEmpty() }) kotlinx.coroutines.delay(10) }
        collector.cancelAndJoin()

        // Every observed state is either "answer still pending" or "grant present and session gone": never in between.
        for (rt in seen) {
            val before = rt.session?.pending != null
            val after = rt.grants.isNotEmpty() && rt.session == null && rt.episode == null
            assertThat(before || after).isTrue()
        }
        val day = stats.current().days.values.single()
        assertThat(day.unlocks).isEqualTo(1)
        assertThat(day.answered).isEqualTo(1)
    }

    @Test fun corruptRuntimeAndStatsRestartEmptyFailClosed() = blocking {
        File(dir, "runtime.json").writeText("nope")
        File(dir, "stats.json").writeText("nope")
        val stats = StatsRepository(File(dir, "stats.json"), scope())
        val repo = RuntimeRepository(File(dir, "runtime.json"), scope(), stats)
        assertThat(repo.current()).isEqualTo(Runtime())
        assertThat(repo.current().grants).isEmpty()
        assertThat(stats.current().days).isEmpty()
    }
}
