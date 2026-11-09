package dev.tinow.brainrotstopper.lock

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.debug.fake.FakeCardSource
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.quiz.Phase
import dev.tinow.brainrotstopper.domain.quiz.QuizState
import dev.tinow.brainrotstopper.lock.quiz.CardContent
import dev.tinow.brainrotstopper.lock.quiz.ErrorTarget
import dev.tinow.brainrotstopper.lock.quiz.QuizActions
import dev.tinow.brainrotstopper.lock.quiz.QuizEngine
import dev.tinow.brainrotstopper.lock.quiz.QuizHost
import dev.tinow.brainrotstopper.lock.quiz.QuizView
import dev.tinow.brainrotstopper.lock.quiz.UnlockInfo
import dev.tinow.brainrotstopper.lock.screens.IntroState
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.components.LocalCursorBlink
import dev.tinow.brainrotstopper.ui.theme.BsTheme
import org.junit.Rule
import org.junit.Test

/** M4-T4: every quiz screen is driven by [QuizView]: its title, its text and exactly one main action (plus cancel). */
class QuizHostTest {
    @get:Rule
    val rule = createComposeRule()

    private val log = mutableListOf<String>()
    private val actions = QuizActions(
        start = { log += "start" }, reveal = { log += "reveal" }, rate = { log += "rate:${it.name}" }, next = { log += "next" },
        cancel = { log += "cancel" }, openTarget = { log += "open" }, retry = { log += "retry" }, emergency = { log += "emergency" },
    )
    private val card = CardContent(FakeCardSource.cards(1).single(), listOf("<1 min", "<6 min", "1 d", "4 d"), emptyMap())
    private val intro = IntroState("instagram", "alle decks", 3, 12, 0, 5)

    private fun show(phase: Phase, dark: Boolean = true, block: QuizState.() -> QuizState = { this }, view: (QuizView) -> QuizView = { it }) {
        val quiz = QuizState(phase = phase, target = "p", required = 5, correct = 2, answered = 3, card = card.ref).block()
        val v = view(QuizView(quiz = quiz, appLabel = "instagram", deckLabel = "alle decks", intro = intro, card = card))
        rule.setContent {
            CompositionLocalProvider(LocalCursorBlink provides false) {
                BsTheme(dark = dark) { QuizHost(v, actions) { log += "target:${it.name}" } }
            }
        }
    }

    @Test fun intro() {
        show(Phase.Intro)
        rule.onNodeWithText(Strings.BTN_START).assertIsDisplayed().performClick()
        rule.onNodeWithText(Strings.BTN_CANCEL).performClick()
        assertThat(log).containsExactly("start", "cancel").inOrder()
    }

    @Test fun loadingShowsAfterTheSpinnerDelay() {
        show(Phase.Loading)
        rule.waitUntil(2_000) { rule.onAllNodesWithText(Strings.LOADING, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(Strings.BTN_CANCEL).assertIsDisplayed()
    }

    @Test fun question() {
        show(Phase.Question)
        rule.onNodeWithText(Strings.cardLabel(4)).assertIsDisplayed() // answered 3 -> card 4
        rule.onNodeWithText(Strings.BTN_SHOW_ANSWER).performClick()
        assertThat(log).containsExactly("reveal")
    }

    @Test fun answerHasFourRatingsWithIntervals() {
        show(Phase.Answer)
        for (label in listOf(Strings.RATE_AGAIN, Strings.RATE_HARD, Strings.RATE_GOOD, Strings.RATE_EASY)) {
            rule.onAllNodesWithText(label, substring = true).assertCountEquals(1)
        }
        rule.onNodeWithText("<1 min", substring = true).assertIsDisplayed()
        rule.onNodeWithText(Strings.RATE_GOOD, substring = true).performClick()
        assertThat(log).containsExactly("rate:${Ease.GOOD.name}")
    }

    @Test fun answerWithoutIntervalLabelsStillRates() {
        show(Phase.Answer, view = { it.copy(card = it.card?.copy(intervals = null)) })
        rule.onNodeWithText(Strings.RATE_EASY, substring = true).performClick()
        assertThat(log).containsExactly("rate:${Ease.EASY.name}")
    }

    @Test fun answerStaysVisibleWhileTheRatingIsSaved() {
        show(Phase.SubmitPending, block = { copy(lastEase = Ease.GOOD) })
        rule.onNodeWithText(Strings.RATE_GOOD, substring = true).assertIsDisplayed()
    }

    @Test fun feedbackAgainAndHard() {
        show(Phase.Feedback, block = { copy(lastEase = Ease.AGAIN) })
        rule.onNodeWithText(Strings.TAG_FAIL).assertIsDisplayed()
        rule.onNodeWithText(Strings.BTN_CONTINUE).performClick()
        assertThat(log).containsExactly("next")
    }

    @Test fun feedbackHard() {
        show(Phase.Feedback, dark = false, block = { copy(lastEase = Ease.HARD) })
        rule.onNodeWithText(Strings.TAG_WARN).assertIsDisplayed()
    }

    private fun unlocked(kind: GrantKind, command: String) {
        show(Phase.Unlocked, block = { copy(unlockKind = kind) }, view = { it.copy(unlock = UnlockInfo(15, 7, 5, 95_000, 4)) })
        rule.onNodeWithText(command).assertIsDisplayed().performClick()
        rule.onNodeWithText(Strings.BTN_CLOSE).assertIsDisplayed()
        assertThat(log).containsExactly("open")
    }

    @Test fun unlockedQuiz() = unlocked(GrantKind.QUIZ, Strings.openCommand("instagram"))
    @Test fun unlockedNothingDue() = unlocked(GrantKind.NOTHING_DUE, Strings.openCommand("instagram"))
    @Test fun unlockedEmergency() = unlocked(GrantKind.EMERGENCY, Strings.openCommand("instagram"))

    @Test fun waiting() {
        show(Phase.Waiting)
        rule.onNodeWithText(Strings.TAG_WAITING).assertIsDisplayed()
        rule.onNodeWithText(Strings.BTN_CANCEL).performClick()
        assertThat(log).containsExactly("cancel")
    }

    private fun error(error: AnkiError, action: String, expectedLog: String) {
        val texts = QuizEngine.errorTexts(error)
        show(Phase.Error, block = { copy(error = error) })
        rule.onNodeWithText(texts.title).assertIsDisplayed()
        rule.onNodeWithText(texts.text).assertIsDisplayed()
        // exactly one main action and the cancel button; no second action, no emergency button yet
        rule.onAllNodesWithText(action).assertCountEquals(1)
        rule.onAllNodesWithText(Strings.BTN_CANCEL).assertCountEquals(1)
        rule.onAllNodesWithText(Strings.BTN_EMERGENCY, substring = true).assertCountEquals(0)
        rule.onNodeWithText(action).performClick()
        assertThat(log).containsExactly(expectedLog)
    }

    @Test fun errorNotInstalled() = error(AnkiError.NotInstalledOrApiOff, Strings.ERR_NOT_AVAILABLE.action, "retry")
    @Test fun errorPermission() = error(AnkiError.PermissionMissing, Strings.ERR_PERMISSION.action, "target:${ErrorTarget.AppInfo.name}")
    @Test fun errorStorage() = error(AnkiError.StorageNotConfigured, Strings.ERR_STORAGE.action, "target:${ErrorTarget.AnkiDroid.name}")
    @Test fun errorUnreachable() = error(AnkiError.Unreachable, Strings.ERR_UNREACHABLE.action, "retry")
    @Test fun errorDeckMissing() = error(AnkiError.DeckMissing, Strings.ERR_DECK_MISSING.action, "target:${ErrorTarget.Settings.name}")
    @Test fun errorBadData() = error(AnkiError.BadData, Strings.ERR_BAD_DATA.action, "retry")
    @Test fun errorUnexpected() = error(AnkiError.Unexpected("x"), Strings.ERR_BAD_DATA.action, "retry")
    @Test fun errorAnswerNotSaved() = error(AnkiError.AnswerNotSaved, Strings.ERR_ANSWER_NOT_SAVED.action, "retry")

    @Test fun errorTextsAreDistinctPerKindExceptTheSharedOnes() {
        val titles = listOf(
            AnkiError.NotInstalledOrApiOff, AnkiError.PermissionMissing, AnkiError.StorageNotConfigured, AnkiError.Unreachable,
            AnkiError.DeckMissing, AnkiError.BadData, AnkiError.AnswerNotSaved,
        ).map { QuizEngine.errorTexts(it).title }
        assertThat(titles.toSet()).hasSize(7)
    }

    @Test fun lightThemeRendersTheQuestionToo() {
        show(Phase.Question, dark = false)
        rule.onNodeWithText(Strings.BTN_SHOW_ANSWER).assertIsDisplayed()
        assertThat(CardRef(100, 0)).isEqualTo(card.ref)
    }
}
