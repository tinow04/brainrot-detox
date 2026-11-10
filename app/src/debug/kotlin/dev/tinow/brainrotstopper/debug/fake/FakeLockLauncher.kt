package dev.tinow.brainrotstopper.debug.fake

import dev.tinow.brainrotstopper.service.GateDiagSink
import dev.tinow.brainrotstopper.service.LockLauncher

/** Records what the gate asked for; the test decides whether and when the lock "appears" (via the beacon). */
class FakeLockLauncher : LockLauncher {
    data class Start(val pkg: String, val retry: Boolean)

    val starts = mutableListOf<Start>()
    var homeCount = 0
        private set

    override fun start(pkg: String, retry: Boolean) {
        starts += Start(pkg, retry)
    }

    override fun goHome() {
        homeCount++
    }

    var syncs = 0
        private set
    var syncResult = true

    override fun startAnkiSync(): Boolean {
        syncs++
        return syncResult
    }
}

class FakeDiagSink : GateDiagSink {
    var launchFailed = 0
    var breaker = 0
    var handlerErrors = 0

    override fun launchFailed() { launchFailed++ }
    override fun breaker() { breaker++ }
    override fun handlerError() { handlerErrors++ }
}
