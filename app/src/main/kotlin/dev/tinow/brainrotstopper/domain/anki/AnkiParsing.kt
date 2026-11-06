package dev.tinow.brainrotstopper.domain.anki

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Pure parsers for the strings the provider returns. They return null instead of throwing: the caller decides it is BadData. */
object AnkiParsing {
    private val json = Json

    // U+2068 FIRST STRONG ISOLATE and U+2069 POP DIRECTIONAL ISOLATE as code points (not as characters in the source: lint BidiSpoofing)
    private const val ISOLATE_FIRST = 0x2068
    private const val ISOLATE_POP = 0x2069

    /** `deck_count` is `[learn, review, new]`; anything else is unreadable. */
    fun deckCounts(raw: String?): DeckCounts? {
        val array = parseArray(raw) ?: return null
        if (array.size != 3) return null
        val n = array.map { (it as? JsonPrimitive)?.intOrNull ?: return null }
        if (n.any { it < 0 }) return null
        return DeckCounts(learn = n[0], review = n[1], new = n[2])
    }

    /**
     * `next_review_times`: exactly four non-empty strings (again, hard, good, easy). AnkiDroid wraps the numbers in the
     * Unicode isolates U+2068/U+2069 (measured in M0-S1a); they are removed before display.
     */
    fun intervalLabels(raw: String?): List<String>? {
        val array = parseArray(raw) ?: return null
        if (array.size != 4) return null
        val labels = array.map { element ->
            val p = element as? JsonPrimitive ?: return null
            if (!p.isString) return null
            p.content.filterNot { it.code == ISOLATE_FIRST || it.code == ISOLATE_POP }.trim().ifEmpty { return null }
        }
        return labels
    }

    /** `media_files` is a JSON array of file names; a damaged value only costs the hint, so it yields an empty list. */
    fun mediaFiles(raw: String?): List<String> =
        parseArray(raw)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()

    private fun parseArray(raw: String?): JsonArray? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.parseToJsonElement(raw) as? JsonArray
        } catch (_: Exception) {
            null
        }
    }
}
