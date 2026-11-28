package com.ffmathy.pooparazzi.checks

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import java.util.concurrent.TimeUnit
import org.robolectric.shadows.ShadowLooper

/*
 * The steps the setup, Settings and babies scenarios share: typing a name, picking a birth date, adding babies through
 * Settings › Babies, and switching baby on Home, the way a parent does it. Only what is on the screen is used.
 */

/**
 * Lets the phone catch up after a step in a dialog. On a phone, a dialog's window lays itself out on the next frame;
 * under Robolectric a second window (the Add a baby and Remove dialogs, the date picker) is never laid out on its own,
 * and something in it keeps asking for frames, so the test clock would never call the app idle. So while a dialog is
 * open the checks drive the frames themselves, as a phone would: the Compose clock, the main looper and a layout pass
 * of each window that asks for one. Once no dialog is open, the clock runs on its own again.
 */
fun Phone.settleDialogWindows() {
    compose.mainClock.autoAdvance = false
    var quiet = 0
    var frames = 0
    while ((quiet < 5 || frames < 15) && frames < 300) {
        compose.mainClock.advanceTimeByFrame()
        ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
        val pending = composeWindows().filter { it.hasPendingMeasureOrLayout }
        pending.forEach { it.measureAndLayoutForTest() }
        val busy = pending.isNotEmpty() || Recomposer.runningRecomposers.value.any { it.hasPendingWork }
        quiet = if (busy) 0 else quiet + 1
        frames++
    }
    if (windows().size <= 1 && composeWindows().size <= 1) compose.mainClock.autoAdvance = true
}

/**
 * Waits until [condition] holds while a dialog or a sheet may be open or closing, running its frames ([settleDialogWindows]) between
 * looks; fails with the screen after [timeoutMillis].
 */
fun Phone.waitInTopWindow(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMillis
    while (true) {
        settleDialogWindows()
        val auto = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        val met = try { condition() } finally { compose.mainClock.autoAdvance = auto }
        if (met) return
        if (System.currentTimeMillis() > end) throw AssertionError("waited ${timeoutMillis / 1000} s for $what:\n${screen()}")
        Thread.sleep(100)
    }
}

/** Waits until something reads [text] (or is spoken as [text]) while a dialog or a sheet may be open. */
fun Phone.waitForInTopWindow(text: String) = waitInTopWindow("\"$text\"") { shows(text) }

/** Waits until nothing reads [text] while a dialog or a sheet may be open or closing. */
fun Phone.waitUntilGoneInTopWindow(text: String) = waitInTopWindow("no \"$text\"") { !shows(text) }

/** Lets [millis] go by while a dialog may be open: real time, and the frames of [settleDialogWindows]. */
fun Phone.passInTopWindow(millis: Long) {
    Thread.sleep(millis)
    settleDialogWindows()
}

/**
 * Taps what reads [text] (or the button spoken as [text]) in the topmost window that has it: a dialog over Settings
 * before Settings itself, as a finger would. Then lets the dialog catch up ([settleDialogWindows]).
 */
fun Phone.tapInTopWindow(text: String) {
    val clickable = compose.onAllNodes((hasText(text) or hasContentDescription(text)) and hasClickAction())
    val count = clickable.fetchSemanticsNodes().size
    val node = if (count > 0) clickable[count - 1]
    else compose.onAllNodes(hasText(text) or hasContentDescription(text), useUnmergedTree = true).let { all -> all[all.fetchSemanticsNodes().size - 1] }
    // Below the fold (Settings scrolls): scrolled to first, as a parent would. Not everything is in a scrolling list.
    runCatching { node.performScrollTo() }
    settleDialogWindows()
    node.performClick()
    settleDialogWindows()
}

/**
 * Scrolls to what reads [text] (or the button spoken as [text]) and taps it, as a parent scrolls down Settings to a
 * row below the fold; then gives the app a moment, as tap does.
 */
fun Phone.scrollDownAndTap(text: String) {
    val clickable = compose.onAllNodes((hasText(text) or hasContentDescription(text)) and hasClickAction())
    val node = if (clickable.fetchSemanticsNodes().isNotEmpty()) clickable[0]
    else compose.onAllNodes(hasText(text) or hasContentDescription(text), useUnmergedTree = true)[0]
    node.performScrollTo()
    pass(300)
    node.performClick()
    pass(300)
}

/** Scrolls the list on the screen until something reading [text] is in it, as a parent scrolls down a long page. */
fun Phone.scrollListUntilShown(text: String) {
    compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text))
    pass(300)
}

/** The texts of what is selected or ticked on the screen (a radio row, a segment, a checkbox), in order. */
fun Phone.chosenOnScreen(): List<String> =
    compose.onAllNodes(isSelected() or isOn()).fetchSemanticsNodes().map { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    }

/** Types [text] into the last text field on the screen (the one in a dialog, when one is open), replacing what it held. */
fun Phone.typeBabyName(text: String) {
    val fields = compose.onAllNodes(hasSetTextAction())
    val field = fields[fields.fetchSemanticsNodes().size - 1]
    field.performTextReplacement(text)
    field.performImeAction()
    settleDialogWindows()
}

/** Types [text] into the name field [index] (0 is the first baby's), replacing what it held. */
fun Phone.typeBabyNameAt(index: Int, text: String) {
    val field = compose.onAllNodes(hasSetTextAction())[index]
    field.performTextReplacement(text)
    // Done on the keyboard: saves at once and lets go of the field, whose blinking cursor would keep the phone busy.
    field.performImeAction()
    settleDialogWindows()
}

/** In the open date picker, taps today (the picker reads it "Today, Friday, November 27, 2026") and OK. */
fun Phone.pickTodayAsBirthDate() {
    compose.onAllNodes(hasText("Today, ", substring = true) or hasText("I dag, ", substring = true), useUnmergedTree = true)[0].performClick()
    settleDialogWindows()
    tapInTopWindow("OK")
}

/** In Settings, types [name] into the first baby's Name field and waits out the save (500 ms after the last keystroke). */
fun Phone.nameFirstBaby(name: String) {
    typeBabyNameAt(0, name)
    pass(1_000)
}

/** Settings with two babies, "Noa" (the first, current one) and "Ida": names the first and adds the second. */
fun Phone.noaAndIdaInSettings() {
    nameFirstBaby("Noa")
    addBabyInSettings("Ida")
    waitForInTopWindow("Remove Ida")
}

/**
 * On Home with two or more babies: opens the Switch baby sheet from the top bar's switcher (spoken "<current>. Switch
 * baby") and taps [baby]; ends on Home with [baby] current.
 */
fun Phone.switchBabyTo(baby: String) {
    // Home ignores taps for a moment after a screen or a sheet closes.
    pass(1_000)
    val current = compose.onAllNodes(hasContentDescription(". Switch baby", substring = true)).fetchSemanticsNodes()
        .firstNotNullOf { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
    tapInTopWindow(current)
    waitForInTopWindow("Switch baby")
    tapInTopWindow(baby)
    waitForInTopWindow("$baby. Switch baby")
    waitUntilGoneInTopWindow("Switch baby")
}

/** Settings › Add a baby, named [name] (or left unnamed), then Add; ends on Settings. */
fun Phone.addBabyInSettings(name: String? = null) {
    tapInTopWindow("Add a baby")
    waitForInTopWindow("Cancel")
    if (name != null) typeBabyName(name)
    tapInTopWindow("Add")
    waitUntilGoneInTopWindow("Cancel")
}

/** The phone's windows (their root views), the activity's first. */
private fun windows(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
    val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    return (field.get(global) as List<View>).toList()
}

private fun composeWindows(): List<ViewRootForTest> {
    val windows = windows()
    fun walk(view: View): List<View> =
        listOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { walk(group.getChildAt(it)) } } ?: emptyList())
    return windows.flatMap(::walk).filterIsInstance<ViewRootForTest>()
}
