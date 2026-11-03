package dev.tinow.brainrotstopper.domain.quiz

/** All tunable numbers in one place (architecture.md section 4 "Konstanten", measured values from M0 in section 11). */
object Constants {
    const val REQUIRED_DEFAULT = 5
    val REQUIRED_RANGE = 1..20
    val DURATIONS_MIN = listOf(5, 15, 30, 60)
    const val DEFAULT_DURATION_MIN = 15

    const val SESSION_TTL_MS = 600_000L
    const val RATE_GUARD_MS = 600L
    const val LAUNCH_ACK_MS = 1_500L
    const val RELAUNCH_MIN_MS = 1_500L
    const val REEVAL_TOLERANCE_MS = 50L
    const val BREAKER_STARTS = 6
    const val BREAKER_WINDOW_MS = 10_000L
    const val BREAKER_COOLDOWN_MS = 30_000L
    const val BREAKER_MIN_INTERVAL_MS = 3_000L
    const val BLOCK_DEBOUNCE_MS = 10_000L
    const val WAIT_POLL_MS = 15_000L
    const val LEARN_PENDING_WAIT_MS = 1_200_000L
    const val ANKI_FIRST_TIMEOUT_MS = 8_000L
    const val ANKI_TIMEOUT_MS = 4_000L
    const val SPINNER_DELAY_MS = 250L
    const val GRANT_PLAUSIBILITY_SLACK_MS = 1_000L
    const val ASSUMED_MINUTES_SAVED_PER_ABORT = 5
    const val EMERGENCY_WAIT_MS = 60_000L
    const val EMERGENCY_GRANT_MIN = 5
    const val EMERGENCY_COOLDOWN_MS = 21_600_000L
    const val PROTECTED_REFRESH_MS = 60_000L
    const val STATS_RETENTION_DAYS = 400

    /** Measured in M0-S1a: `deck_count` learn also counts intraday cards that are not due yet. */
    const val LEARN_COUNT_INCLUDES_PENDING = true

    /** An empty deck frees the target app for the normal duration (architecture.md 3B, counts `freeUnlocks`). */
    const val NOTHING_DUE_GRANT = true

    const val EMERGENCY_EXIT_ENABLED = true
}
