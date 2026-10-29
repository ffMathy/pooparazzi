package com.ffmathy.pooparazzi.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BabyJson
import com.ffmathy.pooparazzi.core.sync.FakeEntrySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = FixedClock(1_000_000L)
    private val sync = FakeEntrySync()
    private lateinit var store: DataStore<Preferences>
    private lateinit var repo: DataStoreSettingsRepository

    /** Whether an account is signed in, for the babies' backup flags. */
    private var signedIn = false

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb").also { it.delete() } }
        repo = DataStoreSettingsRepository(store, clock, sync, cloudBackup = { signedIn })
    }

    private fun named(name: String) = listOf(Baby(Baby.FIRST_ID, name))

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaults() = runBlocking {
        assertEquals(Settings(), repo.current())
        assertEquals(listOf(Baby(Baby.FIRST_ID)), repo.current().babies)
        assertEquals(Baby.FIRST_ID, repo.current().selectedBaby.id)
        assertEquals("", repo.current().babyName)
        assertFalse(repo.current().hasSeveralBabies)
        assertEquals(Appearance.AUTO, repo.current().appearance)
        assertEquals(AppLanguage.SYSTEM, repo.current().language)
    }

    @Test
    fun localChangesAreStampedAndMirrored() = runBlocking {
        val named = repo.setBabyName("  Noa \n ")
        assertEquals("Noa", named.babyName)
        assertEquals(clock.now(), named.updatedAtMillis)
        val dark = repo.setAppearance(Appearance.NIGHT)
        val danish = repo.setLanguage(AppLanguage.DA)
        assertTrue(dark.updatedAtMillis > named.updatedAtMillis)
        val stampedNoa = listOf(Baby(Baby.FIRST_ID, "Noa", named.updatedAtMillis))
        assertEquals("the rename stamps the baby", stampedNoa, named.babies)
        assertEquals(Settings(stampedNoa, Appearance.NIGHT, AppLanguage.DA, danish.updatedAtMillis), repo.settings.first())
        assertEquals(listOf(named, dark, danish), sync.pushedSettings)
    }

    @Test
    fun unchangedValuesAreNotWrittenOrPushed() = runBlocking {
        repo.setLanguage(AppLanguage.EN)
        val again = repo.setLanguage(AppLanguage.EN)
        assertEquals(1, sync.pushedSettings.size)
        assertEquals(sync.pushedSettings.single(), again)
    }

    @Test
    fun remoteSettingsAreLastWriterWinsWithoutEcho() = runBlocking {
        val local = repo.setBabyName("Noa")
        sync.pushedSettings.clear()
        assertFalse(repo.applyRemote(Settings(named("Old"), updatedAtMillis = local.updatedAtMillis - 1)))
        assertEquals("Noa", repo.current().babyName)
        val remote = Settings(named("Ida"), Appearance.DAY, AppLanguage.DA, local.updatedAtMillis + 10)
        assertTrue(repo.applyRemote(remote))
        assertEquals(remote, repo.current())
        assertFalse("the same settings again change nothing", repo.applyRemote(remote))
        assertTrue(sync.pushedSettings.isEmpty())
    }

    @Test
    fun json() {
        val settings = Settings(named("Noa"), Appearance.DAY, AppLanguage.DA, 42)
        assertEquals(settings, SettingsJson.decodeOrNull(SettingsJson.encode(settings)))
        assertEquals(Settings(named("x"), Appearance.AUTO, AppLanguage.SYSTEM, 1), SettingsJson.decodeOrNull("""{"babyName":"x","appearance":"SEPIA","language":"FR","updatedAtMillis":1}"""))
        assertNull(SettingsJson.decodeOrNull("nope"))
    }

    @Test
    fun jsonCarriesTheBabiesInOrderButNeverTheCurrentBaby() {
        val twins = Settings(listOf(Baby(Baby.FIRST_ID, "Noa"), Baby("b2", "Ida")), updatedAtMillis = 7, selectedBabyId = "b2")
        val json = SettingsJson.encode(twins)
        assertTrue(json, json.contains("""[{"id":"default","name":"Noa","updatedAtMillis":0},{"id":"b2","name":"Ida","updatedAtMillis":0}]"""))
        assertFalse(json, json.contains("selected"))
        assertEquals(twins.copy(selectedBabyId = Baby.FIRST_ID), SettingsJson.decodeOrNull(json))
    }

    @Test
    fun jsonFromAnOlderDeviceIsTheFirstBaby() {
        // Before several babies: one name, no list. Both devices derive the same id, so nothing diverges.
        val old = SettingsJson.decodeOrNull("""{"babyName":"  Noa  ","appearance":"NIGHT","language":"DA","updatedAtMillis":5}""")
        assertEquals(Settings(named("Noa"), Appearance.NIGHT, AppLanguage.DA, 5), old)
        assertEquals("Noa", old!!.babyName)
        // Without a name either, it is the unnamed first baby; with a list, a stray babyName is ignored.
        assertEquals(Settings(updatedAtMillis = 3), SettingsJson.decodeOrNull("""{"updatedAtMillis":3}"""))
        assertEquals(
            named("Ida"),
            SettingsJson.decodeOrNull("""{"babyName":"Old","babies":[{"id":"default","name":"Ida"}]}""")!!.babies,
        )
    }

    @Test
    fun anOlderDeviceEchoingOurSettingsBackKeepsEveryBabyAndName() = runBlocking {
        repo.setBabyName("Noa")
        val ida = repo.addBaby()
        val local = repo.renameBaby(ida.id, "Ida")
        sync.pushedSettings.clear()

        // The JSON still carries the first baby's name for devices from before several babies.
        val json = SettingsJson.encode(local)
        assertTrue(json, json.contains(""""babyName":"Noa""""))

        // Such a device sends back what it understood (one name, the same stamp) or an empty name: nothing changes.
        for (echo in listOf(
            """{"babyName":"Noa","appearance":"AUTO","language":"SYSTEM","updatedAtMillis":${local.updatedAtMillis}}""",
            """{"babyName":"","appearance":"AUTO","language":"SYSTEM","updatedAtMillis":${local.updatedAtMillis}}""",
            """{"babyName":"Old","appearance":"DAY","language":"DA","updatedAtMillis":${local.updatedAtMillis - 1}}""",
        )) {
            val remote = SettingsJson.decodeRemoteOrNull(echo)!!
            assertTrue(remote.legacy)
            assertFalse(echo, repo.applyLegacyRemote(remote.settings))
            assertEquals(local, repo.current())
        }

        // A newer change from it renames the first baby and takes the appearance; every other baby is kept.
        val newer = SettingsJson.decodeRemoteOrNull(
            """{"babyName":"Noah","appearance":"NIGHT","language":"DA","updatedAtMillis":${local.updatedAtMillis + 5}}""",
        )!!
        assertTrue(repo.applyLegacyRemote(newer.settings))
        // The name is stamped with the remote settings' stamp, so it goes on to the family like a rename here.
        val noah = Baby(Baby.FIRST_ID, "Noah", local.updatedAtMillis + 5)
        val idaNamed = local.babies[1]
        assertEquals(
            Settings(listOf(noah, idaNamed), Appearance.NIGHT, AppLanguage.DA, local.updatedAtMillis + 5),
            repo.current(),
        )
        // A newer one with an empty name never erases a name.
        val blank = SettingsJson.decodeRemoteOrNull("""{"babyName":"","appearance":"DAY","updatedAtMillis":${local.updatedAtMillis + 6}}""")!!
        assertTrue(repo.applyLegacyRemote(blank.settings))
        assertEquals(listOf(noah, idaNamed), repo.current().babies)
        assertEquals(Appearance.DAY, repo.current().appearance)
        assertTrue(sync.pushedSettings.isEmpty())

        // Settings in the current form are not legacy.
        assertFalse(SettingsJson.decodeRemoteOrNull(SettingsJson.encode(local))!!.legacy)
    }

    @Test
    fun normalisation() {
        val messy = Settings(
            listOf(Baby(" ", "Blank"), Baby("a", "  Noa \n Lou "), Baby("b"), Baby("a", "Twice"), Baby("c", "x".repeat(60))),
        )
        assertEquals(
            listOf(Baby("a", "Noa Lou"), Baby("b"), Baby("c", "x".repeat(Settings.MAX_BABY_NAME_LENGTH))),
            messy.normalized().babies,
        )
        assertEquals(listOf(Baby(Baby.FIRST_ID)), Settings(babies = emptyList()).normalized().babies)
        assertEquals("kept as is", "b", Settings(selectedBabyId = "b").normalized().selectedBabyId)
        assertEquals(listOf(Baby(Baby.FIRST_ID)), SettingsJson.decodeOrNull("""{"babies":[]}""")!!.babies)
        // An unknown current baby falls back to the first.
        assertEquals(Baby("a", "Noa Lou"), messy.normalized().copy(selectedBabyId = "zz").selectedBaby)
    }

    @Test
    fun anInstallFromBeforeSeveralBabiesKeepsItsName() = runBlocking {
        store.edit {
            it[stringPreferencesKey("baby_name")] = "Noa"
            it[stringPreferencesKey("appearance")] = "NIGHT"
        }
        assertEquals(named("Noa"), repo.current().babies)
        assertEquals("Noa", repo.current().babyName)
        assertEquals(Appearance.NIGHT, repo.current().appearance)

        // The first write of the babies replaces the old key.
        val added = repo.addBaby()
        val prefs = store.data.first()
        assertNull(prefs[stringPreferencesKey("baby_name")])
        assertEquals(listOf(Baby(Baby.FIRST_ID, "Noa"), added), repo.current().babies)
    }

    @Test
    fun addRenameAndSelect() = runBlocking {
        val second = repo.addBaby()
        assertTrue(second.id.isNotBlank() && second.id != Baby.FIRST_ID)
        assertEquals("", second.name)
        val afterAdd = repo.current()
        assertEquals(listOf(Baby(Baby.FIRST_ID), second), afterAdd.babies)
        assertEquals("a new baby is stamped", afterAdd.updatedAtMillis, second.updatedAtMillis)
        assertTrue(afterAdd.hasSeveralBabies)
        assertEquals("adding does not switch", Baby.FIRST_ID, afterAdd.selectedBabyId)
        assertEquals(clock.now(), afterAdd.updatedAtMillis)
        assertEquals(afterAdd, sync.pushedSettings.last())

        // Renaming any baby is a synced change; the current baby's name is babyName.
        val renamed = repo.renameBaby(second.id, "  Ida ")
        assertEquals("Ida", renamed.babies[1].name)
        assertEquals(renamed.updatedAtMillis, renamed.babies[1].updatedAtMillis)
        assertTrue(renamed.updatedAtMillis > afterAdd.updatedAtMillis)
        assertEquals(renamed, sync.pushedSettings.last())
        assertEquals("", renamed.babyName)
        val pushes = sync.pushedSettings.size
        assertEquals(renamed, repo.renameBaby("unknown", "X"))
        assertEquals(pushes, sync.pushedSettings.size)

        // Selecting is local: no stamp and no push.
        val selected = repo.selectBaby(second.id)
        assertEquals(second.id, selected.selectedBabyId)
        assertEquals("Ida", selected.babyName)
        assertEquals(renamed.updatedAtMillis, selected.updatedAtMillis)
        assertEquals(pushes, sync.pushedSettings.size)
        assertEquals(selected, repo.settings.first())
        assertEquals("unknown ids change nothing", selected, repo.selectBaby("unknown"))

        // setBabyName renames the current baby.
        val named = repo.setBabyName("Alma")
        assertEquals(listOf(Baby(Baby.FIRST_ID), Baby(second.id, "Alma", named.updatedAtMillis)), named.babies)
        assertEquals(second.id, named.selectedBabyId)
        assertEquals(named, sync.pushedSettings.last())
    }

    @Test
    fun atMostEightBabiesCanBeAdded() = runBlocking {
        repeat(Settings.MAX_BABIES - 1) { repo.addBaby() }
        val full = repo.current()
        assertEquals(Settings.MAX_BABIES, full.babies.size)
        val pushes = sync.pushedSettings.size
        assertEquals("the last baby comes back", full.babies.last(), repo.addBaby())
        assertEquals(full, repo.current())
        assertEquals(pushes, sync.pushedSettings.size)
    }

    @Test
    fun ensureBabiesAppendsOnlyUnknownIds() = runBlocking {
        val pushes = sync.pushedSettings.size
        assertEquals(Settings(), repo.ensureBabies(listOf(Baby.FIRST_ID, "")))
        assertEquals("nothing new: no write, no push", pushes, sync.pushedSettings.size)

        val ids = (1..9).map { "restored-$it" }
        val ensured = repo.ensureBabies(listOf(Baby.FIRST_ID) + ids + "restored-1")
        assertEquals(listOf(Baby.FIRST_ID) + ids, ensured.babies.map { it.id })
        assertTrue("not limited: no entry is left without its baby", ensured.babies.size > Settings.MAX_BABIES)
        assertTrue(ensured.babies.all { it.name.isEmpty() })
        assertEquals(clock.now(), ensured.updatedAtMillis)
        assertEquals(ensured, sync.pushedSettings.last())
        assertEquals(ensured, repo.ensureBabies(ids))
    }

    @Test
    fun remoteSettingsNeverChangeTheCurrentBaby() = runBlocking {
        val second = repo.addBaby()
        repo.selectBaby(second.id)
        val local = repo.current()
        sync.pushedSettings.clear()

        // The same settings with another current baby (as the other device has them) are no change.
        assertFalse(repo.applyRemote(local.copy(selectedBabyId = Baby.FIRST_ID)))
        assertEquals(local, repo.current())

        // A newer rename from the other device is taken; the current baby stays this device's.
        val remote = local.copy(
            babies = listOf(Baby(Baby.FIRST_ID, "Noa"), Baby(second.id, "  Ida ")),
            updatedAtMillis = local.updatedAtMillis + 5,
            selectedBabyId = Baby.FIRST_ID,
        )
        assertTrue(repo.applyRemote(remote))
        val merged = repo.current()
        assertEquals(listOf(Baby(Baby.FIRST_ID, "Noa"), Baby(second.id, "Ida")), merged.babies)
        assertEquals(second.id, merged.selectedBabyId)
        assertEquals("Ida", merged.babyName)
        assertTrue(sync.pushedSettings.isEmpty())

        // A list without this device's current baby: the first one is current until it comes back.
        assertTrue(repo.applyRemote(Settings(named("Noa"), updatedAtMillis = merged.updatedAtMillis + 1)))
        assertEquals(Baby.FIRST_ID, repo.current().selectedBabyId)
        assertTrue(repo.applyRemote(merged.copy(updatedAtMillis = merged.updatedAtMillis + 2)))
        assertEquals(second.id, repo.current().selectedBabyId)
    }

    @Test
    fun languageTags() {
        assertEquals(AppLanguage.DA, AppLanguage.fromTag("da-DK"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertNull(AppLanguage.SYSTEM.tag)
    }

    // ---- The family's backup ------------------------------------------------------------------------

    @Test
    fun babiesAreFlaggedForTheBackupOnlyWhileSignedIn() = runBlocking {
        repo.setBabyName("Noa")
        repo.addBaby()
        assertTrue("signed out, nothing is flagged", repo.cloudPendingBabies().isEmpty())

        signedIn = true
        val named = repo.setBabyName("Noah")
        val ida = repo.addBaby()
        assertEquals(listOf(named.babies[0], ida), repo.cloudPendingBabies())
        // The appearance, the language and the current baby are no babies' change.
        repo.markBabiesCloudSynced(repo.cloudPendingBabies())
        repo.setAppearance(Appearance.NIGHT)
        repo.setLanguage(AppLanguage.DA)
        repo.selectBaby(ida.id)
        assertTrue(repo.cloudPendingBabies().isEmpty())

        // Adopted babies carry nothing the family lacks: never flagged, and unstamped.
        val adopted = repo.ensureBabies(listOf("from-entries"))
        assertEquals(Baby("from-entries"), adopted.babies.last())
        assertTrue(repo.cloudPendingBabies().isEmpty())

        // Every baby (the first sync for a family), and none (signed out).
        repo.markAllBabiesForCloud()
        assertEquals(repo.current().babies, repo.cloudPendingBabies())
        repo.clearBabiesCloudPending()
        assertTrue(repo.cloudPendingBabies().isEmpty())
    }

    @Test
    fun aBabyRenamedWhileItsUploadIsOnItsWayStaysFlagged() = runBlocking {
        signedIn = true
        val ida = repo.addBaby()
        val seen = mutableListOf<List<Baby>>()
        val uploading = repo.cloudPendingBabies()
        assertEquals(listOf(ida), uploading)
        seen += repo.observeCloudPendingBabies().first()

        val renamed = repo.renameBaby(ida.id, "Ida").babies.last()
        seen += repo.observeCloudPendingBabies().first()
        assertEquals("a write to a flagged baby is a change too", listOf(listOf(ida), listOf(renamed)), seen)
        assertEquals("the upload of the old version clears nothing", 0, repo.markBabiesCloudSynced(uploading))
        assertEquals(listOf(renamed), repo.cloudPendingBabies())
        assertEquals(1, repo.markBabiesCloudSynced(listOf(renamed)))
        assertTrue(repo.cloudPendingBabies().isEmpty())
    }

    @Test
    fun babiesFromTheBackupMergeOneByOneAndReachTheWatch() = runBlocking {
        signedIn = true
        val local = repo.setBabyName("Noa")
        val ida = repo.addBaby()
        repo.markBabiesCloudSynced(repo.cloudPendingBabies())
        val before = repo.current()
        sync.pushedSettings.clear()

        val stamp = before.updatedAtMillis
        val incoming = listOf(
            // Older than the stored first baby: the stored one wins and goes up.
            Baby(Baby.FIRST_ID, "Old", local.updatedAtMillis - 1),
            // Newer than the stored second baby: taken.
            Baby(ida.id, "  Ida ", stamp + 10),
            // Unknown: appended.
            Baby("alma", "Alma", stamp + 20),
        )
        assertEquals(2, repo.applyCloudBabies(incoming))
        val merged = repo.current()
        assertEquals(
            listOf(before.babies[0], Baby(ida.id, "Ida", stamp + 10), Baby("alma", "Alma", stamp + 20)),
            merged.babies,
        )
        assertEquals(listOf(before.babies[0]), repo.cloudPendingBabies())
        assertTrue("a stamped settings change", merged.updatedAtMillis > before.updatedAtMillis)
        assertEquals("mirrored to the watch", listOf(merged), sync.pushedSettings)
        assertEquals(merged.babies, SettingsJson.decodeOrNull(SettingsJson.encode(sync.pushedSettings.last()))!!.babies)

        // The same babies again change nothing: no write, no push. The backup holding the stored first baby clears it.
        assertEquals(0, repo.applyCloudBabies(merged.babies))
        assertEquals(merged, repo.current())
        assertEquals(1, sync.pushedSettings.size)
        assertTrue(repo.cloudPendingBabies().isEmpty())

        // Signed out, a stored version that wins is not flagged.
        signedIn = false
        repo.applyCloudBabies(listOf(Baby(Baby.FIRST_ID, "Older", 1)))
        assertTrue(repo.cloudPendingBabies().isEmpty())
    }

    @Test
    fun equalStampsSettleOnTheSameNameEverywhere() {
        val a = Baby(Baby.FIRST_ID, "Noa", 5)
        val b = Baby(Baby.FIRST_ID, "Ida", 5)
        assertTrue(a.winsOver(b))
        assertFalse(b.winsOver(a))
        assertFalse(a.winsOver(a))
        assertTrue("a name beats no name", Baby(Baby.FIRST_ID, "Noa").winsOver(Baby(Baby.FIRST_ID)))
        assertTrue(Baby(Baby.FIRST_ID, "", 6).winsOver(a))
        assertEquals(listOf(a), Settings(listOf(b)).withMergedBabies(listOf(a)).babies)
        assertEquals(listOf(a), Settings(listOf(a)).withMergedBabies(listOf(b)).babies)
    }

    @Test
    fun aRenameWinsEvenWhenThisPhonesClockIsBehindTheBabysStamp() = runBlocking {
        // Another member's phone, its clock ahead, named the baby; this phone renames it a moment later.
        repo.applyCloudBabies(listOf(Baby(Baby.FIRST_ID, "Noa", clock.now() + 60_000)))
        val renamed = repo.setBabyName("Noah").babies.single()
        assertEquals(clock.now() + 60_001, renamed.updatedAtMillis)
        assertTrue(renamed.winsOver(Baby(Baby.FIRST_ID, "Noa", clock.now() + 60_000)))
    }

    @Test
    fun settingsFromTheWatchThatChangeABabyFlagIt() = runBlocking {
        // A reinstalled phone: the watch still has the names.
        signedIn = true
        val fromWatch = Settings(listOf(Baby(Baby.FIRST_ID, "Noa", 7), Baby("b2", "Ida", 8)), updatedAtMillis = 9)
        assertTrue(repo.applyRemote(fromWatch))
        assertEquals(fromWatch.babies, repo.cloudPendingBabies())
        repo.markBabiesCloudSynced(fromWatch.babies)

        // The same babies with a newer appearance flag nothing.
        assertTrue(repo.applyRemote(fromWatch.copy(appearance = Appearance.DAY, updatedAtMillis = 10)))
        assertTrue(repo.cloudPendingBabies().isEmpty())
    }

    @Test
    fun babiesFromBeforeFamiliesAreUnstampedAndTheFlagsNeverReachTheWatch() = runBlocking {
        store.edit { it[stringPreferencesKey("babies")] = """[{"id":"default","name":"Noa"},{"id":"b2"}]""" }
        assertEquals(listOf(Baby(Baby.FIRST_ID, "Noa", 0), Baby("b2", "", 0)), repo.current().babies)
        assertEquals(
            listOf(Baby(Baby.FIRST_ID, "Noa", 0)),
            SettingsJson.decodeOrNull("""{"babies":[{"id":"default","name":"Noa"}]}""")!!.babies,
        )

        signedIn = true
        repo.markAllBabiesForCloud()
        val json = SettingsJson.encode(repo.current())
        assertFalse(json, json.contains("pending", ignoreCase = true))
        assertEquals(2, repo.cloudPendingBabies().size)
    }

    @Test
    fun babyJson() {
        val baby = Baby("b2", "Ida", 42)
        assertEquals("""{"id":"b2","name":"Ida","updatedAtMillis":42}""", BabyJson.encode(baby))
        assertEquals(baby, BabyJson.decodeOrNull(BabyJson.encode(baby)))
        assertEquals(Baby("b2"), BabyJson.decodeOrNull("""{"id":"b2","future":true}"""))
        assertNull(BabyJson.decodeOrNull("""{"id":" "}"""))
        assertNull(BabyJson.decodeOrNull("""{"name":"x"}"""))
        assertNull(BabyJson.decodeOrNull("nope"))
    }
}
