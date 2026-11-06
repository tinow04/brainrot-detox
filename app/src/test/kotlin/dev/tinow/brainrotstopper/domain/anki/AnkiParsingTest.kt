package dev.tinow.brainrotstopper.domain.anki

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnkiParsingTest {
    @Test fun `deck counts in the order learn review new`() {
        assertThat(AnkiParsing.deckCounts("[2,0,10]")).isEqualTo(DeckCounts(2, 0, 10))
        assertThat(AnkiParsing.deckCounts(" [ 0 , 3 , 1 ] ")).isEqualTo(DeckCounts(0, 3, 1))
    }

    @Test fun `deck counts that do not fit are unreadable`() {
        for (raw in listOf(null, "", "[]", "[1,2]", "[1,2,3,4]", "[1,2,\"x\"]", "[-1,0,0]", "{}", "garbage", "[1,2,3.5]")) {
            assertThat(AnkiParsing.deckCounts(raw)).isNull()
        }
    }

    @Test fun `interval labels lose the unicode isolates measured in M0`() {
        val raw = "[\"⁨<1⁩m\",\"⁨ 6⁩m\",\"⁨ 10⁩m\",\"⁨ 5⁩d\"]"
        assertThat(AnkiParsing.intervalLabels(raw)).containsExactly("<1m", "6m", "10m", "5d").inOrder()
    }

    @Test fun `interval labels need exactly four non-empty strings`() {
        assertThat(AnkiParsing.intervalLabels("[\"1m\",\"6m\",\"10m\"]")).isNull()
        assertThat(AnkiParsing.intervalLabels("[\"1m\",\"6m\",\"10m\",\"\"]")).isNull()
        assertThat(AnkiParsing.intervalLabels("[\"1m\",\"6m\",\"10m\",5]")).isNull()
        assertThat(AnkiParsing.intervalLabels("[\"⁨⁩\",\"6m\",\"10m\",\"5d\"]")).isNull()
        assertThat(AnkiParsing.intervalLabels(null)).isNull()
        assertThat(AnkiParsing.intervalLabels("nope")).isNull()
    }

    @Test fun `media files are a hint, damage means an empty list`() {
        assertThat(AnkiParsing.mediaFiles("[\"a.png\",\"b c.jpg\"]")).containsExactly("a.png", "b c.jpg").inOrder()
        assertThat(AnkiParsing.mediaFiles("[]")).isEmpty()
        assertThat(AnkiParsing.mediaFiles("nope")).isEmpty()
        assertThat(AnkiParsing.mediaFiles(null)).isEmpty()
    }
}
