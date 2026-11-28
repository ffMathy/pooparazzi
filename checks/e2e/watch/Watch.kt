package com.ffmathy.pooparazzi.checks

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.expression.VersionBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourcesCallback
import androidx.wear.tiles.ResourcesData
import androidx.wear.tiles.ResourcesRequestData
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileCallback
import androidx.wear.tiles.TileData
import androidx.wear.tiles.TileProvider
import androidx.wear.tiles.TileRequestData
import androidx.wear.tiles.TileService
import androidx.wear.tiles.renderer.TileRenderer
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import org.robolectric.Robolectric
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.wear.MainActivity
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The checks' watch: the real watch app (WearApp and MainActivity, with its Room database, settings and every screen)
 * on a 227 dp round watch, run on the JVM by Robolectric on API 36 (Wear OS 6), as the watch app's own tests run it.
 * There is no phone: the watch logs on its own, as it does when the phone is out of reach. A check sees and does only
 * what a user can: it finds what is on the screen by its text or its spoken name, taps it, turns the crown, swipes
 * back and lets time pass. It never reaches into the app's code, so it works the same against a rebuilt app.
 *
 * It is the counterpart of the phone's harness (checks/e2e/Phone.kt), with the same helper names.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
abstract class Watch {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /**
     * The screen's frame clock follows the wall clock (see [pass]) instead of running ahead to the end of every
     * animation, so Logged's five seconds and its first inert second last as long as they do on a watch.
     */
    @Before
    fun followTheWallClock() {
        compose.mainClock.autoAdvance = false
    }

    @After
    fun closeTheApp() {
        Core.reset()
    }

    /**
     * A new install, opened from the launcher: Main with nothing logged. The watch has no first run; its notification
     * permission is allowed, as a parent allows it when the first sleep asks.
     */
    fun freshInstall() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        waitFor("Start sleep", 30_000)
        waitFor("Feed. No feeds yet")
        assertShows("Diaper. No diapers yet")
        // Main ignores nothing, but the first frames settle.
        pass(500)
    }

    /** Waits until something on the screen reads [text] (or is spoken as [text]). */
    fun waitFor(text: String, timeoutMillis: Long = 10_000) {
        waitUntil("\"$text\"", timeoutMillis) { all(text).isNotEmpty() }
    }

    /**
     * Waits until [condition] holds for what is on the screen, letting time pass meanwhile; fails with the screen after
     * [timeoutMillis].
     */
    fun waitUntil(what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("waited ${timeoutMillis / 1000} s for $what:\n${screen()}")
            pass(FRAME_MILLIS * 3)
        }
    }

    /** Waits until nothing on the screen reads [text]. */
    fun waitUntilGone(text: String, timeoutMillis: Long = 10_000) {
        waitUntil("no \"$text\"", timeoutMillis) { all(text).isEmpty() }
    }

    /** Whether something on the screen reads [text] (or is spoken as [text]). */
    fun shows(text: String): Boolean = all(text).isNotEmpty()

    /** Whether some text on the screen, or something's spoken name or state ("Asleep since 2:05 PM"), contains [part]. */
    fun showsPart(part: String): Boolean {
        val inState = SemanticsMatcher("state containing \"$part\"") { it.config.getOrNull(SemanticsProperties.StateDescription)?.contains(part) == true }
        return compose.onAllNodes(hasText(part, substring = true) or hasContentDescription(part, substring = true) or inState, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    fun assertShowsPart(part: String) = assertTrue("expected something containing \"$part\" on the screen:\n${screen()}", showsPart(part))

    fun assertGonePart(part: String) = assertTrue("expected nothing containing \"$part\" on the screen:\n${screen()}", !showsPart(part))

    /** Whole clock minutes from one shown clock time to another ("4:20 PM" to "4:41 PM" is 21), as the app counts durations. */
    fun clockMinutesBetween(from: String, to: String): Long {
        val minutes = java.time.Duration.between(parse(from), parse(to)).toMinutes()
        return if (minutes < 0) minutes + 24 * 60 else minutes
    }

    /** Taps what reads [text], or the button spoken as [text], and gives the app a moment to answer. */
    fun tap(text: String) {
        tileInFront = false
        button(text).performClick()
        pass(300)
    }

    /**
     * Taps the button whose text or spoken name starts with [start]: Main's rows are spoken with what they show
     * ("Feed. Last feed, food, 12 minutes ago, at 14:20"), so tapStarting("Feed. ") taps the Feed row.
     */
    fun tapStarting(start: String) {
        val matcher = SemanticsMatcher("text or spoken name starting with \"$start\"") { node ->
            (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
                .any { it.startsWith(start) }
        } and hasClickAction()
        tileInFront = false
        compose.onAllNodes(matcher)[0].performClick()
        pass(300)
    }

    /** Taps the screen near its top edge, where nothing else is: on Logged, its dismiss target ("Dismiss"). */
    fun tapTheBackground() {
        tileInFront = false
        compose.onRoot().performTouchInput { click(Offset(centerX, height * 0.08f)) }
        pass(300)
    }

    /**
     * Swipes right, the watch's back gesture: the screen on top is dismissed. On Wear OS 6 the system turns the swipe
     * into back (predictive back), so this sends the system's back, as the gesture does on a watch.
     */
    fun swipeToDismiss() {
        tileInFront = false
        Espresso.pressBack()
        pass(800)
    }

    /** Scrolls the list on the screen with a swipe up, as a finger does. */
    fun swipeUp() {
        compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.75f, endY = top + bottom * 0.25f, durationMillis = 300) }
        pass(500)
    }

    /**
     * Turns the crown until what reads [text] (or is spoken as [text]) sits in the open middle of the screen, clear of
     * the round edge and of the button pinned at the bottom, at most [turns] times.
     */
    fun scrollTo(text: String, turns: Int = 12) = scroll(text, turns, down = true)

    /** As [scrollTo], back up the list: anticlockwise, for what the list has scrolled past. */
    fun scrollBackTo(text: String, turns: Int = 12) = scroll(text, turns, down = false)

    private fun scroll(text: String, turns: Int, down: Boolean) {
        var turned = false
        repeat(turns) {
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val node = all(text).firstOrNull()?.boundsInRoot
            val y = node?.center?.y?.div(root.height)
            when {
                y == null -> turnCrown(if (down) 2 else -2)
                y > 0.6f -> turnCrown(2)
                y < 0.15f -> turnCrown(-1)
                else -> {
                    // The list snaps into place before a finger can tap in it.
                    if (turned) pass(700)
                    return
                }
            }
            turned = true
        }
        assertShows(text)
    }

    /**
     * Turns the crown by [steps] detents: clockwise for a positive number, anticlockwise for a negative one. One step is
     * a short turn (about 18 dp of rotation, one detent of a rotating bezel).
     */
    fun turnCrown(steps: Int) {
        val density = compose.activity.resources.displayMetrics.density
        compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(steps * CROWN_STEP_DP * density) }
        pass(300)
    }

    /** Whether what reads [text] can be tapped now: it is shown, and enabled. */
    fun canTap(text: String): Boolean =
        compose.onAllNodes(named(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodes(named(text) and hasClickAction() and isEnabled(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Lets [millis] go by: the watch's wall clock, its uptime clock and the screen's frame clock (its animations and
     * timers) all move on together, a frame at a time, and the app catches up.
     */
    fun pass(millis: Long) {
        val start = System.currentTimeMillis()
        val end = start + millis
        var uptime = 0L
        var frames = 0L
        while (true) {
            val now = System.currentTimeMillis()
            // The uptime clock and the frame clock catch up with the wall clock, however long the app took to answer.
            val elapsed = minOf(now, end) - start
            if (elapsed > uptime) {
                ShadowLooper.idleMainLooper(elapsed - uptime, TimeUnit.MILLISECONDS)
                uptime = elapsed
            }
            val due = elapsed / FRAME_MILLIS
            if (due > frames) {
                compose.mainClock.advanceTimeBy((due - frames) * FRAME_MILLIS, ignoreFrameDuration = true)
                frames = due
            }
            if (now >= end) break
            Thread.sleep(minOf(FRAME_MILLIS, end - now))
        }
        compose.waitForIdle()
    }

    /** The watch's clock time as the app writes it, [minutesFromNow] from now: "14:32", or "2:32 PM" on a 12-hour watch. */
    fun clock(minutesFromNow: Long = 0): String = format(LocalTime.now().plusMinutes(minutesFromNow))

    /** The clock times [minutesFromNow] from now and a minute before: what the app wrote a moment ago, if the minute has turned since. */
    fun recentClocks(minutesFromNow: Long = 0): List<String> =
        listOf(0L, -1L).map { format(LocalTime.now().plusMinutes(minutesFromNow + it)) }.distinct()

    /** Whether the screen shows [text] with "{time}" as the clock time [minutesFromNow] from now (or a minute before). */
    fun showsAt(text: String, minutesFromNow: Long = 0): Boolean =
        recentClocks(minutesFromNow).any { shows(text.replace("{time}", it)) }

    fun assertShowsAt(text: String, minutesFromNow: Long = 0) =
        assertTrue("expected \"$text\" with {time} one of ${recentClocks(minutesFromNow)} on the screen:\n${screen()}", showsAt(text, minutesFromNow))

    fun assertShows(text: String) = assertTrue("expected \"$text\" on the screen:\n${screen()}", shows(text))

    fun assertGone(text: String) = assertTrue("expected no \"$text\" on the screen:\n${screen()}", !shows(text))

    fun assertCanTap(text: String) = assertTrue("expected \"$text\" to be tappable:\n${screen()}", canTap(text))

    fun assertCannotTap(text: String) = assertTrue("expected \"$text\" shown but not tappable:\n${screen()}", shows(text) && !canTap(text))

    /** Every text, spoken name and state on the screen, top to bottom, for a failure message. */
    fun screen(): String = compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        .lines().map { it.trimStart(' ', '|', '-').trim() }
        .filter {
            it.startsWith("Text = ") || it.startsWith("ContentDescription = ") || it.startsWith("StateDescription = ") ||
                it.startsWith("PaneTitle = ") || it.startsWith("ToggleableState = ") || it.startsWith("Selected = ") || it == "[Disabled]"
        }
        .joinToString("\n")

    /** What a derived check pictures when its scenario ends, as [name].png: the watch's screen ([saveScreen]). */
    open fun pictureTheScreen(name: String) = saveScreen(name)

    /**
     * Saves what the watch shows now as [name].png where an AIL derived check is told to (AIL_SHOTS_DIR, passed on by
     * scripts/ail/check.sh), once the screen has settled: a screen still sliding in or out (a swipe to dismiss, a
     * screen opening or closing) is let finish first ([settle]). End-to-end checks don't call it.
     */
    fun saveScreen(name: String) {
        val dir = listOf(System.getProperty("ail.shots"), System.getenv("AIL_SHOTS_DIR")).firstOrNull { !it.isNullOrBlank() } ?: return
        File(dir).mkdirs()
        settle()
        // Roborazzi writes images only while recording, which its Gradle plugin switches on for its own record tasks.
        System.setProperty("roborazzi.test.record", "true")
        captureScreenRoboImage(File(dir, "$name.png").path)
    }

    /**
     * Lets what is moving on the screen come to rest, without waiting for anything a scenario names: until the screen
     * has looked the same, pixel for pixel, for two looks in a row, 100 ms apart (the clocks move on as in [pass]), or
     * [maxMillis] has gone by (Logged's bezel arc drains all the time, so its screen is never still).
     */
    fun settle(maxMillis: Long = 1_500) {
        compose.waitForIdle()
        var last = look()
        var still = 0
        val until = System.currentTimeMillis() + maxMillis
        while (still < 2 && System.currentTimeMillis() < until) {
            pass(100)
            // A whole frame too: pass moves the frame clock without drawing each frame, and a screen's transition
            // can wait for a drawn one.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val now = look()
            still = if (now != null && last != null && now.sameAs(last)) still + 1 else 0
            last = now
        }
    }

    /** The watch's screen as it is drawn now, or nothing when it can't be drawn. */
    private fun look(): android.graphics.Bitmap? = runCatching { compose.onRoot().captureToImage().asAndroidBitmap() }.getOrNull()

    // ---- The watch face and the tile carousel: the Tile, the complications and the Ongoing Activity ------------------

    /**
     * The Tile, as the watch's tile carousel shows it now: the system binds the app's tile service, asks it for a tile
     * (renderer 1.500, so its times tick), and the real ProtoLayout renderer draws it. [tileShows], [tileScreen] and
     * [tapTile] read and tap what it drew; [tapTile] on a button that logs asks for the next tile, as the host does.
     */
    fun openTile() = requestTile(clickId = null)

    /** Whether the Tile drew a text, or something spoken as, [text]. */
    fun tileShows(text: String): Boolean = tileViews().any { it.reads(text) }

    /** Whether some text on the Tile, or something's spoken name there, contains [part]. */
    fun tileShowsPart(part: String): Boolean = tileViews().any { it.readsPart(part) }

    fun assertTileShows(text: String) = assertTrue("expected \"$text\" on the Tile:\n${tileScreen()}", tileShows(text))

    fun assertTileShowsPart(part: String) = assertTrue("expected something containing \"$part\" on the Tile:\n${tileScreen()}", tileShowsPart(part))

    /** Every text and spoken name the Tile drew, top to bottom. */
    fun tileScreen(): String = tileViews().flatMap { view ->
        listOfNotNull(
            view.contentDescription?.let { "ContentDescription = '$it'" },
            (view as? TextView)?.text?.let { "Text = '$it'" },
        )
    }.joinToString("\n")

    /**
     * Taps what the Tile drew as [text] (or spoke as [text]), or the Tile's background with [text] null. A button that
     * logs asks the tile service for its next tile at once; a button that opens the app brings the app up, at the
     * screen the Tile asked for.
     */
    fun tapTile(text: String?) {
        val views = tileViews()
        val target = if (text == null) {
            views.firstOrNull { it.isClickable && it.contentDescription == null && (it as? TextView)?.text.isNullOrEmpty() }
        } else {
            views.firstOrNull { it.reads(text) }?.let { found -> generateSequence(found) { it.parent as? View }.firstOrNull { it.isClickable } }
        } ?: throw AssertionError("nothing to tap for ${text ?: "the background"} on the Tile:\n${tileScreen()}")
        target.performClick()
        ShadowLooper.idleMainLooper()
        pendingTileClick?.let { pendingTileClick = null; requestTile(it) }
        launchWhatWasStarted()
        pass(300)
    }

    /**
     * What the complication the parent put on the watch face shows now, by its name in the face's editor ("Since last
     * feed", "Last diaper", "Asleep or awake", "Sleep and last feed"): its text, then its title, then its spoken name,
     * one per line (a missing one left out).
     */
    fun complication(name: String): String {
        val data = complicationData(name)
        val res = app.resources
        val now = Instant.now()
        fun ComplicationText?.at(): String? = this?.getTextAt(res, now)?.toString()?.takeIf { it.isNotEmpty() }
        val lines = when (data) {
            is ShortTextComplicationData -> listOf(data.text.at(), data.title.at(), data.contentDescription.at())
            is LongTextComplicationData -> listOf(data.text.at(), data.title.at(), data.contentDescription.at())
            else -> listOf(data.toString())
        }
        return lines.filterNotNull().joinToString("\n")
    }

    /** Taps the complication named [name] on the watch face: the app opens where it says. */
    fun tapComplication(name: String) {
        val tap = complicationData(name).tapAction ?: throw AssertionError("the complication $name can't be tapped")
        tap.send()
        ShadowLooper.idleMainLooper()
        launchWhatWasStarted()
        pass(300)
    }

    /**
     * The Ongoing Activity on the watch face: what its status says now ("Asleep 5m"), or null when the app shows none.
     */
    fun ongoingActivity(): String? {
        pass(300)
        val manager = app.getSystemService(NotificationManager::class.java)
        val ongoing = manager.activeNotifications.firstOrNull { it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 }
        return ongoing?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    }

    /** Taps the Ongoing Activity's icon on the watch face: the app opens. */
    fun tapOngoingActivity() {
        val manager = app.getSystemService(NotificationManager::class.java)
        val ongoing = manager.activeNotifications.firstOrNull { it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 }
            ?: throw AssertionError("no Ongoing Activity on the watch face")
        ongoing.notification.contentIntent.send()
        ShadowLooper.idleMainLooper()
        launchWhatWasStarted()
        pass(300)
    }

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** The tile service the system holds while the Tile is shown (its binder keeps only a weak reference to it). */
    private var tileService: Service? = null
    private var tileProvider: TileProvider? = null
    private var tileRoot: FrameLayout? = null
    private var pendingTileClick: String? = null

    /** Whether the parent is looking at the Tile: it was opened or tapped since the app last came to the front. */
    private var tileInFront = false

    /**
     * What the Tile checks picture when a scenario ends, as [name].png: the Tile, as the carousel last drew it, while
     * the parent is looking at it ([saveTile]); else the app's screen ([saveScreen]).
     */
    fun pictureTheTileOrScreen(name: String) = if (tileInFront) saveTile(name) else saveScreen(name)

    /** Saves the Tile as the carousel last drew it, filling the round screen on black, as [name].png. */
    fun saveTile(name: String) {
        val dir = listOf(System.getProperty("ail.shots"), System.getenv("AIL_SHOTS_DIR")).firstOrNull { !it.isNullOrBlank() } ?: return
        File(dir).mkdirs()
        val tile = tileRoot ?: return saveScreen(name)
        val activity = compose.activity
        val carousel = FrameLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            isClickable = true
        }
        val window = activity.window.decorView as ViewGroup
        compose.runOnUiThread {
            (tile.parent as? ViewGroup)?.removeView(tile)
            carousel.addView(tile, FrameLayout.LayoutParams(tile.layoutParams.width, tile.layoutParams.height, android.view.Gravity.CENTER))
            window.addView(carousel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        try {
            ShadowLooper.idleMainLooper()
            System.setProperty("roborazzi.test.record", "true")
            captureScreenRoboImage(File(dir, "$name.png").path)
        } finally {
            compose.runOnUiThread { window.removeView(carousel); carousel.removeView(tile) }
        }
    }

    private fun requestTile(clickId: String?) {
        val provider = tileProvider ?: run {
            val intent = Intent(TileService.ACTION_BIND_TILE_PROVIDER).setPackage(app.packageName)
            val info = app.packageManager.queryIntentServices(intent, 0).single().serviceInfo
            @Suppress("UNCHECKED_CAST")
            val type = Class.forName(info.name) as Class<out Service>
            val service = Robolectric.buildService(type, intent).create().get()
            tileService = service
            TileProvider.Stub.asInterface(service.onBind(intent)).also { tileProvider = it }
        }
        val request = RequestBuilders.TileRequest.Builder().setTileId(TILE_ID).setDeviceConfiguration(device())
        if (clickId != null) request.setCurrentState(clickedState(clickId))
        val answer = AtomicReference<TileData>()
        provider.onTileRequest(
            TILE_ID,
            TileRequestData(protoBytes(request.build()), TileRequestData.VERSION_PROTOBUF),
            object : TileCallback.Stub() {
                override fun updateTileData(tileData: TileData) = answer.set(tileData)
            },
        )
        awaitService { answer.get() != null }
        val tile = fromProto("androidx.wear.tiles.proto.TileProto\$Tile", answer.get().contents, TileBuilders.Tile::class.java)
        val resources = requestTileResources(provider, tile.resourcesVersion)
        val now = System.currentTimeMillis()
        val entry = tile.tileTimeline!!.timelineEntries.firstOrNull { entry ->
            val validity = entry.validity
            validity == null || (validity.startMillis <= now && (validity.endMillis == 0L || now < validity.endMillis))
        } ?: tile.tileTimeline!!.timelineEntries.first()
        val density = app.resources.displayMetrics.density
        val sizePx = (WATCH_DP * density).roundToInt()
        val root = FrameLayout(compose.activity)
        root.layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
        val renderer = TileRenderer(compose.activity, Runnable::run) { state: StateBuilders.State -> pendingTileClick = state.lastClickableId }
        val inflated = renderer.inflateAsync(entry.layout!!, resources, root)
        awaitService { inflated.isDone }
        inflated.get()
        root.measure(View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, sizePx, sizePx)
        ShadowLooper.idleMainLooper()
        tileRoot = root
        tileInFront = true
    }

    private fun requestTileResources(provider: TileProvider, version: String): ResourceBuilders.Resources {
        val request = RequestBuilders.ResourcesRequest.Builder().setTileId(TILE_ID).setVersion(version).setDeviceConfiguration(device()).build()
        val answer = AtomicReference<ResourcesData>()
        provider.onResourcesRequest(
            TILE_ID,
            ResourcesRequestData(protoBytes(request), ResourcesRequestData.VERSION_PROTOBUF),
            object : ResourcesCallback.Stub() {
                override fun updateResources(resourcesData: ResourcesData) = answer.set(resourcesData)
            },
        )
        awaitService { answer.get() != null }
        return fromProto("androidx.wear.protolayout.proto.ResourceProto\$Resources", answer.get().contents, ResourceBuilders.Resources::class.java)
    }

    private fun device() = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(WATCH_DP)
        .setScreenHeightDp(WATCH_DP)
        .setScreenDensity(app.resources.displayMetrics.density)
        .setFontScale(app.resources.configuration.fontScale)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
        .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
        .setRendererSchemaVersion(VersionBuilders.VersionInfo.Builder().setMajor(1).setMinor(500).build())
        .build()

    /** The host fills in the tapped clickable's id; it is only settable on the proto. */
    private fun clickedState(clickId: String): StateBuilders.State {
        val protoClass = Class.forName("androidx.wear.protolayout.proto.StateProto\$State")
        val builder = protoClass.getMethod("newBuilder").invoke(null)
        builder.javaClass.getMethod("setLastClickableId", String::class.java).invoke(builder, clickId)
        val proto = builder.javaClass.getMethod("build").invoke(builder)
        return StateBuilders.State::class.java.getMethod("fromProto", protoClass).invoke(null, proto) as StateBuilders.State
    }

    /** The tiles library's proto classes are runtime-only: (de)serialised through reflection, as a host does with bytes. */
    private fun protoBytes(builderOutput: Any): ByteArray {
        val proto = builderOutput.javaClass.getMethod("toProto").invoke(builderOutput)
        return proto.javaClass.getMethod("toByteArray").invoke(proto) as ByteArray
    }

    private fun <T> fromProto(protoClass: String, bytes: ByteArray, type: Class<T>): T {
        val proto = Class.forName(protoClass).getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes)
        @Suppress("UNCHECKED_CAST")
        return type.getMethod("fromProto", proto.javaClass).invoke(null, proto) as T
    }

    private fun tileViews(): List<View> {
        val root = tileRoot ?: throw AssertionError("the Tile isn't open: openTile() first")
        fun walk(view: View): List<View> = listOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { walk(group.getChildAt(it)) } } ?: emptyList())
        return walk(root).filter { it.visibility == View.VISIBLE }
    }

    private fun View.reads(text: String) = contentDescription?.toString() == text || (this as? TextView)?.text?.toString() == text

    private fun View.readsPart(part: String) =
        contentDescription?.toString()?.contains(part) == true || (this as? TextView)?.text?.toString()?.contains(part) == true

    private fun complicationData(name: String): ComplicationData {
        val intent = Intent(ComplicationDataSourceService.ACTION_COMPLICATION_UPDATE_REQUEST).setPackage(app.packageName)
        val info = app.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
            .map { it.serviceInfo }
            .firstOrNull { it.loadLabel(app.packageManager).toString() == name }
            ?: throw AssertionError("no complication named $name")
        val types = info.metaData?.getString(ComplicationDataSourceService.METADATA_KEY_SUPPORTED_TYPES).orEmpty()
        val type = when (types.split(",").first().trim()) {
            "LONG_TEXT" -> ComplicationType.LONG_TEXT
            else -> ComplicationType.SHORT_TEXT
        }
        @Suppress("UNCHECKED_CAST")
        val serviceClass = Class.forName(info.name) as Class<out ComplicationDataSourceService>
        val service = Robolectric.buildService(serviceClass, intent).create().get()
        service.onBind(intent)
        val answer = AtomicReference<Result<ComplicationData?>>()
        service.onComplicationRequest(
            ComplicationRequest(1, type, false),
            object : ComplicationDataSourceService.ComplicationRequestListener {
                override fun onComplicationData(complicationData: ComplicationData?) {
                    answer.set(Result.success(complicationData))
                }
            },
        )
        awaitService { answer.get() != null }
        return answer.get().getOrThrow() ?: throw AssertionError("the complication $name sent nothing")
    }

    /** An activity the Tile, a complication or the Ongoing Activity started: the system brings the app up with it. */
    private fun launchWhatWasStarted() {
        val started = shadowOf(compose.activity).nextStartedActivity ?: shadowOf(app).nextStartedActivity ?: return
        tileInFront = false
        compose.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(compose.activity, started) }
        pass(500)
    }

    /** Runs the main looper (the services' answers) while their background work (Room) finishes. */
    private fun awaitService(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!condition()) {
            ShadowLooper.idleMainLooper()
            if (condition()) break
            check(System.currentTimeMillis() < deadline) { "the watch's service didn't answer in 30 s" }
            Thread.sleep(10)
        }
    }

    private fun parse(shown: String): LocalTime =
        LocalTime.parse(shown, DateTimeFormatter.ofPattern(if (shown.endsWith("M")) "h:mm a" else "HH:mm", Locale.US))

    private fun format(time: LocalTime): String {
        val is24 = DateFormat.is24HourFormat(ApplicationProvider.getApplicationContext<Application>())
        return time.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.US))
    }

    private fun named(text: String): SemanticsMatcher =
        hasText(text) or hasContentDescription(text) or SemanticsMatcher("window titled $text") {
            it.config.getOrNull(SemanticsProperties.PaneTitle) == text
        }

    private fun all(text: String) = compose.onAllNodes(named(text), useUnmergedTree = true).fetchSemanticsNodes()


    /** The tappable node that reads [text]: the button itself when the text sits inside one. */
    private fun button(text: String): SemanticsNodeInteraction {
        val clickable = compose.onAllNodes(named(text) and hasClickAction())
        return if (clickable.fetchSemanticsNodes().isNotEmpty()) clickable[0] else compose.onAllNodes(named(text), useUnmergedTree = true)[0]
    }

    private companion object {
        /** One detent of the crown, in dp of rotation (the watch's Time screen counts a step per 18 dp). */
        const val CROWN_STEP_DP = 18f

        /** One frame of the screen's clock. */
        const val FRAME_MILLIS = 16L

        /** The watch's screen, in dp (its qualifiers). */
        const val WATCH_DP = 227

        /** The id the tile carousel gives the Tile. */
        const val TILE_ID = 7
    }
}
