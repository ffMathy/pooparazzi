package com.ffmathy.pooparazzi.checks

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.WeakHashMap

/*
 * The phone's home screen, as a launcher runs it, for the widget checks: the widget picker lists the app's widgets by
 * their names ("Log and status", "Feed, diaper and sleep", "Cover screen"), a placed widget is asked for its content
 * as the system asks (the APPWIDGET_UPDATE broadcast) and drawn at its size from what the app sends, and a tap on it
 * goes where the app said (a broadcast back into the app, or the app opened on a screen). A check reads a widget only
 * by what it shows: its texts and its buttons' spoken names. Nothing here calls the app's code.
 */

/** A widget on the home screen: its id, and the size it is drawn at, in dp. */
class HomeScreenWidget(val id: Int, val widthDp: Int, val heightDp: Int)

private val app: Application get() = ApplicationProvider.getApplicationContext()

/**
 * Places the widget the picker lists as [name] at [widthDp] × [heightDp] on the home screen, and waits until it is
 * drawn. Starts the phone's job scheduler first (see [startTheJobScheduler]).
 */
fun Phone.placeWidget(name: String, widthDp: Int, heightDp: Int): HomeScreenWidget {
    startTheJobScheduler()
    val receiver = widgetPicker()[name] ?: throw AssertionError("the widget picker lists ${widgetPicker().keys}, not \"$name\"")
    val manager = AppWidgetManager.getInstance(app)
    val launcher = shadowOf(manager)
    val density = app.resources.displayMetrics.density
    val info = AppWidgetProviderInfo().apply {
        provider = receiver
        minWidth = (widthDp * density).toInt()
        minHeight = (heightDp * density).toInt()
    }
    val id = nextWidgetId++
    launcher.addInstalledProvider(info)
    launcher.addBoundWidget(id, info)
    manager.updateAppWidgetOptions(
        id,
        Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        },
    )
    deliverTo(receiver, Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setComponent(receiver).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id)))
    val widget = HomeScreenWidget(id, widthDp, heightDp)
    waitUntilWidget(widget, "the widget \"$name\" to be drawn", 30_000) { widgetTexts(widget).isNotEmpty() }
    lookingAt[this] = widget
    return widget
}

/**
 * Where the parent is looking: the widget they last placed, tapped or waited on on the home screen, or nothing once a
 * tap on it opened the app (the app is in front then, until the parent looks at the widget again).
 */
private val lookingAt = WeakHashMap<Phone, HomeScreenWidget?>()

/**
 * What a widget check pictures when its scenario ends, as [name].png: the home screen with the widget the parent
 * last placed or tapped, drawn at its size from what the app last sent, alone on a plain wallpaper ([saveWidget]); or
 * the app's screen ([Phone.saveScreen]) when a tap on the widget opened the app.
 */
fun Phone.pictureTheHomeScreen(name: String) = lookingAt[this]?.let { saveWidget(it, name) } ?: saveScreen(name)

/** Saves [widget] as the home screen shows it now, at its size in the middle of a plain wallpaper, as [name].png. */
fun Phone.saveWidget(widget: HomeScreenWidget, name: String) {
    val dir = shotsDir() ?: return
    settle()
    val density = app.resources.displayMetrics.density
    val view = widgetView(widget)
    val activity = compose.activity
    val homeScreen = FrameLayout(activity).apply {
        setBackgroundColor(WALLPAPER)
        isClickable = true
        addView(view, FrameLayout.LayoutParams((widget.widthDp * density).toInt(), (widget.heightDp * density).toInt(), Gravity.CENTER))
    }
    val window = activity.window.decorView as ViewGroup
    compose.runOnUiThread { window.addView(homeScreen, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
    try {
        pass(300)
        // Roborazzi writes images only while recording, which its Gradle plugin switches on for its own record tasks.
        System.setProperty("roborazzi.test.record", "true")
        captureScreenRoboImage(File(dir, "$name.png").path)
    } finally {
        compose.runOnUiThread { window.removeView(homeScreen) }
    }
}

/** The home screen's wallpaper behind a pictured widget: a plain mid grey, neither the Night nor the Day surface. */
private const val WALLPAPER = 0xFF6B635B.toInt()

private var nextWidgetId = 100

/**
 * The system's broadcast to one widget provider (the phone sends a placed widget's update to its own provider only).
 * Robolectric would hand an explicit APPWIDGET_UPDATE to every widget provider of the app, so it is delivered here.
 */
private fun Phone.deliverTo(receiver: ComponentName, intent: Intent) {
    val instance = Class.forName(receiver.className).getDeclaredConstructor().newInstance() as BroadcastReceiver
    val result = Class.forName("org.robolectric.shadows.ShadowBroadcastPendingResult")
        .getDeclaredMethod("create", Int::class.java, String::class.java, Bundle::class.java, Boolean::class.java)
        .apply { isAccessible = true }.invoke(null, 0, null, null, false)
    BroadcastReceiver::class.java.getDeclaredMethod("setPendingResult", BroadcastReceiver.PendingResult::class.java)
        .apply { isAccessible = true }.invoke(instance, result)
    compose.runOnUiThread { instance.onReceive(app, intent) }
}

/** The widget picker: the app's widgets by the names it lists them under. */
private fun widgetPicker(): Map<String, ComponentName> =
    app.packageManager.queryBroadcastReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(app.packageName), 0)
        .associate { it.loadLabel(app.packageManager).toString() to ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

/** What the app last sent the home screen for [widget]. */
private fun remoteViews(widget: HomeScreenWidget): RemoteViews? {
    val launcher = shadowOf(AppWidgetManager.getInstance(app))
    val infos = launcher.javaClass.getDeclaredField("widgetInfos").apply { isAccessible = true }.get(launcher) as Map<*, *>
    val info = infos[widget.id] ?: return null
    return info.javaClass.getDeclaredField("lastRemoteViews").apply { isAccessible = true }.get(info) as RemoteViews?
}

/** [widget] as the home screen draws it now: the app's content for its size (a launcher picks the layout that fits). */
fun Phone.widgetView(widget: HomeScreenWidget): View {
    val views = remoteViews(widget) ?: throw AssertionError("the widget ${widget.id} is not drawn")
    val size = SizeF(widget.widthDp.toFloat(), widget.heightDp.toFloat())
    val fitting = RemoteViews::class.java.getDeclaredMethod("getRemoteViewsToApply", Context::class.java, SizeF::class.java)
        .apply { isAccessible = true }.invoke(views, app, size) as RemoteViews
    var view: View? = null
    compose.runOnUiThread { view = fitting.apply(app, FrameLayout(app)) }
    return view!!
}

/** Every text and spoken name [widget] shows, top to bottom ("Text = …", "ContentDescription = …"), for a failure message. */
fun Phone.widgetScreen(widget: HomeScreenWidget): String = widgetTexts(widget).joinToString("\n")

private fun Phone.widgetTexts(widget: HomeScreenWidget): List<String> {
    if (remoteViews(widget) == null) return emptyList()
    val out = mutableListOf<String>()
    fun walk(view: View) {
        if (view.visibility != View.VISIBLE) return
        view.contentDescription?.let { out += "ContentDescription = $it" }
        (view as? TextView)?.text?.takeIf { it.isNotBlank() }?.let { out += "Text = ${it.toString().replace(' ', ' ')}" }
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
    }
    walk(widgetView(widget))
    return out
}

/** The widget's texts, and its buttons' spoken names, as plain strings (no-break spaces read as spaces). */
fun Phone.widgetReads(widget: HomeScreenWidget): List<String> = widgetTexts(widget).map { it.substringAfter(" = ") }

/** Whether [widget] shows a text, or a button spoken as, exactly [text]. */
fun Phone.widgetShows(widget: HomeScreenWidget, text: String): Boolean = widgetReads(widget).any { it == text }

/** Whether a text or a spoken name on [widget] contains [part]. */
fun Phone.widgetShowsPart(widget: HomeScreenWidget, part: String): Boolean = widgetReads(widget).any { it.contains(part) }

fun Phone.assertWidgetShows(widget: HomeScreenWidget, text: String) {
    lookingAt[this] = widget
    assertTrue("expected \"$text\" on the widget:\n${widgetScreen(widget)}", widgetShows(widget, text))
}

fun Phone.assertWidgetGone(widget: HomeScreenWidget, text: String) {
    lookingAt[this] = widget
    assertTrue("expected no \"$text\" on the widget:\n${widgetScreen(widget)}", !widgetShows(widget, text))
}

/** Waits until [widget] shows [text] (a text, or a button spoken as it). */
fun Phone.waitForWidget(widget: HomeScreenWidget, text: String, timeoutMillis: Long = 10_000) =
    waitUntilWidget(widget, "\"$text\"", timeoutMillis) { widgetShows(widget, text) }

/** Waits until [condition] holds for [widget]; fails with what it shows after [timeoutMillis]. */
fun Phone.waitUntilWidget(widget: HomeScreenWidget, what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    // Waiting for the widget to show something is looking at it: back on the home screen after the app.
    lookingAt[this] = widget
    val until = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
        if (System.currentTimeMillis() > until) throw AssertionError("waited ${timeoutMillis / 1000} s for $what on the widget:\n${widgetScreen(widget)}")
        pass(200)
    }
}

/**
 * Taps what [widget] shows as [text] (a text, or a button spoken as [text], or one whose spoken name starts with it):
 * the button it is part of. What the app answers is delivered as the phone delivers it: a broadcast to the app, or
 * the app brought forward on a screen.
 */
fun Phone.tapWidget(widget: HomeScreenWidget, text: String) {
    lookingAt[this] = widget
    val root = widgetView(widget)
    val target = find(root) { (it as? TextView)?.text?.toString()?.replace(' ', ' ') == text || it.contentDescription?.toString() == text }
        ?: find(root) { it.contentDescription?.toString()?.startsWith(text) == true }
        ?: throw AssertionError("no \"$text\" on the widget:\n${widgetScreen(widget)}")
    click(target)
}

/** Taps [widget] outside its buttons (its background). */
fun Phone.tapWidgetBackground(widget: HomeScreenWidget) {
    lookingAt[this] = widget
    click(find(widgetView(widget)) { it.hasOnClickListeners() } ?: throw AssertionError("the widget has no background to tap"))
}

private fun Phone.click(view: View) {
    var clickable: View? = view
    while (clickable != null && !clickable.hasOnClickListeners()) clickable = clickable.parent as? View
    val button = clickable ?: throw AssertionError("nothing to tap at $view")
    compose.runOnUiThread { button.performClick() }
    pass(300)
    openStartedActivity()
}

/** An activity the tap started (the app's own, on one of its screens) comes forward, as singleTop delivers it to the open app. */
private fun Phone.openStartedActivity() {
    val started = shadowOf(app).nextStartedActivity ?: shadowOf(compose.activity).nextStartedActivity ?: return
    bringTheAppForward(started)
    // The app in front now, unless what started was the system's (its permission dialog, asked earlier by the app).
    if (started.component?.packageName == app.packageName) lookingAt[this] = null
    pass(800)
}

private fun find(view: View, matches: (View) -> Boolean): View? {
    if (view.visibility != View.VISIBLE) return null
    if (matches(view)) return view
    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), matches)?.let { return it }
    return null
}
