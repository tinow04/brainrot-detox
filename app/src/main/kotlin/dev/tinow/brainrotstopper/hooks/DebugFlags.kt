package dev.tinow.brainrotstopper.hooks

/** Debug switches read by production code only through this interface; defaults are the neutral release values. */
interface DebugFlags {
    /** Tests switch the blinking terminal cursor off for stable screenshots and UI tests. */
    val cursorBlinkEnabled: Boolean get() = true
}

object NoDebugFlags : DebugFlags
