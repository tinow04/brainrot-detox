package dev.tinow.brainrotstopper.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.tinow.brainrotstopper.BrainrotApp
import dev.tinow.brainrotstopper.data.anki.CardHtml
import dev.tinow.brainrotstopper.data.anki.CardSide
import dev.tinow.brainrotstopper.debug.fake.CardFixtures
import dev.tinow.brainrotstopper.lock.render.CardView
import dev.tinow.brainrotstopper.lock.render.rememberCardStyle
import dev.tinow.brainrotstopper.lock.screens.AnswerScreen
import dev.tinow.brainrotstopper.lock.screens.Intervals
import dev.tinow.brainrotstopper.lock.screens.LockHead
import dev.tinow.brainrotstopper.lock.screens.QuestionScreen

/**
 * Gallery names `card-<fixture>-<q|a>[-plain][-img]` show a fixture card in the real A2/A3 frames, e.g.
 * `--es screen card-cloze-a`. `-plain` forces the plain-text renderer, `-img` hands the red PNG to the card as `rot.png`,
 * `-net` loads the images through the real path (`AppContainer.mediaImages`: settings, cache, server) before the card shows.
 */
object CardGallery {
    fun handles(name: String) = name.startsWith("card-")

    val names: List<String> get() = CardFixtures.all.keys.flatMap { listOf("card-$it-q", "card-$it-a") }

    @Composable
    fun Screen(name: String) {
        val parts = name.removePrefix("card-").split('-')
        val card = CardFixtures.all.getValue(parts[0])
        val answer = parts.getOrNull(1) == "a"
        val plain = "plain" in parts
        val ctx = LocalContext.current
        val loaded: Map<String, String>? by produceState<Map<String, String>?>(null, name) {
            value = when {
                "net" in parts -> {
                    val names = CardSide.entries.flatMap { CardHtml.imageNames(card, it) }
                    (ctx.applicationContext as BrainrotApp).container.mediaImages.load(names)
                }
                "img" in parts -> mapOf("rot.png" to CardFixtures.RED_PNG)
                else -> emptyMap()
            }
        }
        val images = loaded ?: return // like the quiz engine: images first, then the card
        val style = rememberCardStyle()
        val head = LockHead("instagram", "biologie", 1, 5)
        val q = CardHtml.build(card, CardSide.Question, 0, style, images, dimmed = answer)
        if (!answer) {
            QuestionScreen(head, 2, {}, {}) { CardView(q, forcePlain = plain) }
        } else {
            val a = CardHtml.build(card, CardSide.Answer, 0, style, images)
            AnswerScreen(
                head, Intervals("<1m", "6m", "10m", "4d"), {},
                question = { if (!CardHtml.answerRepeatsQuestion(card)) CardView(q, dimmed = true, forcePlain = plain) },
                answer = { CardView(a, forcePlain = plain) },
            )
        }
    }
}
