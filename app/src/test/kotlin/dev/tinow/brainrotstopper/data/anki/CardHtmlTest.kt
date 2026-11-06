package dev.tinow.brainrotstopper.data.anki

import com.google.common.truth.Truth.assertThat
import dev.tinow.brainrotstopper.domain.anki.RenderedCard
import dev.tinow.brainrotstopper.domain.model.CardRef
import org.junit.Test

class CardHtmlTest {
    private val dark = CardStyle(true, "#cdd6f4", "#9399b2", "#cba6f7", "#45475a", "#313244")
    private val light = dark.copy(isDark = false, text = "#4c4f69")

    /** Reads `src/test/resources/anki/card-NN.txt` (raw provider row, see the README there). */
    private fun fixture(nn: String, css: String? = null): RenderedCard {
        val raw = javaClass.getResourceAsStream("/anki/card-$nn.txt")!!.readBytes().toString(Charsets.UTF_8).trimEnd()
        val keys = listOf("question", "answer", "answer_pure", "question_simple", "answer_simple", "card_name")
        val marks = keys.mapIndexed { i, k -> (if (i == 0) "Row: 0 $k=" else ", $k=") }
        val at = IntArray(keys.size)
        var from = 0
        for (i in keys.indices) { at[i] = raw.indexOf(marks[i], from).also { check(it >= 0) { "$nn: ${keys[i]} missing" } }; from = at[i] + marks[i].length }
        fun part(i: Int) = raw.substring(at[i] + marks[i].length, if (i + 1 < keys.size) at[i + 1] else raw.length)
        return RenderedCard(CardRef(1, 0), part(0), part(1), part(2), css)
    }

    private fun card(q: String, a: String = q, pure: String = a, css: String? = null) = RenderedCard(CardRef(1, 0), q, a, pure, css)

    private fun build(c: RenderedCard, side: CardSide = CardSide.Question, style: CardStyle = dark, images: Map<String, String> = emptyMap()) =
        CardHtml.build(c, side, 0, style, images)

    /** No external reference of any kind may survive: no src/href/srcset/poster attribute, no url(), no @import, no event handler. */
    private fun assertNoExternalReferences(html: String) {
        val body = html.substringAfter("<body").substringBefore("</body>")
        assertThat(body).doesNotContainMatch("""(?i)\b(src|href|srcset|poster|background|action)\s*=(?!\s*"data:image/)""")
        assertThat(body).doesNotContainMatch("""(?i)\bon[a-z]+\s*=""")
        assertThat(body).doesNotContainMatch("""(?i)<(script|iframe|object|embed|link|meta|base|form|input|video|audio|source|svg)\b""")
        val css = html.substringAfter("<style>").substringBefore("</style>")
        assertThat(css).doesNotContainMatch("""(?i)url\s*\(|@import""")
    }

    @Test fun basicQuestionKeepsTextAndDropsTypeField() {
        val c = fixture("00")
        val d = build(c)
        assertThat(d.plainText).isEqualTo("Hauptstadt von Frankreich?")
        assertThat(d.html).doesNotContain("[[type")
        assertThat(d.html).contains("Hauptstadt von Frankreich?")
        assertThat(d.imageNames).isEmpty()
        assertNoExternalReferences(d.html)
    }

    @Test fun answerSideShowsOnlyTextAfterSeparator() {
        val c = fixture("05")
        val a = build(c, CardSide.Answer)
        assertThat(a.plainText).isEqualTo("Ein rotes Rechteck")
        assertThat(a.html).doesNotContain("Was zeigt das Bild")
        assertThat(a.imageNames).isEmpty()
        assertThat(CardHtml.answerRepeatsQuestion(c)).isFalse()
    }

    @Test fun clozeHasNoSeparatorAndShowsWholeAnswer() {
        val c = fixture("03")
        assertThat(CardHtml.answerRepeatsQuestion(c)).isTrue()
        val q = build(c)
        val a = build(c, CardSide.Answer)
        assertThat(q.plainText).isEqualTo("Die [...] von Frankreich ist Paris.")
        assertThat(a.plainText).isEqualTo("Die Hauptstadt von Frankreich ist Paris.\nExtra-Hinweis")
        // the solution sits in data-cloze of the question: it must not appear in the document at all
        assertThat(q.html).doesNotContain("data-cloze")
        assertThat(q.html.substringAfter("<body")).doesNotContain("Hauptstadt")
        assertThat(q.html).contains("class=\"cloze\"")
        assertNoExternalReferences(q.html)
    }

    @Test fun imageBecomesPlaceholderWithDecodedName() {
        val c = fixture("01")
        val q = build(c)
        assertThat(q.imageNames).containsExactly("bild ä 1.png")
        assertThat(q.plainText).isEqualTo("Was zeigt das Bild? [ bild: bild ä 1.png ]")
        assertThat(q.html).contains("[ bild: bild ä 1.png ]")
        assertNoExternalReferences(q.html)
    }

    @Test fun inlinedImageReplacesPlaceholderOnlyForValidDataUri() {
        val c = card("A <img src=\"x.png\"> B <img src='y.png'>")
        val ok = "data:image/png;base64,iVBORw0KGgo="
        val d = build(c, images = mapOf("x.png" to ok, "y.png" to "https://evil.example/y.png"))
        assertThat(d.html).contains("<img src=\"$ok\" alt=\"\">")
        assertThat(d.html).doesNotContain("evil.example")
        assertThat(d.html).contains("[ bild: y.png ]")
        // a data: URI that is not an image is refused as well
        val bad = build(c, images = mapOf("x.png" to "data:text/html;base64,PHNjcmlwdD4="))
        assertThat(bad.html).doesNotContain("text/html")
        assertThat(bad.html).contains("[ bild: x.png ]")
    }

    @Test fun unsafeImageNamesStayPlaceholdersWithoutName() {
        val c = card("""<img src="../../etc/passwd"><img src="http://x/y.png"><img src="a/b.png"><img src="C:\x.png"><img>""")
        val d = build(c)
        assertThat(d.imageNames).isEmpty()
        assertThat(d.plainText).isEqualTo("[ bild ][ bild ][ bild ][ bild ][ bild ]")
        assertNoExternalReferences(d.html)
    }

    @Test fun entitiesInImageNamesAreDecoded() {
        val d = build(card("""<img src="a&amp;b &#228;.png">"""))
        assertThat(d.imageNames).containsExactly("a&b ä.png")
        assertThat(d.html).contains("[ bild: a&amp;b ä.png ]")
    }

    @Test fun duplicateImageNamesAreListedOnce() {
        val d = build(card("""<img src="a.png"><img src="b.png"><img src="a.png">"""))
        assertThat(d.imageNames).containsExactly("a.png", "b.png").inOrder()
    }

    @Test fun audioBecomesMarker() {
        val c = fixture("06")
        val d = build(c)
        assertThat(d.plainText).contains("[ audio ]")
        assertThat(d.html).doesNotContain("[sound:")
        assertThat(build(card("x [anki:play:q:0] y")).plainText).isEqualTo("x [ audio ] y")
    }

    @Test fun entitiesAndListsInPlainText() {
        val c = fixture("02")
        val d = build(c)
        assertThat(d.plainText).isEqualTo("Fett & kursiv\nZeile 2 <tag>")
        // in the HTML document the decoded text stays escaped text, not a tag
        assertThat(d.html).contains("Zeile 2 &lt;tag&gt;")
        assertThat(d.html).doesNotContain("<tag>")
        assertThat(build(c, CardSide.Answer).plainText).isEqualTo("- eins\n- zwei")
        assertThat(CardHtml.plainText(card("a&nbsp;b &lt;c&gt; &#65;&#x42;<ul><li>eins</li><li>zwei</li></ul>"), CardSide.Question))
            .isEqualTo("a b <c> AB\n- eins\n- zwei")
    }

    @Test fun brokenTemplateStaysLiteralAndIsBewertbar() {
        val c = fixture("08")
        assertThat(build(c).plainText).isEqualTo("{{c1::kein cloze in basic}} {{Unbekannt}}")
        assertThat(build(c, CardSide.Answer).plainText).isEqualTo("Antwort")
        assertThat(CardHtml.hasContent(c)).isTrue()
    }

    @Test fun emptyCardHasNoContent() {
        assertThat(CardHtml.hasContent(card("", ""))).isFalse()
        assertThat(CardHtml.hasContent(card("<br> <div></div>", "&nbsp;"))).isFalse()
        assertThat(CardHtml.hasContent(card("", "Q<hr id=answer>A"))).isTrue()
    }

    @Test fun separatorVariantsAreRecognised() {
        for (hr in listOf("<hr id=answer>", "<hr id=\"answer\">", "<HR ID='answer' />", "<hr  id = answer/>")) {
            val c = card("Q", "Q\n\n$hr\n\nA")
            assertThat(build(c, CardSide.Answer).plainText).isEqualTo("A")
        }
        assertThat(build(card("Q", "Q und A"), CardSide.Answer).plainText).isEqualTo("Q und A")
    }

    @Test fun activeContentIsRemoved() {
        val evil = """<script>alert(1)</script><iframe src="http://x"></iframe><object data="x"></object><embed src="x">
            <link rel="stylesheet" href="http://x/a.css"><meta http-equiv="refresh" content="0;url=http://x"><base href="http://x/">
            <a href="http://x" onclick="boom()">Link</a> <div onmouseover='x()' style="color:red">Text</div>
            <form action="http://x"><input name="a"><button>go</button></form><video src="x"></video><svg onload="x()"></svg>
            <img src="x.png" onerror="boom()">"""
        val d = build(card(evil))
        assertNoExternalReferences(d.html)
        assertThat(d.html).doesNotContain("alert(1)")
        assertThat(d.html).doesNotContain("boom")
        assertThat(d.plainText).contains("Link")
        assertThat(d.plainText).contains("Text")
    }

    @Test fun textThatLooksLikeAttributesOutsideTagsSurvives() {
        assertThat(build(card("schreibe <code>src=\"a\"</code> oder href=b")).plainText).isEqualTo("schreibe src=\"a\" oder href=b")
    }

    @Test fun markerCharactersInCardsCannotForgeImages() {
        val d = build(card("a\uE000secret\uE001b &#xE000;x&#xE001;"))
        assertThat(d.imageNames).isEmpty()
        assertThat(d.plainText).doesNotContain("[ bild")
    }

    @Test fun modelCssIsSanitisedAndThemeWins() {
        val css = "@import url(http://x/a.css);\n.card{color:black;background:white;background-image:url(http://x/b.png)}\n@font-face{src:url(http://x/f.ttf)}</style><script>"
        val d = build(card("Q", css = css))
        assertNoExternalReferences(d.html)
        assertThat(d.html.substringAfter("<style>").substringBefore("</style>")).contains("color:black")
        // the theme block is last, so its !important rules beat the note type
        val styleBlock = d.html.substringAfter("<style>").substringBefore("</style>")
        assertThat(styleBlock.lastIndexOf("color:#cdd6f4!important")).isGreaterThan(styleBlock.indexOf("color:black"))
        assertThat(d.html.split("</style>").size).isEqualTo(2)
    }

    @Test fun documentHasCspAndModeClasses() {
        val dk = build(card("Q"), style = dark).html
        val lt = build(card("Q"), style = light).html
        assertThat(dk).contains("""content="default-src 'none'; style-src 'unsafe-inline'; img-src data:"""")
        assertThat(dk).contains("""class="card card1 mobile android nightMode night_mode"""")
        assertThat(lt).contains("""class="card card1 mobile android"""")
        assertThat(lt).contains("color:#4c4f69!important")
        assertThat(dk).startsWith("<!doctype html>")
        assertThat(dk).doesNotContain("<script")
        assertThat(CardHtml.build(card("Q"), CardSide.Question, 2, dark).html).contains("card card3 ")
    }

    @Test fun dimmedQuestionIsSmallerAndDim() {
        val d = CardHtml.build(card("Q"), CardSide.Question, 0, dark, dimmed = true).html
        assertThat(d).contains("font-size:15px!important")
        assertThat(d).contains("color:#9399b2!important")
    }

    @Test fun modelCssFixturesStayHarmless() {
        val all = javaClass.getResourceAsStream("/anki/models-css.txt")!!.readBytes().toString(Charsets.UTF_8)
        val d = build(card("Q", css = all))
        assertNoExternalReferences(d.html)
    }
}
