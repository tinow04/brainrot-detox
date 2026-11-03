package dev.tinow.brainrotstopper.domain.gate

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.UnlockGrant
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot
import org.junit.Test

class GrantPolicyTest {
    private fun now(elapsed: Long, boot: Int = 7, wall: Long = 1_700_000_000_000L) = TimeSnapshot(elapsed, wall, boot)
    private val grant = UnlockGrant(
        grantedAtElapsedMs = 1_000, untilElapsedMs = 1_000 + 900_000, durationMs = 900_000,
        bootCount = 7, grantedAtWallMs = 1_700_000_000_000L, kind = GrantKind.QUIZ,
    )

    @Test fun validInsideWindow() {
        assertThat(GrantPolicy.isValid(grant, now(1_000))).isTrue()
        assertThat(GrantPolicy.isValid(grant, now(450_000))).isTrue()
        assertThat(GrantPolicy.isValid(grant, now(900_999))).isTrue()
    }

    @Test fun expiredAndExactBoundaryAreInvalid() {
        assertThat(GrantPolicy.isValid(grant, now(grant.untilElapsedMs))).isFalse() // elapsed == until
        assertThat(GrantPolicy.isValid(grant, now(grant.untilElapsedMs + 1))).isFalse()
    }

    @Test fun otherBootCountIsInvalid() {
        assertThat(GrantPolicy.isValid(grant, now(5_000, boot = 8))).isFalse()
    }

    @Test fun unreadableBootCountIsNeverValid() {
        assertThat(GrantPolicy.isValid(grant.copy(bootCount = -1), now(5_000, boot = -1))).isFalse()
        assertThat(GrantPolicy.isValid(grant, now(5_000, boot = -1))).isFalse()
    }

    @Test fun nowBeforeGrantedAtIsInvalid() {
        assertThat(GrantPolicy.isValid(grant, now(999))).isFalse()
    }

    @Test fun remainingBeyondDurationPlusSlackIsInvalid() {
        val tampered = grant.copy(untilElapsedMs = grant.untilElapsedMs + Constants.GRANT_PLAUSIBILITY_SLACK_MS + 1)
        assertThat(GrantPolicy.isValid(tampered, now(1_000))).isFalse()
        val withinSlack = grant.copy(untilElapsedMs = grant.untilElapsedMs + Constants.GRANT_PLAUSIBILITY_SLACK_MS)
        assertThat(GrantPolicy.isValid(withinSlack, now(1_000))).isTrue()
    }

    @Test fun wallClockIsIrrelevant() {
        assertThat(GrantPolicy.isValid(grant, now(5_000, wall = 0))).isTrue()
        assertThat(GrantPolicy.isValid(grant, now(5_000, wall = Long.MAX_VALUE / 2))).isTrue()
        assertThat(GrantPolicy.isValid(grant, now(grant.untilElapsedMs, wall = 0))).isFalse()
    }

    @Test fun nullGrantIsInvalid() {
        assertThat(GrantPolicy.isValid(null, now(1))).isFalse()
        assertThat(GrantPolicy.remainingMs(null, now(1))).isEqualTo(0)
    }

    @Test fun remainingMs() {
        assertThat(GrantPolicy.remainingMs(grant, now(401_000))).isEqualTo(500_000)
        assertThat(GrantPolicy.remainingMs(grant, now(grant.untilElapsedMs))).isEqualTo(0)
    }

    @Test fun createUsesMonotonicClockOnly() {
        val g = GrantPolicy.create(GrantKind.EMERGENCY, 300_000, now(50_000, boot = 3, wall = 42))
        assertThat(g.grantedAtElapsedMs).isEqualTo(50_000)
        assertThat(g.untilElapsedMs).isEqualTo(350_000)
        assertThat(g.bootCount).isEqualTo(3)
        assertThat(g.grantedAtWallMs).isEqualTo(42)
        assertThat(GrantPolicy.isValid(g, now(60_000, boot = 3))).isTrue()
    }

    @Test fun pruneDropsExpiredAndImplausible() {
        val expired = grant.copy(untilElapsedMs = 2_000, durationMs = 1_000)
        val kept = GrantPolicy.prune(mapOf("a" to grant, "b" to expired, "c" to grant.copy(bootCount = 1)), now(10_000))
        assertThat(kept.keys).containsExactly("a")
    }
}
