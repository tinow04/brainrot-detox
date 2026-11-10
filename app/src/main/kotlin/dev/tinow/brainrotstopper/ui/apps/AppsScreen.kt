package dev.tinow.brainrotstopper.ui.apps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.components.AppRow
import dev.tinow.brainrotstopper.ui.components.BsSearchField
import dev.tinow.brainrotstopper.ui.components.BsSep
import dev.tinow.brainrotstopper.ui.components.BsText
import dev.tinow.brainrotstopper.ui.main.ScreenTitle
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

data class AppItem(val pkg: String, val label: String, val blocked: Boolean)

/** B1: blocked apps first (highlighted), then all others; the filter applies to both lists. */
@Composable
fun AppsScreen(
    blocked: List<AppItem>,
    others: List<AppItem>,
    query: String,
    onQuery: (String) -> Unit,
    onToggle: (AppItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BsTheme.colors
    val t = BsTheme.type
    LazyColumn(modifier.fillMaxSize()) {
        item { ScreenTitle(Strings.TITLE_APPS, Modifier.padding(bottom = BsDims.S16)) }
        item {
            BsSearchField(query, onQuery, Strings.SEARCH_PLACEHOLDER, Strings.SEARCH_PROMPT, Modifier.padding(bottom = BsDims.S16), description = "apps suchen")
        }
        item { BsSep(Strings.sepBlocked(blocked.size), Modifier.padding(bottom = BsDims.S16)) }
        // "keine treffer" only once and only when the search found nothing at all (not under an empty blocked section
        // while the hits are listed below it)
        if (blocked.isEmpty() && query.isEmpty()) {
            item { BsText(Strings.EMPTY_BLOCKED, t.body, c.dim, Modifier.padding(vertical = BsDims.S12)) }
        }
        items(blocked, key = { it.pkg }) { AppRow(it.label, it.blocked, { on -> onToggle(it, on) }) }
        item { BsSep(Strings.SEP_ALL_APPS, Modifier.padding(top = BsDims.S16, bottom = BsDims.S16)) }
        if (blocked.isEmpty() && others.isEmpty() && query.isNotEmpty()) {
            item { BsText(Strings.EMPTY_SEARCH, t.body, c.dim, Modifier.fillMaxWidth().padding(vertical = BsDims.S12)) }
        }
        items(others, key = { it.pkg }) { AppRow(it.label, it.blocked, { on -> onToggle(it, on) }) }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp)) }
    }
}
