package dev.tinow.brainrotstopper.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import org.junit.Test

class ModelsSerializationTest {
    private inline fun <reified T> roundtrip(value: T): T = BsJson.decodeFromString(BsJson.encodeToString(value))

    private val session = QuizSession(
        sessionId = "s1", targetPkg = "com.insta", deck = DeckSelection.Single(42L, "Deck"), required = 5, unlockMinutes = 15,
        correct = 2, answered = 3, wrong = 1, activeMs = 12_000, lastWrongElapsed = 99L, startedElapsed = 10, lastActiveElapsed = 20,
        bootCount = 7, startedWall = 1_700_000_000_000,
        pending = PendingAnswer("a1", 1L, 0, Ease.GOOD, 5, repsBefore = 3),
    )

    @Test fun settingsRoundtripBothDecks() {
        val all = Settings(blockedPackages = setOf("a", "b"), requiredCorrect = 7, unlockMinutes = 30, mediaBaseUrl = "http://x", mediaToken = "t")
        assertThat(roundtrip(all)).isEqualTo(all)
        val single = all.copy(deck = DeckSelection.Single(5, "Name"))
        assertThat(roundtrip(single)).isEqualTo(single)
    }

    @Test fun deckSelectionUsesStableDiscriminators() {
        assertThat(BsJson.encodeToString<DeckSelection>(DeckSelection.All)).contains("\"all\"")
        assertThat(BsJson.encodeToString<DeckSelection>(DeckSelection.Single(1, "x"))).contains("\"single\"")
    }

    @Test fun runtimeRoundtripFull() {
        val runtime = Runtime(
            grants = mapOf("com.insta" to UnlockGrant(1, 2, 1, 3, 4, GrantKind.EMERGENCY)),
            session = session,
            episode = Episode("com.insta", 1, 2, 3),
            emergency = EmergencyState(5, 6),
            diag = Diag(1, 2, 3, 4, true),
        )
        assertThat(roundtrip(runtime)).isEqualTo(runtime)
    }

    @Test fun runtimeRoundtripEmpty() {
        assertThat(roundtrip(Runtime())).isEqualTo(Runtime())
    }

    @Test fun statsRoundtripWithLongKeys() {
        val stats = Stats(days = mapOf(20_400L to DayStats(blocks = 3, aborted = 1, perApp = mapOf("a" to 2)), 20_401L to DayStats()))
        val decoded = roundtrip(stats)
        assertThat(decoded).isEqualTo(stats)
        assertThat(decoded.days.keys).containsExactly(20_400L, 20_401L)
    }

    @Test fun unknownKeysAreIgnoredAndMissingKeysGetDefaults() {
        val decoded = BsJson.decodeFromString<Settings>("""{"requiredCorrect":9,"somethingNew":{"x":1}}""")
        assertThat(decoded.requiredCorrect).isEqualTo(9)
        assertThat(decoded.unlockMinutes).isEqualTo(15)
        assertThat(decoded.deck).isEqualTo(DeckSelection.All)
    }

    @Test fun defaultsAreWrittenOut() {
        assertThat(BsJson.encodeToString(Settings())).contains("requiredCorrect")
    }

    @Test fun sanitizedClampsRequiredAndResetsUnknownDuration() {
        assertThat(Settings(requiredCorrect = 0).sanitized().requiredCorrect).isEqualTo(1)
        assertThat(Settings(requiredCorrect = 99).sanitized().requiredCorrect).isEqualTo(20)
        assertThat(Settings(unlockMinutes = 7).sanitized().unlockMinutes).isEqualTo(15)
        assertThat(Settings(unlockMinutes = 60).sanitized().unlockMinutes).isEqualTo(60)
    }

    @Test fun onlyGoodAndEasyCount() {
        assertThat(Ease.entries.filter { it.counts }).containsExactly(Ease.GOOD, Ease.EASY)
        assertThat(Ease.fromValue(3)).isEqualTo(Ease.GOOD)
        assertThat(Ease.fromValue(0)).isNull()
    }
}
