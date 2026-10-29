package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.cloud.CloudAccount
import com.ffmathy.pooparazzi.phone.account.Family
import com.ffmathy.pooparazzi.phone.account.FamilyError
import com.ffmathy.pooparazzi.phone.account.FamilyMember
import com.ffmathy.pooparazzi.phone.account.FamilyResult
import com.ffmathy.pooparazzi.phone.ui.family.FamilyConfirm
import com.ffmathy.pooparazzi.phone.ui.family.FamilyViewModel
import com.ffmathy.pooparazzi.phone.ui.family.inviteShareIntent
import com.ffmathy.pooparazzi.phone.ui.family.inviteShareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Family's actions (BABIES_AND_BACKUP §3.4) on a fake family: the members load with the family, "Invite
 * someone" hands an invite link to the share sheet (with its text in English and Danish), leaving and removing ask
 * first, and only the family's creator removes anyone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FamilyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val you = FamilyMember("anna", "anna@example.com", you = true, creator = true)
    private val partner = FamilyMember("bo", "bo@example.com", you = false, creator = false)
    private val family = Family("fam-1", listOf(you, partner))

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun test(families: FakeFamilies = signedInFamilies(), block: suspend TestScope.(FamilyViewModel, FakeFamilies) -> Unit) =
        runTest(dispatcher) {
            val vm = FamilyViewModel(families, backgroundScope)
            runCurrent()
            block(vm, families)
        }

    private fun signedInFamilies(familyId: String? = "fam-1") = FakeFamilies(CloudAccount("anna", "anna@example.com", familyId)).apply {
        loadResult = FamilyResult.Ok(family)
    }

    @Test
    fun theMembersLoadWithTheFamily() = test { vm, families ->
        assertEquals(family, vm.state.value.family)
        assertTrue(vm.state.value.signedIn)
        assertEquals(listOf("load"), families.calls)

        // Joined another family: it loads by itself.
        families.loadResult = FamilyResult.Ok(Family("fam-2", listOf(you.copy(creator = false), partner.copy(creator = true))))
        families.current.value = CloudAccount("anna", "anna@example.com", "fam-2")
        runCurrent()
        assertEquals("fam-2", vm.state.value.family?.id)
    }

    @Test
    fun whileTheFamilyIsNotKnownItSaysItIsBeingSetUp() = test(signedInFamilies(familyId = null)) { vm, families ->
        assertTrue(vm.state.value.waiting)
        assertTrue(families.calls.isEmpty())
    }

    @Test
    fun signedOutThereIsNoFamilySection() = test(FakeFamilies()) { vm, _ ->
        assertEquals(false, vm.state.value.signedIn)
    }

    @Test
    fun aFailedLoadCanBeRetried() = test(signedInFamilies().apply { loadResult = FamilyResult.Failed(FamilyError.NO_CONNECTION) }) { vm, families ->
        assertEquals(FamilyError.NO_CONNECTION, vm.state.value.loadError)
        families.loadResult = FamilyResult.Ok(family)
        vm.onRetry()
        runCurrent()
        assertNull(vm.state.value.loadError)
        assertEquals(family, vm.state.value.family)
    }

    @Test
    fun inviteSomeoneHandsTheLinkToTheShareSheet() = test { vm, families ->
        val shared = mutableListOf<String>()
        backgroundScope.launch { vm.shares.collect { shared += it } }
        vm.onInvite()
        runCurrent()
        assertEquals(listOf("https://demo-pooparazzi.web.app/join/AbCdEfGhIjKlMnOpQrStUv"), shared)
        assertTrue("invite" in families.calls)

        families.inviteResult = FamilyResult.Failed(FamilyError.NO_CONNECTION)
        vm.onInvite()
        runCurrent()
        assertEquals(1, shared.size)
        assertEquals(FamilyError.NO_CONNECTION, vm.state.value.actionError)
    }

    @Test
    fun theShareTextInBothLanguages() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val link = "https://demo-pooparazzi.web.app/join/AbCdEfGhIjKlMnOpQrStUv"
        assertEquals(
            "Join my family on Pooparazzi to share our baby log: $link (works once, for 7 days)",
            inviteShareText(app, link),
        )
        val chooser = inviteShareIntent(app, link)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(inviteShareText(app, link), send.getStringExtra(Intent.EXTRA_TEXT))

        val danish = app.createConfigurationContext(
            android.content.res.Configuration(app.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("da")) },
        )
        assertEquals(
            "Bliv en del af min familie i Pooparazzi, så vi deler babyloggen: $link (virker én gang og i 7 dage)",
            inviteShareText(danish, link),
        )
    }

    @Test
    fun leavingAsksFirst() = test { vm, families ->
        vm.onLeave()
        runCurrent()
        assertEquals(FamilyConfirm.Leave, vm.state.value.confirm)
        vm.onDismiss()
        runCurrent()
        assertNull(vm.state.value.confirm)
        assertTrue("leave" !in families.calls)

        vm.onLeave()
        vm.onConfirm()
        runCurrent()
        assertTrue("leave" in families.calls)
        assertNull(vm.state.value.confirm)
        assertNull(vm.state.value.actionError)
    }

    @Test
    fun theCreatorRemovesAfterAsking() = test { vm, families ->
        vm.onRemove(partner)
        runCurrent()
        assertEquals(FamilyConfirm.Remove(partner), vm.state.value.confirm)
        vm.onConfirm()
        runCurrent()
        assertTrue("remove:bo" in families.calls)
        assertEquals("the members load again", 2, families.calls.count { it == "load" })

        vm.onRemove(you)
        runCurrent()
        assertNull("nobody removes themselves", vm.state.value.confirm)
    }

    @Test
    fun othersRemoveNobody() = test(
        signedInFamilies().apply { loadResult = FamilyResult.Ok(Family("fam-1", listOf(you.copy(creator = false), partner.copy(creator = true)))) },
    ) { vm, families ->
        vm.onRemove(partner)
        vm.onConfirm()
        runCurrent()
        assertNull(vm.state.value.confirm)
        assertTrue(families.calls.none { it.startsWith("remove") })
    }

    @Test
    fun aFailedLeaveIsSaid() = test(signedInFamilies().apply { leaveResult = FamilyResult.Failed(FamilyError.NO_CONNECTION) }) { vm, _ ->
        vm.onLeave()
        vm.onConfirm()
        runCurrent()
        assertEquals(FamilyError.NO_CONNECTION, vm.state.value.actionError)
        assertNull(vm.state.value.confirm)
    }
}
