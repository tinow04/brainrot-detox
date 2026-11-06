package dev.tinow.brainrotstopper.debug.fake

import dev.tinow.brainrotstopper.data.anki.ProviderGateway
import dev.tinow.brainrotstopper.data.anki.Row
import kotlinx.coroutines.delay

/** Scriptable gateway for JVM tests: [queryHandler] / [updateHandler] decide, [delayMs] simulates a slow provider. */
class FakeProviderGateway : ProviderGateway {
    data class Call(val path: String, val projection: List<String>?, val selection: String?, val args: List<String>)

    val calls = mutableListOf<Call>()
    val updates = mutableListOf<Pair<String, Map<String, Any>>>()
    var available = true
    var delayMs = 0L
    var queryHandler: (Call) -> List<Row>? = { emptyList() }
    var updateHandler: (String, Map<String, Any>) -> Int = { _, _ -> 1 }

    /** Highest number of calls that were in flight at the same time (the source must keep it at 1). */
    var maxInFlight = 0
        private set
    private var inFlight = 0

    override fun providerAvailable() = available

    override suspend fun query(path: String, projection: List<String>?, selection: String?, args: List<String>): List<Row>? {
        val call = Call(path, projection, selection, args)
        calls += call
        return tracked { queryHandler(call) }
    }

    override suspend fun update(path: String, values: Map<String, Any>): Int {
        updates += path to values
        return tracked { updateHandler(path, values) }
    }

    private suspend fun <T> tracked(block: () -> T): T {
        inFlight++
        maxInFlight = maxOf(maxInFlight, inFlight)
        try {
            if (delayMs > 0) delay(delayMs)
            return block()
        } finally {
            inFlight--
        }
    }
}
