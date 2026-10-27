package com.ffmathy.pooparazzi.core.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every English shared string has a Danish twin with the same placeholders (V1_ADDITIONS §5). */
class StringResourcesTest {
    private val english = Resources.parse(File("src/main/res/values/strings.xml"))
    private val danish = Resources.parse(File("src/main/res/values-da/strings.xml"))

    @Test
    fun everyTranslatableStringHasADanishTwin() {
        val missing = english.strings.filterKeys { it !in english.untranslatable }.keys - danish.strings.keys
        assertTrue("Missing in values-da: $missing", missing.isEmpty())
    }

    @Test
    fun everyPluralHasADanishTwinWithOneAndOther() {
        val missing = english.plurals.keys - danish.plurals.keys
        assertTrue("Missing plurals in values-da: $missing", missing.isEmpty())
        for ((name, items) in english.plurals + danish.plurals) {
            assertTrue("$name needs one + other", items.keys.containsAll(listOf("one", "other")))
        }
    }

    @Test
    fun danishHasNoStrayKeysOrUntranslatableOverrides() {
        val stray = danish.strings.keys - english.strings.keys
        assertTrue("Unknown keys in values-da: $stray", stray.isEmpty())
        val overridden = danish.strings.keys.intersect(english.untranslatable)
        assertTrue("translatable=false strings must not be translated: $overridden", overridden.isEmpty())
    }

    @Test
    fun placeholdersMatch() {
        for ((name, value) in danish.strings) {
            assertEquals("Placeholders of $name", placeholders(english.strings.getValue(name)), placeholders(value))
        }
        for ((name, items) in danish.plurals) {
            val englishPlaceholders = placeholders(english.plurals.getValue(name).getValue("other"))
            items.values.forEach { assertEquals("Placeholders of plural $name", englishPlaceholders, placeholders(it)) }
        }
    }

    @Test
    fun noEmptyValuesAndUnicodeIsReal() {
        (english.strings + danish.strings).forEach { (name, value) -> assertTrue("$name is empty", value.isNotBlank()) }
        // The glossary needs real æ/ø/å, the arrow and the middle dot, not ASCII stand-ins.
        assertEquals("Venstre → Højre", danish.strings["side_left_then_right"])
        assertEquals("Synkroniseret med ur · %1\$s", danish.strings["sync_synced_with_watch"])
        assertEquals("Modermælks­erstatning", danish.strings["feed_type_formula_compact"])
    }

    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value }.sorted().toList()

    private class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val untranslatable: Set<String>,
    ) {
        companion object {
            fun parse(file: File): Resources {
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val strings = linkedMapOf<String, String>()
                val untranslatable = mutableSetOf<String>()
                val stringNodes = doc.getElementsByTagName("string")
                for (i in 0 until stringNodes.length) {
                    val element = stringNodes.item(i) as Element
                    val name = element.getAttribute("name")
                    strings[name] = element.textContent
                    if (element.getAttribute("translatable") == "false") untranslatable += name
                }
                val plurals = linkedMapOf<String, Map<String, String>>()
                val pluralNodes = doc.getElementsByTagName("plurals")
                for (i in 0 until pluralNodes.length) {
                    val element = pluralNodes.item(i) as Element
                    val items = element.getElementsByTagName("item")
                    plurals[element.getAttribute("name")] = (0 until items.length).associate { j ->
                        val item = items.item(j) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                }
                return Resources(strings, plurals, untranslatable)
            }
        }
    }
}
