package dev.tinow.brainrotstopper.data.anki

import kotlinx.coroutines.delay

/**
 * Debug hook (SET_GATEWAY_DELAY_MS): delays the provider calls per stage (`query` or `answer`). In release builds the
 * delay function always returns 0, so this only forwards.
 */
class DelayingGateway(private val inner: ProviderGateway, private val delayMs: (stage: String) -> Long) : ProviderGateway {
    override fun providerAvailable() = inner.providerAvailable()

    override suspend fun query(path: String, projection: List<String>?, selection: String?, args: List<String>): List<Row>? {
        delayMs("query").takeIf { it > 0 }?.let { delay(it) }
        return inner.query(path, projection, selection, args)
    }

    override suspend fun update(path: String, values: Map<String, Any>): Int {
        delayMs("answer").takeIf { it > 0 }?.let { delay(it) }
        return inner.update(path, values)
    }
}
