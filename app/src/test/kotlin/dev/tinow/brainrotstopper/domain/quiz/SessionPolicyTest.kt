package dev.tinow.brainrotstopper.domain.quiz

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test

class SessionPolicyTest {
    private val settings = Settings(requiredCorrect = 5, unlockMinutes = 30, deck = DeckSelection.Single(7, "Vokabeln"))
    private fun t(elapsed: Long, boot: Int = 3) = TimeSnapshot(elapsed, 1_700_000_000_000 + elapsed, boot)
    private val start = t(100_000)
    private val base = SessionPolicy.create("a", settings, start, "id1").copy(correct = 3, answered = 4)

    @Test fun noSessionCreatesFreshFrozenFromSettings() {
        val r = SessionPolicy.resolve(null, "a", settings, start) { "new" }
        assertThat(r).isInstanceOf(SessionResolution.New::class.java)
        assertThat(r.session.correct).isEqualTo(0)
        assertThat(r.session.required).isEqualTo(5)
        assertThat(r.session.unlockMinutes).isEqualTo(30)
        assertThat(r.session.deck).isEqualTo(DeckSelection.Single(7, "Vokabeln"))
        assertThat(r.session.bootCount).isEqualTo(3)
    }

    @Test fun sameTargetWithinTtlResumes() {
        val r = SessionPolicy.resolve(base, "a", settings, t(100_000 + Constants.SESSION_TTL_MS - 1)) { "new" }
        assertThat(r).isInstanceOf(SessionResolution.Resume::class.java)
        assertThat(r.session.correct).isEqualTo(3)
    }

    @Test fun ttlBoundaryIsStale() {
        val r = SessionPolicy.resolve(base, "a", settings, t(100_000 + Constants.SESSION_TTL_MS)) { "new" }
        assertThat(r).isInstanceOf(SessionResolution.New::class.java)
        assertThat(r.session.correct).isEqualTo(0)
    }

    @Test fun otherBootCountIsStale() {
        assertThat(SessionPolicy.resolve(base, "a", settings, t(100_500, boot = 4)) { "n" }).isInstanceOf(SessionResolution.New::class.java)
    }

    @Test fun unreadableBootCountIsStale() {
        assertThat(SessionPolicy.resolve(base, "a", settings, t(100_500, boot = -1)) { "n" }).isInstanceOf(SessionResolution.New::class.java)
    }

    @Test fun timeGoingBackwardsIsStale() {
        assertThat(SessionPolicy.resolve(base, "a", settings, t(50_000)) { "n" }).isInstanceOf(SessionResolution.New::class.java)
    }

    @Test fun otherTargetWithinTtlRetargetsAndKeepsCorrect() {
        val r = SessionPolicy.resolve(base, "b", settings, t(160_000)) { "n" }
        assertThat(r).isInstanceOf(SessionResolution.Retarget::class.java)
        assertThat(r.session.targetPkg).isEqualTo("b")
        assertThat(r.session.correct).isEqualTo(3)
        assertThat(r.session.sessionId).isEqualTo("id1")
    }

    @Test fun otherTargetAfterTtlStartsAtZero() {
        val r = SessionPolicy.resolve(base, "b", settings, t(100_000 + Constants.SESSION_TTL_MS + 1)) { "n" }
        assertThat(r).isInstanceOf(SessionResolution.New::class.java)
        assertThat(r.session.correct).isEqualTo(0)
    }

    @Test fun invalidSettingsAreSanitizedWhenFrozen() {
        val r = SessionPolicy.resolve(null, "a", Settings(requiredCorrect = 99, unlockMinutes = 7), start) { "n" }
        assertThat(r.session.required).isEqualTo(20)
        assertThat(r.session.unlockMinutes).isEqualTo(15)
    }
}
