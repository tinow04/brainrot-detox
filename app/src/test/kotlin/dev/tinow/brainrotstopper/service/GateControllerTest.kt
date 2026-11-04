package dev.tinow.brainrotstopper.service

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.debug.fake.FakeDiagSink
import dev.tinow.brainrotstopper.debug.fake.FakeLockLauncher
import dev.tinow.brainrotstopper.domain.gate.GateState
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.model.UnlockGrant
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import dev.tinow.brainrotstopper.domain.time.TimeSource
import dev.tinow.brainrotstopper.hooks.DebugFlags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Test

private const val INSTA = "com.instagram.android"
private const val LAUNCHER = "com.google.android.apps.nexuslauncher"
private const val LOCK_CLS = "dev.tinow.brainrotstopper.lock.LockActivity"

/** Controller in virtual time with fakes: the lock "appears" only when the test calls the beacon. */
@OptIn(ExperimentalCoroutinesApi::class)
class GateControllerTest {
    private class Flags : DebugFlags {
        @Volatile override var throwInHandler = false
        @Volatile override var simulateLaunchBlock = false
    }

    private class Rig(val test: TestScope, blocked: Set<String> = setOf(INSTA)) {
        val settings = MutableStateFlow(Settings(blockedPackages = blocked))
        val runtime = MutableStateFlow(Runtime())
        var protectedNow = ProtectedSets()
        val launcher = FakeLockLauncher()
        val diag = FakeDiagSink()
        val flags = Flags()
        val logs = mutableListOf<String>()
        val time = object : TimeSource {
            override fun now() = TimeSnapshot(elapsedMs = 1_000_000L + test.currentTime, wallMs = 1_700_000_000_000L + test.currentTime, bootCount = 7)
        }
        val tracker = ForegroundTracker(LOCK_CLS)
        val controller = GateController(
            scope = test.backgroundScope,
            timeSource = time,
            settings = settings,
            runtime = runtime,
            protectedProvider = { protectedNow },
            launcher = launcher,
            diag = diag,
            tracker = tracker,
            flags = { flags },
            log = { logs += it },
        )

        fun begin() { controller.start(); controller.onConnected() }
    }

    @After fun cleanBeacon() { LockUiBeacon.listener = null }

    @Test fun `blocked app starts the lock once and the ack ends the watch`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        assertThat(rig.launcher.starts).containsExactly(FakeLockLauncher.Start(INSTA, false))
        LockUiBeacon.resumed(INSTA)
        advanceTimeBy(5_000)
        assertThat(rig.launcher.starts).hasSize(1)
        assertThat(rig.launcher.homeCount).isEqualTo(0)
        assertThat(rig.controller.state).isEqualTo(GateState.LockVisible(INSTA))
    }

    @Test fun `unblocked app and the lock's own events do nothing`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.onAccessibilityEvent("com.example.free", "A")
        rig.controller.onAccessibilityEvent("com.android.systemui", "F")
        runCurrent()
        assertThat(rig.launcher.starts).isEmpty()
        assertThat(rig.controller.state).isEqualTo(GateState.Free)
    }

    @Test fun `without ack the gate retries once and then goes home and counts the failure`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        advanceTimeBy(Constants.LAUNCH_ACK_MS + 10)
        assertThat(rig.launcher.starts.map { it.retry }).containsExactly(false, true).inOrder()
        assertThat(rig.launcher.homeCount).isEqualTo(0)
        advanceTimeBy(Constants.LAUNCH_ACK_MS + 10)
        assertThat(rig.launcher.homeCount).isEqualTo(1)
        assertThat(rig.diag.launchFailed).isEqualTo(1)
    }

    @Test fun `debug SIMULATE_LAUNCH_BLOCK suppresses the start and ends in HOME`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.flags.simulateLaunchBlock = true
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        advanceTimeBy(2 * Constants.LAUNCH_ACK_MS + 50)
        assertThat(rig.launcher.starts).isEmpty()
        assertThat(rig.launcher.homeCount).isEqualTo(1)
    }

    @Test fun `event flood never lets the app through and stays under the breaker rate`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.inject(INSTA, 50)
        runCurrent()
        assertThat(rig.launcher.starts.size).isAtMost(Constants.BREAKER_STARTS)
        advanceTimeBy(Constants.BREAKER_COOLDOWN_MS + 5_000)
        // no ack ever came, so the gate keeps trying/going home, but it never reaches Free for the blocked app
        assertThat(rig.controller.state).isNotEqualTo(GateState.Free)
        assertThat(rig.launcher.homeCount).isGreaterThan(0)
    }

    @Test fun `exception in the handler keeps the loop alive and is counted`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.flags.throwInHandler = true
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        assertThat(rig.diag.handlerErrors).isEqualTo(1)
        assertThat(rig.launcher.starts).isEmpty()
        rig.flags.throwInHandler = false
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        assertThat(rig.launcher.starts).hasSize(1)
    }

    @Test fun `events before the stores delivered wait and are then evaluated`() = runTest {
        val launcher = FakeLockLauncher()
        val controller = GateController(
            scope = backgroundScope,
            timeSource = object : TimeSource {
                override fun now() = TimeSnapshot(1_000_000L + currentTime, 1_700_000_000_000L, 7)
            },
            // the settings store answers only after 2 s, like a slow first read of the file
            settings = kotlinx.coroutines.flow.flow { kotlinx.coroutines.delay(2_000); emit(Settings(blockedPackages = setOf(INSTA))) },
            runtime = MutableStateFlow(Runtime()),
            protectedProvider = { ProtectedSets() },
            launcher = launcher,
            diag = FakeDiagSink(),
            tracker = ForegroundTracker(LOCK_CLS),
        )
        controller.start()
        controller.onAccessibilityEvent(INSTA, "A")
        advanceTimeBy(1_000)
        assertThat(launcher.starts).isEmpty() // no decision on an empty snapshot, and no free pass either
        advanceTimeBy(1_500)
        assertThat(launcher.starts).containsExactly(FakeLockLauncher.Start(INSTA, false))
    }

    @Test fun `grant expiry locks again without any further event`() = runTest {
        val rig = Rig(this).also { it.begin() }
        val now = rig.time.now()
        rig.runtime.value = Runtime(
            grants = mapOf(INSTA to UnlockGrant(now.elapsedMs, now.elapsedMs + 5_000, 5_000, now.bootCount, now.wallMs, GrantKind.QUIZ)),
        )
        runCurrent()
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        assertThat(rig.launcher.starts).isEmpty()
        assertThat(rig.controller.state).isEqualTo(GateState.Free)
        advanceTimeBy(5_100)
        assertThat(rig.launcher.starts).containsExactly(FakeLockLauncher.Start(INSTA, false))
    }

    @Test fun `granting while the lock is visible frees the next evaluation`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        LockUiBeacon.resumed(INSTA)
        runCurrent()
        val now = rig.time.now()
        rig.runtime.value = Runtime(
            grants = mapOf(INSTA to UnlockGrant(now.elapsedMs, now.elapsedMs + 60_000, 60_000, now.bootCount, now.wallMs, GrantKind.QUIZ)),
        )
        runCurrent()
        LockUiBeacon.gone()
        rig.controller.onAccessibilityEvent(INSTA, "A") // the unlocked target comes to the front
        advanceTimeBy(1_000)
        assertThat(rig.launcher.starts).hasSize(1)
        assertThat(rig.controller.state).isEqualTo(GateState.Free)
    }

    @Test fun `lock goes away and the home launcher event follows - no relaunch`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        LockUiBeacon.resumed(INSTA)
        runCurrent()
        advanceTimeBy(5_000)
        LockUiBeacon.gone()
        rig.controller.onAccessibilityEvent(LAUNCHER, "Launcher")
        advanceTimeBy(5_000)
        assertThat(rig.launcher.starts).hasSize(1)
        assertThat(rig.controller.state).isEqualTo(GateState.Free)
    }

    @Test fun `lock goes away and nothing follows - fail closed, the lock is shown again`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        LockUiBeacon.resumed(INSTA)
        runCurrent()
        advanceTimeBy(5_000)
        LockUiBeacon.gone()
        advanceTimeBy(Constants.LOCK_GONE_GRACE_MS + 100)
        assertThat(rig.launcher.starts).hasSize(2)
    }

    @Test fun `own starting window after the launch does not free the gate`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        rig.controller.onAccessibilityEvent("dev.tinow.brainrotstopper", "android.widget.FrameLayout")
        LockUiBeacon.resumed(INSTA)
        runCurrent()
        advanceTimeBy(5_000)
        assertThat(rig.launcher.starts).hasSize(1)
        assertThat(rig.controller.state).isEqualTo(GateState.LockVisible(INSTA))
    }

    @Test fun `changed home launcher becomes protected after a refresh`() = runTest {
        val rig = Rig(this, blocked = setOf("launcher.b")).also { it.begin() }
        rig.protectedNow = ProtectedSets(protectedPackages = setOf("launcher.a"))
        rig.controller.refreshProtected()
        rig.controller.onAccessibilityEvent("launcher.b", "L")
        runCurrent()
        assertThat(rig.launcher.starts).hasSize(1) // launcher.b is not (yet) the default launcher
        LockUiBeacon.resumed("launcher.b"); runCurrent()
        // user changes the default launcher to launcher.b
        rig.protectedNow = ProtectedSets(protectedPackages = setOf("launcher.b"))
        rig.controller.refreshProtected()
        runCurrent()
        LockUiBeacon.gone()
        rig.controller.onAccessibilityEvent("launcher.b", "L")
        advanceTimeBy(1_000)
        assertThat(rig.launcher.starts).hasSize(1)
        assertThat(rig.controller.state).isEqualTo(GateState.Free)
    }

    @Test fun `periodic refresh picks up a newly enabled keyboard as ignored`() = runTest {
        val rig = Rig(this).also { it.begin() }
        rig.controller.onAccessibilityEvent(INSTA, "A")
        runCurrent()
        LockUiBeacon.resumed(INSTA); runCurrent()
        rig.protectedNow = ProtectedSets(protectedPackages = setOf("com.kbd"), inputMethods = setOf("com.kbd"))
        advanceTimeBy(Constants.PROTECTED_REFRESH_MS + 100)
        assertThat(rig.tracker.ignoredDynamic).containsExactly("com.kbd")
        rig.controller.onAccessibilityEvent("com.kbd", "Keyboard")
        runCurrent()
        assertThat(rig.tracker.current).isEqualTo(INSTA)
    }
}
