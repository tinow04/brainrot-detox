package dev.tinow.brainrotstopper.data.anki

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import kotlinx.serialization.SerializationException
import org.junit.Test

class AnkiErrorMapperTest {
    private fun map(t: Throwable) = AnkiErrorMapper.map(t)

    @Test fun `security exception is a missing permission`() {
        assertThat(map(SecurityException("Permission not granted for: CardContentProvider.query /decks"))).isEqualTo(AnkiError.PermissionMissing)
    }

    @Test fun `unconfigured storage is recognised by its message`() {
        assertThat(map(IllegalStateException("AnkiDroid storage is not configured"))).isEqualTo(AnkiError.StorageNotConfigured)
    }

    @Test fun `other illegal state is unexpected, not a setup problem`() {
        assertThat(map(IllegalStateException("collection closed"))).isEqualTo(AnkiError.Unexpected("IllegalStateException"))
    }

    @Test fun `unknown uri is unreachable`() {
        assertThat(map(IllegalArgumentException("Unknown URI content://x"))).isEqualTo(AnkiError.Unreachable)
    }

    @Test fun `dead provider process is unreachable`() {
        assertThat(map(ProviderDeadException())).isEqualTo(AnkiError.Unreachable)
    }

    @Test fun `damaged json and numbers are bad data, also though serialization is an illegal argument`() {
        assertThat(map(SerializationException("x"))).isEqualTo(AnkiError.BadData)
        assertThat(map(NumberFormatException("x"))).isEqualTo(AnkiError.BadData)
    }

    @Test fun `anything else is unexpected and carries only the class name`() {
        assertThat(map(UnsupportedOperationException("secret card text"))).isEqualTo(AnkiError.Unexpected("UnsupportedOperationException"))
    }
}
