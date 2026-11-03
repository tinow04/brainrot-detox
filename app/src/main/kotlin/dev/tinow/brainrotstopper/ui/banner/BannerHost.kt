package dev.tinow.brainrotstopper.ui.banner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tinow.brainrotstopper.ui.Strings
import dev.tinow.brainrotstopper.ui.components.BsBanner
import dev.tinow.brainrotstopper.ui.components.BsTagKind

/** Protection state shown above all tabs (architecture 7); nothing is shown when everything is fine. */
enum class BannerState(val text: String, val kind: BsTagKind) {
    Degraded(Strings.BANNER_DEGRADED, BsTagKind.Warn),
    ProtectionOffDisabled(Strings.BANNER_OFF_DISABLED, BsTagKind.Fail),
    ProtectionOffUnbound(Strings.BANNER_OFF_UNBOUND, BsTagKind.Fail),
    ProtectionOffRestricted(Strings.BANNER_OFF_RESTRICTED, BsTagKind.Fail),
    SettingsCorrupt(Strings.BANNER_CORRUPT, BsTagKind.Fail),
}

@Composable
fun BannerHost(state: BannerState?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (state != null) BsBanner(state.text, state.kind, onClick, modifier)
}
