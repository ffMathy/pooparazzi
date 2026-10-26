package com.ffmathy.xaternity.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ffmathy.xaternity.core.data.FixedClock
import com.ffmathy.xaternity.core.sync.FakeEntrySync
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
    private lateinit var repo: DataStoreSettingsRepository

    @Before
    fun setUp() {
        val store = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb").also { it.delete() } }
        repo = DataStoreSettingsRepository(store, clock, sync)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaults() = runBlocking {
        assertEquals(Settings(), repo.current())
        assertEquals("", repo.current().babyName)
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
        assertEquals(Settings("Noa", Appearance.NIGHT, AppLanguage.DA, danish.updatedAtMillis), repo.settings.first())
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
        assertFalse(repo.applyRemote(Settings("Old", updatedAtMillis = local.updatedAtMillis - 1)))
        assertEquals("Noa", repo.current().babyName)
        val remote = Settings("Ida", Appearance.DAY, AppLanguage.DA, local.updatedAtMillis + 10)
        assertTrue(repo.applyRemote(remote))
        assertEquals(remote, repo.current())
        assertFalse("the same settings again change nothing", repo.applyRemote(remote))
        assertTrue(sync.pushedSettings.isEmpty())
    }

    @Test
    fun json() {
        val settings = Settings("Noa", Appearance.DAY, AppLanguage.DA, 42)
        assertEquals(settings, SettingsJson.decodeOrNull(SettingsJson.encode(settings)))
        assertEquals(Settings("x", Appearance.AUTO, AppLanguage.SYSTEM, 1), SettingsJson.decodeOrNull("""{"babyName":"x","appearance":"SEPIA","language":"FR","updatedAtMillis":1}"""))
        assertNull(SettingsJson.decodeOrNull("nope"))
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
