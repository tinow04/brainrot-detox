package dev.tinow.brainrotstopper.ui.config

import dev.tinow.brainrotstopper.domain.anki.DeckInfo
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.quiz.Constants
import java.text.Collator
import java.util.Locale

/**
 * B2 rules (architecture.md 8), android-free:
 * - deck list: every non-dynamic deck with its full `::` name, sorted alphabetically (German collator); the empty
 *   `Default` deck (id 1, all counts 0) is hidden; `alle decks` is the last row with the sum of the top-level decks
 *   (their counts already include the sub decks, so nothing is counted twice);
 * - the stepper is clamped to [Constants.REQUIRED_RANGE], the duration is one of [Constants.DURATIONS_MIN].
 * Changes only apply to the next NEW session (a running session keeps its frozen values).
 */
object ConfigModel {
    const val DEFAULT_DECK_ID = 1L
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    fun deckItems(decks: List<DeckInfo>): List<DeckItem> {
        val shown = decks
            .filter { !it.dynamic }
            .filterNot { it.id == DEFAULT_DECK_ID && it.counts.total == 0 }
            .sortedWith { a, b -> collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.name.compareTo(b.name) }
        val allDue = decks.filter { !it.dynamic && !it.name.contains("::") }.sumOf { it.counts.total }
        return shown.map { DeckItem(it.name, it.counts.total, it.id) } + DeckItem(null, allDue)
    }

    /** Index of the selected row, or -1 if the chosen deck is not in the list any more (deleted, renamed to dynamic). */
    fun deckIndex(items: List<DeckItem>, selection: DeckSelection): Int = when (selection) {
        DeckSelection.All -> items.indexOfFirst { it.id == null }
        is DeckSelection.Single -> items.indexOfFirst { it.id == selection.deckId }
    }

    fun selectionOf(item: DeckItem): DeckSelection =
        if (item.id == null || item.name == null) DeckSelection.All else DeckSelection.Single(item.id, item.name)

    fun required(value: Int): Int = value.coerceIn(Constants.REQUIRED_RANGE)

    fun durationIndex(unlockMinutes: Int): Int = Constants.DURATIONS_MIN.indexOf(unlockMinutes).takeIf { it >= 0 }
        ?: Constants.DURATIONS_MIN.indexOf(Constants.DEFAULT_DURATION_MIN)

    fun minutesAt(index: Int): Int = Constants.DURATIONS_MIN.getOrElse(index) { Constants.DEFAULT_DURATION_MIN }
}
