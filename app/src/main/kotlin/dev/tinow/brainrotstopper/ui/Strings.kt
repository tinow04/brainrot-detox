package dev.tinow.brainrotstopper.ui

import java.util.Locale

/** A piece of text, optionally bold (the mockups emphasise numbers with `<b>`). */
data class Span(val text: String, val bold: Boolean = false)

/**
 * Single source of all UI texts (German, real umlauts), kept in line with design/mockups and the text matrix in
 * .agents/mockups.md. Pure constants and functions, JVM-tested. Only app_name and the accessibility description live in strings.xml.
 */
object Strings {
    // ---- shared formats
    fun plural(n: Int, one: String, many: String) = if (n == 1) "$n $one" else "$n $many"
    fun karten(n: Int) = plural(n, "karte", "karten")
    fun tage(n: Int) = plural(n, "tag", "tage")
    fun minuten(n: Int) = "$n min"

    /** `[██░░░] 2/5`; bars up to 20 characters. */
    fun progress(correct: Int, required: Int): String {
        val r = required.coerceAtLeast(0)
        val c = correct.coerceIn(0, r)
        return "[" + "█".repeat(c) + "░".repeat(r - c) + "] $c/$r"
    }

    fun progressDescription(correct: Int, required: Int) = "$correct von $required richtig"

    /** Deck label for header/intro: last `::` segment, lower case; all decks -> `alle decks`. */
    fun deckLabel(deckName: String?): String =
        if (deckName == null) ALL_DECKS else deckName.substringAfterLast("::").lowercase(Locale.GERMAN)

    fun appLabel(label: String) = label.lowercase(Locale.GERMAN)

    /** `~3h 20m`, `~45 min`, `0 min`. */
    fun timeSaved(minutes: Int): String = when {
        minutes <= 0 -> "0 min"
        minutes < 60 -> "~$minutes min"
        else -> "~${minutes / 60}h ${minutes % 60}m"
    }

    /** `86 %`, `-` when nothing was answered. */
    fun quote(correct: Int, answered: Int): String =
        if (answered <= 0) "-" else "${(correct * 100.0 / answered).let { Math.round(it) }} %"

    fun mmss(totalMs: Long): String {
        val s = (totalMs / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(Locale.ROOT, s / 60, s % 60)
    }

    const val ALL_DECKS = "alle decks"
    const val AND_MORE = "…"

    // ---- A1 intro
    const val HDR_PATH = "~/brainrot-stopper"
    const val HDR_LOCK = "lock.sh"
    fun introCheck(app: String) = "\$ ./check --app $app"
    const val TAG_LOCK = "[ LOCK ] gesperrt"
    fun introText(remaining: Int, resumed: Boolean) = listOf(
        Span(if (resumed) "noch " else "beantworte "),
        Span(karten(remaining), bold = true),
        Span(" richtig, dann geht's weiter."),
    )
    const val BOX_STATS = "stats"
    const val KV_STREAK = "streak"
    const val KV_TODAY = "heute"
    const val KV_DECK = "deck"
    const val BTN_START = "\$ ./start"
    const val BTN_CONTINUE = "\$ ./weiter"
    const val BTN_CANCEL = "abbrechen"

    // ---- A2/A3 card
    fun cardHeader(app: String, deckLabel: String) = "${appLabel(app)} · $deckLabel"
    fun cardLabel(k: Int) = "karte $k · frage"
    const val BTN_SHOW_ANSWER = "antwort zeigen"
    const val SEP_ANSWER = "antwort"
    const val RATE_HINT = "wie gut wusstest du's? nur gut und leicht zählen."
    const val RATE_AGAIN = "[1] nochmal"
    const val RATE_HARD = "[2] schwer"
    const val RATE_GOOD = "[3] gut"
    const val RATE_EASY = "[4] leicht"
    fun mediaPlaceholder(name: String) = "[ bild: $name ]"
    const val MEDIA_AUDIO = "[ audio ]"

    // ---- A4 feedback
    fun rateCommand(k: Int, kind: String) = "\$ ./rate --card $k --$kind"
    const val TAG_FAIL = "[ FAIL ]"
    const val TAG_WARN = "[ WARN ]"
    const val TAG_OK = "[ OK ]"
    const val TAG_WAIT = "[ WAIT ]"
    const val TAG_OPEN = "[ OFFEN ]"
    const val TAG_ERROR = "[ FEHLER ]"
    const val FEEDBACK_AGAIN = "kein problem. die karte kommt später nochmal."
    const val FEEDBACK_HARD = "knapp. die karte kommt später wieder."
    const val BOX_COUNTS = "was zählt"
    const val COUNTS_NO = "– zählt nicht"
    const val COUNTS_YES = "+ zählt"
    fun feedbackProgress(correct: Int, required: Int, app: String): List<Span> = listOf(
        Span("fortschritt bleibt bei "),
        Span("$correct/$required", bold = true),
        Span(". noch "),
        Span("${required - correct}", bold = true),
        Span(" richtige bis ${appLabel(app)} offen ist."),
    )

    // ---- A5 unlocked
    fun unlockCommand(app: String) = "\$ ./unlock ${appLabel(app)}"
    const val TAG_UNLOCKED = "[ OK ] entsperrt"
    fun unlockedText(app: String, minutes: Int) = listOf(
        Span("${appLabel(app)} ist für "),
        Span(minuten(minutes), bold = true),
        Span(" freigegeben."),
    )
    fun allDoneText(app: String, minutes: Int) = listOf(
        Span("alles gelernt für heute. ${appLabel(app)} ist für "),
        Span(minuten(minutes), bold = true),
        Span(" freigegeben."),
    )
    const val BOX_SESSION = "session"
    const val KV_CARDS = "karten"
    const val KV_TIME = "zeit"
    fun sessionCards(answered: Int, correct: Int) = "$answered ($correct richtig)"
    fun openCommand(app: String) = "\$ ./open ${appLabel(app)}"
    const val BTN_CLOSE = "schließen"
    fun emergencyCommand(app: String) = "\$ ./notausgang ${appLabel(app)}"
    const val TAG_PAUSE = "[ WARN ] pause"
    fun emergencyText(app: String): List<Span> = listOf(
        Span("${appLabel(app)} ist für "),
        Span(minuten(5), bold = true),
        Span(" freigegeben. ankidroid-problem beheben."),
    )

    // ---- waiting / loading
    const val TAG_WAITING = "[ WAIT ] nächste karte folgt"
    const val WAITING_TEXT = "lernkarten sind noch nicht fällig. die app prüft alle 15\u00A0s." // no-break space: no lone "s." on the last line
    const val LOADING = "\$ ./laden"

    // ---- errors (title, text, main action)
    data class ErrorTexts(val title: String, val text: String, val action: String)
    val ERR_NOT_AVAILABLE = ErrorTexts("[ FEHLER ] ankidroid nicht erreichbar", "nicht installiert oder die api ist aus.", "\$ ./erneut")
    val ERR_PERMISSION = ErrorTexts("[ FEHLER ] berechtigung fehlt", "die app darf ankidroid nicht lesen.", "\$ ./app-info")
    val ERR_STORAGE = ErrorTexts("[ FEHLER ] ankidroid nicht eingerichtet", "ankidroid einmal öffnen und einrichten.", "\$ ./ankidroid oeffnen")
    val ERR_UNREACHABLE = ErrorTexts("[ FEHLER ] keine antwort von ankidroid", "timeout oder ankidroid nicht bereit.", "\$ ./erneut")
    val ERR_DECK_MISSING = ErrorTexts("[ FEHLER ] deck nicht gefunden", "das gewählte deck existiert nicht mehr.", "\$ ./einstellungen")
    val ERR_BAD_DATA = ErrorTexts("[ FEHLER ] unerwartete antwort", "karte konnte nicht gelesen werden.", "\$ ./erneut")
    val ERR_ANSWER_NOT_SAVED = ErrorTexts("[ FEHLER ] antwort nicht gespeichert", "die bewertung kam nicht an und wird nicht gezählt.", "\$ ./erneut")
    const val BTN_OPEN_ANKIDROID = "\$ ./ankidroid oeffnen"
    const val BTN_EMERGENCY = "\$ ./notausgang"
    fun emergencyCountdown(seconds: Int) = "\$ ./notausgang (${seconds}s)"

    // ---- main app: B1 apps
    const val TITLE_PROMPT = "\$"
    const val TITLE_APPS = "apps"
    const val SEARCH_PROMPT = "\$ grep"
    const val SEARCH_PLACEHOLDER = "app suchen"
    fun sepBlocked(n: Int) = "gesperrt · $n"
    const val SEP_ALL_APPS = "alle apps"
    const val EMPTY_BLOCKED = "noch keine app gesperrt"
    const val EMPTY_SEARCH = "keine treffer"
    const val ST_BLOCKED = "gesperrt"
    const val ST_FREE = "nicht gesperrt"
    fun rowDescription(app: String, blocked: Boolean) = "$app, ${if (blocked) ST_BLOCKED else ST_FREE}"

    // ---- nav
    const val NAV_LABEL = "hauptmenü"
    const val NAV_APPS = "apps"
    const val NAV_CONFIG = "einstellungen"
    const val NAV_STATS = "statistik"

    // ---- B2 config
    const val TITLE_CONFIG = "config"
    const val BOX_CARDS = "karten pro entsperrung"
    const val KV_REQUIRED = "richtige karten"
    const val STEP_MINUS = "−"
    const val STEP_PLUS = "+"
    const val BOX_DURATION = "freigabe-dauer"
    const val BOX_DECK = "anki-deck"
    const val RADIO_ON = "(•)"
    const val RADIO_OFF = "( )"
    fun due(n: Int) = "$n fällig"
    const val BOX_ANKIDROID = "ankidroid"
    const val ANKI_OK = "ankidroid verbunden"
    fun ankiConnected(decks: Int) = "api aktiv · $decks decks gefunden"

    // ---- B3 stats
    const val TITLE_STATS = "stats --week"
    const val STAT_BLOCKED_TODAY = "gesperrt heute"
    const val STAT_WEEK = "diese woche"
    const val STAT_STREAK = "streak"
    const val STAT_CARDS = "karten"
    const val STAT_QUOTE = "richtig-quote"
    const val STAT_SAVED = "zeit gespart*"
    const val STAT_FOOTNOTE = "* geschätzt, nicht gemessen"
    const val BOX_CHART = "gesperrt · letzte 7 tage"
    const val BOX_RANKING = "am häufigsten gesperrt"
    val WEEKDAYS = listOf("mo", "di", "mi", "do", "fr", "sa", "so")

    // ---- banner (above all tabs, only when needed)
    const val BANNER_DEGRADED = "[ WARN ] setup unvollständig"
    const val BANNER_OFF_DISABLED = "[ FEHLER ] schutz inaktiv - bedienungshilfe aus"
    const val BANNER_OFF_UNBOUND = "[ FEHLER ] schutz inaktiv - dienst nicht gebunden"
    const val BANNER_OFF_RESTRICTED = "[ FEHLER ] schutz inaktiv - einstellungen blockiert"
    const val BANNER_CORRUPT = "[ FEHLER ] einstellungen beschädigt - blockliste prüfen"

    // ---- setup
    const val SETUP_TITLE = "setup"
    const val SETUP_PROMPT = "\$ setup"
    const val SETUP_ANKI_INSTALLED = "ankidroid installiert"
    const val SETUP_ANKI_PERMISSION = "ankidroid-zugriff erlaubt"
    const val SETUP_ANKI_READY = "ankidroid bereit"
    const val SETUP_ACCESSIBILITY = "bedienungshilfe aktiv"
    const val SETUP_BATTERY = "akku-optimierung aus (empfohlen)"
    const val SETUP_ACCESSIBILITY_UNBOUND = "bedienungshilfe an, dienst aber nicht gebunden"
    const val SETUP_ACCESSIBILITY_RESTRICTED = "bedienungshilfe blockiert (eingeschränkte einstellungen)"
    const val SETUP_HINT_RESTRICTED = "tipp: lässt sich die bedienungshilfe nicht einschalten, in der app-info über ⋮ eingeschränkte einstellungen zulassen."
    const val SETUP_HINT_RESTART = "hinweis: nach einem neustart des dienstes wird eine schon offene gesperrte app erst beim nächsten fensterwechsel gesperrt."
    const val TAB_SOON = "kommt in einem späteren meilenstein."
    const val SETUP_HINT_AUTOSYNC = "tipp: auto-sync in ankidroid einschalten."
    const val BTN_ALLOW = "\$ ./zugriff erlauben"
    const val BTN_APP_INFO = "\$ ./app-info"
    const val BTN_ACCESSIBILITY = "\$ ./bedienungshilfe"
    const val BTN_BATTERY = "\$ ./akku"
    const val BTN_RETRY = "\$ ./erneut"
    const val BTN_SETTINGS = "\$ ./einstellungen"

    // ---- B2 media box (Revision 3, see architecture.md 5a)
    const val BOX_MEDIA = "bilder"
    const val MEDIA_URL = "server-url"
    const val MEDIA_TOKEN = "token"
    const val BTN_MEDIA_TEST = "\$ ./test"
    const val MEDIA_OK = "bilder-server erreichbar"
    const val MEDIA_FAIL = "bilder-server nicht erreichbar"
    const val MEDIA_OFF = "aus (platzhalter statt bild)"
}
