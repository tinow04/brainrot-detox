package dev.tinow.brainrotstopper.ui.setup

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.permission.AnkiProbe
import dev.tinow.brainrotstopper.domain.permission.PermissionSnapshot
import dev.tinow.brainrotstopper.ui.Strings
import org.junit.Test

class SetupModelTest {
    private val all = PermissionSnapshot(
        a11yEnabled = true, a11yBound = true, batteryUnrestricted = true,
        ankiInstalled = true, ankiProvider = true, ankiPermission = true,
    )

    private fun items(p: PermissionSnapshot, probe: AnkiProbe = AnkiProbe.Ready(2), denied: Boolean = false) = SetupModel.items(p, probe, denied)

    @Test fun `five rows in the order of architecture 6`() {
        assertThat(items(all).map { it.label }).containsExactly(
            Strings.SETUP_ANKI_INSTALLED, Strings.SETUP_ANKI_PERMISSION, Strings.SETUP_ANKI_READY, Strings.SETUP_ACCESSIBILITY, Strings.SETUP_BATTERY,
        ).inOrder()
        assertThat(items(all).map { it.state }.toSet()).containsExactly(SetupState.Ok)
        assertThat(items(all).mapNotNull { it.action }).isEmpty()
        assertThat(items(all)[2].detail).isEqualTo("api aktiv · 2 decks gefunden")
    }

    @Test fun `anki uninstalled`() {
        val rows = items(all.copy(ankiInstalled = false, ankiProvider = false, ankiPermission = false), AnkiProbe.Unknown)
        assertThat(rows[0]).isEqualTo(SetupItem(Strings.SETUP_ANKI_MISSING, SetupState.Open, Strings.SETUP_ANKI_MISSING_DETAIL, SetupAction.InstallAnki))
        // dependent steps have no button: nothing the user could do there yet
        assertThat(rows[1].action).isNull()
        assertThat(rows[2].action).isNull()
        assertThat(rows[2].detail).isEqualTo(Strings.SETUP_ANKI_NEEDS_PREVIOUS)
    }

    @Test fun `permission missing asks, a permanent denial points to the app info`() {
        val p = all.copy(ankiPermission = false)
        assertThat(items(p)[1].action).isEqualTo(SetupAction.RequestPermission)
        assertThat(items(p)[1].state).isEqualTo(SetupState.Open)
        val denied = items(p, denied = true)[1]
        assertThat(denied.state).isEqualTo(SetupState.Error)
        assertThat(denied.action).isEqualTo(SetupAction.AppInfo)
        assertThat(denied.detail).isEqualTo(Strings.SETUP_ANKI_PERMISSION_DENIED_DETAIL)
    }

    @Test fun `api off names the switch and opens ankidroid`() {
        val row = items(all.copy(ankiProvider = false))[2]
        assertThat(row.label).isEqualTo(Strings.SETUP_ANKI_API_OFF)
        assertThat(row.state).isEqualTo(SetupState.Error)
        assertThat(row.action).isEqualTo(SetupAction.OpenAnki)
        assertThat(row.detail).contains("ankidroid-api aktivieren")
    }

    @Test fun `null cursor with a resolving provider points to ankidroid, not to the api switch`() {
        val row = items(all, AnkiProbe.Failed(AnkiError.NotInstalledOrApiOff))[2]
        assertThat(row.label).isEqualTo(Strings.SETUP_ANKI_FAILED)
        assertThat(row.detail).isEqualTo(Strings.SETUP_ANKI_NO_DATA_DETAIL)
        assertThat(row.action).isEqualTo(SetupAction.OpenAnki)
        assertThat(row.action2).isEqualTo(SetupAction.RetryProbe)
    }

    @Test fun `check errors use the lock screen texts and their action`() {
        fun row(e: AnkiError) = items(all, AnkiProbe.Failed(e))[2]
        assertThat(row(AnkiError.StorageNotConfigured).detail).isEqualTo(Strings.ERR_STORAGE.text)
        assertThat(row(AnkiError.StorageNotConfigured).action).isEqualTo(SetupAction.OpenAnki)
        assertThat(row(AnkiError.PermissionMissing).action).isEqualTo(SetupAction.AppInfo)
        assertThat(row(AnkiError.Unreachable).action).isEqualTo(SetupAction.RetryProbe)
        assertThat(row(AnkiError.BadData).action).isEqualTo(SetupAction.RetryProbe)
        assertThat(row(AnkiError.Unexpected("x")).state).isEqualTo(SetupState.Error)
    }

    @Test fun `running check shows a dim progress line without a button`() {
        val row = items(all, AnkiProbe.Unknown)[2]
        assertThat(row.state).isEqualTo(SetupState.Open)
        assertThat(row.detail).isEqualTo(Strings.SETUP_ANKI_CHECKING)
        assertThat(row.action).isNull()
    }

    @Test fun `accessibility rows are unchanged from setup v1`() {
        assertThat(items(all.copy(a11yEnabled = false, a11yBound = false))[3].action).isEqualTo(SetupAction.Accessibility)
        assertThat(items(all.copy(a11yEnabled = false, a11yBound = false))[3].action2).isEqualTo(SetupAction.AppInfo)
        assertThat(items(all.copy(a11yBound = false))[3].label).isEqualTo(Strings.SETUP_ACCESSIBILITY_UNBOUND)
        assertThat(items(all.copy(a11yBound = false, restricted = true))[3].action).isEqualTo(SetupAction.AppInfo)
        assertThat(items(all.copy(batteryUnrestricted = false))[4].action).isEqualTo(SetupAction.Battery)
    }

    @Test fun `hints name the system warning always and advanced protection while the service is not active`() {
        assertThat(SetupModel.hints(all)).containsExactly(Strings.SETUP_HINT_WARNING, Strings.SETUP_HINT_RESTART, Strings.SETUP_HINT_AUTOSYNC).inOrder()
        val off = SetupModel.hints(all.copy(a11yEnabled = false, a11yBound = false))
        assertThat(off).containsAtLeast(Strings.SETUP_HINT_RESTRICTED, Strings.SETUP_HINT_ADVANCED, Strings.SETUP_HINT_WARNING).inOrder()
        assertThat(SetupModel.hints(all.copy(a11yBound = false))).contains(Strings.SETUP_HINT_ADVANCED)
        assertThat(Strings.SETUP_HINT_ADVANCED).contains("advanced protection")
    }

    @Test fun `every action has a button text`() {
        SetupAction.entries.forEach { assertThat(SetupModel.label(it)).startsWith("\$ ./") }
    }
}
