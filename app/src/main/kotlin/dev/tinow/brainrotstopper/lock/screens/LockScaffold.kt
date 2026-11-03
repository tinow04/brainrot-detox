package dev.tinow.brainrotstopper.lock.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

/** Insets rule (architecture 8): top = max(base, statusBar + 24 dp), bottom = max(base, navigationBar + 8 dp); never added on top of the mockup padding. */
@Composable
fun insetPadding(topBase: Dp, bottomBase: Dp): PaddingValues {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(
        start = BsDims.Margin,
        end = BsDims.Margin,
        top = maxOf(topBase, status + 24.dp),
        bottom = maxOf(bottomBase, nav + 8.dp),
    )
}

/** `.scr` without scrolling: header, body filling the rest (it scrolls itself, e.g. inside a card frame) and a fixed action area. */
@Composable
fun LockFrame(
    header: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(BsTheme.colors.bg).padding(insetPadding(BsDims.ScrTop, BsDims.ScrBottom))) {
        header()
        Column(Modifier.weight(1f).fillMaxWidth().padding(vertical = BsDims.S24), content = body)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BsDims.S8), content = bottom)
    }
}

/**
 * `.scr`: optional header, vertically scrollable content (centred when it is short) and a fixed bottom action area,
 * so the main button stays reachable at any font scale.
 */
@Composable
fun LockScaffold(
    header: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    contentGap: Dp = 28.dp,
    centered: Boolean = true,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(BsTheme.colors.bg).padding(insetPadding(BsDims.ScrTop, BsDims.ScrBottom))) {
        header?.invoke()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(vertical = BsDims.S24),
                verticalArrangement = if (centered) Arrangement.spacedBy(contentGap, Alignment.CenterVertically) else Arrangement.spacedBy(contentGap),
                content = content,
            )
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BsDims.S8), content = bottom)
    }
}
