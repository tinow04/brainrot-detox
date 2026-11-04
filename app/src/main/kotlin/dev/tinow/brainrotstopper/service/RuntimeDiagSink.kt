package dev.tinow.brainrotstopper.service

import dev.tinow.brainrotstopper.data.store.RuntimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Writes the gate counters into `runtime.diag` without ever blocking the caller. */
class RuntimeDiagSink(private val runtime: RuntimeRepository, private val scope: CoroutineScope) : GateDiagSink {
    override fun launchFailed() {
        scope.launch { runtime.update { it.copy(diag = it.diag.copy(launchFailedCount = it.diag.launchFailedCount + 1)) } }
    }

    override fun breaker() = Unit // logged only; there is no counter in the diag block

    override fun handlerError() {
        scope.launch { runtime.update { it.copy(diag = it.diag.copy(serviceErrorCount = it.diag.serviceErrorCount + 1)) } }
    }
}
