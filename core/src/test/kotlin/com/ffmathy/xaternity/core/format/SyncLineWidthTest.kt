package com.ffmathy.xaternity.core.format

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.R
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.sync.SyncStatus
import com.ffmathy.xaternity.core.ui.type.PhoneType
import com.ffmathy.xaternity.core.ui.type.WatchType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The sync line fits its slot in English and Danish (DESIGN_SPEC §3.3.1 top bar, §3.5.6 watch Logged line):
 * - phone: no line (including the error retry button, which may overlap only non-interactive content) is wider
 *   than the glossary's own "Synced with watch · 14:31" / "Synkroniseret med ur · 14:31", which the top bar must
 *   hold anyway (the column beside the History pill and Settings is about 160–185 dp);
 * - watch: the Logged line (y 132–146 on a 228 dp round screen, about 200 dp) less the 14 dp icon and 4 dp gap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "xxhdpi")
class SyncLineWidthTest {
    private val base: Context = ApplicationProvider.getApplicationContext()
    private val density = Density(base)
    private val measurer = TextMeasurer(createFontFamilyResolver(base), density, LayoutDirection.Ltr)

    private fun context(language: String): Context =
        base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })

    private fun widthDp(text: String, style: TextStyle): Float = measurer.measure(text, style).size.width / density.density

    private val watchBudget = 182f

    private fun lines(context: Context, peer: SyncPeer): Map<SyncStatus, String> {
        val f = Formatters.from(context, SampleData.ZONE)
        return listOf(SyncStatus.Synced(SampleData.SYNCED_1431), SyncStatus.Pending, SyncStatus.Offline, SyncStatus.Error)
            .associateWith { f.labels.syncLine(it, peer, f.clock, SampleData.ZONE)!! }
    }

    @Test
    fun phoneLinesAreNoWiderThanTheGlossarySyncedLine() {
        val report = StringBuilder()
        var ok = true
        for (language in listOf("en", "da")) {
            val context = context(language)
            val glossary = String.format(context.getString(R.string.sync_synced_with_watch), "14:31")
            val budget = widthDp(glossary, PhoneType.syncLabel) + 1f
            for ((status, line) in lines(context, SyncPeer.WATCH)) {
                if (status is SyncStatus.Synced) continue
                val width = widthDp(line, PhoneType.syncLabel)
                report.append("$language \"$line\" ${"%.1f".format(width)} / ${"%.1f".format(budget)} dp\n")
                if (width > budget) ok = false
            }
        }
        println(report)
        assertTrue("Phone sync lines too wide:\n$report", ok)
    }

    @Test
    fun watchLinesFitTheLoggedScreen() {
        val report = StringBuilder()
        var ok = true
        for (language in listOf("en", "da")) {
            for ((status, line) in lines(context(language), SyncPeer.PHONE)) {
                if (status is SyncStatus.Pending) continue // not shown on the watch (§4.5)
                val width = widthDp(line, WatchType.bodySmall)
                report.append("$language \"$line\" ${"%.1f".format(width)} / $watchBudget dp\n")
                if (width > watchBudget) ok = false
            }
        }
        println(report)
        assertTrue("Watch sync lines too wide:\n$report", ok)
    }
}
