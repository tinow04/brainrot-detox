package dev.tinow.brainrotstopper.ui.config

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.components.BsBox
import dev.tinow.brainrotstopper.ui.components.BsButton
import dev.tinow.brainrotstopper.ui.components.BsButtonStyle
import dev.tinow.brainrotstopper.ui.components.BsKv
import dev.tinow.brainrotstopper.ui.components.BsRadioOption
import dev.tinow.brainrotstopper.ui.components.BsSearchField
import dev.tinow.brainrotstopper.ui.components.BsSegments
import dev.tinow.brainrotstopper.ui.components.BsStepper
import dev.tinow.brainrotstopper.ui.components.BsTag
import dev.tinow.brainrotstopper.ui.components.BsTagKind
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.main.ScreenTitle
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

data class DeckItem(val name: String?, val due: Int) {
    val label get() = if (name == null) Strings.ALL_DECKS else name
}

enum class MediaCheck { Unknown, Ok, Failed }

/** Optional `bilder` box (media server, architecture 5a). The token is masked and never logged. */
data class MediaConfig(val url: String, val token: String, val check: MediaCheck)

data class ConfigState(
    val required: Int,
    val durations: List<Int>,
    val durationIndex: Int,
    val decks: List<DeckItem>,
    val deckIndex: Int,
    val ankiConnected: Boolean,
    val media: MediaConfig? = null,
)

/** B2: settings. Everything is stateless; the host owns the values. */
@Composable
fun ConfigScreen(
    state: ConfigState,
    onRequired: (Int) -> Unit,
    onDuration: (Int) -> Unit,
    onDeck: (Int) -> Unit,
    onMediaUrl: (String) -> Unit = {},
    onMediaToken: (String) -> Unit = {},
    onMediaTest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(28.dp)) { // mockup B2 sets `gap: 28px` inline (off the 4/8/12/16/24/32 grid on purpose)
        ScreenTitle(Strings.TITLE_CONFIG)
        BsBox(Strings.BOX_CARDS) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BsText(Strings.KV_REQUIRED, t.body, c.dim, Modifier.weight(1f))
                BsStepper(state.required, { onRequired(state.required - 1) }, { onRequired(state.required + 1) })
            }
        }
        BsBox(Strings.BOX_DURATION) {
            BsSegments(state.durations.map(Strings::minuten), state.durationIndex, onDuration)
        }
        BsBox(Strings.BOX_DECK) {
            state.decks.forEachIndexed { i, d ->
                BsRadioOption(i == state.deckIndex, d.label, Strings.due(d.due), { onDeck(i) }, divider = i != state.decks.lastIndex)
            }
        }
        BsBox(Strings.BOX_ANKIDROID) {
            Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S12), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BsTag(if (state.ankiConnected) Strings.TAG_OK else Strings.TAG_ERROR, if (state.ankiConnected) BsTagKind.Ok else BsTagKind.Fail)
                BsText(Strings.ANKI_OK.takeIf { state.ankiConnected } ?: Strings.ERR_NOT_AVAILABLE.title.substringAfter("] "), t.body, c.text)
            }
            if (state.ankiConnected) BsText(Strings.ankiConnected(state.decks.count { it.name != null }), t.label, c.dim, Modifier.padding(top = BsDims.S4))
        }
        state.media?.let { m ->
            BsBox(Strings.BOX_MEDIA) {
                Column(verticalArrangement = Arrangement.spacedBy(BsDims.S8)) {
                    BsSearchField(m.url, onMediaUrl, "https://", Strings.MEDIA_URL, description = Strings.MEDIA_URL)
                    BsSearchField(m.token, onMediaToken, "", Strings.MEDIA_TOKEN, description = Strings.MEDIA_TOKEN, password = true)
                    BsButton(Strings.BTN_MEDIA_TEST, onMediaTest, style = BsButtonStyle.Ghost)
                    when (m.check) {
                        MediaCheck.Unknown -> BsText(Strings.MEDIA_OFF, t.label, c.dim)
                        MediaCheck.Ok -> BsText(Strings.TAG_OK + " " + Strings.MEDIA_OK, t.label, c.green)
                        MediaCheck.Failed -> BsText(Strings.TAG_ERROR + " " + Strings.MEDIA_FAIL, t.label, c.red)
                    }
                }
            }
        }
    }
}
