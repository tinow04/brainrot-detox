package dev.tinow.brainrotstopper.lock.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tinow.brainrotstopper.domain.runtime.EmergencyButton
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.components.AppIconInitials
import dev.tinow.brainrotstopper.ui.components.BlinkCursor
import dev.tinow.brainrotstopper.ui.components.BsBox
import dev.tinow.brainrotstopper.ui.components.BsButton
import dev.tinow.brainrotstopper.ui.components.BsButtonStyle
import dev.tinow.brainrotstopper.ui.components.BsHeader
import dev.tinow.brainrotstopper.ui.components.BsKv
import dev.tinow.brainrotstopper.ui.components.BsRichText
import dev.tinow.brainrotstopper.ui.components.BsSep
import dev.tinow.brainrotstopper.ui.components.BsTag
import dev.tinow.brainrotstopper.ui.components.BsTagKind
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.components.RateButton
import dev.tinow.brainrotstopper.ui.components.RateKind
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

/** Common header data of the card, feedback and unlock screens. */
data class LockHead(val app: String, val deckLabel: String, val correct: Int, val required: Int)

@Composable
private fun CardHeader(h: LockHead) {
    BsHeader(
        Strings.cardHeader(h.app, h.deckLabel),
        Strings.progress(h.correct, h.required),
        rightDescription = Strings.progressDescription(h.correct, h.required),
    )
}

@Composable
private fun CancelButton(onCancel: () -> Unit) = BsButton(Strings.BTN_CANCEL, onCancel, style = BsButtonStyle.Ghost)

// ---------------------------------------------------------------- A1 intro

data class IntroState(
    val app: String,
    val deckLabel: String,
    val streakDays: Int,
    val todayCards: Int,
    val correct: Int,
    val required: Int,
) {
    val resumed get() = correct > 0
}

/** A1: intro without any AnkiDroid access (architecture 2.7); continuation variant when correct > 0. */
@Composable
fun IntroScreen(state: IntroState, onStart: () -> Unit, onCancel: () -> Unit) {
    val c = BsTheme.colors
    val t = BsTheme.type
    LockScaffold(
        header = { BsHeader(Strings.HDR_PATH, Strings.HDR_LOCK) },
        bottom = {
            BsButton(if (state.resumed) Strings.BTN_CONTINUE else Strings.BTN_START, onStart, cursor = true)
            CancelButton(onCancel)
        },
    ) {
        BsText(Strings.introCheck(Strings.appLabel(state.app)), t.body, c.dim)
        Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S16), verticalAlignment = Alignment.CenterVertically) {
            AppIconInitials(state.app, large = true)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BsText(Strings.appLabel(state.app), t.title, c.text)
                BsTag(Strings.TAG_LOCK, BsTagKind.Fail)
            }
        }
        BsRichText(Strings.introText(state.required - state.correct, state.resumed), t.card, c.text)
        BsText(
            Strings.progress(state.correct, state.required),
            if (state.required <= 10) t.hero else t.heroSmall,
            c.text,
            Modifier.semantics { contentDescription = Strings.progressDescription(state.correct, state.required) },
        )
        BsBox(Strings.BOX_STATS) {
            BsKv(Strings.KV_STREAK, Strings.tage(state.streakDays))
            BsKv(Strings.KV_TODAY, Strings.karten(state.todayCards))
            BsKv(Strings.KV_DECK, state.deckLabel)
        }
    }
}

// ---------------------------------------------------------------- A2 question

/** A2: question side. [card] renders the sanitised card content; it scrolls inside the frame. */
@Composable
fun QuestionScreen(head: LockHead, cardNumber: Int, onShowAnswer: () -> Unit, onCancel: () -> Unit, card: @Composable () -> Unit) {
    LockFrame(
        header = { CardHeader(head) },
        bottom = {
            BsButton(Strings.BTN_SHOW_ANSWER, onShowAnswer)
            CancelButton(onCancel)
        },
    ) {
        BsBox(Strings.cardLabel(cardNumber), Modifier.weight(1f).padding(top = BsDims.S8)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) { card() }
        }
    }
}

// ---------------------------------------------------------------- A3 answer

data class Intervals(val again: String?, val hard: String?, val good: String?, val easy: String?)

/** A3: answer side with the four rating buttons; no cancel button (back gesture cancels). */
@Composable
fun AnswerScreen(
    head: LockHead,
    intervals: Intervals,
    onRate: (RateKind) -> Unit,
    question: @Composable () -> Unit,
    answer: @Composable () -> Unit,
) {
    val c = BsTheme.colors
    val t = BsTheme.type
    LockScaffold(
        header = { CardHeader(head) },
        centered = false,
        contentGap = BsDims.S16,
        bottom = {
            BsText(Strings.RATE_HINT, t.label, c.dim) // mockup: margin-bottom 8 = the bottom column spacing
            // mockup grid: gap 12 in both directions
            Column(verticalArrangement = Arrangement.spacedBy(BsDims.S12)) {
                Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S12)) {
                    RateButton(Strings.RATE_AGAIN, intervals.again, RateKind.Again, { onRate(RateKind.Again) }, Modifier.weight(1f))
                    RateButton(Strings.RATE_HARD, intervals.hard, RateKind.Hard, { onRate(RateKind.Hard) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S12)) {
                    RateButton(Strings.RATE_GOOD, intervals.good, RateKind.Good, { onRate(RateKind.Good) }, Modifier.weight(1f))
                    RateButton(Strings.RATE_EASY, intervals.easy, RateKind.Easy, { onRate(RateKind.Easy) }, Modifier.weight(1f))
                }
            }
        },
    ) {
        question()
        BsSep(Strings.SEP_ANSWER)
        answer()
    }
}

// ---------------------------------------------------------------- A4 feedback

/** A4: feedback after nochmal/schwer (progress unchanged). */
@Composable
fun FeedbackScreen(head: LockHead, cardNumber: Int, kind: RateKind, onContinue: () -> Unit) {
    require(kind == RateKind.Again || kind == RateKind.Hard)
    val c = BsTheme.colors
    val t = BsTheme.type
    val again = kind == RateKind.Again
    LockScaffold(
        header = { CardHeader(head) },
        bottom = { BsButton(Strings.BTN_CONTINUE, onContinue, cursor = true) },
    ) {
        BsText(Strings.rateCommand(cardNumber, if (again) "again" else "hard"), t.body, c.dim)
        BsText(if (again) Strings.TAG_FAIL else Strings.TAG_WARN, t.hero, if (again) c.red else c.orange)
        BsText(if (again) Strings.FEEDBACK_AGAIN else Strings.FEEDBACK_HARD, t.card, c.text)
        BsBox(Strings.BOX_COUNTS) {
            CountsRow(Strings.RATE_AGAIN, false, c.red)
            CountsRow(Strings.RATE_HARD, false, c.orange)
            CountsRow(Strings.RATE_GOOD, true, c.green)
            CountsRow(Strings.RATE_EASY, true, c.blue)
        }
        BsRichText(Strings.feedbackProgress(head.correct, head.required, head.app), t.body, c.muted)
    }
}

@Composable
private fun CountsRow(rating: String, counts: Boolean, color: androidx.compose.ui.graphics.Color) {
    // Mockup A4: key `[n] name` in the body text colour, only the verdict is coloured.
    BsKv(rating, if (counts) Strings.COUNTS_YES else Strings.COUNTS_NO, keyColor = BsTheme.colors.text, valueColor = color)
}

// ---------------------------------------------------------------- A5 unlocked

data class SessionStats(val answered: Int, val correct: Int, val durationMs: Long, val streakDays: Int)

enum class UnlockVariant { Unlocked, AllDone, Emergency }

/** A5: unlocked; variants for "all cards done today" and the emergency exit. */
@Composable
fun UnlockedScreen(
    head: LockHead,
    minutes: Int,
    session: SessionStats?,
    variant: UnlockVariant,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val c = BsTheme.colors
    val t = BsTheme.type
    val emergency = variant == UnlockVariant.Emergency
    LockScaffold(
        header = { CardHeader(head) },
        bottom = {
            BsButton(Strings.openCommand(head.app), onOpen, cursor = true)
            BsButton(Strings.BTN_CLOSE, onClose, style = BsButtonStyle.Ghost)
        },
    ) {
        BsText(if (emergency) Strings.emergencyCommand(head.app) else Strings.unlockCommand(head.app), t.body, c.dim)
        BsText(if (emergency) Strings.TAG_PAUSE else Strings.TAG_UNLOCKED, t.hero, if (emergency) c.orange else c.green)
        BsRichText(
            when (variant) {
                UnlockVariant.Unlocked -> Strings.unlockedText(head.app, minutes)
                UnlockVariant.AllDone -> Strings.allDoneText(head.app, minutes)
                UnlockVariant.Emergency -> Strings.emergencyText(head.app)
            },
            t.card,
            c.text,
        )
        if (session != null && !emergency) {
            BsBox(Strings.BOX_SESSION) {
                BsKv(Strings.KV_CARDS, Strings.sessionCards(session.answered, session.correct))
                BsKv(Strings.KV_TIME, Strings.mmss(session.durationMs))
                BsKv(Strings.KV_STREAK, Strings.tage(session.streakDays))
            }
        }
    }
}

// ---------------------------------------------------------------- waiting / loading / error

/** Learning cards exist but are not yet due (architecture 2.7): wait and re-check. */
@Composable
fun WaitingScreen(head: LockHead, onCancel: () -> Unit) {
    val c = BsTheme.colors
    val t = BsTheme.type
    LockScaffold(header = { CardHeader(head) }, bottom = { CancelButton(onCancel) }) {
        BsText(Strings.TAG_WAITING, t.title, c.orange)
        BsText(Strings.WAITING_TEXT, t.card, c.text)
    }
}

@Composable
fun LoadingScreen(app: String, onCancel: () -> Unit) {
    val c = BsTheme.colors
    val t = BsTheme.type
    LockScaffold(header = { BsHeader(Strings.HDR_PATH, Strings.HDR_LOCK) }, bottom = { CancelButton(onCancel) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BsDims.BtnGap)) {
            BsText(Strings.LOADING, t.title, c.text)
            BlinkCursor(c.text, fontSize = 22.sp)
        }
        BsText(Strings.appLabel(app), t.body, c.dim)
    }
}

/**
 * Fail-closed error screen (architecture 2.7): the app stays blocked. [emergency] is the emergency exit below the main
 * action: counting down, ready, on cooldown (both disabled) or hidden.
 */
@Composable
fun ErrorScreen(
    app: String,
    error: Strings.ErrorTexts,
    emergency: EmergencyButton,
    onAction: () -> Unit,
    onEmergency: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = BsTheme.colors
    val t = BsTheme.type
    LockScaffold(
        header = { BsHeader(Strings.HDR_PATH, Strings.HDR_LOCK) },
        bottom = {
            BsButton(error.action, onAction)
            val label = when (emergency) {
                EmergencyButton.Hidden -> null
                is EmergencyButton.Wait -> Strings.emergencyCountdown(emergency.seconds)
                EmergencyButton.Ready -> Strings.BTN_EMERGENCY
                is EmergencyButton.Cooldown -> Strings.emergencyCooldown(emergency.remainingMs)
            }
            if (label != null) BsButton(label, onEmergency, style = BsButtonStyle.Ghost, enabled = emergency == EmergencyButton.Ready)
            CancelButton(onCancel)
        },
    ) {
        BsText(Strings.appLabel(app), t.body, c.dim)
        BsText(error.title, t.title, c.red)
        BsText(error.text, t.card, c.text)
    }
}
