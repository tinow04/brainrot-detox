package dev.tinow.brainrotstopper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalBsColors = staticCompositionLocalOf { BsColors.Dark }
val LocalBsType = staticCompositionLocalOf { BsType() }

object BsTheme {
    val colors: BsColors
        @Composable @ReadOnlyComposable get() = LocalBsColors.current
    val type: BsType
        @Composable @ReadOnlyComposable get() = LocalBsType.current
}

/** Follows the system dark mode (Mocha / Latte); [dark] is for previews and tests. */
@Composable
fun BsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBsColors provides if (dark) BsColors.Dark else BsColors.Light,
        LocalBsType provides BsType(),
        content = content,
    )
}
