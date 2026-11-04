package dev.tinow.brainrotstopper.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Package/class sequences are the ones recorded in M0-S2 (gotchas.md), not invented ones. */
class ForegroundTrackerTest {
    private val lockCls = "dev.tinow.brainrotstopper.lock.LockActivity"
    private val tracker = ForegroundTracker(lockCls)
    private val insta = "com.instagram.android"
    private val launcher = "com.google.android.apps.nexuslauncher"

    @Test fun `normal app becomes foreground`() {
        assertThat(tracker.onEvent(insta, "com.instagram.MainActivity")).isEqualTo(ForegroundTracker.Result.Foreground(insta))
        assertThat(tracker.current).isEqualTo(insta)
    }

    @Test fun `cold start starting window of the target counts, only the package matters`() {
        tracker.onEvent(launcher, null)
        assertThat(tracker.onEvent(insta, "android.widget.FrameLayout")).isEqualTo(ForegroundTracker.Result.Foreground(insta))
    }

    @Test fun `notification shade does not replace the app below it`() {
        tracker.onEvent(insta, "A")
        assertThat(tracker.onEvent("com.android.systemui", "android.widget.FrameLayout")).isEqualTo(ForegroundTracker.Result.Ignored)
        tracker.onEvent(insta, "A") // closing reports the host again
        tracker.onEvent("com.android.systemui", "android.widget.FrameLayout") // and then systemui once more
        assertThat(tracker.current).isEqualTo(insta)
    }

    @Test fun `share chooser and permission dialog are ignored`() {
        tracker.onEvent(insta, "A")
        assertThat(tracker.onEvent("android", "com.android.internal.app.ResolverActivity")).isEqualTo(ForegroundTracker.Result.Ignored)
        assertThat(tracker.onEvent("com.google.android.permissioncontroller", "GrantPermissionsActivity")).isEqualTo(ForegroundTracker.Result.Ignored)
        assertThat(tracker.onEvent("com.google.android.packageinstaller", "X")).isEqualTo(ForegroundTracker.Result.Ignored)
        assertThat(tracker.current).isEqualTo(insta)
    }

    @Test fun `own dialog of the target app is the target package`() {
        tracker.onEvent(insta, "A")
        assertThat(tracker.onEvent(insta, "android.app.AlertDialog")).isEqualTo(ForegroundTracker.Result.Foreground(insta))
    }

    @Test fun `home launcher is a normal foreground, not ignored`() {
        tracker.onEvent(insta, "A")
        assertThat(tracker.onEvent(launcher, "Launcher")).isEqualTo(ForegroundTracker.Result.Foreground(launcher))
        assertThat(tracker.current).isEqualTo(launcher)
    }

    @Test fun `unknown package counts as a normal app`() {
        assertThat(tracker.onEvent("org.example.never.seen", "X")).isEqualTo(ForegroundTracker.Result.Foreground("org.example.never.seen"))
    }

    @Test fun `lock activity events never change the foreground`() {
        tracker.onEvent(insta, "A")
        assertThat(tracker.onEvent("dev.tinow.brainrotstopper", lockCls)).isEqualTo(ForegroundTracker.Result.Ignored)
        assertThat(tracker.current).isEqualTo(insta)
    }

    @Test fun `own starting window while the lock is active is ignored`() {
        tracker.onEvent(insta, "A")
        tracker.lockActive = true
        assertThat(tracker.onEvent("dev.tinow.brainrotstopper", "android.widget.FrameLayout")).isEqualTo(ForegroundTracker.Result.Ignored)
        assertThat(tracker.current).isEqualTo(insta)
    }

    @Test fun `own main screen counts when no lock is active`() {
        tracker.onEvent(insta, "A")
        assertThat(tracker.onEvent("dev.tinow.brainrotstopper", "dev.tinow.brainrotstopper.MainActivity"))
            .isEqualTo(ForegroundTracker.Result.Foreground("dev.tinow.brainrotstopper"))
    }

    @Test fun `enabled input method is ignored, a disabled one is a normal app`() {
        tracker.onEvent(insta, "A")
        tracker.ignoredDynamic = setOf("com.google.android.inputmethod.latin")
        assertThat(tracker.onEvent("com.google.android.inputmethod.latin", "Keyboard")).isEqualTo(ForegroundTracker.Result.Ignored)
        assertThat(tracker.current).isEqualTo(insta)
        assertThat(tracker.onEvent("com.other.ime", "Keyboard")).isEqualTo(ForegroundTracker.Result.Foreground("com.other.ime"))
    }
}
