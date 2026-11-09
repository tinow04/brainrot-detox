package dev.tinow.brainrotstopper.debug

import android.os.Build
import android.os.StrictMode
import dev.tinow.brainrotstopper.di.AppContainer
import dev.tinow.brainrotstopper.hooks.DebugFlags

/** Debug variant: installs debug flags and StrictMode logging. Release has a no-op with the same FQCN. */
object DebugInit {
    @Volatile
    var flags: DebugFlagsImpl = DebugFlagsImpl()

    /** Mutable flags set by [DebugControlReceiver]; visible across threads. */
    class DebugFlagsImpl : DebugFlags {
        @Volatile override var cursorBlinkEnabled: Boolean = true
        @Volatile override var sessionTtlMsOverride: Long? = null
        @Volatile override var unlockSecondsOverride: Int? = null
        @Volatile override var simulateLaunchBlock: Boolean = false
        @Volatile override var throwInHandler: Boolean = false
        @Volatile override var wallOffsetMs: Long = 0L
        @Volatile override var permissionOverride: String? = null
        @Volatile override var cardSource: String = "real"
        @Volatile override var scenario: String = "normal"
        @Volatile var answerDelayMs: Long = 0L
        @Volatile var queryDelayMs: Long = 0L

        /** Last EVENT_FLOOD request; the gate controller consumes it from M3 on. */
        @Volatile var floodRequest: Pair<String, Int>? = null

        override fun gatewayDelayMs(stage: String): Long = if (stage == "answer") answerDelayMs else queryDelayMs

        fun reset() {
            sessionTtlMsOverride = null; unlockSecondsOverride = null; simulateLaunchBlock = false; throwInHandler = false
            wallOffsetMs = 0; permissionOverride = null; cardSource = "real"; scenario = "normal"; answerDelayMs = 0; queryDelayMs = 0; floodRequest = null
        }
    }

    fun install(container: AppContainer) {
        container.debugFlags = flags
        container.fakeCardSource = { scenario -> dev.tinow.brainrotstopper.debug.fake.FakeCardSource.scenario(scenario) }
        // detectBlockedBackgroundActivityLaunch needs API 36 (lint NewApi); minSdk is 35.
        if (Build.VERSION.SDK_INT >= 36) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectBlockedBackgroundActivityLaunch()
                    .penaltyLog()
                    .build(),
            )
        }
    }
}
