package dev.tinow.brainrotstopper.domain.permission

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.ui.banner.BannerState
import org.junit.Test

class ProtectionTest {
    private val off = PermissionSnapshot()
    private val unbound = PermissionSnapshot(a11yEnabled = true)
    private val ok = PermissionSnapshot(a11yEnabled = true, a11yBound = true)
    private val restricted = PermissionSnapshot(restricted = true)

    @Test fun `status table`() {
        assertThat(Protection.status(off)).isEqualTo(ProtectionStatus.NotEnabled)
        assertThat(Protection.status(unbound)).isEqualTo(ProtectionStatus.EnabledNotBound)
        assertThat(Protection.status(ok)).isEqualTo(ProtectionStatus.Active)
        assertThat(Protection.status(restricted)).isEqualTo(ProtectionStatus.Restricted)
    }

    @Test fun `bound wins even if the settings entry looks off`() {
        assertThat(Protection.status(PermissionSnapshot(a11yEnabled = false, a11yBound = true))).isEqualTo(ProtectionStatus.Active)
    }

    @Test fun `restricted wins over enabled when not bound`() {
        assertThat(Protection.status(PermissionSnapshot(a11yEnabled = true, restricted = true))).isEqualTo(ProtectionStatus.Restricted)
    }

    @Test fun `setup is the start route only on the first start with an open step`() {
        assertThat(Protection.startOnSetup(false, off)).isTrue()
        assertThat(Protection.startOnSetup(false, unbound)).isTrue()
        assertThat(Protection.startOnSetup(false, ok)).isFalse()
        assertThat(Protection.startOnSetup(true, off)).isFalse()
    }

    @Test fun `battery alone is not mandatory`() {
        assertThat(Protection.mandatoryOpen(ok.copy(batteryUnrestricted = false))).isFalse()
    }

    @Test fun `banner variants and priority`() {
        assertThat(BannerState.from(ProtectionStatus.Active, false, false)).isNull()
        assertThat(BannerState.from(ProtectionStatus.NotEnabled, false, false)).isEqualTo(BannerState.ProtectionOffDisabled)
        assertThat(BannerState.from(ProtectionStatus.EnabledNotBound, false, false)).isEqualTo(BannerState.ProtectionOffUnbound)
        assertThat(BannerState.from(ProtectionStatus.Restricted, false, false)).isEqualTo(BannerState.ProtectionOffRestricted)
        assertThat(BannerState.from(ProtectionStatus.Active, true, true)).isEqualTo(BannerState.SettingsCorrupt)
        assertThat(BannerState.from(ProtectionStatus.Active, false, true)).isEqualTo(BannerState.Degraded)
        // no protection beats damaged settings
        assertThat(BannerState.from(ProtectionStatus.NotEnabled, true, true)).isEqualTo(BannerState.ProtectionOffDisabled)
    }
}
