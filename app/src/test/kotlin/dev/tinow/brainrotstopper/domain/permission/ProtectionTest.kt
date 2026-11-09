package dev.tinow.brainrotstopper.domain.permission

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.debug.fake.FakeCardSource
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.CardSource
import dev.tinow.brainrotstopper.domain.anki.DeckCounts
import dev.tinow.brainrotstopper.domain.anki.DeckInfo
import dev.tinow.brainrotstopper.ui.banner.BannerState
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtectionTest {
    private val ankiOk = PermissionSnapshot(ankiInstalled = true, ankiProvider = true, ankiPermission = true)
    private val off = ankiOk
    private val unbound = ankiOk.copy(a11yEnabled = true)
    private val ok = ankiOk.copy(a11yEnabled = true, a11yBound = true)
    private val restricted = ankiOk.copy(restricted = true)
    private val ready = AnkiProbe.Ready(3)

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
        // an AnkiDroid step alone is enough for the start route
        assertThat(Protection.startOnSetup(false, ok.copy(ankiPermission = false))).isTrue()
        assertThat(Protection.startOnSetup(true, ok.copy(ankiPermission = false))).isFalse()
    }

    @Test fun `battery alone is not mandatory`() {
        assertThat(Protection.mandatoryOpen(ok.copy(batteryUnrestricted = false), ready)).isFalse()
    }

    // ---- AnkiDroid steps 1-3

    @Test fun `anki missing - permission waits, ready waits`() {
        val s = Protection.ankiSteps(PermissionSnapshot(), AnkiProbe.Unknown)
        assertThat(s.installed).isEqualTo(AnkiInstallStep.Missing)
        assertThat(s.permission).isEqualTo(AnkiPermissionStep.NeedsInstall)
        assertThat(s.ready).isEqualTo(AnkiReadyStep.NeedsPrevious)
        assertThat(s.open).isTrue()
    }

    @Test fun `installed without permission - ask, or app info after a permanent denial`() {
        val p = PermissionSnapshot(ankiInstalled = true, ankiProvider = true)
        assertThat(Protection.ankiSteps(p, AnkiProbe.Unknown).permission).isEqualTo(AnkiPermissionStep.Ask)
        assertThat(Protection.ankiSteps(p, AnkiProbe.Unknown, deniedPermanently = true).permission).isEqualTo(AnkiPermissionStep.Denied)
        assertThat(Protection.ankiSteps(p, AnkiProbe.Unknown).ready).isEqualTo(AnkiReadyStep.NeedsPrevious)
        // granted later (app info): the old denial no longer matters
        assertThat(Protection.ankiSteps(ankiOk, ready, deniedPermanently = true).permission).isEqualTo(AnkiPermissionStep.Ok)
    }

    @Test fun `provider gone while installed and allowed is the API switch`() {
        val s = Protection.ankiSteps(ankiOk.copy(ankiProvider = false), ready)
        assertThat(s.ready).isEqualTo(AnkiReadyStep.ApiOff)
        assertThat(s.open).isTrue()
        assertThat(Protection.mayProbe(ankiOk.copy(ankiProvider = false))).isFalse()
    }

    @Test fun `null cursor while the provider resolves is not the API switch`() {
        // measured: AnkiDroid without storage access answers null although its provider resolves
        val s = Protection.ankiSteps(ankiOk, AnkiProbe.Failed(AnkiError.NotInstalledOrApiOff))
        assertThat(s.ready).isEqualTo(AnkiReadyStep.NoData)
        assertThat(s.open).isTrue()
    }

    @Test fun `check result decides step 3`() {
        assertThat(Protection.ankiSteps(ankiOk, ready).ready).isEqualTo(AnkiReadyStep.Ok(3))
        assertThat(Protection.ankiSteps(ankiOk, AnkiProbe.Failed(AnkiError.StorageNotConfigured)).ready)
            .isEqualTo(AnkiReadyStep.Failed(AnkiError.StorageNotConfigured))
        assertThat(Protection.ankiSteps(ankiOk, AnkiProbe.Failed(AnkiError.Unreachable)).open).isTrue()
    }

    @Test fun `a running check is neither open nor complete`() {
        val s = Protection.ankiSteps(ankiOk, AnkiProbe.Unknown)
        assertThat(s.ready).isEqualTo(AnkiReadyStep.Checking)
        assertThat(s.open).isFalse()
        assertThat(s.complete).isFalse()
        assertThat(Protection.setupComplete(ok, AnkiProbe.Unknown)).isFalse()
        assertThat(Protection.setupComplete(ok, ready)).isTrue()
    }

    @Test fun `setup is complete only with protection and all three anki steps`() {
        assertThat(Protection.setupComplete(ok.copy(a11yBound = false), ready)).isFalse()
        assertThat(Protection.setupComplete(ok.copy(ankiInstalled = false), ready)).isFalse()
        assertThat(Protection.setupComplete(ok.copy(ankiPermission = false), ready)).isFalse()
        assertThat(Protection.setupComplete(ok, AnkiProbe.Failed(AnkiError.Unreachable))).isFalse()
    }

    // ---- provider check

    @Test fun `probe counts normal decks only`() = runTest {
        val source = object : CardSource by FakeCardSource() {
            override suspend fun listDecks() = AnkiResult.Ok(
                listOf(
                    DeckInfo(1, "Default", DeckCounts(0, 0, 0), dynamic = false),
                    DeckInfo(2, "Sprachen::Spanisch", DeckCounts(0, 1, 2), dynamic = false),
                    DeckInfo(3, "Filtered Deck 1", DeckCounts(0, 1, 0), dynamic = true),
                ),
            )
        }
        assertThat(Protection.probe(source)).isEqualTo(AnkiProbe.Ready(2))
    }

    @Test fun `probe reports the first failing call and does not list decks after a failed probe`() = runTest {
        val fake = FakeCardSource().also { it.failing["probe"] = AnkiError.PermissionMissing }
        assertThat(Protection.probe(fake)).isEqualTo(AnkiProbe.Failed(AnkiError.PermissionMissing))
        assertThat(fake.calls["listDecks"]).isNull()

        val decksFail = FakeCardSource().also { it.failing["listDecks"] = AnkiError.StorageNotConfigured }
        assertThat(Protection.probe(decksFail)).isEqualTo(AnkiProbe.Failed(AnkiError.StorageNotConfigured))
    }

    @Test fun `fake error scenarios fail the setup check too`() = runTest {
        assertThat(Protection.probe(FakeCardSource.scenario("error-storage"))).isEqualTo(AnkiProbe.Failed(AnkiError.StorageNotConfigured))
        assertThat(Protection.probe(FakeCardSource.scenario("normal"))).isEqualTo(AnkiProbe.Ready(1))
        // a missing deck is a lock-screen error only (the setup has no selected deck)
        assertThat(Protection.probe(FakeCardSource.scenario("error-deck"))).isEqualTo(AnkiProbe.Ready(1))
    }

    // ---- banner

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

    @Test fun `degraded banner follows the anki steps while the service runs`() {
        fun banner(p: PermissionSnapshot, probe: AnkiProbe) =
            BannerState.from(Protection.status(p), false, Protection.ankiSteps(p, probe).open)
        assertThat(banner(ok, ready)).isNull()
        assertThat(banner(ok, AnkiProbe.Unknown)).isNull() // still checking: no warning yet
        assertThat(banner(ok.copy(ankiInstalled = false), AnkiProbe.Unknown)).isEqualTo(BannerState.Degraded)
        assertThat(banner(ok.copy(ankiPermission = false), AnkiProbe.Unknown)).isEqualTo(BannerState.Degraded)
        assertThat(banner(ok.copy(ankiProvider = false), AnkiProbe.Unknown)).isEqualTo(BannerState.Degraded)
        assertThat(banner(ok, AnkiProbe.Failed(AnkiError.StorageNotConfigured))).isEqualTo(BannerState.Degraded)
        // without the service the red banner wins
        assertThat(banner(off.copy(ankiInstalled = false), AnkiProbe.Unknown)).isEqualTo(BannerState.ProtectionOffDisabled)
    }
}
