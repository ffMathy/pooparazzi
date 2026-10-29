package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.ffmathy.pooparazzi.core.model.Baby
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
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
