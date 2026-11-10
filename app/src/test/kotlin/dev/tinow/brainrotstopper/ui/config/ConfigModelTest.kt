package dev.tinow.brainrotstopper.ui.config

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.anki.DeckCounts
import dev.tinow.brainrotstopper.domain.anki.DeckInfo
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Settings
import org.junit.Test

class ConfigModelTest {
    private fun deck(id: Long, name: String, learn: Int = 0, review: Int = 0, new: Int = 0, dyn: Boolean = false) =
        DeckInfo(id, name, DeckCounts(learn, review, new), dyn)

    private val decks = listOf(
        deck(1, "Default"),
        deck(10, "Englisch", review = 20, new = 38),
        deck(11, "Englisch::Vokabeln", review = 20, new = 38),
        deck(20, "Biologie", learn = 4, review = 100, new = 20),
        deck(21, "Biologie::Zellbiologie", learn = 4, review = 100, new = 20),
        deck(30, "Filtered Deck 1", review = 7, dyn = true),
        deck(40, "Ökologie", new = 3),
    )

    @Test fun `deck list sorted by full name, dynamic and empty default hidden, all decks last with top-level sum`() {
        val items = ConfigModel.deckItems(decks)
        assertThat(items.map { it.label }).containsExactly(
            "Biologie", "Biologie::Zellbiologie", "Englisch", "Englisch::Vokabeln", "Ökologie", "alle decks",
        ).inOrder()
        assertThat(items.map { it.due }).containsExactly(124, 124, 58, 58, 3, 185).inOrder()
        assertThat(items.last().id).isNull()
    }

    @Test fun `default deck with cards is shown`() {
        val items = ConfigModel.deckItems(listOf(deck(1, "Default", new = 2)))
        assertThat(items.map { it.label }).containsExactly("Default", "alle decks").inOrder()
        assertThat(items.map { it.due }).containsExactly(2, 2).inOrder()
    }

    @Test fun `no decks leaves only all decks with 0`() {
        assertThat(ConfigModel.deckItems(emptyList())).containsExactly(DeckItem(null, 0))
    }

    @Test fun `selection index for all, single and a missing deck`() {
        val items = ConfigModel.deckItems(decks)
        assertThat(ConfigModel.deckIndex(items, DeckSelection.All)).isEqualTo(items.lastIndex)
        assertThat(ConfigModel.deckIndex(items, DeckSelection.Single(11, "Englisch::Vokabeln"))).isEqualTo(3)
        assertThat(ConfigModel.deckIndex(items, DeckSelection.Single(999, "Weg"))).isEqualTo(-1)
        // a dynamic deck cannot be chosen: not in the list
        assertThat(ConfigModel.deckIndex(items, DeckSelection.Single(30, "Filtered Deck 1"))).isEqualTo(-1)
    }

    @Test fun `selection of a row`() {
        val items = ConfigModel.deckItems(decks)
        assertThat(ConfigModel.selectionOf(items[1])).isEqualTo(DeckSelection.Single(21, "Biologie::Zellbiologie"))
        assertThat(ConfigModel.selectionOf(items.last())).isEqualTo(DeckSelection.All)
    }

    @Test fun `stepper is clamped to 1 to 20`() {
        assertThat(ConfigModel.required(0)).isEqualTo(1)
        assertThat(ConfigModel.required(-3)).isEqualTo(1)
        assertThat(ConfigModel.required(1)).isEqualTo(1)
        assertThat(ConfigModel.required(20)).isEqualTo(20)
        assertThat(ConfigModel.required(21)).isEqualTo(20)
        // the store clamps as well (hand-edited file)
        assertThat(Settings(requiredCorrect = 99).sanitized().requiredCorrect).isEqualTo(20)
        assertThat(Settings(requiredCorrect = 0).sanitized().requiredCorrect).isEqualTo(1)
    }

    @Test fun `durations map to segments, unknown values fall back to 15 min`() {
        assertThat(ConfigModel.durationIndex(5)).isEqualTo(0)
        assertThat(ConfigModel.durationIndex(60)).isEqualTo(3)
        assertThat(ConfigModel.durationIndex(7)).isEqualTo(1)
        assertThat(ConfigModel.minutesAt(2)).isEqualTo(30)
        assertThat(ConfigModel.minutesAt(9)).isEqualTo(15)
        assertThat(Settings(unlockMinutes = 7).sanitized().unlockMinutes).isEqualTo(15)
    }
}
