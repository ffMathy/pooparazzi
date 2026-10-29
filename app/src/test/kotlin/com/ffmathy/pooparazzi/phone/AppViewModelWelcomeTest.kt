package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.phone.app.AppViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse
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
}
