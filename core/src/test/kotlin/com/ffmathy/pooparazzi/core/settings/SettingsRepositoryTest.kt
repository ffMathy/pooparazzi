package com.ffmathy.pooparazzi.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.model.Baby
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

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb").also { it.delete() } }
        repo = DataStoreSettingsRepository(store, clock, sync)
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
        assertEquals(Settings(named("Noa"), Appearance.NIGHT, AppLanguage.DA, danish.updatedAtMillis), repo.settings.first())
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
        assertTrue(json, json.contains("""[{"id":"default","name":"Noa"},{"id":"b2","name":"Ida"}]"""))
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
        assertEquals(
            Settings(listOf(Baby(Baby.FIRST_ID, "Noah"), Baby(ida.id, "Ida")), Appearance.NIGHT, AppLanguage.DA, local.updatedAtMillis + 5),
            repo.current(),
        )
        // A newer one with an empty name never erases a name.
        val blank = SettingsJson.decodeRemoteOrNull("""{"babyName":"","appearance":"DAY","updatedAtMillis":${local.updatedAtMillis + 6}}""")!!
        assertTrue(repo.applyLegacyRemote(blank.settings))
        assertEquals(listOf(Baby(Baby.FIRST_ID, "Noah"), Baby(ida.id, "Ida")), repo.current().babies)
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
        assertTrue(afterAdd.hasSeveralBabies)
        assertEquals("adding does not switch", Baby.FIRST_ID, afterAdd.selectedBabyId)
        assertEquals(clock.now(), afterAdd.updatedAtMillis)
        assertEquals(afterAdd, sync.pushedSettings.last())

        // Renaming any baby is a synced change; the current baby's name is babyName.
        val renamed = repo.renameBaby(second.id, "  Ida ")
        assertEquals("Ida", renamed.babies[1].name)
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
        assertEquals(listOf(Baby(Baby.FIRST_ID), Baby(second.id, "Alma")), named.babies)
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
}
