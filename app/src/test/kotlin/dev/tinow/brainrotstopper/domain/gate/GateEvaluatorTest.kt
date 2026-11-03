package dev.tinow.brainrotstopper.domain.gate

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test

class GateEvaluatorTest {
    private val now = TimeSnapshot(10_000, 0, 7)
    private val grant = GrantPolicy.create(GrantKind.QUIZ, 60_000, TimeSnapshot(5_000, 0, 7))
    private val snapshot = GateSnapshot(blockedPackages = setOf("com.insta", "com.tiktok", ProtectedPackages.ANKIDROID))

    @Test fun blockedPackageWithoutGrantIsLocked() {
        assertThat(GateEvaluator.evaluate("com.insta", snapshot, now)).isEqualTo(GateDecision.Lock("com.insta"))
    }

    @Test fun otherPackageIsNotBlocked() {
        assertThat(GateEvaluator.evaluate("com.maps", snapshot, now)).isEqualTo(GateDecision.NotBlocked)
    }

    @Test fun unknownForegroundIsNotBlocked() {
        assertThat(GateEvaluator.evaluate(null, snapshot, now)).isEqualTo(GateDecision.NotBlocked)
    }

    @Test fun validGrantAllowsWithRemainingTime() {
        val s = snapshot.copy(grants = mapOf("com.insta" to grant))
        assertThat(GateEvaluator.evaluate("com.insta", s, now)).isEqualTo(GateDecision.Granted(remainingMs = 55_000, untilElapsedMs = 65_000))
    }

    @Test fun expiredGrantLocksAgain() {
        val s = snapshot.copy(grants = mapOf("com.insta" to grant))
        assertThat(GateEvaluator.evaluate("com.insta", s, TimeSnapshot(65_000, 0, 7))).isEqualTo(GateDecision.Lock("com.insta"))
    }

    @Test fun grantIsPerPackage() {
        val s = snapshot.copy(grants = mapOf("com.insta" to grant))
        assertThat(GateEvaluator.evaluate("com.tiktok", s, now)).isEqualTo(GateDecision.Lock("com.tiktok"))
    }

    @Test fun grantFromBeforeRebootIsIgnored() {
        val s = snapshot.copy(grants = mapOf("com.insta" to grant))
        assertThat(GateEvaluator.evaluate("com.insta", s, TimeSnapshot(10_000, 0, 8))).isEqualTo(GateDecision.Lock("com.insta"))
    }

    @Test fun ankidroidIsNeverLockedEvenWhenListed() {
        assertThat(GateEvaluator.evaluate(ProtectedPackages.ANKIDROID, snapshot, now)).isEqualTo(GateDecision.Protected)
    }

    @Test fun ownPackageAndSystemPackagesAreProtected() {
        val all = snapshot.copy(blockedPackages = ProtectedPackages.STATIC)
        for (pkg in ProtectedPackages.STATIC) assertThat(GateEvaluator.evaluate(pkg, all, now)).isEqualTo(GateDecision.Protected)
    }

    @Test fun dynamicProtectedPackagesAreProtected() {
        val s = snapshot.copy(blockedPackages = setOf("com.launcher"), dynamicProtected = setOf("com.launcher"))
        assertThat(GateEvaluator.evaluate("com.launcher", s, now)).isEqualTo(GateDecision.Protected)
    }
}
