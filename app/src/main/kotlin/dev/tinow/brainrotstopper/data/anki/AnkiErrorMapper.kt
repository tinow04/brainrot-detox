package dev.tinow.brainrotstopper.data.anki

import dev.tinow.brainrotstopper.domain.anki.AnkiError
import kotlinx.serialization.SerializationException

/** Maps every exception of a provider call to the error model. Cancellation is not an error and must be rethrown by the caller. */
object AnkiErrorMapper {
    fun map(t: Throwable): AnkiError = when {
        t is SecurityException -> AnkiError.PermissionMissing
        // checked before IllegalArgumentException, which SerializationException extends
        t is SerializationException || t is NumberFormatException -> AnkiError.BadData
        t is ProviderDeadException -> AnkiError.Unreachable
        // "AnkiDroid storage is not configured" (measured in M0-S1b); any other IllegalStateException is not a setup problem
        t is IllegalStateException && storageMessage(t.message) -> AnkiError.StorageNotConfigured
        t is IllegalStateException -> AnkiError.Unexpected("IllegalStateException")
        // "Unknown URI": the provider is not there
        t is IllegalArgumentException -> AnkiError.Unreachable
        else -> AnkiError.Unexpected(t::class.java.simpleName)
    }

    private fun storageMessage(message: String?) = message?.contains("storage", ignoreCase = true) == true
}
