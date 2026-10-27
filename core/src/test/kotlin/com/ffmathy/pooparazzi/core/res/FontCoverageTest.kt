package com.ffmathy.pooparazzi.core.res

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.awt.Font
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every character of the shared strings exists in the bundled Google Sans Flex, so no label silently falls back
 * to another font (e.g. the "→" of "Venstre → Højre"). The watch numerals font covers what it renders.
 */
class FontCoverageTest {
    private val phoneFont = Font.createFont(Font.TRUETYPE_FONT, File("src/main/res/font/google_sans_flex.ttf"))
    private val watchFont = Font.createFont(Font.TRUETYPE_FONT, File("src/main/res/font/google_sans_flex_watch.ttf"))

    private val english = strings(File("src/main/res/values/strings.xml"))
    private val danish = strings(File("src/main/res/values-da/strings.xml"))

    @Test
    fun googleSansFlexCoversEverySharedString() {
        val missing = (english + danish).flatMap { (name, text) -> missing(phoneFont, text).map { "$name: U+%04X '%c'".format(it, it) } }
        assertTrue("Characters missing from google_sans_flex.ttf: $missing", missing.isEmpty())
    }

    @Test
    fun theWatchNumeralsFontCoversNumeralsUnitsAndJustNow() {
        val used = listOf("unit_day", "unit_hour", "unit_minute", "rel_just_now")
            .flatMap { listOf(english.getValue(it), danish.getValue(it)) } + "0123456789<: "
        val missing = used.flatMap { missing(watchFont, it) }.distinct().map { "U+%04X".format(it) }
        assertTrue("Characters missing from google_sans_flex_watch.ttf: $missing", missing.isEmpty())
    }

    private fun missing(font: Font, text: String): List<Int> =
        text.codePoints().toArray().filter { !Character.isWhitespace(it) && it != 0x00A0 && !font.canDisplay(it) }

    /** Display text of every string and plural item, with placeholders and escapes removed. */
    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, String>()
        fun clean(text: String) = text.replace(Regex("%(\\d+\\$)?[sd]"), "").replace("\\'", "'").replace("\\\"", "\"")
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val element = strings.item(i) as Element
            if (element.getAttribute("name").startsWith("pattern_")) continue // java.time patterns, not text
            result[element.getAttribute("name")] = clean(element.textContent)
        }
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val element = items.item(i) as Element
            val parent = element.parentNode as Element
            if (parent.tagName == "plurals") result[parent.getAttribute("name") + "/" + element.getAttribute("quantity")] = clean(element.textContent)
        }
        return result
    }
}
