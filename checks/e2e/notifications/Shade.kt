package com.ffmathy.pooparazzi.checks

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.concurrent.Executors

/*
 * The rest of the phone around the app, for the widget and notification checks: the notification shade (what each
 * notification shows, its buttons, a tap or a swipe on it), the system's permission dialog, the system's notification
 * settings, and the phone's scheduler and alarms, which run what the app asked for once its time has come. A check
 * reads a notification only by what the shade shows: its title, text, expanded text, sub-text and buttons. Nothing
 * here calls the app's code.
 */

private val app: Application get() = ApplicationProvider.getApplicationContext()

/** A notification as the shade shows it. */
data class ShadeNotification(
    val title: String,
    val text: String,
    /** The expanded text, one line per line; [text] when it has none. */
    val expanded: List<String>,
    /** The line above the title (the baby's name on a prediction), or "". */
    val subText: String,
    val buttons: List<String>,
    /** Whether it stays until the app takes it away (it cannot be swiped away). */
    val ongoing: Boolean,
    internal val notification: Notification,
    internal val tag: String?,
    internal val id: Int,
) {
    override fun toString() = buildString {
        if (subText.isNotEmpty()) append("[$subText] ")
        append("\"$title\" / \"$text\"")
        if (expanded != listOf(text)) append(" expanded ${expanded.map { "\"$it\"" }}")
        if (buttons.isNotEmpty()) append(" buttons ${buttons.map { "\"$it\"" }}")
        if (ongoing) append(" ongoing")
    }
}

/** The notifications in the shade, in the order they were posted. */
fun Phone.shade(): List<ShadeNotification> {
    val manager = app.getSystemService(NotificationManager::class.java)
    return shadowOf(manager).activeNotifications.map { posted ->
        val n = posted.notification
        val extras = n.extras
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        ShadeNotification(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = text,
            expanded = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text).lines(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            buttons = n.actions.orEmpty().map { it.title.toString() },
            ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
            notification = n,
            tag = posted.tag,
            id = posted.id,
        )
    }
}

/**
 * What a notification check pictures when its scenario ends, as [name].png: the shade pulled down over the top of the
 * app, as the parent sees it. Each notification in it, newest first, is drawn as the system's own template draws it,
 * expanded (its title, text, expanded lines and buttons), on the shade's card; an empty shade reads "No
 * notifications". What the app shows stays visible under the shade.
 */
fun Phone.pictureTheShade(name: String) {
    val dir = shotsDir() ?: return
    settle()
    val activity = compose.activity
    val density = activity.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    val night = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).activeNotifications.sortedByDescending { it.postTime }
    val shade = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(if (night) 0xF2101012.toInt() else 0xF2E9E4EC.toInt())
        setPadding(dp(12), dp(40), dp(12), dp(16))
        isClickable = true
    }
    val card = if (night) 0xFF2B2930.toInt() else 0xFFFFFFFF.toInt()
    if (posted.isEmpty()) {
        shade.addView(TextView(activity).apply {
            text = "No notifications"
            setTextColor(if (night) 0xFFE6E1E5.toInt() else 0xFF1D1B20.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        })
    }
    for (each in posted) {
        val builder = Notification.Builder.recoverBuilder(activity, each.notification)
        val views = builder.createBigContentView() ?: builder.createContentView()
        val frame = FrameLayout(activity).apply {
            background = GradientDrawable().apply { setColor(card); cornerRadius = dp(24).toFloat() }
            clipToOutline = true
            addView(views.apply(activity, this))
        }
        shade.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }
    val window = activity.window.decorView as ViewGroup
    compose.runOnUiThread {
        window.addView(shade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
    }
    try {
        pass(300)
        // Roborazzi writes images only while recording, which its Gradle plugin switches on for its own record tasks.
        System.setProperty("roborazzi.test.record", "true")
        captureScreenRoboImage(File(dir, "$name.png").path)
    } finally {
        compose.runOnUiThread { window.removeView(shade) }
    }
}

/** What the shade shows, one notification a line, for a failure message. */
fun Phone.shadeScreen(): String = shade().joinToString("\n").ifEmpty { "(no notifications)" }

/** The notification in the shade titled [title], or nothing. */
fun Phone.notification(title: String): ShadeNotification? = shade().firstOrNull { it.title == title }

/** Waits until the shade shows a notification titled [title], and returns it. */
fun Phone.waitForNotification(title: String, timeoutMillis: Long = 10_000): ShadeNotification {
    waitUntilShade("a notification \"$title\"", timeoutMillis) { notification(title) != null }
    return notification(title)!!
}

/** Waits until [condition] holds for the shade; fails with what it shows after [timeoutMillis]. */
fun Phone.waitUntilShade(what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    val until = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
        if (System.currentTimeMillis() > until) throw AssertionError("waited ${timeoutMillis / 1000} s for $what in the shade:\n${shadeScreen()}")
        pass(200)
    }
}

fun Phone.assertNoNotification(title: String) =
    assertTrue("expected no notification \"$title\" in the shade:\n${shadeScreen()}", notification(title) == null)

/** Presses the button [button] of the notification titled [title], from the shade or the lock screen. */
fun Phone.pressNotificationButton(title: String, button: String) {
    val shown = notification(title) ?: throw AssertionError("no notification \"$title\" in the shade:\n${shadeScreen()}")
    val action = shown.notification.actions.orEmpty().firstOrNull { it.title.toString() == button }
        ?: throw AssertionError("the notification \"$title\" has no button \"$button\": $shown")
    action.actionIntent.send()
    pass(500)
}

/** Taps the notification titled [title]: the shade closes on what it opens. */
fun Phone.tapNotification(title: String) {
    val shown = notification(title) ?: throw AssertionError("no notification \"$title\" in the shade:\n${shadeScreen()}")
    shown.notification.contentIntent.send()
    pass(300)
    val started = shadowOf(app).nextStartedActivity ?: return
    bringTheAppForward(started)
    pass(800)
}

/** Swipes the notification titled [title] away, as the shade allows for one that is not ongoing (and tells the app). */
fun Phone.swipeAwayNotification(title: String) {
    val shown = notification(title) ?: throw AssertionError("no notification \"$title\" in the shade:\n${shadeScreen()}")
    app.getSystemService(NotificationManager::class.java).cancel(shown.tag, shown.id)
    shown.notification.deleteIntent?.send()
    pass(500)
}

/** Whether the system's dialog asks to allow the app's notifications now. */
fun Phone.asksToAllowNotifications(): Boolean =
    shadowOf(compose.activity).lastRequestedPermission?.requestedPermissions?.contains(Manifest.permission.POST_NOTIFICATIONS) == true &&
        shadowOf(compose.activity).lastRequestedPermission !== answered

private var answered: Any? = null

/** Answers the system's notification dialog with Allow ([allow]) or Don't allow. */
fun Phone.answerNotificationDialog(allow: Boolean) {
    val request = shadowOf(compose.activity).lastRequestedPermission ?: throw AssertionError("the system asks for nothing")
    if (allow) shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS) else shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val result = if (allow) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    answered = request
    compose.runOnUiThread { compose.activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(result)) }
    pass(800)
}

/** Notifications allowed in the system's settings, without the app asking (as on a phone where they were allowed before). */
fun Phone.allowNotificationsInSystemSettings() {
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    pass(300)
}

/**
 * Turns the app's notification category named [name] (as the system's notification settings list it, e.g. "Next
 * feed") off, or on again, as the parent does in Android's settings.
 */
fun Phone.switchNotificationCategory(name: String, on: Boolean) {
    val manager = app.getSystemService(NotificationManager::class.java)
    val channel = manager.notificationChannels.firstOrNull { it.name.toString() == name }
        ?: throw AssertionError("the system lists no notification category \"$name\": ${manager.notificationChannels.map { it.name }}")
    channel.importance = if (on) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_NONE
    pass(300)
}

/** The names of the app's notification categories in the system's settings, with whether each is on. */
fun Phone.notificationCategories(): Map<String, Boolean> =
    app.getSystemService(NotificationManager::class.java).notificationChannels
        .associate { it.name.toString() to (it.importance != NotificationManager.IMPORTANCE_NONE) }

/**
 * Starts the phone's job scheduler, which runs the work the app hands it (redrawing a widget, a refresh at a set time)
 * once it is due; [letThePhoneRun] runs what has come due. Each check's phone has its own.
 */
fun Phone.startTheJobScheduler() {
    if (schedulerFor === app) return
    WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(Executors.newCachedThreadPool()).build())
    schedulerFor = app
}

private var schedulerFor: Application? = null

/**
 * Lets [millis] go by on the phone (real time: seconds only), then runs what has come due as the phone would: the
 * app's alarms whose time has come, and the scheduled work whose delay is over.
 */
fun Phone.letThePhoneRun(millis: Long) {
    pass(millis)
    val now = System.currentTimeMillis()
    val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
    alarms.scheduledAlarms.filter { it.triggerAtMs <= now }.forEach { alarms.fireAlarm(it) }
    if (schedulerFor === app) {
        val work = WorkManager.getInstance(app)
        val driver = WorkManagerTestInitHelper.getTestDriver(app)!!
        work.getWorkInfos(WorkQuery.fromStates(WorkInfo.State.ENQUEUED)).get()
            .filter { it.nextScheduleTimeMillis <= now }
            .forEach { driver.setInitialDelayMet(it.id) }
    }
    pass(500)
}

/**
 * The app brought forward by [intent] (a widget's or a notification's): the open app is single-top, so the phone hands
 * it the intent (onNewIntent) rather than opening a second one.
 */
internal fun Phone.bringTheAppForward(intent: android.content.Intent) {
    val onNewIntent = android.app.Activity::class.java.getDeclaredMethod("onNewIntent", android.content.Intent::class.java).apply { isAccessible = true }
    compose.runOnUiThread {
        val activity = compose.activity
        val launched = activity.intent
        onNewIntent.invoke(activity, intent)
        // The test rule knows its activity by the intent it was started with: give that back, or it never sees it close.
        activity.intent = launched
    }
}

/**
 * The page of the system's notification settings the app just opened, by the name the system lists it under: a
 * category ("Next feed") or, for the app's own page, "the app"; nothing when none opened.
 */
fun Phone.openedNotificationSettings(): String? {
    val started = shadowOf(app).nextStartedActivity ?: shadowOf(compose.activity).nextStartedActivity ?: return null
    return when (started.action) {
        android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS -> {
            val id = started.getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID)
            app.getSystemService(NotificationManager::class.java).getNotificationChannel(id)?.name?.toString() ?: id
        }
        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS -> "the app"
        else -> null
    }
}

/** The app goes to the background and comes back to the front, as after a visit to the system's settings. */
fun Phone.leaveAndComeBackToTheApp() {
    compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
    compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    pass(800)
}
