package dev.tinow.brainrotstopper.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tinow.brainrotstopper.lock.screens.insetPadding
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.components.BsButton
import dev.tinow.brainrotstopper.ui.components.BsButtonStyle
import dev.tinow.brainrotstopper.ui.components.BsTag
import dev.tinow.brainrotstopper.ui.components.BsTagKind
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

enum class SetupState { Open, Ok, Error }

/** One setup step: status tag, text and the action that fixes it (shown when not ok). */
data class SetupRow(val label: String, val state: SetupState, val action: String?, val onAction: () -> Unit)

/** First-run / repair checklist (architecture 7); the host decides the order and the actions. */
@Composable
fun SetupScreen(rows: List<SetupRow>, hint: String?, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Column(
        modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(insetPadding(BsDims.ScrTop, BsDims.ScrBottom)),
        verticalArrangement = Arrangement.spacedBy(BsDims.S16),
    ) {
        Row {
            BsText(Strings.TITLE_PROMPT + " ", t.title, c.accent)
            BsText(Strings.SETUP_TITLE, t.title, c.text)
        }
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(BsDims.S8)) {
                Row(horizontalArrangement = Arrangement.spacedBy(BsDims.S12), verticalAlignment = Alignment.CenterVertically) {
                    when (row.state) {
                        SetupState.Ok -> BsTag(Strings.TAG_OK, BsTagKind.Ok)
                        SetupState.Open -> BsTag(Strings.TAG_OPEN, BsTagKind.Warn)
                        SetupState.Error -> BsTag(Strings.TAG_ERROR, BsTagKind.Fail)
                    }
                    BsText(row.label, t.body, c.text, Modifier.weight(1f))
                }
                if (row.state != SetupState.Ok && row.action != null) {
                    BsButton(row.action, row.onAction, style = if (row.state == SetupState.Error) BsButtonStyle.Primary else BsButtonStyle.Ghost)
                }
            }
        }
        if (hint != null) BsText(hint, t.label, c.dim)
    }
}
