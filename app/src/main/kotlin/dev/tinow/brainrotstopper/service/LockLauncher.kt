package dev.tinow.brainrotstopper.service

/** What the gate can do to the screen. The Android implementation lives next to the service; tests use a fake. */
interface LockLauncher {
    /** Shows the lock UI for [pkg]; [retry] is true for the second attempt after a missing ack. */
    fun start(pkg: String, retry: Boolean)

    /** Sends the user to the home screen (`GLOBAL_ACTION_HOME`). */
    fun goHome()

    /** Asks AnkiDroid to sync (`com.ichi2.anki.DO_SYNC`, M6-T7); false if it could not be started. */
    fun startAnkiSync(): Boolean = false
}

/** Counters for the diagnostics block of `runtime.json`; implemented over the RuntimeRepository. */
interface GateDiagSink {
    fun launchFailed()
    fun breaker()
    fun handlerError()
}

/** Dynamic parts of the protection: packages never locked and input methods whose windows are ignored by the tracker. */
data class ProtectedSets(
    val protectedPackages: Set<String> = emptySet(),
    val inputMethods: Set<String> = emptySet(),
)

fun interface ProtectedSetProvider {
    suspend fun load(): ProtectedSets
}
