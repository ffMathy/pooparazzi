package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
import com.ffmathy.pooparazzi.phone.widget.PrefsWidgetMemoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Backup and device transfer leave the system surfaces' device-local flags behind: restored on a new phone, the
 * published-previews version would stop the widget previews from being published there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupRulesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** The shared-preference files the rules name, for each section of [xml]. */
    private fun excludedPrefs(xml: Int): Map<String, Set<String>> {
        val parser = app.resources.getXml(xml)
        val result = mutableMapOf<String, MutableSet<String>>()
        var section = "root"
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name
                "exclude" -> if (parser.getAttributeValue(null, "domain") == "sharedpref") {
                    result.getOrPut(section) { mutableSetOf() } += parser.getAttributeValue(null, "path")
                }
            }
        }
        return result
    }

    private fun prefsFile(name: String) = File(app.dataDir, "shared_prefs/$name")

    @Test
    fun theSurfaceFlagsStayOnThisPhone() {
        // The file the surface flags really live in.
        SurfacePrefs(app).widgetPreviewsVersion = 1
        assertTrue(prefsFile("${SurfacePrefs.FILE}.xml").exists())

        val local = setOf("${SurfacePrefs.FILE}.xml", "${PrefsWidgetMemoStore.FILE}.xml")
        val rules = excludedPrefs(R.xml.data_extraction_rules)
        assertEquals(local, rules["cloud-backup"])
        assertEquals(local, rules["device-transfer"])
        assertEquals(local, excludedPrefs(R.xml.backup_rules_legacy)["root"])
    }
}
