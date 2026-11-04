package dev.tinow.brainrotstopper.lock

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.gate.GateSnapshot
import dev.tinow.brainrotstopper.domain.model.DayStats
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.QuizSession
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.model.UnlockGrant
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test

class LockEntryTest {
    private val now = TimeSnapshot(elapsedMs = 1_000_000, wallMs = 1_700_000_000_000, bootCount = 7)
    private val pkg = "com.instagram.android"

    @Test fun `blocked without grant shows the lock`() {
        assertThat(LockEntry.decide(pkg, GateSnapshot(blockedPackages = setOf(pkg)), now)).isEqualTo(LockEntry.Show)
    }

    @Test fun `valid grant opens the target`() {
        val grant = UnlockGrant(now.elapsedMs - 1_000, now.elapsedMs + 60_000, 61_000, 7, now.wallMs, GrantKind.QUIZ)
        assertThat(LockEntry.decide(pkg, GateSnapshot(setOf(pkg), mapOf(pkg to grant)), now)).isEqualTo(LockEntry.OpenTarget)
    }

    @Test fun `expired grant still shows the lock`() {
        val grant = UnlockGrant(now.elapsedMs - 70_000, now.elapsedMs - 10_000, 60_000, 7, now.wallMs, GrantKind.QUIZ)
        assertThat(LockEntry.decide(pkg, GateSnapshot(setOf(pkg), mapOf(pkg to grant)), now)).isEqualTo(LockEntry.Show)
    }

    @Test fun `package that is no longer blocked or is protected is dismissed`() {
        assertThat(LockEntry.decide(pkg, GateSnapshot(), now)).isEqualTo(LockEntry.Dismiss)
        assertThat(LockEntry.decide("com.ichi2.anki", GateSnapshot(blockedPackages = setOf("com.ichi2.anki")), now)).isEqualTo(LockEntry.Dismiss)
    }

    private fun session(correct: Int, deck: DeckSelection = DeckSelection.All) = QuizSession(
        sessionId = "s", targetPkg = pkg, deck = deck, required = 5, unlockMinutes = 15, correct = correct,
        startedElapsed = 0, lastActiveElapsed = 0, bootCount = 7, startedWall = 0,
    )

    @Test fun `intro uses stored values only, streak counts days before today`() {
        val today = now.epochDay
        val stats = Stats(
            days = mapOf(
                today to DayStats(answered = 12, unlocks = 1),
                today - 1 to DayStats(unlocks = 1),
                today - 2 to DayStats(freeUnlocks = 1),
            ),
        )
        val s = IntroStateFactory.build("instagram", session(0), stats, now)
        assertThat(s.streakDays).isEqualTo(2)
        assertThat(s.todayCards).isEqualTo(12)
        assertThat(s.deckLabel).isEqualTo("alle decks")
        assertThat(s.required).isEqualTo(5)
        assertThat(s.resumed).isFalse()
    }

    @Test fun `resumed session and single deck label`() {
        val s = IntroStateFactory.build("instagram", session(3, DeckSelection.Single(5, "Medizin::Biologie")), Stats(), now)
        assertThat(s.resumed).isTrue()
        assertThat(s.correct).isEqualTo(3)
        assertThat(s.deckLabel).isEqualTo("biologie")
        assertThat(s.streakDays).isEqualTo(0)
        assertThat(s.todayCards).isEqualTo(0)
    }
}
