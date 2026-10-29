package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import com.ffmathy.pooparazzi.phone.widget.InMemoryWidgetMemoStore
import com.ffmathy.pooparazzi.phone.widget.NoticeType
import com.ffmathy.pooparazzi.phone.widget.WidgetCommands
import com.ffmathy.pooparazzi.phone.widget.WidgetSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The widget buttons (DESIGN_SPEC §3.3.6, §10.7, §10.11) on a real repository: the sleep toggle with its receipt and
 * Undo, its 2 s guard and 60 s discard, and stale Undo. Feed and Diaper only open the app's sheets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WidgetCommandsTest : CoreTestBase() {
    private val store = InMemoryWidgetMemoStore()

    private fun commands(rig: CoreTestRig) = WidgetCommands(rig.repository, store, rig.clock)

    private suspend fun snapshot(rig: CoreTestRig): WidgetSnapshot =
        WidgetSnapshot.of(rig.entries(), rig.settings.current(), store.memo.value, rig.clock.now(), SampleData.ZONE, systemDark = false)

    @Test
    fun startSleepHasAReceiptThatUndoRemoves() = coreTest { rig ->
        commands(rig).toggleSleep()
        runCurrent()
        val sleep = rig.entries().single { it.isSleep && it.isRunning }

        val receipt = snapshot(rig).receipt!!
        assertEquals(ReceiptKind.SLEEP_STARTED, receipt.kind)
        assertEquals(sleep.id, receipt.entryId)
        assertEquals(HomeLaunch.Receipt(ReceiptKind.SLEEP_STARTED, sleep.id), snapshot(rig).backgroundLaunch)

        commands(rig).undo(sleep.id)
        runCurrent()
        assertNull(rig.entries().firstOrNull { it.id == sleep.id })
        val after = snapshot(rig)
        assertNull(after.receipt)
        assertEquals(NoticeType.UNDONE, after.notice!!.type)
    }

    @Test
    fun theReceiptExpiresAfterTenSeconds() = coreTest { rig ->
        commands(rig).toggleSleep()
        runCurrent()
        rig.clock.advanceBy(WidgetCommands.RECEIPT_MILLIS - 1)
        assertNotNull(snapshot(rig).receipt)
        rig.clock.advanceBy(1)
        assertNull(snapshot(rig).receipt)
    }

    @Test
    fun toggleStartsAndEndsTheSleepWithTheTwoSecondGuard() = coreTest { rig ->
        commands(rig).toggleSleep()
        runCurrent()
        val sleep = rig.entries().single { it.isSleep && it.isRunning }
        assertEquals(SampleData.NOW_1432, sleep.startMillis)
        assertEquals(ReceiptKind.SLEEP_STARTED, snapshot(rig).receipt!!.kind)
        assertTrue("a running sleep makes the widget Night under Auto", snapshot(rig).night)

        rig.clock.advanceBy(1_000)
        commands(rig).toggleSleep()
        runCurrent()
        assertTrue("the guard keeps the sleep running", rig.entry(sleep.id)!!.isRunning)
        assertEquals(NoticeType.ALREADY_ASLEEP, snapshot(rig).notice!!.type)

        rig.clock.advanceBy(10 * MINUTE)
        commands(rig).toggleSleep()
        runCurrent()
        val ended = rig.entry(sleep.id)!!
        assertEquals(SampleData.NOW_1432 + 10 * MINUTE + 1_000, ended.endMillis)
        assertEquals(ReceiptKind.WOKE_UP, snapshot(rig).receipt!!.kind)

        // Undo of Woke up: the sleep runs again.
        commands(rig).undo(sleep.id)
        runCurrent()
        assertTrue(rig.entry(sleep.id)!!.isRunning)
    }

    @Test
    fun wokeUpWithinAMinuteCancelsTheSleepAndUndoBringsItBack() = coreTest { rig ->
        commands(rig).toggleSleep()
        rig.clock.advanceBy(30_000)
        commands(rig).toggleSleep()
        runCurrent()
        assertTrue(rig.entries().none { it.isSleep && it.isRunning })
        val receipt = snapshot(rig).receipt!!
        assertEquals(ReceiptKind.SLEEP_CANCELLED, receipt.kind)

        commands(rig).undo(receipt.entryId)
        runCurrent()
        assertTrue(rig.entries().single { it.id == receipt.entryId }.isRunning)
    }

    @Test
    fun aStaleUndoDoesNothing() = coreTest { rig ->
        commands(rig).toggleSleep()
        runCurrent()
        val sleep = snapshot(rig).receipt!!.entryId
        rig.clock.advanceBy(WidgetCommands.RECEIPT_MILLIS)

        commands(rig).undo(sleep)
        runCurrent()
        assertNotNull("Undo of an expired receipt is ignored", rig.entries().firstOrNull { it.id == sleep })
    }

    @Test
    fun aReceiptDisappearsWhenItsLogIsUndoneInTheApp() = coreTest { rig ->
        commands(rig).toggleSleep()
        runCurrent()
        val sleep = snapshot(rig).receipt!!.entryId
        rig.repository.delete(sleep)
        runCurrent()
        assertNull(snapshot(rig).receipt)
        assertEquals(HomeLaunch.Home, snapshot(rig).backgroundLaunch)
    }
}
