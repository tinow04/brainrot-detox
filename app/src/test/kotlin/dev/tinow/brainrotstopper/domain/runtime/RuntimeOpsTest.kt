package dev.tinow.brainrotstopper.domain.runtime

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.gate.GrantPolicy
import dev.tinow.brainrotstopper.domain.model.EmergencyState
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.PendingAnswer
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.quiz.SessionResolution
import dev.tinow.brainrotstopper.domain.stats.StatsDelta
import dev.tinow.brainrotstopper.domain.stats.plus
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test
import java.util.Random

class RuntimeOpsTest {
    private val settings = Settings(requiredCorrect = 3, unlockMinutes = 15)
    private fun t(elapsed: Long, boot: Int = 1) = TimeSnapshot(elapsed, 1_700_000_000_000 + elapsed, boot)
    private var ids = 0
    private fun id() = "id${ids++}"

    private fun attach(rt: Runtime, pkg: String, now: Long, boot: Int = 1) = RuntimeOps.attach(rt, pkg, settings, t(now, boot), newId = ::id)

    private fun rate(rt: Runtime, ease: Ease, now: Long): Applied<CommitInfo> {
        val withPending = RuntimeOps.setPending(rt, PendingAnswer(id(), 1, 0, ease, 2_000, 5), t(now))
        return RuntimeOps.commitAnswer(withPending, t(now))
    }

    // --- episode / block counting ---------------------------------------------------------------------------------

    @Test fun firstAttachOpensEpisodeAndCountsOneBlock() {
        val a = attach(Runtime(), "a", 1_000)
        assertThat(a.delta).isEqualTo(StatsDelta(blocks = 1, perApp = mapOf("a" to 1)))
        assertThat(a.runtime.episode?.pkg).isEqualTo("a")
        assertThat(a.info.resolution).isInstanceOf(SessionResolution.New::class.java)
    }

    @Test fun attachWithinDebounceIsTheSameEpisode() {
        val first = attach(Runtime(), "a", 1_000)
        val second = attach(first.runtime, "a", 1_000 + Constants.BLOCK_DEBOUNCE_MS - 1)
        assertThat(second.delta.isEmpty).isTrue()
        assertThat(second.info.newEpisode).isFalse()
        assertThat(second.runtime.episode?.openedElapsed).isEqualTo(1_000)
    }

    @Test fun everyAttachExtendsTheDebounceWindow() {
        var rt = attach(Runtime(), "a", 0).runtime
        for (i in 1..5) rt = attach(rt, "a", i * 9_000L).also { assertThat(it.delta.isEmpty).isTrue() }.runtime
    }

    @Test fun attachAfterDebounceWithoutCloseCountsNewBlockAndAbortsTheOld() {
        val first = attach(Runtime(), "a", 0)
        val second = attach(first.runtime, "a", Constants.BLOCK_DEBOUNCE_MS)
        assertThat(second.delta).isEqualTo(StatsDelta(blocks = 1, aborted = 1, perApp = mapOf("a" to 1)))
    }

    @Test fun bootChangeOpensNewEpisode() {
        val first = attach(Runtime(), "a", 5_000)
        val second = attach(first.runtime, "a", 6_000, boot = 2)
        assertThat(second.delta.blocks).isEqualTo(1)
        assertThat(second.delta.aborted).isEqualTo(1)
    }

    @Test fun retargetCountsNewBlockAndAbortsOldAndKeepsCorrect() {
        val first = attach(Runtime(), "a", 0)
        val answered = rate(first.runtime, Ease.GOOD, 1_000).runtime
        val second = attach(answered, "b", 2_000)
        assertThat(second.delta).isEqualTo(StatsDelta(blocks = 1, aborted = 1, perApp = mapOf("b" to 1)))
        assertThat(second.info.resolution).isInstanceOf(SessionResolution.Retarget::class.java)
        assertThat(second.runtime.session?.correct).isEqualTo(1)
        assertThat(second.runtime.session?.targetPkg).isEqualTo("b")
    }

    @Test fun cancelAbortsOpenEpisodeOnceAndKeepsSession() {
        val a = attach(Runtime(), "a", 0).runtime
        val c = RuntimeOps.cancel(a)
        assertThat(c.delta).isEqualTo(StatsDelta(aborted = 1))
        assertThat(c.runtime.episode).isNull()
        assertThat(c.runtime.session).isNotNull()
        assertThat(RuntimeOps.cancel(c.runtime).delta.isEmpty).isTrue()
    }

    @Test fun attachWithValidGrantCountsNothingAndOpensNoEpisode() {
        val grant = GrantPolicy.create(GrantKind.QUIZ, 60_000, t(0))
        val a = attach(Runtime(grants = mapOf("a" to grant)), "a", 10_000)
        assertThat(a.delta.isEmpty).isTrue()
        assertThat(a.runtime.episode).isNull()
        assertThat(a.info.grantKind).isEqualTo(GrantKind.QUIZ)
    }

    @Test fun attachDropsExpiredGrants() {
        val grant = GrantPolicy.create(GrantKind.QUIZ, 1_000, t(0))
        val a = attach(Runtime(grants = mapOf("x" to grant)), "a", 5_000)
        assertThat(a.runtime.grants).isEmpty()
    }

    // --- answers --------------------------------------------------------------------------------------------------

    @Test fun onlyGoodAndEasyCountAndAgainHardKeepProgress() {
        var rt = attach(Runtime(), "a", 0).runtime
        rt = rate(rt, Ease.GOOD, 1_000).runtime
        val again = rate(rt, Ease.AGAIN, 2_000)
        val hard = rate(again.runtime, Ease.HARD, 3_000)
        assertThat(again.delta).isEqualTo(StatsDelta(answered = 1))
        assertThat(hard.runtime.session?.correct).isEqualTo(1)
        assertThat(hard.runtime.session?.answered).isEqualTo(3)
        assertThat(hard.runtime.session?.wrong).isEqualTo(2)
        assertThat(hard.runtime.session?.lastWrongElapsed).isEqualTo(3_000)
        assertThat(hard.runtime.session?.pending).isNull()
        assertThat(hard.runtime.session?.activeMs).isEqualTo(6_000)
    }

    @Test fun commitWithoutPendingChangesNothing() {
        val rt = attach(Runtime(), "a", 0).runtime
        val c = RuntimeOps.commitAnswer(rt, t(1_000))
        assertThat(c.runtime).isEqualTo(rt)
        assertThat(c.delta.isEmpty).isTrue()
    }

    @Test fun reachingRequiredCreatesGrantInTheSameResultAndClosesEverything() {
        var rt = attach(Runtime(), "a", 0).runtime
        rt = rate(rt, Ease.GOOD, 1_000).runtime
        rt = rate(rt, Ease.EASY, 2_000).runtime
        val last = rate(rt, Ease.GOOD, 3_000)
        assertThat(last.info.unlocked).isTrue()
        assertThat(last.delta).isEqualTo(StatsDelta(answered = 1, correct = 1, unlocks = 1))
        assertThat(last.runtime.session).isNull()
        assertThat(last.runtime.episode).isNull()
        val grant = last.runtime.grants.getValue("a")
        assertThat(grant.kind).isEqualTo(GrantKind.QUIZ)
        assertThat(grant.durationMs).isEqualTo(15 * 60_000L)
        assertThat(GrantPolicy.isValid(grant, t(3_001))).isTrue()
    }

    @Test fun againAtTheEndDoesNotUnlock() {
        var rt = attach(Runtime(), "a", 0).runtime
        rt = rate(rt, Ease.GOOD, 1_000).runtime
        rt = rate(rt, Ease.GOOD, 2_000).runtime
        val r = rate(rt, Ease.AGAIN, 3_000)
        assertThat(r.info.unlocked).isFalse()
        assertThat(r.runtime.grants).isEmpty()
    }

    @Test fun nothingDueGrantCountsFreeUnlockNotUnlock() {
        val rt = attach(Runtime(), "a", 0).runtime
        val r = RuntimeOps.commitNothingDue(rt, t(500))
        assertThat(r.delta).isEqualTo(StatsDelta(freeUnlocks = 1))
        assertThat(r.runtime.grants.getValue("a").kind).isEqualTo(GrantKind.NOTHING_DUE)
        assertThat(r.runtime.episode).isNull()
    }

    // --- emergency ------------------------------------------------------------------------------------------------

    @Test fun emergencyGrantsFiveMinutesAndCountsOnce() {
        val rt = attach(Runtime(), "a", 0).runtime
        val r = RuntimeOps.commitEmergency(rt, "a", t(100_000))
        assertThat(r.info).isTrue()
        assertThat(r.delta).isEqualTo(StatsDelta(emergencies = 1))
        assertThat(r.runtime.grants.getValue("a").durationMs).isEqualTo(5 * 60_000L)
        assertThat(r.runtime.episode).isNull()
    }

    @Test fun emergencyCooldownBlocksSecondUseUntilSixHoursOrReboot() {
        val used = EmergencyState(lastUsedElapsed = 1_000, bootCount = 1)
        val rt = Runtime(emergency = used)
        assertThat(EmergencyPolicy.available(used, t(1_000 + Constants.EMERGENCY_COOLDOWN_MS - 1))).isFalse()
        assertThat(RuntimeOps.commitEmergency(rt, "a", t(2_000)).info).isFalse()
        assertThat(EmergencyPolicy.available(used, t(1_000 + Constants.EMERGENCY_COOLDOWN_MS))).isTrue()
        assertThat(EmergencyPolicy.available(used, t(2_000, boot = 2))).isTrue()
        assertThat(EmergencyPolicy.available(used, t(1_000 + Constants.EMERGENCY_COOLDOWN_MS, boot = -1))).isFalse()
    }

    // --- invariant ------------------------------------------------------------------------------------------------

    /** Random mixes of attach/cancel/answer/retarget/unlock never produce more aborts than blocks. */
    @Test fun abortedNeverExceedsBlocksOverRandomHistories() {
        repeat(300) { seed ->
            val rnd = Random(seed.toLong())
            var rt = Runtime()
            var stats = Stats()
            var now = 0L
            repeat(80) {
                now += listOf(100L, 2_000L, 9_999L, 10_000L, 40_000L, Constants.SESSION_TTL_MS + 1).random(rnd)
                val pkg = listOf("a", "b").random(rnd)
                val applied: Applied<*> = when (rnd.nextInt(6)) {
                    0, 1 -> attach(rt, pkg, now)
                    2 -> RuntimeOps.cancel(rt)
                    3, 4 -> if (rt.session != null) rate(rt, Ease.entries.random(rnd), now) else attach(rt, pkg, now)
                    else -> if (rt.session != null) RuntimeOps.commitNothingDue(rt, t(now)) else RuntimeOps.cancel(rt)
                }
                rt = applied.runtime
                stats = stats.plus(20_000, applied.delta)
            }
            val day = stats.days.getValue(20_000)
            assertThat(day.aborted).isAtMost(day.blocks)
            assertThat(day.correct).isAtMost(day.answered)
            assertThat(day.perApp.values.sum()).isEqualTo(day.blocks)
        }
    }

    private fun <T> List<T>.random(r: Random): T = this[r.nextInt(size)]
}
