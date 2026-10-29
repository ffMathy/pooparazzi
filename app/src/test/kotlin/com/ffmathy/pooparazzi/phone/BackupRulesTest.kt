package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.account.EmailLinkPrefs
import com.ffmathy.pooparazzi.phone.account.FamilyPrefs
import com.ffmathy.pooparazzi.phone.account.LinkPurpose
import com.ffmathy.pooparazzi.phone.account.SentLink
import com.ffmathy.pooparazzi.phone.account.WelcomePrefs
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
 * published-previews version would stop the widget previews from being published there. The settings, which hold the
 * babies' names, never go to Android's cloud backup (with an account they are in the family's backup, BABIES_AND_BACKUP
 * §1), and the backup's bookkeeping goes nowhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupRulesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** The files of [domain] the rules exclude, for each section of [xml]. */
    private fun excluded(xml: Int, domain: String): Map<String, Set<String>> {
        val parser = app.resources.getXml(xml)
        val result = mutableMapOf<String, MutableSet<String>>()
        var section = "root"
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name
                "exclude" -> if (parser.getAttributeValue(null, "domain") == domain) {
                    result.getOrPut(section) { mutableSetOf() } += parser.getAttributeValue(null, "path")
                }
            }
        }
        return result
    }

    private fun excludedPrefs(xml: Int) = excluded(xml, "sharedpref")

    private fun prefsFile(name: String) = File(app.dataDir, "shared_prefs/$name")

    @Test
    fun theSurfaceFlagsStayOnThisPhone() {
        // The file the surface flags really live in.
        SurfacePrefs(app).widgetPreviewsVersion = 1
        assertTrue(prefsFile("${SurfacePrefs.FILE}.xml").exists())
        // And the Welcome's answer (BABIES_AND_BACKUP §3.1: remembered on this phone only).
        WelcomePrefs(app).seen = true
        assertTrue(prefsFile("${WelcomePrefs.FILE}.xml").exists())

        // The email link this phone asked for, and the cached family (BABIES_AND_BACKUP §3.3, §4.1).
        EmailLinkPrefs(app).sent = SentLink("parent@example.com", LinkPurpose.SIGN_IN)
        assertTrue(prefsFile("${EmailLinkPrefs.FILE}.xml").exists())
        FamilyPrefs(app).setFamily("uid", "fam")
        assertTrue(prefsFile("${FamilyPrefs.FILE}.xml").exists())

        val local = setOf(
            "${SurfacePrefs.FILE}.xml", "${PrefsWidgetMemoStore.FILE}.xml", "${WelcomePrefs.FILE}.xml",
            "${EmailLinkPrefs.FILE}.xml", "${FamilyPrefs.FILE}.xml",
        )
        val rules = excludedPrefs(R.xml.data_extraction_rules)
        assertEquals(local, rules["cloud-backup"])
        assertEquals(local, rules["device-transfer"])
        assertEquals(local, excludedPrefs(R.xml.backup_rules_legacy)["root"])
    }

    @Test
    fun theNamesNeverGoToTheCloudAndTheBackupBookkeepingStaysHere() {
        // Where core's DataStores keep their files (preferencesDataStore: files/datastore/<name>.preferences_pb).
        val settings = "datastore/pooparazzi_settings.preferences_pb"
        val cloud = "datastore/pooparazzi_cloud.preferences_pb"
        assertEquals(File(app.filesDir, settings), app.preferencesDataStoreFile("pooparazzi_settings"))

        val rules = excluded(R.xml.data_extraction_rules, "file")
        assertEquals("the names stay out of Android's cloud backup", setOf(settings, cloud), rules["cloud-backup"])
        assertEquals("a transfer to the new phone may carry them", setOf(cloud), rules["device-transfer"])
        // API 30 cannot tell the two apart: the names stay out of both.
        assertEquals(setOf(settings, cloud), excluded(R.xml.backup_rules_legacy, "file")["root"])
    }
}
