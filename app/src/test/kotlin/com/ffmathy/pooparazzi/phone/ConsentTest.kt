package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.account.ConsentPrefs
import com.ffmathy.pooparazzi.phone.account.MemoryConsent
import com.ffmathy.pooparazzi.phone.app.AppViewModel
import com.ffmathy.pooparazzi.phone.ui.account.ConsentViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The explicit consent before the first upload (BABIES_AND_BACKUP §3.6, docs/PRIVACY.md §3): asked of every signed-in
 * account that has not agreed on this phone, before anything else; "I agree" is kept per account, "Not now, sign out"
 * signs out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ConsentTest : CoreTestBase() {

    @Test
    fun aSignedInAccountIsAskedUntilItAgrees() = coreTest { core ->
        val accounts = FakeAccounts()
        val consent = MemoryConsent()
        val app = AppViewModel(core.repository, core.settings, accounts, MemoryWelcomeStore(seen = true), consent = consent)
        backgroundScope.launch { app.consentNeeded.collect {} }
        runCurrent()
        assertFalse("nobody signed in, nothing to ask", app.consentNeeded.value)

        accounts.signInAs(accounts.nextUser)
        runCurrent()
        assertTrue(app.consentNeeded.value)

        val vm = ConsentViewModel(accounts, consent)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertEquals("parent@example.com", vm.state.value.email)
        vm.onAgree()
        runCurrent()
        assertEquals(setOf(accounts.nextUser.uid), consent.agreed.value)
        assertFalse(app.consentNeeded.value)

        // Another account on the same phone is asked again.
        accounts.signInAs(accounts.googleUser)
        runCurrent()
        assertTrue(app.consentNeeded.value)
    }

    @Test
    fun notNowSignsOutAndAgreesToNothing() = coreTest { core ->
        val accounts = FakeAccounts(signedIn = FakeAccounts().nextUser)
        val consent = MemoryConsent()
        val vm = ConsentViewModel(accounts, consent)
        vm.onDecline()
        runCurrent()
        assertEquals(listOf("signOut"), accounts.calls)
        assertTrue(consent.agreed.value.isEmpty())
    }

    @Test
    fun theConsentIsKeptOnThisPhonePerAccount() {
        val app: Application = ApplicationProvider.getApplicationContext()
        ConsentPrefs(app).agree("anna")
        ConsentPrefs(app).agree("bo")
        assertEquals(setOf("anna", "bo"), ConsentPrefs(app).agreed.value)
        ConsentPrefs(app).withdraw("anna")
        assertEquals(setOf("bo"), ConsentPrefs(app).agreed.value)
    }
}
