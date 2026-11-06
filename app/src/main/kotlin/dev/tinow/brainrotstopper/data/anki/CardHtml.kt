package dev.tinow.brainrotstopper.data.anki

import dev.tinow.brainrotstopper.domain.anki.RenderedCard

/** Which side of a card a document shows. */
enum class CardSide { Question, Answer }

/** Colours (CSS hex, `#rrggbb`) and mode for one card document; built from the app theme by the renderer. */
data class CardStyle(
    val isDark: Boolean,
    val text: String,
    val dim: String,
    val accent: String,
    val line: String,
    val fill: String,
)

/**
 * One side of a card, ready to show.
 *
 * @property html complete HTML document (CSP, no script, no external `src`/`href`)
 * @property plainText the same content as plain text for the fallback renderer
 * @property imageNames file names of the images in the side, in order, without duplicates; only safe names
 */
data class CardDocument(val html: String, val plainText: String, val imageNames: List<String>)

/**
 * Builds the HTML shown for a card (architecture.md 5, "Rendering"). Pure Kotlin on purpose.
 *
 * The WebView runs without JavaScript and without network, and the document carries a CSP as defence in depth, so
 * this sanitiser is the third line, not the only one: it still removes everything that could load or run something.
 * Images become the placeholder `[ bild: name ]` unless [build] gets the image as a `data:` URI in `images`.
 */
object CardHtml {
    private val TYPE_FIELD = Regex("""\[\[type:[^\]]*\]\]""")
    private val SOUND = Regex("""\[sound:[^\]]*\]|\[anki:play:[^\]]*\]""")
    private val ANSWER_SEPARATOR = Regex("""<hr\s+id\s*=\s*["']?answer["']?\s*/?>""", RegexOption.IGNORE_CASE)

    private val DROP_WITH_CONTENT = Regex(
        """<(script|iframe|object|embed|noscript|template|video|audio|applet|frameset|svg|math)\b[^>]*>.*?</\1\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val DROP_TAG = Regex(
        """</?(script|iframe|object|embed|noscript|template|video|audio|applet|frameset|svg|math|link|meta|base|source|track|form|input|button|select|textarea)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val IMG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val SRC = Regex("""\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

    /** Attributes that can load something or run code, plus `data-*` (cloze cards keep the solution in `data-cloze`). */
    private val DROP_ATTR = Regex(
        """\s+(?:on[a-z]+|src|srcset|href|xlink:href|action|formaction|poster|background|ping|data-[a-z0-9_.:-]*)\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val OPEN_TAG = Regex("""<[a-zA-Z][^>]*>""")
    private val DROP_BARE_ATTR = Regex("""\s+(?:src|href|srcset|poster|background|data-[a-z0-9_.:-]*)(?=[\s/>])""", RegexOption.IGNORE_CASE)

    private val BLOCK_BREAK = Regex("""</(div|p|tr|h[1-6]|ul|ol|table|blockquote)\s*>|<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val LI_OPEN = Regex("""<li\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val STYLE_BLOCK = Regex("""<style\b[^>]*>.*?</style\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val WHITESPACE = Regex("""[\r\n\t ]+""")
    private val ANY_TAG = Regex("""<[^>]*>""")

    private val CSS_IMPORT = Regex("""@import[^;]*;?""", RegexOption.IGNORE_CASE)
    private val CSS_URL = Regex("""url\s*\([^)]*\)""", RegexOption.IGNORE_CASE)
    private val CSS_EXPRESSION = Regex("""expression\s*\([^)]*\)|-moz-binding\s*:[^;}]*|behavior\s*:[^;}]*""", RegexOption.IGNORE_CASE)
    private val DATA_IMAGE = Regex("""data:image/(?:png|jpeg|gif|webp);base64,[A-Za-z0-9+/=]+""")

    /** The side as HTML fragment (still without the document frame). */
    private fun sideHtml(card: RenderedCard, side: CardSide): String = when (side) {
        CardSide.Question -> card.question
        CardSide.Answer -> {
            // A3 shows only what follows the separator; cloze cards have none and show the whole answer.
            val m = ANSWER_SEPARATOR.find(card.answer)
            if (m != null) card.answer.substring(m.range.last + 1) else card.answer
        }
    }

    /** True when the answer is just the question again (no `<hr id=answer>`, e.g. cloze): the UI may skip the dimmed question. */
    fun answerRepeatsQuestion(card: RenderedCard): Boolean = !ANSWER_SEPARATOR.containsMatchIn(card.answer)

    /** False when neither side has anything to show; the caller treats that as bad data (architecture.md 5). */
    fun hasContent(card: RenderedCard): Boolean =
        plainText(card, CardSide.Question).isNotBlank() || plainText(card, CardSide.Answer).isNotBlank()

    fun imageNames(card: RenderedCard, side: CardSide): List<String> = imageNamesOf(prepare(sideHtml(card, side)))

    fun plainText(card: RenderedCard, side: CardSide): String = toPlainText(prepare(sideHtml(card, side)))

    /**
     * @param images file name to `data:image/...;base64,...` URI, as delivered by the media inliner; names that are
     * missing (or whose value is not such a URI) stay placeholders.
     * @param dimmed the question above the answer on A3: smaller and dim
     */
    fun build(
        card: RenderedCard,
        side: CardSide,
        ord: Int,
        style: CardStyle,
        images: Map<String, String> = emptyMap(),
        dimmed: Boolean = false,
    ): CardDocument {
        val prepared = prepare(sideHtml(card, side))
        val body = replaceImages(prepared, images)
        val css = buildString {
            append(BASE_CSS)
            card.css?.let { append(sanitizeCss(it)).append('\n') }
            append(themeCss(style, dimmed))
        }
        val classes = buildString {
            append("card card").append(ord + 1).append(" mobile android")
            if (style.isDark) append(" nightMode night_mode")
        }
        val html = "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"$CSP\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>$css</style></head><body class=\"$classes\"><div id=\"bs\">$body</div></body></html>"
        return CardDocument(html, toPlainText(prepared), imageNamesOf(prepared))
    }

    const val CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:"

    // ------------------------------------------------------------------ sanitising

    /** Placeholder marker, removed again in [replaceImages]/[toPlainText]; U+E000 does not occur in cards. */
    private const val IMG_OPEN = ""
    private const val IMG_CLOSE = ""

    /**
     * Keeps only harmless markup. Images are replaced by a marker `IMG_OPEN name IMG_CLOSE` (name empty when unsafe), the
     * other stages see only text and basic tags.
     */
    private fun prepare(html: String): String {
        var s = html.replace(IMG_OPEN, "").replace(IMG_CLOSE, "")
        s = TYPE_FIELD.replace(s, "")
        s = SOUND.replace(s, "[ audio ]")
        s = DROP_WITH_CONTENT.replace(s, "")
        s = DROP_TAG.replace(s, "")
        s = IMG.replace(s) { m ->
            val raw = SRC.find(m.value)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] }.ifEmpty { it.groupValues[3] } }
            IMG_OPEN + (raw?.let { safeName(HtmlEntities.decode(it)) } ?: "") + IMG_CLOSE
        }
        // attributes only inside tags, so visible text such as "src=..." stays
        s = OPEN_TAG.replace(s) { m -> DROP_BARE_ATTR.replace(DROP_ATTR.replace(m.value, ""), "") }
        return s
    }

    /** Only plain file names pass: no path, no scheme, no `..`, no control characters, not empty. */
    fun safeName(name: String): String? {
        val n = name.trim()
        if (n.isEmpty() || n.length > 255) return null
        if (n.any { it == '/' || it == '\\' || it == ':' || it.isISOControl() || it == '' || it == '' }) return null
        if (n.contains("..")) return null
        return n
    }

    private fun imageNamesOf(prepared: String): List<String> {
        val out = LinkedHashSet<String>()
        var i = 0
        while (true) {
            val a = prepared.indexOf(IMG_OPEN, i)
            if (a < 0) break
            val b = prepared.indexOf(IMG_CLOSE, a)
            if (b < 0) break
            val name = prepared.substring(a + 1, b)
            if (name.isNotEmpty()) out += name
            i = b + 1
        }
        return out.toList()
    }

    private fun replaceImages(prepared: String, images: Map<String, String>): String {
        val sb = StringBuilder()
        var i = 0
        while (true) {
            val a = prepared.indexOf(IMG_OPEN, i)
            if (a < 0) break
            val b = prepared.indexOf(IMG_CLOSE, a)
            if (b < 0) break
            sb.append(prepared, i, a)
            val name = prepared.substring(a + 1, b)
            val uri = images[name]?.takeIf { name.isNotEmpty() && DATA_IMAGE.matches(it) }
            if (uri != null) sb.append("<img src=\"").append(uri).append("\" alt=\"\">")
            else sb.append("<span class=\"bs-img\">").append(escape(placeholder(name))).append("</span>")
            i = b + 1
        }
        sb.append(prepared, i, prepared.length)
        return sb.toString()
    }

    private fun placeholder(name: String) = if (name.isEmpty()) "[ bild ]" else "[ bild: $name ]"

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun sanitizeCss(css: String): String {
        var s = css.replace("</", "<\\/")
        s = CSS_IMPORT.replace(s, "")
        s = CSS_URL.replace(s, "none")
        s = CSS_EXPRESSION.replace(s, "")
        return s
    }

    // ------------------------------------------------------------------ plain text

    private fun toPlainText(prepared: String): String {
        var s = STYLE_BLOCK.replace(prepared, "")
        s = WHITESPACE.replace(s, " ") // raw line breaks in HTML are plain whitespace
        s = LI_OPEN.replace(s, "\n- ")
        s = BLOCK_BREAK.replace(s, "\n")
        s = ANY_TAG.replace(s, "")
        s = HtmlEntities.decode(s)
        // markers back to text (after the entity decoding, so an `&#xE000;` in a card cannot forge one)
        val sb = StringBuilder()
        var i = 0
        while (true) {
            val a = s.indexOf(IMG_OPEN, i)
            if (a < 0) break
            val b = s.indexOf(IMG_CLOSE, a)
            if (b < 0) break
            sb.append(s, i, a).append(placeholder(s.substring(a + 1, b)))
            i = b + 1
        }
        sb.append(s, i, s.length)
        return sb.toString()
            .replace(' ', ' ')
            .lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    // ------------------------------------------------------------------ CSS

    private const val BASE_CSS = "html,body{margin:0;padding:0}" +
        "body{font-size:20px;line-height:1.45;overflow-wrap:anywhere;word-break:normal}" +
        "#bs{padding:0}" +
        ".cloze{font-weight:bold}.cloze-inactive{font-weight:normal}" +
        "img{max-width:100%;height:auto}" +
        "ul,ol{display:inline-block;text-align:left;margin:0;padding-left:1.4em}" +
        ".bs-img{display:inline-block;border:1px dashed currentColor;padding:2px 6px;font-size:.8em}\n"

    /** Appended last so the theme wins over the note type CSS (black on white would be unreadable on dark). */
    private fun themeCss(s: CardStyle, dimmed: Boolean): String {
        val color = if (dimmed) s.dim else s.text
        val size = if (dimmed) "15px" else "20px"
        return ":root{color-scheme:${if (s.isDark) "dark" else "light"}}" +
            "html,body,.card{background:transparent!important;background-color:transparent!important;" +
            "color:$color!important;font-family:monospace!important;font-size:$size!important}" +
            "*{color:$color!important;background-color:transparent!important;border-color:${s.line}!important}" +
            ".cloze,.cloze *{color:${s.accent}!important}" +
            ".bs-img{color:${s.dim}!important;border-color:${s.line}!important}" +
            "hr{border:0;border-top:1px solid ${s.line}!important}" +
            "code,pre{background-color:${s.fill}!important}\n"
    }
}

/** HTML entity decoding for the few named entities cards use plus numeric ones (no table of all 2000+). */
internal object HtmlEntities {
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "szlig" to "ß",
        "hellip" to "…", "ndash" to "–", "mdash" to "—", "euro" to "€", "copy" to "©", "laquo" to "«", "raquo" to "»",
    )
    private val ENTITY = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]*);""")

    fun decode(s: String): String {
        if (!s.contains('&')) return s
        return ENTITY.replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> codePoint(e.substring(2).toIntOrNull(16)) ?: m.value
                e.startsWith("#") -> codePoint(e.substring(1).toIntOrNull()) ?: m.value
                else -> NAMED[e] ?: m.value
            }
        }
    }

    private fun codePoint(cp: Int?): String? =
        if (cp != null && cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF && cp != 0xE000 && cp != 0xE001) String(Character.toChars(cp)) else null
}
