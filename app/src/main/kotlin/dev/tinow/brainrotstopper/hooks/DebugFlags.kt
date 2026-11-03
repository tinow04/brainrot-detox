package dev.tinow.brainrotstopper.hooks

/**
 * Debug switches read by production code only through this interface; defaults are the neutral release values.
 * Release builds use [NoDebugFlags], so none of the overrides can take effect there.
 */
interface DebugFlags {
    /** Tests switch the blinking terminal cursor off for stable screenshots and UI tests. */
    val cursorBlinkEnabled: Boolean get() = true

    /** Replaces `SESSION_TTL_MS` (SET_TTL_MS). */
    val sessionTtlMsOverride: Long? get() = null

    /** Replaces the unlock duration, in seconds (SET_SHORT_DURATION). */
    val unlockSecondsOverride: Int? get() = null

    /** The lock launcher does not start the lock (watchdog -> HOME test, M3). */
    val simulateLaunchBlock: Boolean get() = false

    /** The accessibility event handler throws on purpose (catch-all test, M3). */
    val throwInHandler: Boolean get() = false

    /** Artificial delay in the Anki gateway, per stage (`answer` or `query`), for pending-answer kill tests (M4). */
    fun gatewayDelayMs(stage: String): Long = 0L

    /** Shifts only wall time and the day boundary; elapsed time never. */
    val wallOffsetMs: Long get() = 0L

    /** `real` or `fake` card source (M4) and the fake scenario. */
    val cardSource: String get() = "real"
    val scenario: String get() = "normal"
}

object NoDebugFlags : DebugFlags
