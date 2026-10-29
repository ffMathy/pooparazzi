package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.DiaperKind
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
 * The widget buttons (DESIGN_SPEC §3.3.6, §10.7, §10.11) on a real repository: one-tap diaper logs with a receipt and
 * Undo, dedupe, the sleep toggle with its 2 s guard and 60 s discard, and stale Undo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WidgetCommandsTest : CoreTestBase() {
    private val store = InMemoryWidgetMemoStore()

    private fun commands(rig: CoreTestRig) = WidgetCommands(rig.repository, store, rig.clock, rig.settings)

    private suspend fun snapshot(rig: CoreTestRig): WidgetSnapshot =
        WidgetSnapshot.of(rig.entries(), rig.settings.current(), store.memo.value, rig.clock.now(), SampleData.ZONE, systemDark = false)

    @Test
    fun poopLogsNowWithAReceiptThatUndoRemoves() = coreTest { rig ->
        commands(rig).logDiaper(DiaperKind.POOP)
        runCurrent()
        val poop = rig.entries().single { it.startMillis == SampleData.NOW_1432 }
        assertTrue(poop.poop)
        assertFalse(poop.pee)
        assertNull("one tap logs no colour", poop.poopColor)

        val receipt = snapshot(rig).receipt!!
        assertEquals(ReceiptKind.LOGGED, receipt.kind)
        assertEquals(poop.id, receipt.entryId)
        assertEquals(DiaperKind.POOP, receipt.diaperKind)
        assertEquals(HomeLaunch.Receipt(ReceiptKind.LOGGED, poop.id), snapshot(rig).backgroundLaunch)

        commands(rig).undo(poop.id)
        runCurrent()
        assertNull(rig.entries().firstOrNull { it.id == poop.id })
        val after = snapshot(rig)
        assertNull(after.receipt)
        assertEquals(NoticeType.UNDONE, after.notice!!.type)
    }

    @Test
    fun aSecondTapWithinTheDedupeWindowLogsNothingAndSaysSo() = coreTest { rig ->
        commands(rig).logDiaper(DiaperKind.BOTH)
        rig.clock.advanceBy(3_000)
        commands(rig).logDiaper(DiaperKind.BOTH)
        runCurrent()
        assertEquals(1, rig.entries().count { it.startMillis >= SampleData.NOW_1432 })
        val snapshot = snapshot(rig)
        assertEquals(NoticeType.ALREADY_LOGGED, snapshot.notice!!.type)
        assertEquals(SampleData.NOW_1432, snapshot.notice!!.atMillis)
        assertNotNull("the receipt of the first tap stays for Undo", snapshot.receipt)
    }

    @Test
    fun theReceiptExpiresAfterTenSeconds() = coreTest { rig ->
        commands(rig).logDiaper(DiaperKind.PEE)
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
        commands(rig).logDiaper(DiaperKind.PEE)
        runCurrent()
        val pee = snapshot(rig).receipt!!.entryId
        rig.clock.advanceBy(6_000)
        commands(rig).logDiaper(DiaperKind.POOP)
        runCurrent()

        commands(rig).undo(pee)
        runCurrent()
        assertNotNull("Undo of a replaced receipt is ignored", rig.entries().firstOrNull { it.id == pee })
    }

    @Test
    fun aReceiptDisappearsWhenItsLogIsUndoneInTheApp() = coreTest { rig ->
        commands(rig).logDiaper(DiaperKind.PEE)
        runCurrent()
        val pee = snapshot(rig).receipt!!.entryId
        rig.repository.delete(pee)
        runCurrent()
        assertNull(snapshot(rig).receipt)
        assertEquals(HomeLaunch.Home, snapshot(rig).backgroundLaunch)
    }

    /** BABIES_AND_BACKUP §2.4: the widgets log for the current baby, and a receipt belongs to the baby it was made for. */
    @Test
    fun aReceiptNoLongerStandsOnceAnotherBabyIsCurrent() = coreTest { rig ->
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        commands(rig).logDiaper(DiaperKind.PEE)
        runCurrent()
        val receipt = snapshot(rig).receipt!!
        assertEquals(Baby.FIRST_ID, receipt.babyId)
        assertEquals(Baby.FIRST_ID, rig.entry(receipt.entryId)!!.babyId)

        rig.settings.selectBaby(SampleData.TWIN_ID)
        runCurrent()
        assertNull("Ida's widget shows no receipt for Noa's log", snapshot(rig).receipt)
        assertEquals(HomeLaunch.Home, snapshot(rig).backgroundLaunch)
        commands(rig).undo(receipt.entryId)
        runCurrent()
        assertNotNull("Undo does nothing for another baby", rig.entry(receipt.entryId)?.takeUnless { it.deleted })

        rig.settings.selectBaby(Baby.FIRST_ID)
        runCurrent()
        assertNotNull("back on Noa the receipt stands again", snapshot(rig).receipt)
    }

    /** A cancelled sleep is gone from every log: its receipt must not stand on the other baby's (empty) log either. */
    @Test
    fun aSleepCancelledReceiptBelongsToItsBaby() = coreTest { rig ->
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        commands(rig).toggleSleep()
        rig.clock.advanceBy(30_000)
        commands(rig).toggleSleep()
        runCurrent()
        assertEquals(ReceiptKind.SLEEP_CANCELLED, snapshot(rig).receipt!!.kind)

        rig.settings.selectBaby(SampleData.TWIN_ID)
        runCurrent()
        assertNull(snapshot(rig).receipt)
    }

    @Test
    fun theWidgetsLogForTheCurrentBabyAndNameIt() = coreTest { rig ->
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        rig.settings.selectBaby(SampleData.TWIN_ID)
        commands(rig).toggleSleep()
        runCurrent()
        val sleep = rig.entries().single { it.isSleep && it.isRunning }
        assertEquals(SampleData.TWIN_ID, sleep.babyId)
        val snapshot = snapshot(rig)
        assertEquals(SampleData.TWIN_ID, snapshot.receipt!!.babyId)
        assertTrue(snapshot.hasSeveralBabies)
        assertEquals(SampleData.TWIN_ID, snapshot.baby.id)
        assertNull("Ida has no feeds yet", snapshot.lastFeed)
    }
}
