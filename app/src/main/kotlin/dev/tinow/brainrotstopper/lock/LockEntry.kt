package dev.tinow.brainrotstopper.lock

import dev.tinow.brainrotstopper.domain.gate.GateDecision
import dev.tinow.brainrotstopper.domain.gate.GateEvaluator
import dev.tinow.brainrotstopper.domain.gate.GateSnapshot
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.QuizSession
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.stats.StreakCalculator
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import dev.tinow.brainrotstopper.lock.screens.IntroState
import dev.tinow.brainrotstopper.ui.Strings

/** What the lock activity does right after it was started for a package (architecture.md 2.7: resume check first). */
enum class LockEntry {
    /** Blocked and not granted: show the lock. */
    Show,

    /** A valid grant appeared in the meantime (race): open the target app and close. */
    OpenTarget,

    /** The package is not blocked (any more) or protected: just close. */
    Dismiss,
    ;

    companion object {
        fun decide(pkg: String, snapshot: GateSnapshot, now: TimeSnapshot): LockEntry =
            when (GateEvaluator.evaluate(pkg, snapshot, now)) {
                is GateDecision.Lock -> Show
                is GateDecision.Granted -> OpenTarget
                GateDecision.NotBlocked, GateDecision.Protected -> Dismiss
            }
    }
}

/** Builds the A1 data from stored state only; never touches AnkiDroid. */
object IntroStateFactory {
    fun build(appLabel: String, session: QuizSession, stats: Stats, now: TimeSnapshot): IntroState {
        val today = now.epochDay
        val deckName = (session.deck as? DeckSelection.Single)?.nameSnapshot
        return IntroState(
            app = appLabel,
            deckLabel = Strings.deckLabel(deckName),
            streakDays = StreakCalculator.streakBeforeToday(stats, today),
            todayCards = stats.days[today]?.answered ?: 0,
            correct = session.correct,
            required = session.required,
        )
    }
}
