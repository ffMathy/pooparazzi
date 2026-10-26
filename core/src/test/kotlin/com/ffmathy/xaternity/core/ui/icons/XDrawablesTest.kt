package com.ffmathy.xaternity.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ffmathy.xaternity.core.ui.tokens.XTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The vector drawables in `res/drawable` are generated from [XIconSpecs], so the Compose vectors and the
 * drawables used by tiles, complications, notifications and widgets share one source (DESIGN_SPEC §6.1).
 *
 * After changing [XIconSpecs], run this test: it writes the expected files to `build/generated/xdrawables/`
 * and fails; copy them into `src/main/res/drawable/`.
 */
class XDrawablesTest {
    private val resDir = File("src/main/res/drawable")
    private val outDir = File("build/generated/xdrawables")

    /** Every file the icon set needs: one tintable drawable per icon, plus the fixed-colour two-tone `both`. */
    private val expected: Map<String, String> by lazy {
        val files = linkedMapOf<String, String>()
        for (spec in XIconSpecs.all) files[spec.drawableName] = render(spec, null, "XIconSpecs.${spec.name}")
        files["x_both_night"] = render(
            XIconSpecs.both,
            listOf(XTokens.NightEvents.pee.strong, XTokens.NightEvents.poop.strong),
            "XIconSpecs.both in Night/watch PEE + POOP strong",
        )
        files["x_both_day"] = render(
            XIconSpecs.both,
            listOf(XTokens.DayEvents.pee.strong, XTokens.DayEvents.poop.strong),
            "XIconSpecs.both in Day PEE + POOP strong",
        )
        files
    }

    @Test
    fun drawablesMatchTheIconSpecs() {
        val mismatched = expected.filter { (name, xml) -> File(resDir, "$name.xml").let { !it.exists() || it.readText() != xml } }
        if (mismatched.isNotEmpty()) {
            outDir.mkdirs()
            mismatched.forEach { (name, xml) -> File(outDir, "$name.xml").writeText(xml) }
        }
        assertTrue(
            "Drawables out of date: ${mismatched.keys}. Copy ${outDir.absolutePath}/*.xml to src/main/res/drawable/",
            mismatched.isEmpty(),
        )
        val stray = resDir.listFiles().orEmpty().map { it.nameWithoutExtension }.filter { it.startsWith("x_") } - expected.keys
        assertTrue("Unknown icon drawables: $stray", stray.isEmpty())
    }

    @Test
    fun drawablePathDataEqualsTheComposeVectors() {
        for (spec in XIconSpecs.all) {
            val paths = parsePaths(File(resDir, "${spec.drawableName}.xml"))
            assertEquals(spec.name, spec.paths.map { it.pathData }, paths.map { it.getAttribute("android:pathData") })
            spec.paths.zip(paths).forEach { (path, element) ->
                assertEquals("${spec.name} stroke", path.stroke, element.hasAttribute("android:strokeColor"))
                if (path.stroke) {
                    assertEquals("2", element.getAttribute("android:strokeWidth"))
                    assertEquals("round", element.getAttribute("android:strokeLineCap"))
                    assertEquals("round", element.getAttribute("android:strokeLineJoin"))
                }
                assertEquals("${spec.name} fill", if (path.fill) WHITE else TRANSPARENT, element.getAttribute("android:fillColor"))
            }
        }
    }

    @Test
    fun everyIconHasAResourceIdLookup() {
        assertEquals(XIconSpecs.all.map { it.name }.toSet(), XDrawables.all.keys)
        assertEquals(XIconSpecs.all.size, XDrawables.all.values.toSet().size)
    }

    private fun parsePaths(file: File): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("path")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun render(spec: XIconSpec, colors: List<Color>?, source: String): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<!-- Generated from $source (DESIGN_SPEC §6). Do not edit: XDrawablesTest regenerates and checks it. -->")
        appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
        appendLine("""    android:width="24dp"""")
        appendLine("""    android:height="24dp"""")
        appendLine("""    android:viewportWidth="24"""")
        append("""    android:viewportHeight="24">""")
        appendLine()
        spec.paths.forEachIndexed { index, path ->
            val color = colors?.get(index)?.let(::hex) ?: WHITE
            appendLine("    <path")
            appendLine("""        android:pathData="${path.pathData}"""")
            appendLine("""        android:fillColor="${if (path.fill) color else TRANSPARENT}"""")
            if (path.evenOdd) appendLine("""        android:fillType="evenOdd"""")
            if (path.stroke) {
                appendLine("""        android:strokeColor="$color"""")
                appendLine("""        android:strokeWidth="2"""")
                appendLine("""        android:strokeLineCap="round"""")
                appendLine("""        android:strokeLineJoin="round"""")
            }
            setLength(length - 1)
            appendLine(" />")
        }
        appendLine("</vector>")
    }

    private fun hex(color: Color) = "#%08X".format(color.toArgb())

    private companion object {
        /** White, so a drawable works as-is for monochrome complication images and notification icons; tint elsewhere. */
        const val WHITE = "#FFFFFFFF"
        const val TRANSPARENT = "#00000000"
    }
}
