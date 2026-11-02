package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.app.AppViewModel
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** When the first-run Welcome appears (BABIES_AND_BACKUP §3.1). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AppViewModelWelcomeTest : CoreTestBase() {

    @Test
    fun shownOnceWhenAccountsExistAndNobodyIsSignedIn() = coreTest { core ->
        val store = MemoryWelcomeStore()
        val vm = AppViewModel(core.repository, core.settings, FakeAccounts(), store)
        assertTrue(vm.welcome.value)
        vm.finishWelcome()
        assertFalse(vm.welcome.value)
        assertTrue(store.seen)
        assertFalse("not again", AppViewModel(core.repository, core.settings, FakeAccounts(), store).welcome.value)
    }

    @Test
    fun signingInAnswersIt() = coreTest { core ->
        val store = MemoryWelcomeStore()
        val accounts = FakeAccounts()
        val vm = AppViewModel(core.repository, core.settings, accounts, store)
        runCurrent()
        accounts.signInAs(accounts.nextUser)
        runCurrent()
        assertFalse(vm.welcome.value)
        assertTrue(store.seen)
    }

    @Test
    fun neverWithoutAccountsOrWhenSignedIn() = coreTest { core ->
        val off = MemoryWelcomeStore()
        assertFalse(AppViewModel(core.repository, core.settings, FakeAccounts(available = false), off).welcome.value)
        assertFalse("a build without accounts never answers it for a later build", off.seen)

        val signedIn = MemoryWelcomeStore()
        val accounts = FakeAccounts(signedIn = FakeAccounts().nextUser)
        assertFalse(AppViewModel(core.repository, core.settings, accounts, signedIn).welcome.value)
        assertTrue("a phone with an account has nothing to ask", signedIn.seen)
    }

    /** A widget set to Ida opens the app for her: she becomes the current baby before the sheet is asked for. */
    @Test
    fun aLaunchForAnotherBabyMakesThatBabyCurrentFirst() = coreTest { core ->
        core.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        val vm = AppViewModel(core.repository, core.settings, FakeAccounts(), MemoryWelcomeStore())
        vm.request(HomeLaunch.Diaper, SampleData.TWIN_ID)
        runCurrent()
        assertEquals(SampleData.TWIN_ID, core.settings.current().selectedBaby.id)
        assertEquals(HomeLaunch.Diaper, vm.launch.value)

        // A baby that is gone (removed on another phone) leaves the current one alone.
        vm.request(HomeLaunch.Feed, "gone")
        runCurrent()
        assertEquals(SampleData.TWIN_ID, core.settings.current().selectedBaby.id)
        assertEquals(HomeLaunch.Feed, vm.launch.value)
    }

    /** The first run's "Your baby" screen: after the Welcome, once, on a fresh start only. */
    @Test
    fun theBabyIsAskedForAfterTheWelcomeOnce() = coreTest { core ->
        // A fresh install: one baby, not named yet.
        core.settings.renameBaby(Baby.FIRST_ID, "")
        val store = MemoryWelcomeStore()
        val vm = AppViewModel(core.repository, core.settings, FakeAccounts(), store)
        runCurrent()
        assertEquals("not over the Welcome", false, vm.babyIntro.value)
        vm.finishWelcome()
        runCurrent()
        assertEquals(true, vm.babyIntro.value)
        vm.finishBabyIntro()
        runCurrent()
        assertEquals(false, vm.babyIntro.value)
        assertTrue(store.babyAsked)
        val again = AppViewModel(core.repository, core.settings, FakeAccounts(), store)
        runCurrent()
        assertEquals("not again", false, again.babyIntro.value)
    }

    /** A phone that already has a log (an update), or a named baby, is not asked. */
    @Test
    fun aPhoneWithALogOrANamedBabyIsNotAsked() = coreTest { core ->
        core.settings.renameBaby(Baby.FIRST_ID, "")
        core.repository.add(Entry.diaper(SampleData.NOW_1432, pee = true, poop = false))
        val logged = AppViewModel(core.repository, core.settings, FakeAccounts(), MemoryWelcomeStore(seen = true))
        runCurrent()
        assertEquals(false, logged.babyIntro.value)
    }

    @Test
    fun aNamedBabyIsNotAsked() = coreTest { core ->
        core.settings.setBabyName("Noa")
        val named = AppViewModel(core.repository, core.settings, FakeAccounts(), MemoryWelcomeStore(seen = true))
        runCurrent()
        assertEquals(false, named.babyIntro.value)
    }

    @Test
    fun unknownUntilTheLogHasLoaded() = coreTest { core ->
        core.settings.renameBaby(Baby.FIRST_ID, "")
        val vm = AppViewModel(core.repository, core.settings, FakeAccounts(), MemoryWelcomeStore(seen = true))
        assertNull("the splash waits for it", vm.babyIntro.value)
        runCurrent()
        assertEquals(true, vm.babyIntro.value)
    }
}
