package dev.tinow.brainrotstopper.domain.quiz

import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.model.GrantKind

enum class Phase { Idle, Intro, Loading, Question, Answer, SubmitPending, Feedback, Unlocked, Waiting, Error, Closed }

data class QuizState(
    val phase: Phase = Phase.Idle,
    /** Bumped for every effect that expects a result; results with another number are stale and dropped. */
    val seq: Int = 0,
    val target: String? = null,
    val required: Int = 0,
    val correct: Int = 0,
    val answered: Int = 0,
    val card: CardRef? = null,
    /** Elapsed time the question appeared / the answer was revealed (rate guard and `takenMs`). */
    val shownAt: Long = 0,
    val revealedAt: Long = 0,
    val lastEase: Ease? = null,
    val error: AnkiError? = null,
    val errorSince: Long = 0,
    val unlockKind: GrantKind? = null,
    /** An emergency / nothing-due grant is being committed; further emergency taps are ignored meanwhile. */
    val grantPending: Boolean = false,
)

sealed interface QuizEvent {
    /** The lock UI attached. [grantKind] != null = a valid grant exists for the target. */
    data class Attach(
        val target: String, val required: Int, val correct: Int, val answered: Int,
        val grantKind: GrantKind?, val hasPending: Boolean, val now: Long,
    ) : QuizEvent
    data class Start(val now: Long) : QuizEvent
    data class NextResult(val seq: Int, val decision: QueueDecision, val now: Long) : QuizEvent
    data class Reveal(val now: Long) : QuizEvent
    data class Rate(val ease: Ease, val now: Long) : QuizEvent
    /** The answer is committed in `runtime.json` (counters and possibly the grant are already persisted). */
    data class AnswerSaved(val seq: Int, val correct: Int, val answered: Int, val unlocked: Boolean) : QuizEvent
    data class AnswerFailed(val seq: Int, val error: AnkiError, val now: Long) : QuizEvent
    data object Continue : QuizEvent
    data object Cancel : QuizEvent
    data object OpenTarget : QuizEvent
    data class Retry(val now: Long) : QuizEvent
    data class Emergency(val now: Long) : QuizEvent
    data class GrantResult(val seq: Int, val kind: GrantKind, val granted: Boolean, val now: Long) : QuizEvent
    data class PollDue(val seq: Int) : QuizEvent
}

sealed interface QuizEffect {
    data class LoadNext(val seq: Int) : QuizEffect
    data class SubmitAnswer(val seq: Int, val card: CardRef, val ease: Ease, val takenMs: Long, val willUnlock: Boolean) : QuizEffect
    /** Attach found a pending answer: verify / resend / commit it (architecture.md 4, recovery). */
    data class RecoverPending(val seq: Int) : QuizEffect
    data class CommitGrant(val seq: Int, val kind: GrantKind) : QuizEffect
    data class SchedulePoll(val seq: Int, val delayMs: Long) : QuizEffect
    data object DiscardPending : QuizEffect
    data object CancelEpisode : QuizEffect
    data object GoHomeAndFinish : QuizEffect
    data class StartTarget(val pkg: String) : QuizEffect
}

data class QuizCtx(val emergencyEnabled: Boolean = Constants.EMERGENCY_EXIT_ENABLED, val nothingDueGrant: Boolean = Constants.NOTHING_DUE_GRANT)

typealias Reduced = Pair<QuizState, List<QuizEffect>>

/** Pure quiz state machine (architecture.md 3B). Anything not listed for a phase is ignored. */
object QuizReducer {
    fun reduce(s: QuizState, e: QuizEvent, ctx: QuizCtx = QuizCtx()): Reduced = when (e) {
        is QuizEvent.Attach -> attach(s, e)
        is QuizEvent.Start -> if (s.phase == Phase.Intro) load(s) else s.none()
        is QuizEvent.NextResult -> if (e.seq == s.seq && s.phase in LOADING) next(s, e, ctx) else s.none()
        is QuizEvent.Reveal ->
            if (s.phase == Phase.Question && e.now - s.shownAt >= Constants.RATE_GUARD_MS) s.copy(phase = Phase.Answer, revealedAt = e.now).none() else s.none()
        is QuizEvent.Rate -> rate(s, e)
        is QuizEvent.AnswerSaved -> saved(s, e)
        is QuizEvent.AnswerFailed ->
            if (e.seq == s.seq && s.phase == Phase.SubmitPending) s.copy(phase = Phase.Error, error = e.error, errorSince = e.now).none() else s.none()
        QuizEvent.Continue -> if (s.phase == Phase.Feedback) load(s) else s.none()
        QuizEvent.Cancel -> cancel(s)
        QuizEvent.OpenTarget -> if (s.phase == Phase.Unlocked && s.target != null) s.copy(phase = Phase.Closed, seq = s.seq + 1).with(QuizEffect.StartTarget(s.target)) else s.none()
        is QuizEvent.Retry -> retry(s)
        is QuizEvent.Emergency -> emergency(s, e, ctx)
        is QuizEvent.GrantResult -> grantResult(s, e)
        is QuizEvent.PollDue -> if (s.phase == Phase.Waiting && e.seq == s.seq) s.copy(seq = s.seq + 1).with(QuizEffect.LoadNext(s.seq + 1)) else s.none()
    }

    private val LOADING = setOf(Phase.Loading, Phase.Waiting)

    private fun QuizState.none(): Reduced = this to emptyList()
    private fun QuizState.with(vararg effects: QuizEffect): Reduced = this to effects.toList()

    private fun attach(s: QuizState, e: QuizEvent.Attach): Reduced {
        val base = QuizState(seq = s.seq + 1, target = e.target, required = e.required, correct = e.correct, answered = e.answered)
        return when {
            e.grantKind != null -> base.copy(phase = Phase.Unlocked, unlockKind = e.grantKind).none()
            e.hasPending -> base.copy(phase = Phase.SubmitPending).with(QuizEffect.RecoverPending(base.seq))
            else -> base.copy(phase = Phase.Intro).none()
        }
    }

    private fun load(s: QuizState): Reduced {
        val seq = s.seq + 1
        return s.copy(phase = Phase.Loading, seq = seq, card = null, error = null, grantPending = false).with(QuizEffect.LoadNext(seq))
    }

    private fun next(s: QuizState, e: QuizEvent.NextResult, ctx: QuizCtx): Reduced = when (val d = e.decision) {
        is QueueDecision.Card -> s.copy(phase = Phase.Question, card = d.ref, shownAt = e.now, error = null, lastEase = null).none()
        QueueDecision.WaitingForLearning ->
            s.copy(phase = Phase.Waiting, seq = s.seq + 1).with(QuizEffect.SchedulePoll(s.seq + 1, Constants.WAIT_POLL_MS))
        QueueDecision.AllDone ->
            if (ctx.nothingDueGrant) s.copy(seq = s.seq + 1).with(QuizEffect.CommitGrant(s.seq + 1, GrantKind.NOTHING_DUE))
            else s.copy(phase = Phase.Waiting, seq = s.seq + 1).with(QuizEffect.SchedulePoll(s.seq + 1, Constants.WAIT_POLL_MS))
        is QueueDecision.Error -> s.copy(phase = Phase.Error, error = d.error, errorSince = e.now).none()
    }

    private fun rate(s: QuizState, e: QuizEvent.Rate): Reduced {
        val card = s.card
        if (s.phase != Phase.Answer || card == null || e.now - s.revealedAt < Constants.RATE_GUARD_MS) return s.none()
        val seq = s.seq + 1
        val willUnlock = e.ease.counts && s.correct + 1 >= s.required
        return s.copy(phase = Phase.SubmitPending, seq = seq, lastEase = e.ease)
            .with(QuizEffect.SubmitAnswer(seq, card, e.ease, e.now - s.shownAt, willUnlock))
    }

    private fun saved(s: QuizState, e: QuizEvent.AnswerSaved): Reduced {
        if (e.seq != s.seq || s.phase != Phase.SubmitPending) return s.none()
        val counted = s.copy(correct = e.correct, answered = e.answered)
        return when {
            e.unlocked -> counted.copy(phase = Phase.Unlocked, unlockKind = GrantKind.QUIZ, card = null).none()
            counted.lastEase?.counts == true -> load(counted)
            // Recovery has no rating on screen: just continue with the next card.
            counted.lastEase == null -> load(counted)
            else -> counted.copy(phase = Phase.Feedback).none()
        }
    }

    private fun cancel(s: QuizState): Reduced = when (s.phase) {
        Phase.Closed, Phase.Idle -> s.none()
        Phase.Unlocked -> s.copy(phase = Phase.Closed, seq = s.seq + 1).with(QuizEffect.GoHomeAndFinish) // grant stays, nothing counted
        else -> s.copy(phase = Phase.Closed, seq = s.seq + 1).with(QuizEffect.CancelEpisode, QuizEffect.GoHomeAndFinish)
    }

    private fun retry(s: QuizState): Reduced {
        if (s.phase != Phase.Error) return s.none()
        val (loading, effects) = load(s)
        return if (s.error == AnkiError.AnswerNotSaved) loading to (listOf<QuizEffect>(QuizEffect.DiscardPending) + effects) else loading to effects
    }

    private fun emergency(s: QuizState, e: QuizEvent.Emergency, ctx: QuizCtx): Reduced {
        if (!ctx.emergencyEnabled || s.phase != Phase.Error || s.grantPending || e.now - s.errorSince < Constants.EMERGENCY_WAIT_MS) return s.none()
        val seq = s.seq + 1
        return s.copy(seq = seq, grantPending = true).with(QuizEffect.CommitGrant(seq, GrantKind.EMERGENCY))
    }

    private fun grantResult(s: QuizState, e: QuizEvent.GrantResult): Reduced {
        if (e.seq != s.seq || s.phase == Phase.Unlocked || s.phase == Phase.Closed) return s.none()
        return if (e.granted) s.copy(phase = Phase.Unlocked, unlockKind = e.kind, card = null, grantPending = false).none()
        // Denied (cooldown) or failed to persist: stay closed and keep showing the error.
        else s.copy(phase = Phase.Error, error = s.error ?: AnkiError.Unexpected("grant not committed"), errorSince = s.error?.let { s.errorSince } ?: e.now, grantPending = false).none()
    }
}
