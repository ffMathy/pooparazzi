package com.ffmathy.pooparazzi.wear.tile

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.expression.VersionBuilders.VersionInfo
import androidx.wear.protolayout.material3.materialScopeWithResources
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.surface.SurfaceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The just-logged tile title fits its slot in every language (DESIGN_SPEC §3.6): parts are dropped (undo glyph, check
 * glyph, then the time) instead of the renderer cutting "Undo". Real text measurement needs native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class TileTitleFitTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val slot = 227 * (1f - 2 * 0.145f)

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun fit(label: String, undo: String, fontScale: Float = 1f) =
        LoggedTitleParts.fitting(label, "14:32", DOT_SEPARATOR, undo, slot, TileTextMeasure(fontScale))

    @Test
    fun englishKeepsTheWholeTitle() {
        assertEquals(LoggedTitleParts.FULL, fit("Pee", "Undo"))
        assertEquals(LoggedTitleParts.FULL, fit("Both", "Undo"))
    }

    @Test
    fun danishDropsGlyphsSoThatFortrydStaysWhole() {
        val pee = fit("Tis", "Fortryd")
        assertTrue(pee.check && pee.time)
        val both = fit("Begge", "Fortryd")
        assertNotEquals(LoggedTitleParts.FULL, both)
        assertTrue(both.time)
    }

    @Test
    fun largeTextDropsMoreButNeverTheUndoWord() {
        val huge = fit("Woke up", "Undo", fontScale = 1.6f)
        assertNotEquals(LoggedTitleParts.FULL, huge)
    }

    @Test
    fun theDanishTileTitleSaysFortrydWithoutBeingCut() {
        val danish = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("da")) })
        val device = DeviceParameters.Builder()
            .setScreenWidthDp(227)
            .setScreenHeightDp(227)
            .setScreenDensity(2f)
            .setFontScale(1f)
            .setScreenShape(SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DEVICE_PLATFORM_WEAR_OS)
            .setRendererSchemaVersion(VersionInfo.Builder().setMajor(1).setMinor(500).build())
            .build()
        val state = WearSamples.tileJustLogged()
        val proto = materialScopeWithResources(danish, ProtoLayoutScope(), device, false, TileTheme.colorScheme) {
            pooparazziTile(state, SurfaceText.from(danish, SampleData.ZONE), token = 1, dynamic = false)
        }.toString()
        // "Vågnede 14:32 · Fortryd" is too wide for the title: the time goes, Fortryd stays whole.
        assertTrue(proto.contains("value=Vågnede · ,"))
        assertTrue(proto.contains("value=Fortryd,"))
        assertTrue(proto.contains("value=Vågnede kl. 14:32. Tryk på flisen for at fortryde,"))
    }

    private fun device(sizeDp: Int, fontScale: Float = 1f) = DeviceParameters.Builder()
        .setScreenWidthDp(sizeDp)
        .setScreenHeightDp(sizeDp)
        .setScreenDensity(2f)
        .setFontScale(fontScale)
        .setScreenShape(SCREEN_SHAPE_ROUND)
        .setDevicePlatform(DEVICE_PLATFORM_WEAR_OS)
        .setRendererSchemaVersion(VersionInfo.Builder().setMajor(1).setMinor(500).build())
        .build()

    @Test
    fun onSmallWatchesTheRowAConfirmationFitsItsLine() {
        val small = device(192)
        val width = TileTextMeasure.rowATextWidthDp(small, withTitle = false)
        val measure = TileTextMeasure.labelSmall(1f)
        val english = RowAConfirmation.text("Both", "Both 14:32", DOT_SEPARATOR, "Undo", width, measure)
        assertTrue(english, english.endsWith("Undo"))
        val danish = RowAConfirmation.text("Begge", "Begge 14:32", DOT_SEPARATOR, "Fortryd", width, measure)
        assertTrue(danish, measure.widthDp(danish) <= width)
        assertTrue(danish, danish.startsWith("Begge"))
    }
}
