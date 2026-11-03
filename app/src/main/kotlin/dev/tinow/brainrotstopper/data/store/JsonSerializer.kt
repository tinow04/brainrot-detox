package dev.tinow.brainrotstopper.data.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.tinow.brainrotstopper.domain.model.BsJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

/** DataStore serializer for one JSON file. Anything unreadable becomes a [CorruptionException] for the corruption handler. */
class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T =
        try {
            BsJson.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("cannot read ${serializer.descriptor.serialName}", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("cannot read ${serializer.descriptor.serialName}", e)
        }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(BsJson.encodeToString(serializer, t).encodeToByteArray())
    }
}
