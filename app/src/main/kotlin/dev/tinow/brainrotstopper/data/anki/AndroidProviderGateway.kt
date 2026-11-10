package dev.tinow.brainrotstopper.data.anki

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.DeadObjectException
import android.os.OperationCanceledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/**
 * [ProviderGateway] over `ContentResolver`. A binder call into a hanging AnkiDroid cannot be interrupted (not even the
 * `CancellationSignal`: `ContentResolver` first fetches it from the provider with a blocking call). So every call runs
 * detached on IO and the caller only awaits it: cancelling the caller (the source's timeout) returns at once, the blocked
 * thread is left behind until the provider answers or dies, and its late result is dropped (M6-T4).
 */
class AndroidProviderGateway(private val context: Context) : ProviderGateway {
    private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun uri(path: String): Uri = Uri.parse("content://${AnkiContract.AUTHORITY}/$path")

    override fun providerAvailable(): Boolean = context.packageManager.resolveContentProvider(AnkiContract.AUTHORITY, 0) != null

    override suspend fun query(path: String, projection: List<String>?, selection: String?, args: List<String>): List<Row>? {
        val signal = CancellationSignal()
        return awaitDetached({ signal.cancel() }) {
            try {
                val cursor = context.contentResolver.query(
                    uri(path), projection?.toTypedArray(), selection, if (args.isEmpty()) null else args.toTypedArray(), null, signal,
                )
                cursor?.use { read(it) }
            } catch (e: OperationCanceledException) {
                throw CancellationException("provider call cancelled").also { it.initCause(e) }
            }
        }
    }

    override suspend fun update(path: String, values: Map<String, Any>): Int {
        val cv = ContentValues()
        for ((k, v) in values) when (v) {
            is Long -> cv.put(k, v)
            is Int -> cv.put(k, v)
            is String -> cv.put(k, v)
            else -> error("unsupported value type for $k")
        }
        return awaitDetached({}) { context.contentResolver.update(uri(path), cv, null, null) }
    }

    /** Runs [block] outside the caller's job; a cancelled caller stops waiting at once and [onAbandon] is called. */
    private suspend fun <T> awaitDetached(onAbandon: () -> Unit, block: () -> T): T {
        val call = detached.async {
            try {
                block()
            } catch (e: DeadObjectException) {
                throw ProviderDeadException(e)
            }
        }
        try {
            return call.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive() // the call itself was cancelled (OperationCanceled): rethrow as is
            throw e
        } finally {
            if (!currentCoroutineContext().isActive) {
                runCatching(onAbandon) // oneway binder call, never blocks; may not reach a provider that hangs
                call.cancel()
            }
        }
    }

    private fun read(cursor: Cursor): List<Row> {
        val names = cursor.columnNames
        val rows = ArrayList<Row>(cursor.count)
        while (cursor.moveToNext()) {
            val row = LinkedHashMap<String, String?>(names.size)
            for (i in names.indices) {
                row[names[i]] = when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL, Cursor.FIELD_TYPE_BLOB -> null
                    else -> cursor.getString(i)
                }
            }
            rows += row
        }
        return rows
    }
}
