package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import org.junit.Test

open class ExploreWidgetsChecks : Phone() {
    private fun show(label: String, w: HomeScreenWidget) = println("EXPLORE [$label]\n" + widgetScreen(w) + "\n---")
    private fun run(body: () -> Unit) = try { body() } catch (e: Throwable) { println("EXPLORE FAIL: $e\n${screen()}") }

    @Test
    fun widgetLogs() = run {
        freshInstall()
        val w = placeWidget("Log and status", 358, 208)
        val q = placeWidget("Feed, diaper and sleep", 260, 100)
        tap("Start sleep"); waitFor("Undo"); pass(600)
        tap("−15m"); tap("Done")
        letThePhoneRun(2500)
        show("asleep 15m", w); show("quick asleep", q)
        tapWidget(w, "Woke up, end sleep")
        waitForWidget(w, "Undo")
        show("woke receipt", w)
        tapWidgetBackground(w)
        pass(1000)
        println("EXPLORE app after bg with receipt: " + screen())
        goBack(); pass(1000)
        tapWidget(w, "Log feed"); pass(800)
        tap("Food")
        letThePhoneRun(1500)
        show("after food", w)
        println("EXPLORE app after food: " + screen())
        tapWidget(w, "Log diaper"); pass(800)
        tap("Poop"); tap("Save")
        letThePhoneRun(1500)
        show("after poop", w)
        tapWidget(w, "Start sleep")
        waitForWidget(w, "Undo")
        show("receipt start", w)
        tapWidget(w, "Undo")
        pass(800)
        show("undone", w)
        letThePhoneRun(2500)
        show("undone+2.5", w)
    }

    @Test
    fun notificationPaths() = run {
        freshInstall()
        tap("Start sleep"); waitFor("Undo"); pass(600)
        println("EXPLORE asks1: " + asksToAllowNotifications())
        answerNotificationDialog(false)
        println("EXPLORE shade after deny: " + shadeScreen())
        tap("Undo"); pass(2500)
        tap("Start sleep"); waitFor("Undo"); pass(600)
        println("EXPLORE asks2: " + asksToAllowNotifications())
        tap("Done"); pass(2500)
        allowNotificationsInSystemSettings()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        pass(1500)
        println("EXPLORE shade after allow in settings: " + shadeScreen())
        tapNotification(shade().first().title)
        println("EXPLORE app after tap: " + screen())
        pass(1500)
        tap("Feed"); waitFor("Food"); pass(600); tap("−15m"); tap("Food")
        pass(2500)
        println("EXPLORE shade after feed: " + shadeScreen())
        println("EXPLORE app after feed: " + screen())
    }

    @Test
    fun forecasts() = run {
        freshInstall()
        allowNotificationsInSystemSettings()
        feedsEveryThreeHours()
        feedJustTheTime()
        letThePhoneRun(1500)
        println("EXPLORE shade forecast: " + shadeScreen())
        val w = placeWidget("Log and status", 358, 208)
        show("forecast widget", w)
        tap("Settings"); pass(1500)
        println("EXPLORE settings: " + screen())
        switchNotificationCategory("Next feed", false)
        goBack(); pass(1000)
        feedJustTheTime()
        letThePhoneRun(1500)
        println("EXPLORE shade after off: " + shadeScreen())
        println("EXPLORE cats: " + notificationCategories())
        tap("Settings"); pass(1500)
        println("EXPLORE settings after off: " + screen())
    }

    @Test
    fun namedBaby() = run {
        freshInstall()
        allowNotificationsInSystemSettings()
        tap("Settings"); pass(1500)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Noa")
        pass(1500)
        goBack(); pass(1000)
        tap("Start sleep"); waitFor("Undo"); pass(600); tap("Done"); pass(2500)
        println("EXPLORE shade named: " + shadeScreen())
        val w = placeWidget("Log and status", 358, 208)
        show("named", w)
    }
}
