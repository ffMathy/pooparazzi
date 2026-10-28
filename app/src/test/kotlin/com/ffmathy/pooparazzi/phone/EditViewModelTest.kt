package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.edit.EditEvent
import com.ffmathy.pooparazzi.phone.ui.edit.EditIssue
import com.ffmathy.pooparazzi.phone.ui.edit.EditMode
import com.ffmathy.pooparazzi.phone.ui.edit.EditUiState
import com.ffmathy.pooparazzi.phone.ui.edit.EditViewModel
import com.ffmathy.pooparazzi.phone.ui.edit.TimeField
import com.ffmathy.pooparazzi.phone.ui.home.HomeHaptic
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The edit sheet: validation, saving each kind, delete, and "Add past entry" (DESIGN_SPEC §3.3.4, V1_ADDITIONS §4). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class EditViewModelTest : CoreTestBase() {

    private class Sheet(val vm: EditViewModel, val events: List<EditEvent>, val haptics: List<HomeHaptic>)

    private fun CoreTestRig.sheet(saved: SavedStateHandle = SavedStateHandle()): Sheet {
        val vm = EditViewModel(repository, clock, SampleData.ZONE, saved)
        collect(vm.state)
        return Sheet(vm, collect(vm.events), collect(vm.haptics))
    }

    private fun TestScope.state(sheet: Sheet): EditUiState {
        runCurrent()
        return checkNotNull(sheet.vm.state.value) { "The sheet is closed" }
    }

    private fun at(hour: Int, minute: Int) = SampleData.at(hour, minute)

    @Test
    fun nudgingTheStartShowsWasAndTheNewDurationThenSaves() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-sleep-1240")
        assertEquals(EditMode.EDIT, state(sheet).mode)
        assertNull("no was before a change", state(sheet).wasStart)

        sheet.vm.onNudge(TimeField.START, -5)
        val edited = state(sheet)
        assertEquals(at(12, 35), edited.draft.start)
        assertEquals(at(12, 40), edited.wasStart)
        assertNull(edited.wasEnd)
        assertEquals(52 * MINUTE, edited.durationMillis)
        assertEquals(47 * MINUTE, edited.wasDurationMillis)
        assertNull(edited.issue)
        assertTrue(edited.canSave)
        assertEquals(listOf(HomeHaptic.SEGMENT_TICK), sheet.haptics)

        sheet.vm.onSave()
        runCurrent()
        val saved = core.entry("sample-sleep-1240")!!
        assertEquals(at(12, 35), saved.startMillis)
        assertEquals(at(13, 27), saved.endMillis)
        assertNull("the sheet closed", sheet.vm.state.value)
        assertTrue(sheet.events.single() is EditEvent.Saved)
    }

    @Test
    fun anEndBeforeTheStartBlocksSave() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-sleep-1240")
        sheet.vm.onTimeSet(TimeField.END, 12, 30)
        val state = state(sheet)

        assertEquals(EditIssue.EndBeforeStart(EntryKind.SLEEP), state.issue)
        assertTrue(state.issue!!.blocksSave)
        assertFalse(state.canSave)

        sheet.vm.onSave()
        runCurrent()
        assertEquals("nothing was written", at(13, 27), core.entry("sample-sleep-1240")!!.endMillis)
        assertEquals(HomeHaptic.REJECT, sheet.haptics.last())
        assertTrue("the sheet stays open", sheet.vm.state.value != null)
    }

    @Test
    fun aFutureTimeBlocksSaveAndUseYesterdayFixesIt() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-formula-1222")
        sheet.vm.onTimeSet(TimeField.START, 14, 50)
        val future = state(sheet)
        assertEquals(EditIssue.InFuture(TimeField.START, at(14, 50)), future.issue)
        assertFalse(future.canSave)

        sheet.vm.onUseYesterday()
        val fixed = state(sheet)
        assertEquals(SampleData.at(14, 50, day = SampleData.YESTERDAY), fixed.draft.start)
        assertNull(fixed.issue)

        sheet.vm.onSave()
        runCurrent()
        assertEquals(SampleData.at(14, 50, day = SampleData.YESTERDAY), core.entry("sample-formula-1222")!!.startMillis)
    }

    @Test
    fun anOverlapWarnsButSaveStaysEnabled() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-sleep-1240")
        sheet.vm.onTimeSet(TimeField.START, 10, 0)
        val state = state(sheet)

        val overlap = state.issue as EditIssue.Overlaps
        assertEquals("sample-sleep-0930", overlap.other.id)
        assertFalse(overlap.blocksSave)
        assertTrue(state.canSave)

        sheet.vm.onSave()
        runCurrent()
        assertEquals(at(10, 0), core.entry("sample-sleep-1240")!!.startMillis)
    }

    @Test
    fun theDayChipMovesATimeToAnotherDayKeepingItsClockTime() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-pee-1105")
        sheet.vm.onDaySet(TimeField.START, SampleData.YESTERDAY)
        assertEquals(SampleData.at(11, 5, day = SampleData.YESTERDAY), state(sheet).draft.start)
    }

    @Test
    fun aBreastfeedSavesItsSideAndTypeChanges() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-breast-0800")
        assertEquals(BreastSide.RIGHT_THEN_LEFT, state(sheet).draft.side)
        // The checkboxes show Right (1) and Left (2): unticking Right leaves Left.
        sheet.vm.onSideTick(Breast.RIGHT)
        assertEquals(BreastSide.LEFT, state(sheet).draft.side)
        sheet.vm.onSave()
        runCurrent()
        assertEquals(BreastSide.LEFT, core.entry("sample-breast-0800")!!.breastSide)

        sheet.vm.openEntry("sample-breast-0800")
        sheet.vm.onFeedType(FeedType.FORMULA)
        val formula = state(sheet)
        assertNull("formula is an instant", formula.draft.end)
        assertNull(formula.draft.side)
        assertNull(formula.durationMillis)
        sheet.vm.onFeedType(FeedType.BREAST)
        assertNull("a breast feed is an instant too", state(sheet).draft.end)
        assertNull(state(sheet).durationMillis)
        assertEquals("switching back restores the original's side", BreastSide.LEFT, state(sheet).draft.side)
        sheet.vm.onFeedType(FeedType.FORMULA)
        sheet.vm.onSave()
        runCurrent()
        val saved = core.entry("sample-breast-0800")!!
        assertEquals(FeedType.FORMULA, saved.feedType)
        assertNull(saved.endMillis)
        assertNull(saved.breastSide)
    }

    @Test
    fun theSideFollowsTheTickOrderAndIsOptional() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-breast-0800")
        sheet.vm.onSideTick(Breast.RIGHT)
        sheet.vm.onSideTick(Breast.LEFT)
        assertNull("nothing ticked", state(sheet).draft.side)
        assertTrue("the side is optional", state(sheet).canSave)
        sheet.vm.onSideTick(Breast.LEFT)
        sheet.vm.onSideTick(Breast.RIGHT)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, state(sheet).draft.side)
        sheet.vm.onSideTick(Breast.LEFT)
        sheet.vm.onSideTick(Breast.LEFT)
        assertEquals("re-ticking appends", BreastSide.RIGHT_THEN_LEFT, state(sheet).draft.side)
        sheet.vm.onSideTick(Breast.RIGHT)
        sheet.vm.onSideTick(Breast.LEFT)
        sheet.vm.onSave()
        runCurrent()
        val saved = core.entry("sample-breast-0800")!!
        assertNull(saved.breastSide)
        assertNull(saved.endMillis)
        assertEquals(at(8, 0), saved.startMillis)
    }

    @Test
    fun tappingTheSelectedFeedTypeClearsIt() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-formula-1222")
        sheet.vm.onFeedType(FeedType.FORMULA)
        assertNull(state(sheet).draft.feedType)
        assertTrue(state(sheet).canSave)
        sheet.vm.onSave()
        runCurrent()
        val saved = core.entry("sample-formula-1222")!!
        assertNull("a feed with just its time", saved.feedType)
        assertEquals(at(12, 22), saved.startMillis)

        // From no type, Breast makes it a breast feed at the same time (no end), whose side can be ticked.
        sheet.vm.openEntry("sample-formula-1222")
        sheet.vm.onFeedType(FeedType.BREAST)
        assertEquals(FeedType.BREAST, state(sheet).draft.feedType)
        assertNull(state(sheet).draft.end)
        sheet.vm.onSideTick(Breast.LEFT)
        assertEquals(BreastSide.LEFT, state(sheet).draft.side)
    }

    @Test
    fun aDiaperSavesPoopWithColourAndTextureAndNeedsAToggle() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-pee-1344")
        sheet.vm.onPoop(true)
        sheet.vm.onPoopColor(PoopColor.GREEN)
        sheet.vm.onPoopTexture(PoopTexture.SEEDY)
        sheet.vm.onSave()
        runCurrent()
        val saved = core.entry("sample-pee-1344")!!
        assertTrue(saved.pee && saved.poop)
        assertEquals(PoopColor.GREEN, saved.poopColor)
        assertEquals(PoopTexture.SEEDY, saved.poopTexture)

        sheet.vm.openEntry("sample-pee-1344")
        sheet.vm.onPoop(false)
        assertNull("colour belongs to poop only", state(sheet).draft.color)
        sheet.vm.onPee(false)
        assertTrue("both are optional: a plain change", state(sheet).canSave)
        sheet.vm.onSave()
        runCurrent()
        val plain = core.entry("sample-pee-1344")!!
        assertFalse(plain.pee || plain.poop)
        assertNull(plain.poopColor)
    }

    @Test
    fun addPastEntryStartsAsTheLastFeedTypeFifteenMinutesAgo() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openNew(SampleData.NOW_1432 - 15 * MINUTE)
        val state = state(sheet)
        assertEquals(EditMode.ADD, state.mode)
        assertEquals(EntryKind.FEED, state.draft.kind)
        assertEquals("the last feed was formula", FeedType.FORMULA, state.draft.feedType)
        assertEquals(at(14, 17), state.draft.start)
        assertTrue(state.canSave)

        sheet.vm.onSave()
        runCurrent()
        val added = core.entries().single { it.startMillis == at(14, 17) }
        assertEquals(FeedType.FORMULA, added.feedType)
        assertEquals(16, core.entries().size)
    }

    @Test
    fun addPastEntrySavesEachKind() = coreTest { core ->
        val sheet = core.sheet()
        val start = SampleData.NOW_1432 - 15 * MINUTE

        sheet.vm.openNew(start)
        sheet.vm.onKind(EntryKind.SLEEP)
        assertEquals(at(14, 32), state(sheet).draft.end)
        sheet.vm.onSave()
        runCurrent()
        val sleep = core.entries().single { it.kind == EntryKind.SLEEP && it.startMillis == at(14, 17) }
        assertEquals(at(14, 32), sleep.endMillis)

        sheet.vm.openNew(start)
        sheet.vm.onFeedType(FeedType.BREAST)
        assertTrue("the side is optional", state(sheet).canSave)
        sheet.vm.onSideTick(Breast.RIGHT)
        sheet.vm.onSave()
        runCurrent()
        val breast = core.entries().single { it.feedType == FeedType.BREAST && it.startMillis == at(14, 17) }
        assertEquals(BreastSide.RIGHT, breast.breastSide)
        assertNull("a breast feed is an instant", breast.endMillis)

        sheet.vm.openNew(start)
        sheet.vm.onKind(EntryKind.DIAPER)
        assertTrue("pee and poop are optional", state(sheet).canSave)
        sheet.vm.onPee(true)
        sheet.vm.onSave()
        runCurrent()
        assertTrue(core.entries().any { it.kind == EntryKind.DIAPER && it.pee && !it.poop && it.startMillis == at(14, 17) })
        assertEquals(18, core.entries().size)
    }

    @Test
    fun deleteClosesTheSheetAndReportsTheEntryForUndo() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-sleep-1240")
        state(sheet)
        sheet.vm.onDelete()
        runCurrent()

        assertNull(sheet.vm.state.value)
        val deleted = sheet.events.single() as EditEvent.Deleted
        assertEquals("sample-sleep-1240", deleted.previous.id)
        assertFalse(deleted.previous.deleted)
        assertTrue(core.entry("sample-sleep-1240")!!.deleted)

        core.repository.restore(deleted.previous)
        runCurrent()
        assertFalse(core.entry("sample-sleep-1240")!!.deleted)
    }

    @Test
    fun aRunningSleepKeepsRunningUntilEndNow() = coreTest(now = SampleData.NOW_0312, seed = SampleData.scenarioAt0312(SampleData.ZONE)) { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-sleep-0205")
        assertTrue(state(sheet).draft.running)
        assertEquals("the duration runs to now", 67 * MINUTE, state(sheet).durationMillis)

        sheet.vm.onNudge(TimeField.START, -5)
        sheet.vm.onSave()
        runCurrent()
        val moved = core.entry("sample-sleep-0205")!!
        assertEquals(at(2, 0), moved.startMillis)
        assertTrue("changing the start keeps the timer running", moved.isRunning)

        sheet.vm.openEntry("sample-sleep-0205")
        sheet.vm.onEndNow()
        assertEquals(at(3, 12), state(sheet).draft.end)
        sheet.vm.onSave()
        runCurrent()
        assertEquals(at(3, 12), core.entry("sample-sleep-0205")!!.endMillis)
    }

    @Test
    fun cancelDiscardsTheDraft() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-sleep-1240")
        sheet.vm.onNudge(TimeField.START, -5)
        sheet.vm.onDismiss()
        runCurrent()
        assertNull(sheet.vm.state.value)
        assertEquals(at(12, 40), core.entry("sample-sleep-1240")!!.startMillis)
    }

    @Test
    fun aHalfMadeCorrectionSurvivesProcessDeath() = coreTest { core ->
        val saved = SavedStateHandle()
        val first = core.sheet(saved)
        first.vm.openEntry("sample-sleep-1240")
        first.vm.onNudge(TimeField.START, -5)
        state(first)

        val restored = core.sheet(saved)
        val state = state(restored)
        assertEquals(at(12, 35), state.draft.start)
        assertEquals(at(12, 40), state.wasStart)
    }

    /** The watch ends a sleep while its start is being corrected here: the end is kept, not overwritten. */
    @Test
    fun anEndFromTheWatchWhileEditingIsNotLost() = coreTest(now = SampleData.NOW_0312, seed = SampleData.scenarioAt0312(SampleData.ZONE)) { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-sleep-0205")
        sheet.vm.onNudge(TimeField.START, -5)
        assertNull(state(sheet).draft.end)
        val stored = core.entry("sample-sleep-0205")!!
        core.repository.applyRemote(stored.copy(endMillis = at(3, 12), updatedAtMillis = stored.updatedAtMillis + 1))
        runCurrent()

        sheet.vm.onSave()
        runCurrent()
        assertEquals("the watch's Woke up is kept", at(3, 12), core.entry("sample-sleep-0205")!!.endMillis)
        // The sheet shows the merged change (the new end, this start) and asks for a second look.
        val shown = state(sheet)
        assertEquals(at(2, 0), shown.draft.start)
        assertEquals(at(3, 12), shown.draft.end)
        assertEquals(EditIssue.ChangedElsewhere(deleted = false), shown.issue)
        assertTrue(shown.canSave)
        assertEquals(HomeHaptic.REJECT, sheet.haptics.last())

        sheet.vm.onSave()
        runCurrent()
        val saved = core.entry("sample-sleep-0205")!!
        assertEquals(at(2, 0), saved.startMillis)
        assertEquals(at(3, 12), saved.endMillis)
        assertNull("the sheet closed", sheet.vm.state.value)
    }

    @Test
    fun anEntryDeletedOnTheWatchWhileEditingIsNotBroughtBack() = coreTest { core ->
        val sheet = core.sheet()
        sheet.vm.openEntry("sample-sleep-1240")
        sheet.vm.onNudge(TimeField.START, -5)
        state(sheet)
        val stored = core.entry("sample-sleep-1240")!!
        core.repository.applyRemote(stored.copy(deleted = true, updatedAtMillis = stored.updatedAtMillis + 1))
        runCurrent()

        sheet.vm.onSave()
        val shown = state(sheet)
        assertTrue("still deleted", core.entry("sample-sleep-1240")!!.deleted)
        assertEquals(EditIssue.ChangedElsewhere(deleted = true), shown.issue)
        assertFalse(shown.canSave)
    }
}
