package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import com.ffmathy.pooparazzi.phone.widget.InMemoryWidgetBabies
import com.ffmathy.pooparazzi.phone.widget.InMemoryWidgetMemoStore
import com.ffmathy.pooparazzi.phone.widget.NoticeType
import com.ffmathy.pooparazzi.phone.widget.WidgetBabies
import com.ffmathy.pooparazzi.phone.widget.WidgetCommands
import com.ffmathy.pooparazzi.phone.widget.WidgetSnapshot
import com.ffmathy.pooparazzi.phone.widget.WidgetSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    private fun commands(rig: CoreTestRig) = WidgetCommands(rig.repository, store, rig.clock, rig.settings)

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

    /** BABIES_AND_BACKUP §2.4: a widget logs for its baby, and a receipt shows only on that baby's widgets. */
    @Test
    fun aReceiptNoLongerStandsOnceAnotherBabyIsCurrent() = coreTest { rig ->
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        commands(rig).toggleSleep()
        runCurrent()
        val receipt = snapshot(rig).receipt!!
        assertEquals(ReceiptKind.SLEEP_STARTED, receipt.kind)
        assertEquals(Baby.FIRST_ID, receipt.babyId)
        assertEquals(Baby.FIRST_ID, rig.entry(receipt.entryId)!!.babyId)

        rig.settings.selectBaby(SampleData.TWIN_ID)
        runCurrent()
        assertNull("Ida's widget shows no receipt for Noa's log", snapshot(rig).receipt)
        assertEquals(HomeLaunch.Home, snapshot(rig).backgroundLaunch)

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

    /** Auto dims for naps while any baby is asleep (BABIES_AND_BACKUP §2.3), as the app does: the widgets too. */
    @Test
    fun underAutoTheWidgetsDimWhileAnyBabyIsAsleep() = coreTest { rig ->
        rig.settings.setAppearance(Appearance.AUTO)
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        val source = WidgetSource(ApplicationProvider.getApplicationContext(), Core.graph, store, InMemoryWidgetBabies())
        assertFalse(source.current().night)
        commands(rig).toggleSleep()
        runCurrent()
        rig.settings.selectBaby(SampleData.TWIN_ID)
        runCurrent()
        val snapshot = source.current()
        assertEquals(SampleData.TWIN_ID, snapshot.baby.id)
        assertFalse("Ida is awake", snapshot.asleep)
        assertTrue("but Noa is asleep", snapshot.night)
    }

    /** A switch of baby never labels the new baby with the previous one's facts, not even for one snapshot. */
    @Test
    fun aSwitchNeverPairsTheNewBabyWithTheOtherBabysFacts() = coreTest { rig ->
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        val seen = rig.collect(WidgetSource(ApplicationProvider.getApplicationContext(), Core.graph, store, InMemoryWidgetBabies()).snapshots(null))
        assertNotNull("Noa's last feed", seen.last().lastFeed)
        rig.settings.selectBaby(SampleData.TWIN_ID)
        runCurrent()
        val ida = seen.filter { it.babyId == SampleData.TWIN_ID }
        assertTrue(ida.isNotEmpty())
        assertTrue("never Noa's feed under Ida's name: $ida", ida.all { it.lastFeed == null && it.lastDiaper == null })
    }

    /** Twins: a widget set to Ida starts Ida's sleep, whichever baby the phone has current, and keeps its own guard. */
    @Test
    fun aWidgetForAnotherBabyLogsForItWithItsOwnGuard() = coreTest { rig ->
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        commands(rig).toggleSleep(SampleData.TWIN_ID)
        runCurrent()
        suspend fun running() = rig.repository.observeAllEntries().first().filter { it.isSleep && it.isRunning }
        val ida = running()
        assertEquals(listOf(SampleData.TWIN_ID), ida.map { it.babyId })
        assertEquals("the phone keeps its current baby", Baby.FIRST_ID, rig.settings.current().selectedBaby.id)

        // Within 2 s, Noa's widget still starts Noa's sleep: the guard is per baby.
        rig.clock.advanceBy(500)
        commands(rig).toggleSleep(Baby.FIRST_ID)
        runCurrent()
        assertEquals(setOf(Baby.FIRST_ID, SampleData.TWIN_ID), running().map { it.babyId }.toSet())

        // A second tap on Ida's widget within 2 s is guarded, as before.
        commands(rig).toggleSleep(SampleData.TWIN_ID)
        runCurrent()
        assertEquals(2, running().size)
    }

    /** A widget with its own baby shows that baby's log and receipt; one without follows the phone's current baby. */
    @Test
    fun eachWidgetShowsItsOwnBaby() = coreTest { rig ->
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        val babies = InMemoryWidgetBabies(mapOf(7 to SampleData.TWIN_ID))
        val source = WidgetSource(ApplicationProvider.getApplicationContext(), Core.graph, store, babies)
        commands(rig).toggleSleep(SampleData.TWIN_ID)
        runCurrent()

        val idas = source.current(7)
        assertEquals(SampleData.TWIN_ID, idas.baby.id)
        assertTrue("Ida's widget shows her sleep", idas.asleep)
        assertEquals(SampleData.TWIN_ID, idas.receipt?.babyId)
        assertEquals(SampleData.TWIN_ID, idas.launchBabyId)

        val noas = source.current(8)
        assertEquals("no choice: the phone's current baby", Baby.FIRST_ID, noas.baby.id)
        assertFalse(noas.asleep)
        assertNull("Ida's receipt is not on Noa's widget", noas.receipt)

        // A removed baby falls back to the current one.
        babies.choose(7, "gone")
        assertEquals(Baby.FIRST_ID, source.current(7).baby.id)
    }

    @Test
    fun theBabyButtonMovesOnToTheNextBabyAndWrapsAround() = coreTest { rig ->
        rig.settings.ensureBabies(listOf(SampleData.TWIN_ID, "third"))
        val settings = rig.settings.current()
        val (noa, ida, third) = settings.babies
        assertEquals(ida, WidgetBabies.next(noa, settings))
        assertEquals(third, WidgetBabies.next(ida, settings))
        assertEquals(noa, WidgetBabies.next(third, settings))
    }
}
