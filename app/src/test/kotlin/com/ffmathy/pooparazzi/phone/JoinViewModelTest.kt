package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.phone.account.FamilyError
import com.ffmathy.pooparazzi.phone.account.InviteCheck
import com.ffmathy.pooparazzi.phone.account.JoinResult
import com.ffmathy.pooparazzi.phone.ui.family.JoinRefusal
import com.ffmathy.pooparazzi.phone.ui.family.JoinUiState
import com.ffmathy.pooparazzi.phone.ui.family.JoinViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The join confirmation's states (BABIES_AND_BACKUP §3.5): confirm → joined, and every refusal in words. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class JoinViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun test(families: FakeFamilies = FakeFamilies(), block: suspend TestScope.(JoinViewModel, FakeFamilies) -> Unit) =
        runTest(dispatcher) {
            val vm = JoinViewModel(CODE, families, backgroundScope)
            block(vm, families)
        }

    @Test
    fun readsTheInviteThenJoins() = test { vm, families ->
        assertEquals(JoinUiState.Loading, vm.state.value)
        runCurrent()
        assertEquals(JoinUiState.Confirm("partner@example.com"), vm.state.value)
        families.gate = CompletableDeferred()
        vm.onJoin()
        runCurrent()
        assertEquals(JoinUiState.Confirm("partner@example.com", busy = true), vm.state.value)
        vm.onJoin()
        families.gate!!.complete(Unit)
        runCurrent()
        assertEquals(JoinUiState.Joined, vm.state.value)
        assertEquals("once", listOf("check:$CODE", "join:$CODE"), families.calls)
    }

    @Test
    fun refusalsInWords() {
        val cases = mapOf(
            InviteCheck.Expired to JoinRefusal.EXPIRED,
            InviteCheck.Invalid to JoinRefusal.INVALID,
            InviteCheck.AlreadyMember to JoinRefusal.ALREADY_MEMBER,
            InviteCheck.Failed(FamilyError.NO_CONNECTION) to JoinRefusal.NO_CONNECTION,
            InviteCheck.Failed(FamilyError.OTHER) to JoinRefusal.OTHER,
        )
        for ((check, refusal) in cases) {
            test(FakeFamilies().apply { this.check = check }) { vm, _ ->
                runCurrent()
                assertEquals(JoinUiState.Refused(refusal), vm.state.value)
            }
        }
    }

    @Test
    fun offlineReadsAgain() = test(FakeFamilies().apply { check = InviteCheck.Failed(FamilyError.NO_CONNECTION) }) { vm, families ->
        runCurrent()
        families.check = InviteCheck.Valid(null)
        vm.onRetry()
        runCurrent()
        assertEquals(JoinUiState.Confirm(null), vm.state.value)
    }

    @Test
    fun anInviteUsedMeanwhileSaysItExpired() = test(FakeFamilies().apply { joinResult = JoinResult.Refused(InviteCheck.Expired) }) { vm, _ ->
        runCurrent()
        vm.onJoin()
        runCurrent()
        assertEquals(JoinUiState.Refused(JoinRefusal.EXPIRED), vm.state.value)
    }

    @Test
    fun aJoinWithoutConnectionKeepsTheQuestion() =
        test(FakeFamilies().apply { joinResult = JoinResult.Refused(InviteCheck.Failed(FamilyError.NO_CONNECTION)) }) { vm, _ ->
            runCurrent()
            vm.onJoin()
            runCurrent()
            assertEquals(JoinUiState.Confirm("partner@example.com", error = FamilyError.NO_CONNECTION), vm.state.value)
        }

    private companion object {
        const val CODE = "AbCdEfGhIjKlMnOpQrSt_-"
    }
}
