package dev.tinow.brainrotstopper.domain.anki

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.debug.fake.FakeCardSource
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SwitchingCardSourceTest {
    private var scenario = "normal"
    private val created = mutableListOf<String>()
    private val source = SwitchingCardSource({ scenario }) { name -> created += name; FakeCardSource.scenario(name) }

    @Test fun `keeps the delegate while the choice stays the same`() = runTest {
        source.probe()
        source.nextCard(DeckSelection.All)
        source.listDecks()
        assertThat(created).containsExactly("normal")
    }

    @Test fun `a changed choice reaches a source that already exists`() = runTest {
        assertThat(source.nextCard(DeckSelection.All)).isInstanceOf(AnkiResult.Ok::class.java)
        scenario = "error-deck"
        assertThat(source.nextCard(DeckSelection.All)).isEqualTo(AnkiResult.Err(AnkiError.DeckMissing))
        assertThat(created).containsExactly("normal", "error-deck").inOrder()
    }
}
