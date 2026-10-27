package com.ffmathy.xaternity.phone

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.ffmathy.xaternity.core.settings.AppLanguage
import com.ffmathy.xaternity.core.settings.Appearance
import com.ffmathy.xaternity.core.sync.NoopEntrySync
import com.ffmathy.xaternity.phone.app.LocaleApplier
import com.ffmathy.xaternity.phone.ui.settings.SettingsViewModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings persist the appearance, the language (applied and synced) and the baby's name (V1_ADDITIONS §5, §7). */
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

    @Test
    fun theNameIsSavedWhenTypingStops() = coreTest { core ->
        val vm = settings(core)
        assertEquals("Noa", vm.state.value!!.babyName)
        vm.onBabyNameChange("Ma")
        advanceTimeBy(200)
        vm.onBabyNameChange("Maja")
        runCurrent()
        assertEquals("the field shows the typing at once", "Maja", vm.state.value!!.babyName)
        assertEquals("not saved while typing", "Noa", core.settings.current().babyName)

        advanceTimeBy(SettingsViewModel.NAME_DEBOUNCE_MILLIS + 1)
        assertEquals("Maja", core.settings.current().babyName)
    }

    @Test
    fun doneSavesTheNameAtOnce() = coreTest { core ->
        val vm = settings(core)
        vm.onBabyNameChange("Ida")
        vm.onBabyNameDone()
        runCurrent()
        assertEquals("Ida", core.settings.current().babyName)
    }

    @Test
    fun aNameTypedJustBeforeLeavingIsStillSaved() = coreTest { core ->
        val store = ViewModelStore()
        val vm = settings(core)
        store.put("settings", vm)
        vm.onBabyNameChange("Alma")
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals("Alma", core.settings.current().babyName)
    }

    @Test
    fun longNamesAreCutAtTheLimit() = coreTest { core ->
        val vm = settings(core)
        vm.onBabyNameChange("x".repeat(60))
        runCurrent()
        assertEquals(40, vm.state.value!!.babyName.length)
    }
}
