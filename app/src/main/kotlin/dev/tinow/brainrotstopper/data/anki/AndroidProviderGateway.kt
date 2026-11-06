package dev.tinow.brainrotstopper.data.anki

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.DeadObjectException
import android.os.OperationCanceledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** [ProviderGateway] over `ContentResolver`; blocking calls run on IO and are cancelled through a `CancellationSignal`. */
class AndroidProviderGateway(private val context: Context) : ProviderGateway {
    private fun uri(path: String): Uri = Uri.parse("content://${AnkiContract.AUTHORITY}/$path")

    override fun providerAvailable(): Boolean = context.packageManager.resolveContentProvider(AnkiContract.AUTHORITY, 0) != null

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun query(path: String, projection: List<String>?, selection: String?, args: List<String>): List<Row>? =
        withContext(Dispatchers.IO) {
            val signal = CancellationSignal()
            val registration = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true) { if (it != null) signal.cancel() }
            try {
                val cursor = context.contentResolver.query(
                    uri(path), projection?.toTypedArray(), selection, if (args.isEmpty()) null else args.toTypedArray(), null, signal,
                )
                cursor?.use { read(it) }
            } catch (e: OperationCanceledException) {
                currentCoroutineContext().ensureActive()
                throw CancellationException("provider call cancelled").also { it.initCause(e) }
            } catch (e: DeadObjectException) {
                throw ProviderDeadException(e)
            } finally {
                registration.dispose()
            }
        }

    override suspend fun update(path: String, values: Map<String, Any>): Int = withContext(Dispatchers.IO) {
        val cv = ContentValues()
        for ((k, v) in values) when (v) {
            is Long -> cv.put(k, v)
            is Int -> cv.put(k, v)
            is String -> cv.put(k, v)
            else -> error("unsupported value type for $k")
        }
        try {
            context.contentResolver.update(uri(path), cv, null, null)
        } catch (e: DeadObjectException) {
            throw ProviderDeadException(e)
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
