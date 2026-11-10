package dev.tinow.brainrotstopper.lock.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tinow.brainrotstopper.data.anki.CardHtml
import dev.tinow.brainrotstopper.data.anki.CardSide
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.quiz.Phase
import dev.tinow.brainrotstopper.domain.runtime.EmergencyButton
import dev.tinow.brainrotstopper.lock.render.CardView
import dev.tinow.brainrotstopper.lock.render.rememberCardStyle
import dev.tinow.brainrotstopper.lock.screens.AnswerScreen
import dev.tinow.brainrotstopper.lock.screens.ErrorScreen
import dev.tinow.brainrotstopper.lock.screens.FeedbackScreen
import dev.tinow.brainrotstopper.lock.screens.Intervals
import dev.tinow.brainrotstopper.lock.screens.IntroScreen
import dev.tinow.brainrotstopper.lock.screens.LoadingScreen
import dev.tinow.brainrotstopper.lock.screens.LockHead
import dev.tinow.brainrotstopper.lock.screens.QuestionScreen
import dev.tinow.brainrotstopper.lock.screens.SessionStats
import dev.tinow.brainrotstopper.lock.screens.UnlockVariant
import dev.tinow.brainrotstopper.lock.screens.UnlockedScreen
import dev.tinow.brainrotstopper.lock.screens.WaitingScreen
import dev.tinow.brainrotstopper.ui.components.RateKind
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import kotlinx.coroutines.delay

/** What the error screen's main button does besides `erneut`; the activity knows how to open other apps and screens. */
enum class ErrorTarget { AppInfo, AnkiDroid, Settings }

/** The quiz screens A1 to A5 and the error, waiting and loading states, driven by [QuizView] (stateless apart from the loading delay). */
@Composable
fun QuizHost(view: QuizView, actions: QuizActions, onErrorTarget: (ErrorTarget) -> Unit) {
    val q = view.quiz
    val head = LockHead(view.appLabel, view.deckLabel, q.correct, q.required)
    when (q.phase) {
        Phase.Idle, Phase.Closed -> Blank()
        Phase.Intro -> view.intro?.let { IntroScreen(it, actions.start, actions.cancel) } ?: Blank()
        Phase.Loading -> DelayedLoading(view.appLabel, actions.cancel)
        Phase.Question -> view.card?.let { card ->
            val style = rememberCardStyle()
            val doc = remember(card, style) { CardHtml.build(card.rendered, CardSide.Question, card.ref.ord, style, card.images) }
            QuestionScreen(head, q.answered + 1, actions.reveal, actions.cancelTap ?: actions.cancel) { CardView(doc) }
        } ?: Blank()
        // While the rating is stored the answer stays on screen (the reducer ignores further taps); recovery has no card.
        Phase.Answer, Phase.SubmitPending -> view.card?.let { card ->
            val style = rememberCardStyle()
            val answerDoc = remember(card, style) { CardHtml.build(card.rendered, CardSide.Answer, card.ref.ord, style, card.images) }
            val questionDoc = remember(card, style) {
                if (CardHtml.answerRepeatsQuestion(card.rendered)) null
                else CardHtml.build(card.rendered, CardSide.Question, card.ref.ord, style, card.images, dimmed = true)
            }
            val iv = card.intervals
            AnswerScreen(
                head = head,
                intervals = Intervals(iv?.getOrNull(0), iv?.getOrNull(1), iv?.getOrNull(2), iv?.getOrNull(3)),
                onRate = { actions.rate(it.ease()) },
                question = { questionDoc?.let { CardView(it, dimmed = true) } },
                answer = { CardView(answerDoc) },
            )
        } ?: DelayedLoading(view.appLabel, actions.cancel)
        Phase.Feedback -> FeedbackScreen(head, q.answered, if (q.lastEase == Ease.HARD) RateKind.Hard else RateKind.Again, actions.next)
        Phase.Unlocked -> {
            val unlock = view.unlock
            val variant = when (q.unlockKind) {
                GrantKind.NOTHING_DUE -> UnlockVariant.AllDone
                GrantKind.EMERGENCY -> UnlockVariant.Emergency
                else -> UnlockVariant.Unlocked
            }
            UnlockedScreen(
                head = head,
                minutes = unlock?.minutes ?: 0,
                session = unlock?.takeIf { variant == UnlockVariant.Unlocked }?.let { SessionStats(it.answered, it.correct, it.durationMs, it.streakDays) },
                variant = variant,
                onOpen = actions.openTarget,
                onClose = actions.cancel,
            )
        }
        Phase.Waiting -> WaitingScreen(head, actions.cancel)
        Phase.Error -> {
            val error = q.error ?: AnkiError.Unexpected("no error")
            val texts = QuizEngine.errorTexts(error)
            // The countdown and the cooldown move with the clock: ask the engine once a second while the screen shows.
            val first = remember(q.errorSince, q.seq) { actions.emergencyButton() }
            val emergency by produceState(first, q.errorSince, q.seq) {
                while (true) {
                    value = actions.emergencyButton()
                    delay(1_000)
                }
            }
            ErrorScreen(
                app = view.appLabel,
                error = texts,
                emergency = emergency,
                onAction = {
                    when (error) {
                        AnkiError.PermissionMissing -> onErrorTarget(ErrorTarget.AppInfo)
                        AnkiError.StorageNotConfigured -> onErrorTarget(ErrorTarget.AnkiDroid)
                        AnkiError.DeckMissing -> onErrorTarget(ErrorTarget.Settings)
                        else -> actions.retry()
                    }
                },
                onEmergency = actions.emergency,
                onCancel = actions.cancel,
            )
        }
    }
}

/** The callbacks of the quiz screens; [forEngine] binds them to a [QuizEngine]. */
class QuizActions(
    val start: () -> Unit,
    val reveal: () -> Unit,
    val rate: (Ease) -> Unit,
    val next: () -> Unit,
    val cancel: () -> Unit,
    val openTarget: () -> Unit,
    val retry: () -> Unit,
    val emergency: () -> Unit,
    val emergencyButton: () -> EmergencyButton = { EmergencyButton.Hidden },
    /** [abbrechen] on the card screen (guarded against the second tap of a double tap); falls back to [cancel]. */
    val cancelTap: (() -> Unit)? = null,
) {
    companion object {
        fun forEngine(e: QuizEngine) =
            QuizActions(e::start, e::reveal, e::rate, e::next, e::cancel, e::openTarget, e::retry, e::emergency, e::emergencyButton, e::cancelTap)
    }
}

@Composable
fun QuizRoot(engine: QuizEngine, onErrorTarget: (ErrorTarget) -> Unit) {
    val view by engine.view.collectAsStateWithLifecycle()
    val actions = remember(engine) { QuizActions.forEngine(engine) }
    QuizHost(view, actions, onErrorTarget)
}

private fun RateKind.ease(): Ease = when (this) {
    RateKind.Again -> Ease.AGAIN
    RateKind.Hard -> Ease.HARD
    RateKind.Good -> Ease.GOOD
    RateKind.Easy -> Ease.EASY
}

@Composable
private fun Blank() {
    Box(Modifier.fillMaxSize().background(BsTheme.colors.bg))
}

/** A fast load shows only the background first so the loading screen does not flash (SPINNER_DELAY_MS). */
@Composable
private fun DelayedLoading(app: String, onCancel: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(Constants.SPINNER_DELAY_MS)
        show = true
    }
    if (show) LoadingScreen(app, onCancel) else Blank()
}
