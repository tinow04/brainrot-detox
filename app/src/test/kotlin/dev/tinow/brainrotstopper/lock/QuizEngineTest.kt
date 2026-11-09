package dev.tinow.brainrotstopper.lock

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.debug.fake.FakeCardSource
import dev.tinow.brainrotstopper.debug.fake.FakeTimeSource
import dev.tinow.brainrotstopper.domain.anki.AnkiCapabilities
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.gate.GrantPolicy
import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.quiz.Phase
import dev.tinow.brainrotstopper.domain.runtime.Applied
import dev.tinow.brainrotstopper.domain.runtime.EmergencyButton
import dev.tinow.brainrotstopper.domain.runtime.RuntimeStore
import dev.tinow.brainrotstopper.domain.stats.plus
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import dev.tinow.brainrotstopper.hooks.DebugFlags
import dev.tinow.brainrotstopper.lock.quiz.QuizCommand
import dev.tinow.brainrotstopper.lock.quiz.QuizEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** `runtime.json` in memory; the hooks fire around every atomic apply so tests can end an engine at an exact step. */
private class MemoryRuntimeStore : RuntimeStore {
    var rt = Runtime()
    var stats = Stats()
    var beforeApply: suspend () -> Unit = {}
    var afterApply: suspend () -> Unit = {}
    var failApply = false

    override suspend fun current() = rt

    override suspend fun <T> apply(now: TimeSnapshot, op: (Runtime) -> Applied<T>): T {
        beforeApply()
        check(!failApply) { "disk full" }
        val applied = op(rt)
        rt = applied.runtime
        stats = stats.plus(now.epochDay, applied.delta)
        afterApply()
        return applied.info
    }

    override suspend fun update(transform: (Runtime) -> Runtime): Runtime {
        rt = transform(rt)
        return rt
    }
}

private class Flags(override val unlockSecondsOverride: Int? = null, override val sessionTtlMsOverride: Long? = null) : DebugFlags

/** Ends the calling coroutine's scope as if the process had been killed at this point. */
private suspend fun kill(scope: CoroutineScope): Nothing {
    scope.cancel()
    currentCoroutineContext().ensureActive()
    error("unreachable")
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuizEngineTest {
    private val pkg = "com.example.target"
    private val other = "com.example.other"

    private class Rig(
        val tests: TestScope,
        val source: FakeCardSource = FakeCardSource(FakeCardSource.cards(30)),
        val verify: Boolean = true,
        required: Int = 5,
        flags: DebugFlags = Flags(),
        blocked: Set<String> = setOf("com.example.target", "com.example.other"),
    ) {
        val time = FakeTimeSource()
        val store = MemoryRuntimeStore()
        val settings = Settings(blockedPackages = blocked, requiredCorrect = required)
        val flagsImpl = flags
        val scopes = mutableListOf<CoroutineScope>()
        var imagesSeen = emptyList<String>()
        var images: suspend (List<String>) -> Map<String, String> = { names -> imagesSeen = names; emptyMap() }

        fun scope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(tests.testScheduler)).also { scopes += it }

        fun engine(scope: CoroutineScope = scope()) = QuizEngine(
            scope = scope,
            source = source,
            runtime = store,
            settings = { settings },
            stats = { store.stats },
            time = time,
            capabilities = AnkiCapabilities(answerVerify = verify),
            images = { images(it) },
            labelOf = { it.substringAfterLast('.') },
            newId = run { var n = 0; { "id-${n++}" } },
            flags = flagsImpl,
        )

        val day get() = store.stats.days[time.now().epochDay]
    }

    private fun TestScope.rig(verify: Boolean = true, required: Int = 5, source: FakeCardSource = FakeCardSource(FakeCardSource.cards(30)), flags: DebugFlags = Flags()) =
        Rig(this, source, verify, required, flags)

    /** Attaches, waits for the intro and presses [start]; ends on the first card. */
    private fun TestScope.begin(e: QuizEngine, target: String = pkg) {
        e.attach(target)
        runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Intro)
        e.start()
        runCurrent()
    }

    private fun TestScope.rate(r: Rig, e: QuizEngine, ease: Ease) {
        assertThat(e.phase).isEqualTo(Phase.Question)
        r.time.advance(Constants.RATE_GUARD_MS + 100)
        e.reveal()
        r.time.advance(Constants.RATE_GUARD_MS + 100)
        e.rate(ease)
        runCurrent()
    }

    private fun TestScope.good(r: Rig, e: QuizEngine, times: Int) = repeat(times) { rate(r, e, Ease.GOOD) }

    // ------------------------------------------------------------------ intro and the happy path

    @Test fun `intro does not touch AnkiDroid until start`() = runTest {
        val r = rig()
        val e = r.engine()
        e.attach(pkg)
        runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Intro)
        assertThat(r.source.totalCalls).isEqualTo(0)
        assertThat(e.view.value.intro?.required).isEqualTo(5)
        e.start()
        runCurrent()
        assertThat(r.source.calls["nextCard"]).isEqualTo(1)
        assertThat(e.phase).isEqualTo(Phase.Question)
    }

    @Test fun `five good answers unlock the target for the session duration`() = runTest {
        for (verify in listOf(true, false)) {
            val r = rig(verify)
            val e = r.engine()
            begin(e)
            good(r, e, 5)
            assertThat(e.phase).isEqualTo(Phase.Unlocked)
            assertThat(e.view.value.quiz.unlockKind).isEqualTo(GrantKind.QUIZ)
            val grant = r.store.rt.grants.getValue(pkg)
            assertThat(grant.kind).isEqualTo(GrantKind.QUIZ)
            assertThat(r.store.rt.session).isNull()
            assertThat(r.store.rt.episode).isNull()
            assertThat(r.day).isEqualTo(dev.tinow.brainrotstopper.domain.model.DayStats(blocks = 1, unlocks = 1, answered = 5, correct = 5, perApp = mapOf(pkg to 1)))
            assertThat(e.view.value.unlock?.answered).isEqualTo(5)
            assertThat(e.view.value.unlock?.minutes).isEqualTo(Constants.DEFAULT_DURATION_MIN)
        }
    }

    @Test fun `five right with two misses unlocks after seven cards and the misses do not count`() = runTest {
        for (verify in listOf(true, false)) {
            val r = rig(verify)
            val e = r.engine()
            begin(e)
            good(r, e, 2)
            rate(r, e, Ease.AGAIN)
            assertThat(e.phase).isEqualTo(Phase.Feedback)
            assertThat(e.view.value.quiz.correct).isEqualTo(2)
            e.next(); runCurrent()
            good(r, e, 1)
            rate(r, e, Ease.HARD)
            assertThat(e.phase).isEqualTo(Phase.Feedback)
            assertThat(e.view.value.quiz.correct).isEqualTo(3)
            e.next(); runCurrent()
            good(r, e, 2)
            assertThat(e.phase).isEqualTo(Phase.Unlocked)
            assertThat(r.store.stats.days.values.sumOf { it.answered }).isEqualTo(7)
            assertThat(r.store.stats.days.values.sumOf { it.correct }).isEqualTo(5)
            assertThat(r.source.answers).hasSize(7)
        }
    }

    @Test fun `the missed card comes back and the next card is requested after every answer`() = runTest {
        val r = rig()
        val e = r.engine()
        begin(e)
        val first = e.view.value.quiz.card!!
        rate(r, e, Ease.AGAIN)
        e.next(); runCurrent()
        assertThat(e.view.value.quiz.card).isNotEqualTo(first)
        assertThat(r.source.pending).isEqualTo(30)
    }

    @Test fun `cards carry interval labels and images from the first load`() = runTest {
        val r = rig()
        val e = r.engine()
        r.images = { names -> r.imagesSeen = names; names.associateWith { "data:image/png;base64,AAAA" } }
        begin(e)
        // the first fixture is the html card (no image); advance to the image card (fourth in the pool)
        assertThat(e.view.value.card?.intervals).containsExactly("<1 min", "<6 min", "1 d", "4 d").inOrder()
        good(r, e, 3)
        assertThat(r.imagesSeen).containsExactly("bild ä 1.png")
        assertThat(e.view.value.card?.images).containsKey("bild ä 1.png")
    }

    @Test fun `a failing image fetch never blocks the card`() = runTest {
        val r = rig()
        r.images = { throw IllegalStateException("boom") }
        val e = r.engine()
        begin(e)
        good(r, e, 3) // the image card
        assertThat(e.phase).isEqualTo(Phase.Question)
        assertThat(e.view.value.card?.images).isEmpty()
    }

    // ------------------------------------------------------------------ resume

    @Test fun `a new engine resumes the session with its progress`() = runTest {
        val r = rig()
        val e = r.engine()
        begin(e)
        good(r, e, 2)
        e.release()
        val second = r.engine()
        second.attach(pkg)
        runCurrent()
        assertThat(second.phase).isEqualTo(Phase.Intro)
        assertThat(second.view.value.intro?.correct).isEqualTo(2)
        assertThat(second.view.value.intro?.resumed).isTrue()
    }

    @Test fun `switching to another blocked app keeps the progress`() = runTest {
        val r = rig()
        val e = r.engine()
        begin(e)
        good(r, e, 3)
        r.time.advance(5_000)
        e.attach(other)
        runCurrent()
        assertThat(e.view.value.quiz.target).isEqualTo(other)
        assertThat(e.view.value.quiz.correct).isEqualTo(3)
        assertThat(r.store.rt.session?.targetPkg).isEqualTo(other)
    }

    @Test fun `unblocked or already granted targets never show the lock`() = runTest {
        val r = rig()
        val e = r.engine()
        val commands = mutableListOf<QuizCommand>()
        val scope = r.scope()
        scope.launchCollect(e, commands)
        e.attach("com.example.free")
        runCurrent()
        assertThat(commands).containsExactly(QuizCommand.Finish)
        assertThat(e.phase).isEqualTo(Phase.Idle)
        assertThat(r.source.totalCalls).isEqualTo(0)


        // a valid grant that appeared in the meantime: open the target instead of locking
        r.store.rt = r.store.rt.copy(grants = mapOf(pkg to GrantPolicy.create(GrantKind.QUIZ, 60_000, r.time.now())))
        commands.clear()
        e.attach(pkg)
        runCurrent()
        assertThat(commands).containsExactly(QuizCommand.StartTarget(pkg))
        assertThat(r.source.totalCalls).isEqualTo(0)
    }

    // ------------------------------------------------------------------ failures never count

    @Test fun `an answer that fails to send is not counted and the retry loads again`() = runTest {
        for (verify in listOf(true, false)) {
            val r = rig(verify)
            val e = r.engine()
            begin(e)
            r.source.failing["answer"] = AnkiError.Unreachable
            rate(r, e, Ease.GOOD)
            assertThat(e.phase).isEqualTo(Phase.Error)
            assertThat(e.view.value.quiz.error).isEqualTo(AnkiError.Unreachable)
            assertThat(r.store.rt.session?.correct).isEqualTo(0)
            assertThat(r.store.rt.session?.answered).isEqualTo(0)
            assertThat(r.store.stats.days.values.sumOf { it.answered }).isEqualTo(0)
            r.source.failing.clear()
            e.retry(); runCurrent()
            assertThat(e.phase).isEqualTo(Phase.Question)
        }
    }

    @Test fun `an answer AnkiDroid ignores ends in answer not saved after one resend and is not counted`() = runTest {
        val r = rig()
        r.source.answerApplies = false
        val e = r.engine()
        begin(e)
        rate(r, e, Ease.GOOD)
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(e.view.value.quiz.error).isEqualTo(AnkiError.AnswerNotSaved)
        assertThat(r.source.answers).hasSize(2) // sent once, resent once
        assertThat(r.store.rt.session?.pending).isNotNull() // kept for the recovery
        assertThat(r.store.rt.session?.correct).isEqualTo(0)
        r.source.answerApplies = true
        e.retry(); runCurrent()
        assertThat(r.store.rt.session?.pending).isNull() // the user retries: pending dropped, nothing counted
        assertThat(r.store.rt.session?.answered).isEqualTo(0)
        assertThat(e.phase).isEqualTo(Phase.Question)
    }

    @Test fun `an answer applied despite an error is counted once with verification`() = runTest {
        val r = rig()
        r.source.answerAppliesButFails = AnkiError.Unreachable
        val e = r.engine()
        begin(e)
        rate(r, e, Ease.GOOD)
        assertThat(r.source.answers).hasSize(1)
        assertThat(r.store.rt.session?.correct).isEqualTo(1)
        assertThat(r.store.rt.session?.answered).isEqualTo(1)
        assertThat(e.phase).isEqualTo(Phase.Question)
    }

    @Test fun `without verification an error is not counted and not resent`() = runTest {
        val r = rig(verify = false)
        r.source.answerAppliesButFails = AnkiError.Unreachable
        val e = r.engine()
        begin(e)
        rate(r, e, Ease.GOOD)
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(r.source.answers).hasSize(1)
        assertThat(r.store.rt.session?.correct).isEqualTo(0)
    }

    @Test fun `a progress read error before the answer sends nothing`() = runTest {
        val r = rig()
        val e = r.engine()
        begin(e)
        r.source.failing["cardProgress"] = AnkiError.Unreachable
        rate(r, e, Ease.GOOD)
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(r.source.answers).isEmpty()
        assertThat(r.store.rt.session?.pending).isNull()
    }

    @Test fun `a failing commit does not crash and counts nothing`() = runTest {
        val r = rig()
        val e = r.engine()
        begin(e)
        r.store.failApply = true
        rate(r, e, Ease.GOOD)
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(e.view.value.quiz.error).isInstanceOf(AnkiError.Unexpected::class.java)
        assertThat(r.store.rt.session?.correct).isEqualTo(0)
    }

    // ------------------------------------------------------------------ process death at every pipeline step

    /** Runs one answer, ends the engine at [stage], and returns the rig plus the engine that took over. */
    private fun TestScope.killAt(stage: String, verify: Boolean): Pair<Rig, QuizEngine> {
        val r = rig(verify)
        val scopeA = r.scope()
        val a = r.engine(scopeA)
        when (stage) {
            "before-progress" -> r.source.onCall = { if (it == "cardProgress") kill(scopeA) }
            "before-send" -> r.source.beforeAnswer = { kill(scopeA) }
            "after-send" -> r.source.afterAnswer = { kill(scopeA) }
            "before-commit" -> {
                var armed = false
                r.source.afterAnswer = { armed = true }
                r.store.beforeApply = { if (armed) kill(scopeA) }
            }
            "after-commit" -> {
                var armed = false
                r.source.afterAnswer = { armed = true }
                r.store.afterApply = { if (armed) kill(scopeA) }
            }
        }
        begin(a)
        // arm the hook only for the rating: loading the card must not trigger it
        rate(r, a, Ease.GOOD)
        r.source.onCall = {}; r.source.beforeAnswer = {}; r.source.afterAnswer = {}
        r.store.beforeApply = {}; r.store.afterApply = {}
        val b = r.engine()
        b.attach(pkg)
        runCurrent()
        return r to b
    }

    @Test fun `kill before the progress read - nothing happened`() = runTest {
        val (r, b) = killAt("before-progress", verify = true)
        assertThat(r.source.answers).isEmpty()
        assertThat(b.phase).isEqualTo(Phase.Intro)
        assertThat(r.store.rt.session?.correct).isEqualTo(0)
    }

    @Test fun `kill before the send - recovery sends once and counts once`() = runTest {
        val (r, b) = killAt("before-send", verify = true)
        assertThat(r.source.answers).hasSize(1)
        assertThat(r.source.repsOf(CardRef(100, 0))).isEqualTo(1)
        assertThat(r.store.rt.session?.correct).isEqualTo(1)
        assertThat(r.store.rt.session?.answered).isEqualTo(1)
        assertThat(r.store.rt.session?.pending).isNull()
        assertThat(b.phase).isEqualTo(Phase.Question) // recovery has no intro: it continues with the next card
    }

    @Test fun `kill after the send - recovery commits without sending again`() = runTest {
        val (r, b) = killAt("after-send", verify = true)
        assertThat(r.source.answers).hasSize(1)
        assertThat(r.store.rt.session?.correct).isEqualTo(1)
        assertThat(r.store.rt.session?.answered).isEqualTo(1)
        assertThat(r.store.stats.days.values.sumOf { it.answered }).isEqualTo(1)
        assertThat(b.phase).isEqualTo(Phase.Question)
    }

    @Test fun `kill before the commit - recovery commits once`() = runTest {
        val (r, b) = killAt("before-commit", verify = true)
        assertThat(r.source.answers).hasSize(1)
        assertThat(r.store.rt.session?.correct).isEqualTo(1)
        assertThat(r.store.stats.days.values.sumOf { it.answered }).isEqualTo(1)
        assertThat(b.phase).isEqualTo(Phase.Question)
    }

    @Test fun `kill after the commit - nothing is counted twice`() = runTest {
        val (r, b) = killAt("after-commit", verify = true)
        assertThat(r.source.answers).hasSize(1)
        assertThat(r.store.rt.session?.correct).isEqualTo(1)
        assertThat(r.store.rt.session?.answered).isEqualTo(1)
        assertThat(r.store.stats.days.values.sumOf { it.answered }).isEqualTo(1)
        assertThat(b.phase).isEqualTo(Phase.Intro) // the commit is done: a plain intro with the new progress
        assertThat(b.view.value.intro?.correct).isEqualTo(1)
    }

    @Test fun `without verification a kill before the send drops the answer without resending`() = runTest {
        val (r, b) = killAt("before-send", verify = false)
        assertThat(r.source.answers).isEmpty()
        assertThat(r.store.rt.session?.pending).isNull()
        assertThat(r.store.rt.session?.correct).isEqualTo(0)
        assertThat(b.phase).isEqualTo(Phase.Question)
    }

    @Test fun `without verification a kill after the send never answers twice`() = runTest {
        val (r, b) = killAt("after-send", verify = false)
        assertThat(r.source.answers).hasSize(1)
        assertThat(r.store.rt.session?.pending).isNull()
        assertThat(r.store.rt.session?.correct).isEqualTo(0) // applied in Anki but not counted: the documented product consequence
        assertThat(b.phase).isEqualTo(Phase.Question)
    }

    @Test fun `a kill during the fifth answer still unlocks exactly once`() = runTest {
        val r = rig()
        val scopeA = r.scope()
        val a = r.engine(scopeA)
        begin(a)
        good(r, a, 4)
        r.source.afterAnswer = { kill(scopeA) }
        rate(r, a, Ease.GOOD)
        r.source.afterAnswer = {}
        val b = r.engine()
        b.attach(pkg)
        runCurrent()
        assertThat(b.phase).isEqualTo(Phase.Unlocked)
        assertThat(r.store.rt.grants).containsKey(pkg)
        assertThat(r.store.stats.days.values.sumOf { it.unlocks }).isEqualTo(1)
        assertThat(r.store.stats.days.values.sumOf { it.answered }).isEqualTo(5)
        assertThat(r.source.answers).hasSize(5)
    }

    // ------------------------------------------------------------------ empty deck, waiting, errors

    @Test fun `an empty deck frees the target without a quiz and counts a free unlock`() = runTest {
        val r = rig(source = FakeCardSource())
        val e = r.engine()
        begin(e)
        assertThat(e.phase).isEqualTo(Phase.Unlocked)
        assertThat(e.view.value.quiz.unlockKind).isEqualTo(GrantKind.NOTHING_DUE)
        assertThat(r.store.rt.grants.getValue(pkg).kind).isEqualTo(GrantKind.NOTHING_DUE)
        assertThat(r.day?.freeUnlocks).isEqualTo(1)
        assertThat(r.day?.unlocks).isEqualTo(0)
    }

    @Test fun `waiting polls every interval and stops while the screen is hidden`() = runTest {
        val r = rig(source = FakeCardSource().also { it.learning = 1 })
        val e = r.engine()
        begin(e)
        assertThat(e.phase).isEqualTo(Phase.Waiting)
        val first = r.source.calls["nextCard"]!!
        advanceTimeBy(Constants.WAIT_POLL_MS + 1)
        runCurrent()
        assertThat(r.source.calls["nextCard"]).isEqualTo(first + 1)
        e.setVisible(false)
        advanceTimeBy(Constants.WAIT_POLL_MS * 4)
        runCurrent()
        assertThat(r.source.calls["nextCard"]).isEqualTo(first + 1)
        e.setVisible(true)
        runCurrent()
        assertThat(r.source.calls["nextCard"]).isEqualTo(first + 2)
        assertThat(e.phase).isEqualTo(Phase.Waiting)
        assertThat(r.store.rt.grants).isEmpty() // no time based release
        e.cancel(); runCurrent()
    }

    @Test fun `every anki error keeps the lock closed and shows an error`() = runTest {
        val errors = listOf(
            AnkiError.NotInstalledOrApiOff, AnkiError.PermissionMissing, AnkiError.StorageNotConfigured,
            AnkiError.Unreachable, AnkiError.DeckMissing, AnkiError.BadData,
        )
        for (error in errors) {
            val r = rig()
            r.source.failing["nextCard"] = error
            val e = r.engine()
            begin(e)
            assertThat(e.phase).isEqualTo(Phase.Error)
            assertThat(e.view.value.quiz.error).isEqualTo(error)
            assertThat(r.store.rt.grants).isEmpty()
            assertThat(QuizEngine.errorTexts(error).title).startsWith("[ FEHLER ]")
        }
    }

    @Test fun `unreadable counts on an empty cursor are an error and never nothing due`() = runTest {
        val r = rig(source = FakeCardSource())
        r.source.failing["deckCounts"] = AnkiError.Unreachable
        val e = r.engine()
        begin(e)
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(r.store.rt.grants).isEmpty()
    }

    @Test fun `a card without any content is bad data`() = runTest {
        val blank = FakeCardSource.cards(1).map { it.copy(question = "", answer = "", answerPure = "") }
        val r = rig(source = FakeCardSource(blank))
        val e = r.engine()
        begin(e)
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(e.view.value.quiz.error).isEqualTo(AnkiError.BadData)
    }

    @Test fun `retry after an error loads the card again`() = runTest {
        val r = rig()
        r.source.failing["nextCard"] = AnkiError.Unreachable
        val e = r.engine()
        begin(e)
        r.source.failing.clear()
        e.retry(); runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Question)
    }

    // ------------------------------------------------------------------ emergency exit (M4-T6)

    private fun TestScope.toError(r: Rig, target: String = pkg): QuizEngine {
        r.source.failing["nextCard"] = AnkiError.Unreachable
        val e = r.engine()
        begin(e, target)
        assertThat(e.phase).isEqualTo(Phase.Error)
        return e
    }

    @Test fun `emergency exit only after 60 s on the error screen, then 5 minutes for the target`() = runTest {
        val r = rig()
        val e = toError(r)
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Wait(60))
        r.time.advance(Constants.EMERGENCY_WAIT_MS - 1)
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Wait(1))
        e.emergency(); runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(r.store.rt.grants).isEmpty()

        r.time.advance(1)
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Ready)
        e.emergency(); runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Unlocked)
        assertThat(e.view.value.quiz.unlockKind).isEqualTo(GrantKind.EMERGENCY)
        assertThat(e.view.value.unlock?.minutes).isEqualTo(Constants.EMERGENCY_GRANT_MIN)
        val grant = r.store.rt.grants.getValue(pkg)
        assertThat(grant.kind).isEqualTo(GrantKind.EMERGENCY)
        assertThat(grant.untilElapsedMs - grant.grantedAtElapsedMs).isEqualTo(Constants.EMERGENCY_GRANT_MIN * 60_000L)
        assertThat(GrantPolicy.isValid(grant, r.time.now())).isTrue()
        assertThat(r.day?.emergencies).isEqualTo(1)
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Hidden)
    }

    @Test fun `retrying does not restart the emergency countdown`() = runTest {
        val r = rig()
        val e = toError(r)
        r.time.advance(Constants.EMERGENCY_WAIT_MS - 5_000)
        e.retry(); runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Wait(5))
    }

    @Test fun `a second emergency exit within 6 h is refused, also for another app`() = runTest {
        val r = rig()
        val first = toError(r)
        r.time.advance(Constants.EMERGENCY_WAIT_MS)
        first.emergency(); runCurrent()
        assertThat(first.phase).isEqualTo(Phase.Unlocked)
        val usedAt = r.time.elapsedMs

        val e = toError(r, other)
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Cooldown(Constants.EMERGENCY_COOLDOWN_MS))
        r.time.advance(Constants.EMERGENCY_WAIT_MS)
        e.emergency(); runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Error) // refused: the lock stays closed
        assertThat(r.store.rt.grants).doesNotContainKey(other)
        assertThat(r.day?.emergencies).isEqualTo(1)
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Cooldown(Constants.EMERGENCY_COOLDOWN_MS - Constants.EMERGENCY_WAIT_MS))

        r.time.advance(usedAt + Constants.EMERGENCY_COOLDOWN_MS - r.time.elapsedMs)
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Ready)
        e.emergency(); runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Unlocked)
        assertThat(r.store.rt.grants.getValue(other).kind).isEqualTo(GrantKind.EMERGENCY)
    }

    @Test fun `a reboot does not lift the cooldown`() = runTest {
        val r = rig()
        val first = toError(r)
        r.time.advance(Constants.EMERGENCY_WAIT_MS)
        first.emergency(); runCurrent()
        val used = r.store.rt.emergency!!
        assertThat(used.bootCount).isEqualTo(r.time.bootCount)

        // reboot 30 minutes later, 10 minutes up; the engine is new (new process)
        r.time.bootCount += 1
        r.time.elapsedMs = 10 * 60_000L
        r.time.wallMs = used.lastUsedWall + 30 * 60_000L
        val e = toError(r) // the old grant died with the reboot: the lock is back
        r.time.advance(Constants.EMERGENCY_WAIT_MS)
        assertThat(e.emergencyButton()).isInstanceOf(EmergencyButton.Cooldown::class.java)
        e.emergency(); runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Error)
        assertThat(r.store.rt.grants).isEmpty()

        // the phone was off long enough: the wall clock shows 6 h since the use
        r.time.wallMs = used.lastUsedWall + Constants.EMERGENCY_COOLDOWN_MS
        assertThat(e.emergencyButton()).isEqualTo(EmergencyButton.Ready)
        e.emergency(); runCurrent()
        assertThat(e.phase).isEqualTo(Phase.Unlocked)
    }

    // ------------------------------------------------------------------ cancel and guards

    @Test fun `cancel in the question goes home, counts an aborted block and keeps the session`() = runTest {
        val r = rig()
        val e = r.engine()
        val commands = mutableListOf<QuizCommand>()
        r.scope().launchCollect(e, commands)
        begin(e)
        good(r, e, 2)
        e.cancel(); runCurrent()
        assertThat(commands).containsExactly(QuizCommand.GoHome)
        assertThat(r.day?.aborted).isEqualTo(1)
        assertThat(r.store.rt.session?.correct).isEqualTo(2)
        assertThat(r.store.rt.episode).isNull()
    }

    @Test fun `ratings inside the guard time are dropped`() = runTest {
        val r = rig()
        val e = r.engine()
        begin(e)
        e.reveal() // right after the question appeared
        assertThat(e.phase).isEqualTo(Phase.Question)
        r.time.advance(700)
        e.reveal()
        assertThat(e.phase).isEqualTo(Phase.Answer)
        e.rate(Ease.GOOD) // right after the reveal
        assertThat(e.phase).isEqualTo(Phase.Answer)
        assertThat(r.source.answers).isEmpty()
    }

    @Test fun `a double tap on a rating sends once`() = runTest {
        val r = rig()
        val e = r.engine()
        begin(e)
        r.time.advance(700); e.reveal(); r.time.advance(700)
        e.rate(Ease.GOOD); e.rate(Ease.GOOD)
        runCurrent()
        assertThat(r.source.answers).hasSize(1)
    }

    @Test fun `open target after the unlock starts the app`() = runTest {
        val r = rig()
        val e = r.engine()
        val commands = mutableListOf<QuizCommand>()
        r.scope().launchCollect(e, commands)
        begin(e)
        good(r, e, 5)
        e.openTarget(); runCurrent()
        assertThat(commands).containsExactly(QuizCommand.StartTarget(pkg))
    }

    @Test fun `closing the unlock screen goes home without counting anything`() = runTest {
        val r = rig()
        val e = r.engine()
        val commands = mutableListOf<QuizCommand>()
        r.scope().launchCollect(e, commands)
        begin(e)
        good(r, e, 5)
        e.cancel(); runCurrent()
        assertThat(commands).containsExactly(QuizCommand.GoHome)
        assertThat(r.day?.aborted).isEqualTo(0)
        assertThat(r.store.rt.grants).containsKey(pkg)
    }

    // ------------------------------------------------------------------ debug overrides

    @Test fun `the short duration override shortens the grant`() = runTest {
        val r = rig(flags = Flags(unlockSecondsOverride = 30))
        val e = r.engine()
        begin(e)
        good(r, e, 5)
        val grant = r.store.rt.grants.getValue(pkg)
        assertThat(grant.durationMs).isEqualTo(30_000L)
    }

    @Test fun `the ttl override expires the session early`() = runTest {
        val r = rig(flags = Flags(sessionTtlMsOverride = 2_000))
        val e = r.engine()
        begin(e)
        good(r, e, 2)
        e.release()
        r.time.advance(3_000)
        val b = r.engine()
        b.attach(pkg)
        runCurrent()
        assertThat(b.view.value.intro?.correct).isEqualTo(0)
    }

    // ------------------------------------------------------------------ helpers

    private fun CoroutineScope.launchCollect(e: QuizEngine, into: MutableList<QuizCommand>) {
        launch { e.commands.collect { into += it } }
    }
}
