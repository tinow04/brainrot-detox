package dev.tinow.brainrotstopper.debug

import android.os.Build
import android.os.StrictMode
import dev.tinow.brainrotstopper.di.AppContainer
import dev.tinow.brainrotstopper.hooks.DebugFlags

/** Debug variant: installs debug flags and StrictMode logging. Release has a no-op with the same FQCN. */
object DebugInit {
    @Volatile
    var flags: DebugFlagsImpl = DebugFlagsImpl()

    class DebugFlagsImpl(override var cursorBlinkEnabled: Boolean = true) : DebugFlags

    fun install(container: AppContainer) {
        container.debugFlags = flags
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
