package dev.tinow.brainrotstopper.debug.fake

import dev.tinow.brainrotstopper.domain.anki.RenderedCard
import dev.tinow.brainrotstopper.domain.model.CardRef

/**
 * Card sides as AnkiDroid 2.25.3 delivers them (copied from `src/test/resources/anki/card-NN.txt`, see the README there),
 * plus hand-made stress cards (`hostile`, `colors`, `long`). Used by the debug gallery and the instrumented renderer tests.
 */
object CardFixtures {
    private const val BASIC_CSS = ".card {\n font-family: arial;\n font-size: 20px;\n line-height: 1.5;\n text-align: center;\n color: black;\n background-color: white;\n}"
    private const val CLOZE_CSS = "$BASIC_CSS\n.cloze { font-weight: bold; color: blue; }\n.nightMode .cloze { color: lightblue; }"

    /** A valid 8x8 red PNG, base64. */
    const val RED_PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEklEQVR4nGP4z8CAFTEMLQkAQ9DfIbYTPGMAAAAASUVORK5CYII="

    private fun card(q: String, answer: String, pure: String, css: String = BASIC_CSS) = RenderedCard(CardRef(1, 0), q, answer, pure, css)
    private fun basic(q: String, a: String, css: String = BASIC_CSS) = card(q, "$q\n\n<hr id=answer>\n\n$a", a, css)

    val all: Map<String, RenderedCard> = linkedMapOf(
        "type" to basic("Hauptstadt von Frankreich?\n\n[[type:Back]]", "[[type:Back]]"),
        "image" to basic("Was zeigt das Bild? <img src=\"bild ä 1.png\">", "Ein rotes Rechteck"),
        "html" to basic("<b>Fett</b> &amp; <i>kursiv</i><br>Zeile 2 &lt;tag&gt;", "<ul><li>eins</li><li>zwei</li></ul>"),
        "cloze" to card(
            "Die <span class=\"cloze\" data-cloze=\"Hauptstadt\" data-ordinal=\"1\">[...]</span> von Frankreich ist <span class=\"cloze-inactive\" data-ordinal=\"2\">Paris</span>.",
            "Die <span class=\"cloze\" data-ordinal=\"1\">Hauptstadt</span> von Frankreich ist <span class=\"cloze-inactive\" data-ordinal=\"2\">Paris</span>.<br>\nExtra-Hinweis",
            "Die <span class=\"cloze\" data-ordinal=\"1\">Hauptstadt</span> von Frankreich ist <span class=\"cloze-inactive\" data-ordinal=\"2\">Paris</span>.<br>\nExtra-Hinweis",
            CLOZE_CSS,
        ),
        "audio" to basic("Hoere zu [sound:klang.mp3]", "Ein Ton"),
        "broken" to basic("{{c1::kein cloze in basic}} {{Unbekannt}}", "Antwort"),
        // styling the note type brings along: hard-coded colours and a remote font must not break dark mode
        "colors" to basic(
            "<font color=\"#000000\">Schwarz</font> <span style=\"background-color:#ffffff;color:#111\">weiss hinterlegt</span> <b style=\"color:red\">rot</b>",
            "<div style=\"color:black\">Antwort schwarz</div>",
            "@import url(http://example.invalid/a.css);\n@font-face{font-family:x;src:url(http://example.invalid/f.ttf)}\n.card{color:black;background:white url(http://example.invalid/b.png)}",
        ),
        // everything that must not run or load; the visible text proves the card still renders
        "hostile" to basic(
            "SICHTBAR<script>document.body.setAttribute('data-ran','1');document.title='ran'</script>" +
                "<img src=\"http://example.invalid/x.png\" onerror=\"document.title='err'\"><iframe src=\"http://example.invalid/\"></iframe>" +
                "<a href=\"http://example.invalid/\" onclick=\"x()\">Link</a><style>body{background:url(http://example.invalid/c.png)}</style>",
            "Antwort",
        ),
        "inline" to basic("Mit Bild: <img src=\"rot.png\">", "ok"),
        "long" to basic(
            (1..40).joinToString("<br>") { "Zeile $it mit etwas laengerem Text damit der Umbruch greift und die Karte scrollen muss" },
            (1..3).joinToString("<br>") { "Antwort $it" },
        ),
    )
}
