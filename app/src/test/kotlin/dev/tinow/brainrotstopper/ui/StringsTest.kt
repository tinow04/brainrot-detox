package dev.tinow.brainrotstopper.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringsTest {
    @Test fun plural() {
        assertThat(Strings.karten(1)).isEqualTo("1 karte")
        assertThat(Strings.karten(2)).isEqualTo("2 karten")
        assertThat(Strings.karten(0)).isEqualTo("0 karten")
        assertThat(Strings.tage(1)).isEqualTo("1 tag")
        assertThat(Strings.tage(12)).isEqualTo("12 tage")
        assertThat(Strings.minuten(15)).isEqualTo("15 min")
    }

    @Test fun progressBar() {
        assertThat(Strings.progress(0, 5)).isEqualTo("[░░░░░] 0/5")
        assertThat(Strings.progress(1, 5)).isEqualTo("[█░░░░] 1/5")
        assertThat(Strings.progress(5, 5)).isEqualTo("[█████] 5/5")
        assertThat(Strings.progress(9, 5)).isEqualTo("[█████] 5/5")
        assertThat(Strings.progress(3, 20)).hasLength(2 + 20 + 1 + 4)
    }

    @Test fun timeSaved() {
        assertThat(Strings.timeSaved(200)).isEqualTo("~3h 20m")
        assertThat(Strings.timeSaved(45)).isEqualTo("~45 min")
        assertThat(Strings.timeSaved(0)).isEqualTo("0 min")
        assertThat(Strings.timeSaved(60)).isEqualTo("~1h 0m")
    }

    @Test fun quote() {
        assertThat(Strings.quote(86, 100)).isEqualTo("86 %")
        assertThat(Strings.quote(0, 0)).isEqualTo("-")
        assertThat(Strings.quote(2, 3)).isEqualTo("67 %")
    }

    @Test fun deckLabel() {
        assertThat(Strings.deckLabel("Biologie::Zellbiologie")).isEqualTo("zellbiologie")
        assertThat(Strings.deckLabel("Default")).isEqualTo("default")
        assertThat(Strings.deckLabel(null)).isEqualTo("alle decks")
        assertThat(Strings.deckLabel("Ärzte::Über")).isEqualTo("über")
    }

    @Test fun duration() {
        assertThat(Strings.mmss(161_000)).isEqualTo("02:41")
        assertThat(Strings.mmss(0)).isEqualTo("00:00")
        assertThat(Strings.mmss(-5)).isEqualTo("00:00")
    }

    @Test fun texts() {
        assertThat(Strings.introText(5, resumed = false).joinToString("") { it.text }).isEqualTo("beantworte 5 karten richtig, dann geht's weiter.")
        assertThat(Strings.introText(3, resumed = true).joinToString("") { it.text }).isEqualTo("noch 3 karten richtig, dann geht's weiter.")
        assertThat(Strings.feedbackProgress(1, 5, "Instagram").joinToString("") { it.text }).isEqualTo("fortschritt bleibt bei 1/5. noch 4 richtige bis instagram offen ist.")
        assertThat(Strings.unlockedText("Instagram", 15).joinToString("") { it.text }).isEqualTo("instagram ist für 15 min freigegeben.")
        assertThat(Strings.cardHeader("Instagram", "biologie")).isEqualTo("instagram · biologie")
        assertThat(Strings.sepBlocked(4)).isEqualTo("gesperrt · 4")
        assertThat(Strings.sessionCards(7, 5)).isEqualTo("7 (5 richtig)")
        assertThat(Strings.rowDescription("Instagram", true)).isEqualTo("Instagram, gesperrt")
    }

    /** Composed mockup texts must be reproduced exactly (see re: entries in design/reference/string-whitelist.txt). */
    @Test fun mockupFormats() {
        assertThat(Strings.introCheck("instagram")).isEqualTo("\$ ./check --app instagram")
        assertThat(Strings.rateCommand(2, "again")).isEqualTo("\$ ./rate --card 2 --again")
        assertThat(Strings.unlockCommand("Instagram")).isEqualTo("\$ ./unlock instagram")
        assertThat(Strings.openCommand("Instagram")).isEqualTo("\$ ./open instagram")
        assertThat(Strings.cardLabel(2)).isEqualTo("karte 2 · frage")
        assertThat(Strings.ankiConnected(3)).isEqualTo("api aktiv · 3 decks gefunden")
        assertThat(Strings.due(124)).isEqualTo("124 fällig")
        assertThat(Strings.WEEKDAYS).containsExactly("mo", "di", "mi", "do", "fr", "sa", "so").inOrder()
    }
}
