package dev.tinow.brainrotstopper.data

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tinow.brainrotstopper.data.anki.AndroidProviderGateway
import dev.tinow.brainrotstopper.data.anki.AnkiContentProviderSource
import dev.tinow.brainrotstopper.data.anki.AnkiContract
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Ease
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contract test of the card source against the REAL AnkiDroid (v2.25.3 tested). Skipped unless AnkiDroid is installed,
 * the permission is granted (`adb shell pm grant dev.tinow.brainrotstopper com.ichi2.anki.permission.READ_WRITE_DATABASE`)
 * and the collection has due cards. The answer test changes review state, so it only runs on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class AnkiSourceContractTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val source = AnkiContentProviderSource(AndroidProviderGateway(context))

    @Before fun ready() {
        assumeTrue("AnkiDroid not installed", AndroidProviderGateway(context).providerAvailable())
        assumeTrue("permission not granted", context.checkSelfPermission(AnkiContract.PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    private fun <T> ok(r: AnkiResult<T>): T {
        check(r is AnkiResult.Ok) { "expected Ok but got $r" }
        return r.value
    }

    @Test fun probeAndDecks() = runBlocking {
        ok(source.probe())
        val decks = ok(source.listDecks())
        assertTrue("at least the Default deck", decks.isNotEmpty())
        val counts = ok(source.deckCounts(DeckSelection.All))
        assertTrue(counts.total >= 0)
    }

    @Test fun nextCardRenderAndLabels() = runBlocking {
        val card = ok(source.nextCard(DeckSelection.All))
        assumeTrue("no due card in the collection", card != null)
        card!!
        assertEquals(4, card.buttonCount)
        assertNotNull("interval labels (four strings, isolates removed)", card.intervalLabels)
        assertTrue(card.intervalLabels!!.none { it.contains('⁨') || it.contains('⁩') })
        val rendered = ok(source.render(card.ref))
        assertTrue("question is not empty", rendered.question.isNotBlank())
        assertNotNull("note type css", rendered.css)
    }

    @Test fun answerLandsInTheReviewCount() = runBlocking {
        assumeTrue("changes review state: emulator only", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk", ignoreCase = true))
        val card = ok(source.nextCard(DeckSelection.All))
        assumeTrue("no due card in the collection", card != null)
        val before = ok(source.cardProgress(card!!.ref))
        ok(source.answer(card.ref, Ease.GOOD, 1500))
        assertEquals("reps rises by one (answerVerify)", before + 1, ok(source.cardProgress(card.ref)))
    }
}
