package dev.tinow.brainrotstopper.data.anki

/** One result row: column name to value as text (null for SQL NULL and blobs). */
typealias Row = Map<String, String?>

/**
 * The thin layer between the card source and the Android content resolver. It returns plain rows, so the source can be
 * tested on the JVM, and it is cancellable: cancelling the calling coroutine cancels the provider call.
 *
 * Exceptions of the platform propagate unchanged (SecurityException, IllegalStateException, IllegalArgumentException,
 * ...) except a dead provider process, which is [ProviderDeadException]; [AnkiErrorMapper] maps them all.
 */
interface ProviderGateway {
    /** `resolveContentProvider(authority)` is not null (AnkiDroid installed and visible). */
    fun providerAvailable(): Boolean

    /** Rows of the query, or null when the provider answered with a null cursor (not installed or API switched off). */
    suspend fun query(path: String, projection: List<String>? = null, selection: String? = null, args: List<String> = emptyList()): List<Row>?

    /** Number of updated rows. */
    suspend fun update(path: String, values: Map<String, Any>): Int
}

/** The AnkiDroid process died during the call. */
class ProviderDeadException(cause: Throwable? = null) : RuntimeException("AnkiDroid provider process died", cause)
