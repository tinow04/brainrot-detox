package dev.tinow.brainrotstopper.debug.fake

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.debug.DebugInit
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.quiz.QueueDecision
import dev.tinow.brainrotstopper.domain.quiz.QueuePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** M6-T4: the fake scenarios of the error matrix and the debug switch that crashes the WebView renderer. */
class FakeScenarioTest {
    @Test fun poisonCardMarksOnlyTheFirstCard() = runBlocking<Unit> {
        val f = FakeCardSource.scenario("poison_card")
        val first = (f.nextCard(DeckSelection.All) as AnkiResult.Ok).value!!
        val card = (f.render(first.ref) as AnkiResult.Ok).value
        assertThat(card.question).contains(FakeCardSource.POISON_MARKER)
        assertThat(card.answer).contains(FakeCardSource.POISON_MARKER)
        val others = FakeCardSource.cards(30).drop(1)
        assertThat(others.none { it.question.contains(FakeCardSource.POISON_MARKER) }).isTrue()
    }

    @Test fun unreadableCountsWithAnEmptyCursorAreAnErrorNeverNothingDue() = runBlocking<Unit> {
        val f = FakeCardSource.scenario("counts_unreadable")
        val cursor = f.nextCard(DeckSelection.All)
        assertThat(cursor).isEqualTo(AnkiResult.Ok(null))
        val counts = f.deckCounts(DeckSelection.All)
        assertThat(counts).isEqualTo(AnkiResult.Err(AnkiError.BadData))
        assertThat(f.listDecks()).isEqualTo(AnkiResult.Err(AnkiError.BadData))
        val d = QueuePolicy.decide(AnkiResult.Ok(null), { counts }, null, 1_000_000)
        assertThat(d).isEqualTo(QueueDecision.Error(AnkiError.BadData))
    }

    @Test fun rendererCrashOnlyForThePoisonMarkerOfTheFakePoisonScenario() {
        val flags = DebugInit.DebugFlagsImpl()
        val poison = "<p>x</p>" + FakeCardSource.POISON_MARKER
        assertThat(flags.crashesRenderer(poison)).isFalse() // real source
        flags.cardSource = "fake"; flags.scenario = "normal"
        assertThat(flags.crashesRenderer(poison)).isFalse()
        flags.scenario = "poison_card"
        assertThat(flags.crashesRenderer(poison)).isTrue()
        assertThat(flags.crashesRenderer("<p>x</p>")).isFalse()
    }
}
