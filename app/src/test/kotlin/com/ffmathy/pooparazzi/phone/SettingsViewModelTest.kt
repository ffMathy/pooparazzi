package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.ffmathy.pooparazzi.core.export.LogExport
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
import com.ffmathy.pooparazzi.phone.app.LocaleApplier
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.phone.ui.settings.AccountSummary
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import com.ffmathy.pooparazzi.core.model.EntryJson
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings persist the appearance, the language (applied and synced) and the babies' names, and add babies (V1_ADDITIONS
 * §5, §7; BABIES_AND_BACKUP §2.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SettingsViewModelTest : CoreTestBase() {

    private val applied = mutableListOf<AppLanguage>()

    private fun TestScope.settings(core: CoreTestRig): SettingsViewModel =
        SettingsViewModel(core.settings, NoopEntrySync, LocaleApplier { applied += it }, backgroundScope, versionName = "0.1.0")
            .also { core.collect(it.state) }

    @Test
    fun appearanceIsPersisted() = coreTest { core ->
        val vm = settings(core)
        assertEquals(Appearance.AUTO, vm.state.value!!.appearance)
        vm.onAppearance(Appearance.NIGHT)
        runCurrent()
        assertEquals(Appearance.NIGHT, core.settings.current().appearance)
        assertEquals(Appearance.NIGHT, vm.state.value!!.appearance)
    }

    @Test
    fun languageIsPersistedThenApplied() = coreTest { core ->
        val vm = settings(core)
        vm.onLanguage(AppLanguage.DA)
        runCurrent()
        assertEquals(AppLanguage.DA, core.settings.current().language)
        assertEquals(listOf(AppLanguage.DA), applied)

        vm.onLanguage(AppLanguage.SYSTEM)
        runCurrent()
        assertEquals(AppLanguage.SYSTEM, core.settings.current().language)
        assertEquals(listOf(AppLanguage.DA, AppLanguage.SYSTEM), applied)
    }

    private fun SettingsViewModel.name(id: String = Baby.FIRST_ID): String = state.value!!.babies.single { it.id == id }.name

    private suspend fun CoreTestRig.name(id: String = Baby.FIRST_ID): String = settings.current().babies.single { it.id == id }.name

    @Test
    fun theNameIsSavedWhenTypingStops() = coreTest { core ->
        val vm = settings(core)
        assertEquals("Noa", vm.name())
        vm.onBabyNameChange(Baby.FIRST_ID, "Ma")
        advanceTimeBy(200)
        vm.onBabyNameChange(Baby.FIRST_ID, "Maja")
        runCurrent()
        assertEquals("the field shows the typing at once", "Maja", vm.name())
        assertEquals("not saved while typing", "Noa", core.settings.current().babyName)

        advanceTimeBy(SettingsViewModel.NAME_DEBOUNCE_MILLIS + 1)
        assertEquals("Maja", core.settings.current().babyName)
    }

    @Test
    fun doneSavesTheNameAtOnce() = coreTest { core ->
        val vm = settings(core)
        vm.onBabyNameChange(Baby.FIRST_ID, "Ida")
        vm.onBabyNameDone(Baby.FIRST_ID)
        runCurrent()
        assertEquals("Ida", core.settings.current().babyName)
    }

    @Test
    fun aNameTypedJustBeforeLeavingIsStillSaved() = coreTest { core ->
        val store = ViewModelStore()
        val vm = settings(core)
        store.put("settings", vm)
        vm.onBabyNameChange(Baby.FIRST_ID, "Alma")
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals("Alma", core.settings.current().babyName)
    }

    @Test
    fun longNamesAreCutAtTheLimit() = coreTest { core ->
        val vm = settings(core)
        vm.onBabyNameChange(Baby.FIRST_ID, "x".repeat(60))
        runCurrent()
        assertEquals(40, vm.name().length)
    }

    /** BABIES_AND_BACKUP §2.4: one field per baby, each saved on its own, and adding one does not switch. */
    @Test
    fun eachBabysNameIsSavedOnItsOwn() = coreTest { core ->
        core.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        val vm = settings(core)
        runCurrent()
        assertEquals(listOf(Baby.FIRST_ID, SampleData.TWIN_ID), vm.state.value!!.babies.map { it.id })

        vm.onBabyNameChange(Baby.FIRST_ID, "Maja")
        advanceTimeBy(300)
        // Typing in the second field does not cancel the first field's pending write.
        vm.onBabyNameChange(SampleData.TWIN_ID, "Ida")
        runCurrent()
        assertEquals("Maja", vm.name())
        assertEquals("Ida", vm.name(SampleData.TWIN_ID))
        advanceTimeBy(250)
        assertEquals("the first name is saved 500 ms after its last keystroke", "Maja", core.name())
        assertEquals("the second is still being typed", "", core.name(SampleData.TWIN_ID))
        advanceTimeBy(SettingsViewModel.NAME_DEBOUNCE_MILLIS)
        assertEquals("Ida", core.name(SampleData.TWIN_ID))
        assertEquals("the current baby stays", Baby.FIRST_ID, core.settings.current().selectedBaby.id)
    }

    @Test
    fun doneSavesOnlyThatBabysField() = coreTest { core ->
        core.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        val vm = settings(core)
        vm.onBabyNameChange(Baby.FIRST_ID, "Maja")
        vm.onBabyNameChange(SampleData.TWIN_ID, "Ida")
        vm.onBabyNameDone(SampleData.TWIN_ID)
        runCurrent()
        assertEquals("Ida", core.name(SampleData.TWIN_ID))
        assertEquals("Noa", core.name())
        advanceTimeBy(SettingsViewModel.NAME_DEBOUNCE_MILLIS + 1)
        assertEquals("Maja", core.name())
    }

    /** A slow write of an older name never lands after the newer one. */
    @Test
    fun anOlderNameNeverOverwritesANewerOne() = coreTest { core ->
        val slow = SlowSettings(core.settings)
        val vm = SettingsViewModel(slow, NoopEntrySync, LocaleApplier { applied += it }, backgroundScope, versionName = "0.1.0")
            .also { core.collect(it.state) }
        vm.onBabyNameChange(Baby.FIRST_ID, "Ma")
        vm.onBabyNameDone(Baby.FIRST_ID)
        runCurrent()
        // "Ma" is being written (slowly) while the parent types on.
        vm.onBabyNameChange(Baby.FIRST_ID, "Maja")
        vm.onBabyNameDone(Baby.FIRST_ID)
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals(listOf("Ma", "Maja"), slow.writes)
        assertEquals("Maja", core.name())
    }

    @Test
    fun namesTypedInTwoFieldsBeforeLeavingAreBothSaved() = coreTest { core ->
        core.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        val store = ViewModelStore()
        val vm = settings(core)
        store.put("settings", vm)
        vm.onBabyNameChange(Baby.FIRST_ID, "Maja")
        vm.onBabyNameChange(SampleData.TWIN_ID, "Ida")
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals("Maja", core.name())
        assertEquals("Ida", core.name(SampleData.TWIN_ID))
    }

    @Test
    fun addingABabyAppendsAnUnnamedOneUntilTheCap() = coreTest { core ->
        val vm = settings(core)
        assertTrue(vm.state.value!!.canAddBaby)
        vm.onAddBaby()
        runCurrent()
        val babies = vm.state.value!!.babies
        assertEquals(2, babies.size)
        assertEquals("", babies.last().name)
        assertEquals("adding does not switch the current baby", Baby.FIRST_ID, core.settings.current().selectedBaby.id)

        repeat(Settings.MAX_BABIES) { vm.onAddBaby() }
        runCurrent()
        assertEquals(Settings.MAX_BABIES, vm.state.value!!.babies.size)
        assertFalse("the pill is hidden at the cap", vm.state.value!!.canAddBaby)
    }

    /** A watch from before several babies would resolve conflicts across them: no second baby until it is updated. */
    @Test
    fun anOldWatchKeepsAddABabyDisabled() = coreTest { core ->
        val vm = SettingsViewModel(
            core.settings, NoopEntrySync, LocaleApplier { applied += it }, backgroundScope, versionName = "0.1.0",
            watchNeedsUpdate = { true },
        ).also { core.collect(it.state) }
        runCurrent()
        val state = vm.state.value!!
        assertTrue(state.watchNeedsUpdate)
        assertTrue("shown, but disabled", state.canAddBaby)
        vm.onAddBaby()
        runCurrent()
        assertEquals("nothing is added", 1, core.settings.current().babies.size)

        // An up-to-date watch (or none): as before.
        assertFalse(settings(core).state.value!!.watchNeedsUpdate)
    }

    @Test
    fun removingABabyAsksFirstThenDeletesItAndItsLog() = coreTest { core ->
        val twin = core.settings.addBaby()
        val noa = Entry.feed(FeedType.FORMULA, SampleData.NOW_1432 - 30 * MINUTE)
        val ida = Entry.diaper(SampleData.NOW_1432 - 20 * MINUTE, pee = true, poop = false, babyId = twin.id)
        core.seed(listOf(noa))
        // From the other device: an entry of the twin (Add past entry would log it for the current baby).
        core.repository.applyRemote(ida)
        val before = core.repository.observeAllEntries().first().map { it.id }
        assertTrue(ida.id in before)
        val vm = SettingsViewModel(
            core.settings, NoopEntrySync, LocaleApplier { applied += it }, backgroundScope,
            versionName = "0.1.0", entries = core.repository, clock = core.clock,
        ).also { core.collect(it.state) }
        runCurrent()
        assertTrue(vm.state.value!!.canRemoveBaby)

        // Cancel removes nothing.
        vm.onRemoveBaby(twin.id)
        runCurrent()
        assertEquals(twin.id, vm.state.value!!.removing?.id)
        vm.onDismissRemoveBaby()
        runCurrent()
        assertEquals(null, vm.state.value!!.removing)
        assertEquals(2, core.settings.current().babies.size)

        // A name typed meanwhile is dropped with the baby, never written after it.
        vm.onBabyNameChange(twin.id, "Ida")
        vm.onRemoveBaby(twin.id)
        vm.onConfirmRemoveBaby()
        runCurrent()
        assertEquals(listOf(Baby.FIRST_ID), core.settings.current().babies.map { it.id })
        assertEquals(listOf(twin.id), core.settings.current().removedBabies.map { it.id })
        assertEquals(null, vm.state.value!!.removing)
        assertFalse(vm.state.value!!.canRemoveBaby)
        assertEquals("only the removed baby's log is deleted", before - ida.id, core.repository.observeAllEntries().first().map { it.id })
        assertTrue(core.entry(ida.id)!!.deleted)
    }

    @Test
    fun downloadMyDataWritesTheWholeLogAndTheNames() = coreTest { core ->
        core.settings.renameBaby(Baby.FIRST_ID, "Noa")
        val feed = Entry.feed(FeedType.FORMULA, SampleData.NOW_1432 - 30 * MINUTE)
        val twin = Entry.diaper(SampleData.NOW_1432 - 20 * MINUTE, pee = true, poop = false, babyId = "baby-2")
        val gone = Entry.feed(FeedType.FOOD, SampleData.NOW_1432 - 10 * MINUTE)
        core.seed(listOf(feed, twin, gone))
        core.repository.delete(gone.id)
        val vm = SettingsViewModel(
            core.settings, NoopEntrySync, LocaleApplier { applied += it }, backgroundScope,
            versionName = "0.1.0", entries = core.repository, clock = core.clock,
        ).also { core.collect(it.state) }
        runCurrent()
        // A name typed but not saved yet is exported as typed.
        vm.onBabyNameChange(Baby.FIRST_ID, "Noa Marie")

        val out = ByteArrayOutputStream()
        val active = core.repository.observeAllEntries().first()
        assertEquals(active.size, vm.export(out))
        val files = generateSequence(ZipInputStream(ByteArrayInputStream(out.toByteArray()))::getNextEntry).map { it.name }
        assertEquals("one ZIP file", listOf(LogExport.MANIFEST, LogExport.BABIES, LogExport.ENTRIES, LogExport.README), files.toList())
        val export = LogExport.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(LogExport.FORMAT, export.format)
        assertEquals(LogExport.VERSION, export.version)
        assertEquals(core.clock.now(), export.exportedAtMillis)
        assertEquals("Noa Marie", export.babies.first().name)
        assertEquals("every baby's entries, oldest first", active.sortedBy { it.startMillis }, export.entries)
        assertTrue(export.entries.map { it.id }.containsAll(listOf(feed.id, twin.id)))
        assertTrue("deleted ones are left out", export.entries.none { it.id == gone.id })
    }

    @Test
    fun importDataMergesAnExportIntoTheLog() = coreTest(seed = emptyList()) { core ->
        val vm = SettingsViewModel(
            core.settings, NoopEntrySync, LocaleApplier { applied += it }, backgroundScope,
            versionName = "0.1.0", entries = core.repository, clock = core.clock,
        ).also { core.collect(it.state) }
        runCurrent()
        val feed = Entry.feed(FeedType.FORMULA, SampleData.NOW_1432 - 30 * MINUTE, amountMl = 120)
        val twin = Entry.diaper(SampleData.NOW_1432 - 20 * MINUTE, pee = true, poop = false, babyId = "baby-2")
        val file = ByteArrayOutputStream().also {
            LogExport.write(it, listOf(Baby(Baby.FIRST_ID, "Lea"), Baby("baby-2", "Ida", updatedAtMillis = 1L)), listOf(feed, twin), SampleData.NOW_1432)
        }.toByteArray()

        assertEquals(2, vm.importData(ByteArrayInputStream(file)))
        runCurrent()
        assertEquals(feed, core.entry(feed.id))
        assertEquals(twin, core.entry(twin.id))
        assertEquals("the twin is added with their name", "Ida", core.name("baby-2"))
        assertEquals("the name here stays", SampleData.BABY_NAME, core.name())

        assertEquals("the same file again changes nothing", 0, vm.importData(ByteArrayInputStream(file)))
        val edited = core.repository.update(feed.copy(amountMl = 90))
        assertEquals("what is newer here stays", 0, vm.importData(ByteArrayInputStream(file)))
        assertEquals(edited, core.entry(feed.id))

        // The single JSON file of version 1 is read too.
        val v1 = """{"format":"pooparazzi-log","version":1,"exportedAtMillis":1,"babies":[],"entries":[${EntryJson.encode(feed.copy(id = "v1", startMillis = feed.startMillis - 60 * MINUTE))}]}"""
        assertEquals(1, vm.importData(ByteArrayInputStream(v1.toByteArray())))

        assertThrows(IOException::class.java) { runBlocking { vm.importData(ByteArrayInputStream("{}".toByteArray())) } }
        assertThrows(IOException::class.java) { runBlocking { vm.importData(ByteArrayInputStream("not a file".toByteArray())) } }
    }
}

/** Settings › Account (BABIES_AND_BACKUP §3.2). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SettingsAccountTest : CoreTestBase() {
    private fun TestScope.settings(core: CoreTestRig, accounts: FakeAccounts, cloud: FakeCloudSync): SettingsViewModel =
        SettingsViewModel(core.settings, NoopEntrySync, LocaleApplier {}, backgroundScope, accounts = accounts, cloud = cloud)
            .also { core.collect(it.state) }

    @Test
    fun theAccountRowSaysWhereTheLogIs() = coreTest { core ->
        assertEquals(
            "builds without a configuration",
            AccountSummary.Unavailable,
            settings(core, FakeAccounts(available = false), FakeCloudSync()).state.value!!.account,
        )
        val accounts = FakeAccounts()
        val cloud = FakeCloudSync()
        val vm = settings(core, accounts, cloud)
        assertEquals(AccountSummary.SignedOut, vm.state.value!!.account)

        accounts.signInAs(accounts.nextUser)
        cloud.state.value = CloudStatus.Synced(SampleData.SYNCED_1431)
        runCurrent()
        assertEquals(AccountSummary.SignedIn("parent@example.com", CloudStatus.Synced(SampleData.SYNCED_1431)), vm.state.value!!.account)

        cloud.state.value = CloudStatus.Error
        runCurrent()
        assertEquals(AccountSummary.SignedIn("parent@example.com", CloudStatus.Error), vm.state.value!!.account)
        vm.onRetryBackup()
        runCurrent()
        assertEquals(1, cloud.syncs)
    }

    @Test
    fun aboutLinksThePrivacyPolicy() = coreTest { core ->
        assertEquals(
            "https://demo-pooparazzi.web.app/privacy",
            settings(core, FakeAccounts(), FakeCloudSync()).state.value!!.privacyUrl,
        )
        assertEquals(
            "without a Firebase configuration, the policy in the repository",
            "https://github.com/ffMathy/pooparazzi/blob/main/docs/PRIVACY.md",
            settings(core, FakeAccounts(available = false, hostingDomain = null), FakeCloudSync()).state.value!!.privacyUrl,
        )
    }
}

/** Settings whose renames take a second each, to race the name writes. */
private class SlowSettings(private val delegate: SettingsRepository) : SettingsRepository by delegate {
    val writes = mutableListOf<String>()

    override suspend fun renameBaby(id: String, name: String): Settings {
        writes += name
        delay(1_000)
        return delegate.renameBaby(id, name)
    }
}
