package dev.tinow.brainrotstopper.domain.quiz

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.model.GrantKind
import org.junit.Test

/** Drives the pure reducer; `t` is the virtual elapsed clock. */
private class Quiz(required: Int = 2, correct: Int = 0, private val ctx: QuizCtx = QuizCtx()) {
    var state = QuizState()
    var t = 10_000L
    val effects = mutableListOf<QuizEffect>()
    val card = CardRef(11, 0)

    init { attach(required, correct) }

    fun send(e: QuizEvent): List<QuizEffect> {
        val (s, fx) = QuizReducer.reduce(state, e, ctx)
        state = s; effects += fx
        return fx
    }

    fun attach(required: Int = state.required, correct: Int = 0, grant: GrantKind? = null, pending: Boolean = false) =
        send(QuizEvent.Attach("com.insta", required, correct, 0, grant, pending, t))

    fun advance(ms: Long) { t += ms }
    fun start() = send(QuizEvent.Start(t))
    fun deliver(d: QueueDecision, seq: Int = state.seq) = send(QuizEvent.NextResult(seq, d, t))
    fun toQuestion() { start(); deliver(QueueDecision.Card(card)) }
    fun toAnswer() { toQuestion(); advance(Constants.RATE_GUARD_MS); send(QuizEvent.Reveal(t)) }
    fun rate(ease: Ease) = run { advance(Constants.RATE_GUARD_MS); send(QuizEvent.Rate(ease, t)) }
    fun toSubmit(ease: Ease = Ease.GOOD) { toAnswer(); rate(ease) }
    fun toError(error: AnkiError = AnkiError.Unreachable) { start(); deliver(QueueDecision.Error(error)) }
    val phase get() = state.phase
    inline fun <reified T : QuizEffect> last() = effects.filterIsInstance<T>().last()
    inline fun <reified T : QuizEffect> count() = effects.count { it is T }
}

class QuizReducerTest {
    // --- attach / intro -------------------------------------------------------------------------------------------

    @Test fun attachStartsAtIntroWithoutTouchingAnki() {
        val q = Quiz()
        assertThat(q.phase).isEqualTo(Phase.Intro)
        assertThat(q.effects).isEmpty()
    }

    @Test fun attachWithValidGrantShowsUnlocked() {
        val q = Quiz(); q.attach(grant = GrantKind.QUIZ)
        assertThat(q.phase).isEqualTo(Phase.Unlocked)
        assertThat(q.state.unlockKind).isEqualTo(GrantKind.QUIZ)
    }

    @Test fun attachWithPendingAnswerRecovers() {
        val q = Quiz(); q.attach(pending = true)
        assertThat(q.phase).isEqualTo(Phase.SubmitPending)
        assertThat(q.last<QuizEffect.RecoverPending>().seq).isEqualTo(q.state.seq)
    }

    @Test fun reattachResetsTheState() {
        val q = Quiz(); q.toAnswer(); q.attach()
        assertThat(q.phase).isEqualTo(Phase.Intro)
        assertThat(q.state.card).isNull()
    }

    @Test fun startOnlyWorksFromIntro() {
        val q = Quiz(); q.toQuestion(); val before = q.state
        q.start()
        assertThat(q.state).isEqualTo(before)
    }

    // --- loading / queue results ----------------------------------------------------------------------------------

    @Test fun startLoadsAndCardShowsQuestion() {
        val q = Quiz(); q.start()
        assertThat(q.phase).isEqualTo(Phase.Loading)
        assertThat(q.last<QuizEffect.LoadNext>().seq).isEqualTo(q.state.seq)
        q.deliver(QueueDecision.Card(q.card))
        assertThat(q.phase).isEqualTo(Phase.Question)
        assertThat(q.state.card).isEqualTo(q.card)
    }

    @Test fun staleNextResultIsIgnored() {
        val q = Quiz(); q.start()
        val stale = q.state.seq - 1
        q.deliver(QueueDecision.Card(q.card), seq = stale)
        assertThat(q.phase).isEqualTo(Phase.Loading)
    }

    @Test fun resultAfterCancelIsIgnored() {
        val q = Quiz(); q.start(); val seq = q.state.seq
        q.send(QuizEvent.Cancel)
        q.deliver(QueueDecision.Card(q.card), seq = seq)
        assertThat(q.phase).isEqualTo(Phase.Closed)
    }

    @Test fun waitingPollsWithoutAnyTimeBasedUnlock() {
        val q = Quiz(); q.start()
        q.deliver(QueueDecision.WaitingForLearning)
        assertThat(q.phase).isEqualTo(Phase.Waiting)
        val poll = q.last<QuizEffect.SchedulePoll>()
        assertThat(poll.delayMs).isEqualTo(Constants.WAIT_POLL_MS)
        q.advance(24 * 3_600_000L)
        assertThat(q.phase).isEqualTo(Phase.Waiting)
        assertThat(q.effects.filterIsInstance<QuizEffect.CommitGrant>()).isEmpty()
        q.send(QuizEvent.PollDue(poll.seq))
        assertThat(q.phase).isEqualTo(Phase.Waiting)
        assertThat(q.last<QuizEffect.LoadNext>().seq).isEqualTo(q.state.seq)
    }

    @Test fun pollWithOldSeqIsIgnored() {
        val q = Quiz(); q.start(); q.deliver(QueueDecision.WaitingForLearning)
        val n = q.effects.size
        q.send(QuizEvent.PollDue(q.state.seq - 1))
        assertThat(q.effects).hasSize(n)
    }

    @Test fun waitingBecomesQuestionWhenCardArrives() {
        val q = Quiz(); q.start(); q.deliver(QueueDecision.WaitingForLearning)
        q.send(QuizEvent.PollDue(q.state.seq)); q.deliver(QueueDecision.Card(q.card))
        assertThat(q.phase).isEqualTo(Phase.Question)
    }

    @Test fun allDoneCommitsNothingDueGrantThenUnlocked() {
        val q = Quiz(required = 5); q.start(); q.deliver(QueueDecision.AllDone)
        val grant = q.last<QuizEffect.CommitGrant>()
        assertThat(grant.kind).isEqualTo(GrantKind.NOTHING_DUE)
        assertThat(q.phase).isEqualTo(Phase.Loading) // UI moves on only after the commit
        q.send(QuizEvent.GrantResult(grant.seq, GrantKind.NOTHING_DUE, true, q.t))
        assertThat(q.phase).isEqualTo(Phase.Unlocked)
        assertThat(q.state.unlockKind).isEqualTo(GrantKind.NOTHING_DUE)
    }

    @Test fun allDoneWithoutNothingDueGrantWaitsInstead() {
        val q = Quiz(ctx = QuizCtx(nothingDueGrant = false)); q.start(); q.deliver(QueueDecision.AllDone)
        assertThat(q.phase).isEqualTo(Phase.Waiting)
    }

    @Test fun queueErrorShowsErrorAndRecordsTime() {
        val q = Quiz(); q.start(); q.advance(500); q.deliver(QueueDecision.Error(AnkiError.PermissionMissing))
        assertThat(q.phase).isEqualTo(Phase.Error)
        assertThat(q.state.error).isEqualTo(AnkiError.PermissionMissing)
        assertThat(q.state.errorSince).isEqualTo(q.t)
    }

    @Test fun retryFromErrorLoadsAgain() {
        val q = Quiz(); q.toError()
        q.send(QuizEvent.Retry(q.t))
        assertThat(q.phase).isEqualTo(Phase.Loading)
        assertThat(q.effects.filterIsInstance<QuizEffect.DiscardPending>()).isEmpty()
    }

    // --- rate guard -----------------------------------------------------------------------------------------------

    @Test fun revealInsideRateGuardIsDropped() {
        val q = Quiz(); q.toQuestion(); q.advance(Constants.RATE_GUARD_MS - 1)
        q.send(QuizEvent.Reveal(q.t))
        assertThat(q.phase).isEqualTo(Phase.Question)
        q.advance(1); q.send(QuizEvent.Reveal(q.t))
        assertThat(q.phase).isEqualTo(Phase.Answer)
    }

    @Test fun rateInsideRateGuardAfterRevealIsDropped() {
        val q = Quiz(); q.toAnswer(); q.advance(Constants.RATE_GUARD_MS - 1)
        q.send(QuizEvent.Rate(Ease.GOOD, q.t))
        assertThat(q.phase).isEqualTo(Phase.Answer)
        assertThat(q.count<QuizEffect.SubmitAnswer>()).isEqualTo(0)
    }

    @Test fun rateOutsideAnswerPhaseDoesNothing() {
        val q = Quiz(); q.toQuestion(); q.advance(5_000)
        q.send(QuizEvent.Rate(Ease.GOOD, q.t))
        assertThat(q.phase).isEqualTo(Phase.Question)
    }

    // --- answer pipeline ------------------------------------------------------------------------------------------

    @Test fun rateSubmitsOnceWithTakenMs() {
        val q = Quiz(); q.toAnswer(); val shown = q.state.shownAt
        q.rate(Ease.GOOD)
        val submit = q.last<QuizEffect.SubmitAnswer>()
        assertThat(submit.ease).isEqualTo(Ease.GOOD)
        assertThat(submit.takenMs).isEqualTo(q.t - shown)
        assertThat(submit.willUnlock).isFalse()
        assertThat(q.phase).isEqualTo(Phase.SubmitPending)
    }

    @Test fun doubleRateHasNoSecondEffect() {
        val q = Quiz(); q.toAnswer(); q.rate(Ease.GOOD); q.advance(1_000); q.send(QuizEvent.Rate(Ease.EASY, q.t))
        assertThat(q.count<QuizEffect.SubmitAnswer>()).isEqualTo(1)
    }

    @Test fun willUnlockOnlyForCountingEaseOnTheLastCard() {
        val good = Quiz(required = 1); good.toAnswer(); good.rate(Ease.EASY)
        assertThat(good.last<QuizEffect.SubmitAnswer>().willUnlock).isTrue()
        val again = Quiz(required = 1); again.toAnswer(); again.rate(Ease.AGAIN)
        assertThat(again.last<QuizEffect.SubmitAnswer>().willUnlock).isFalse()
        val hard = Quiz(required = 1); hard.toAnswer(); hard.rate(Ease.HARD)
        assertThat(hard.last<QuizEffect.SubmitAnswer>().willUnlock).isFalse()
    }

    @Test fun savedCountingAnswerLoadsNextCardAndShowsProgress() {
        val q = Quiz(required = 3); q.toSubmit(Ease.GOOD)
        q.send(QuizEvent.AnswerSaved(q.state.seq, correct = 1, answered = 1, unlocked = false))
        assertThat(q.phase).isEqualTo(Phase.Loading)
        assertThat(q.state.correct).isEqualTo(1)
        assertThat(q.last<QuizEffect.LoadNext>().seq).isEqualTo(q.state.seq)
    }

    @Test fun savedAgainAndHardShowFeedbackWithoutProgress() {
        for (ease in listOf(Ease.AGAIN, Ease.HARD)) {
            val q = Quiz(required = 3, correct = 0); q.toSubmit(ease)
            q.send(QuizEvent.AnswerSaved(q.state.seq, correct = 0, answered = 1, unlocked = false))
            assertThat(q.phase).isEqualTo(Phase.Feedback)
            assertThat(q.state.correct).isEqualTo(0)
            assertThat(q.state.answered).isEqualTo(1)
            q.send(QuizEvent.Continue)
            assertThat(q.phase).isEqualTo(Phase.Loading)
        }
    }

    @Test fun savedLastCardUnlocks() {
        val q = Quiz(required = 1); q.toSubmit(Ease.GOOD)
        q.send(QuizEvent.AnswerSaved(q.state.seq, correct = 1, answered = 1, unlocked = true))
        assertThat(q.phase).isEqualTo(Phase.Unlocked)
        assertThat(q.state.unlockKind).isEqualTo(GrantKind.QUIZ)
    }

    @Test fun staleSavedIsIgnored() {
        val q = Quiz(); q.toSubmit(); val seq = q.state.seq
        q.send(QuizEvent.AnswerSaved(seq - 1, 1, 1, false))
        assertThat(q.phase).isEqualTo(Phase.SubmitPending)
        assertThat(q.state.correct).isEqualTo(0)
    }

    @Test fun answerFailedDoesNotCountAndShowsError() {
        val q = Quiz(); q.toSubmit(Ease.GOOD)
        q.send(QuizEvent.AnswerFailed(q.state.seq, AnkiError.AnswerNotSaved, q.t))
        assertThat(q.phase).isEqualTo(Phase.Error)
        assertThat(q.state.correct).isEqualTo(0)
        assertThat(q.state.answered).isEqualTo(0)
    }

    @Test fun retryAfterAnswerNotSavedDiscardsPendingAndReloads() {
        val q = Quiz(); q.toSubmit(Ease.GOOD)
        q.send(QuizEvent.AnswerFailed(q.state.seq, AnkiError.AnswerNotSaved, q.t))
        q.send(QuizEvent.Retry(q.t))
        assertThat(q.phase).isEqualTo(Phase.Loading)
        assertThat(q.count<QuizEffect.DiscardPending>()).isEqualTo(1)
        assertThat(q.state.correct).isEqualTo(0)
    }

    @Test fun recoveryResultContinuesWithNextCard() {
        val q = Quiz(required = 3); q.attach(pending = true)
        q.send(QuizEvent.AnswerSaved(q.state.seq, 1, 1, false))
        assertThat(q.phase).isEqualTo(Phase.Loading)
    }

    // --- cancel / unlocked ----------------------------------------------------------------------------------------

    @Test fun cancelInEveryPhaseAbortsTheEpisodeAndGoesHome() {
        val setups = mapOf<String, (Quiz) -> Unit>(
            "intro" to { }, "loading" to { it.start() }, "question" to { it.toQuestion() }, "answer" to { it.toAnswer() },
            "submit" to { it.toSubmit() }, "error" to { it.toError() },
            "waiting" to { it.start(); it.deliver(QueueDecision.WaitingForLearning) },
            "feedback" to { it.toSubmit(Ease.AGAIN); it.send(QuizEvent.AnswerSaved(it.state.seq, 0, 1, false)) },
        )
        for ((_, setup) in setups) {
            val q = Quiz(); setup(q); q.send(QuizEvent.Cancel)
            assertThat(q.phase).isEqualTo(Phase.Closed)
            assertThat(q.effects.filterIsInstance<QuizEffect.CancelEpisode>()).hasSize(1)
            assertThat(q.effects.filterIsInstance<QuizEffect.GoHomeAndFinish>()).hasSize(1)
            q.send(QuizEvent.Cancel) // second back press is a no-op
            assertThat(q.effects.filterIsInstance<QuizEffect.GoHomeAndFinish>()).hasSize(1)
        }
    }

    @Test fun backInUnlockedGoesHomeWithoutCountingAnAbort() {
        val q = Quiz(); q.attach(grant = GrantKind.QUIZ)
        q.send(QuizEvent.Cancel)
        assertThat(q.phase).isEqualTo(Phase.Closed)
        assertThat(q.effects).containsExactly(QuizEffect.GoHomeAndFinish)
    }

    @Test fun openTargetOnlyFromUnlocked() {
        val q = Quiz(); q.send(QuizEvent.OpenTarget)
        assertThat(q.effects).isEmpty()
        q.attach(grant = GrantKind.QUIZ); q.send(QuizEvent.OpenTarget)
        assertThat(q.effects).containsExactly(QuizEffect.StartTarget("com.insta"))
        assertThat(q.phase).isEqualTo(Phase.Closed)
    }

    // --- emergency exit -------------------------------------------------------------------------------------------

    @Test fun emergencyOnlyAfterWaitOnErrorScreens() {
        val q = Quiz(); q.toError()
        q.advance(Constants.EMERGENCY_WAIT_MS - 1); q.send(QuizEvent.Emergency(q.t))
        assertThat(q.count<QuizEffect.CommitGrant>()).isEqualTo(0)
        q.advance(1); q.send(QuizEvent.Emergency(q.t))
        val grant = q.last<QuizEffect.CommitGrant>()
        assertThat(grant.kind).isEqualTo(GrantKind.EMERGENCY)
        q.send(QuizEvent.GrantResult(grant.seq, GrantKind.EMERGENCY, true, q.t))
        assertThat(q.phase).isEqualTo(Phase.Unlocked)
        assertThat(q.state.unlockKind).isEqualTo(GrantKind.EMERGENCY)
    }

    @Test fun emergencyTwiceInFlightCommitsOnce() {
        val q = Quiz(); q.toError(); q.advance(Constants.EMERGENCY_WAIT_MS)
        q.send(QuizEvent.Emergency(q.t)); q.send(QuizEvent.Emergency(q.t))
        assertThat(q.count<QuizEffect.CommitGrant>()).isEqualTo(1)
        q.send(QuizEvent.GrantResult(q.state.seq, GrantKind.EMERGENCY, true, q.t))
        assertThat(q.phase).isEqualTo(Phase.Unlocked)
    }

    @Test fun emergencyDeniedStaysOnTheErrorScreen() {
        val q = Quiz(); q.toError(); q.advance(Constants.EMERGENCY_WAIT_MS)
        q.send(QuizEvent.Emergency(q.t))
        q.send(QuizEvent.GrantResult(q.state.seq, GrantKind.EMERGENCY, false, q.t))
        assertThat(q.phase).isEqualTo(Phase.Error)
        assertThat(q.state.error).isEqualTo(AnkiError.Unreachable)
    }

    @Test fun noEmergencyInWaitingOrQuestionOrWhenDisabled() {
        val w = Quiz(); w.start(); w.deliver(QueueDecision.WaitingForLearning); w.advance(10 * Constants.EMERGENCY_WAIT_MS)
        w.send(QuizEvent.Emergency(w.t))
        val q = Quiz(); q.toQuestion(); q.advance(10 * Constants.EMERGENCY_WAIT_MS); q.send(QuizEvent.Emergency(q.t))
        val off = Quiz(ctx = QuizCtx(emergencyEnabled = false)); off.toError(); off.advance(10 * Constants.EMERGENCY_WAIT_MS)
        off.send(QuizEvent.Emergency(off.t))
        for (x in listOf(w, q, off)) assertThat(x.count<QuizEffect.CommitGrant>()).isEqualTo(0)
    }

    @Test fun countdownRestartsWithEachNewError() {
        val q = Quiz(); q.toError()
        q.advance(Constants.EMERGENCY_WAIT_MS - 1_000)
        q.send(QuizEvent.Retry(q.t)); q.deliver(QueueDecision.Error(AnkiError.Unreachable))
        q.advance(2_000); q.send(QuizEvent.Emergency(q.t))
        assertThat(q.count<QuizEffect.CommitGrant>()).isEqualTo(0)
    }

    // --- a whole run ----------------------------------------------------------------------------------------------

    @Test fun fullRunWithOneMistakeNeedsTwoGoodAnswers() {
        val q = Quiz(required = 2)
        var correct = 0; var answered = 0
        for (ease in listOf(Ease.GOOD, Ease.AGAIN, Ease.EASY)) {
            q.toAnswerFrom()
            q.rate(ease)
            answered++; if (ease.counts) correct++
            q.send(QuizEvent.AnswerSaved(q.state.seq, correct, answered, unlocked = correct >= 2))
            if (q.phase == Phase.Feedback) q.send(QuizEvent.Continue)
        }
        assertThat(q.phase).isEqualTo(Phase.Unlocked)
        assertThat(q.state.correct).isEqualTo(2)
        assertThat(q.state.answered).isEqualTo(3)
    }

    private fun Quiz.toAnswerFrom() {
        if (phase == Phase.Intro) start()
        deliver(QueueDecision.Card(card))
        advance(Constants.RATE_GUARD_MS); send(QuizEvent.Reveal(t))
    }
}
