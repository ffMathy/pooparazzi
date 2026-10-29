package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.phone.launch.DeepLinks
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import com.ffmathy.pooparazzi.phone.widget.NoticeType
import com.ffmathy.pooparazzi.phone.widget.WidgetMemo
import com.ffmathy.pooparazzi.phone.widget.WidgetNotice
import com.ffmathy.pooparazzi.phone.widget.WidgetSamples
import com.ffmathy.pooparazzi.phone.widget.WidgetSnapshot
import com.ffmathy.pooparazzi.phone.widget.WidgetUpdater
import com.ffmathy.pooparazzi.phone.widget.nextExpiry
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the widgets show (DESIGN_SPEC §3.3.6, §8.5) and the deep links they and the notifications open (§3.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WidgetSnapshotTest {
    private val zone = SampleData.ZONE

    @Test
    fun theReferenceScenarioAt1432() {
        val idle = WidgetSamples.idle(night = true)
        assertEquals(SampleData.at(12, 22), idle.lastFeed!!.startMillis)
        assertEquals(FeedType.FORMULA, idle.lastFeed!!.feedType)
        assertEquals(SampleData.at(13, 44), idle.lastDiaper!!.startMillis)
        assertEquals(SampleData.at(13, 27), idle.awakeSinceMillis)
        assertFalse(idle.asleep)
        assertEquals(HomeLaunch.Home, idle.backgroundLaunch)
    }

    @Test
    fun theWidgetFollowsTheAppsAppearanceNotTheLauncher() {
        val settings = SampleData.settings
        val entries = SampleData.scenario(zone)
        fun night(appearance: Appearance, now: Long, systemDark: Boolean = false) =
            WidgetSnapshot.of(entries, settings.copy(appearance = appearance), WidgetMemo(), now, zone, systemDark).night

        assertFalse("Auto at 14:32 is Day", night(Appearance.AUTO, SampleData.NOW_1432))
        assertTrue("Auto in the night window is Night", night(Appearance.AUTO, SampleData.at(19, 0)))
        assertTrue("Auto with the system dark theme is Night, as in the app", night(Appearance.AUTO, SampleData.NOW_1432, systemDark = true))
        assertFalse("Day is fixed", night(Appearance.DAY, SampleData.at(23, 0), systemDark = true))
        assertTrue("Night is fixed", night(Appearance.NIGHT, SampleData.NOW_1432))
        assertTrue("asleep is always Night under Auto", WidgetSamples.asleep().night)
    }

    @Test
    fun afterABreastFeedAndAsleepStates() {
        val fed = WidgetSamples.afterBreastfeed(night = false)
        assertEquals(SampleData.at(14, 32), fed.lastFeed!!.startMillis)
        assertEquals(FeedType.BREAST, fed.lastFeed!!.feedType)
        assertFalse("a breast feed is an instant", fed.asleep)
        val asleep = WidgetSamples.asleep()
        assertEquals(SampleData.at(2, 5), asleep.runningSleepStart)
        assertNull("no awake-since while asleep", asleep.awakeSinceMillis)
    }

    @Test
    fun noticesExpireAndTheNextExpiryIsTheEarliest() {
        val now = SampleData.NOW_1432
        val memo = WidgetMemo(notice = WidgetNotice(NoticeType.UNDONE, now, now + 2_000))
        val entries = SampleData.scenario(zone)
        assertEquals(NoticeType.UNDONE, WidgetSnapshot.of(entries, SampleData.settings, memo, now, zone, false).notice!!.type)
        assertNull(WidgetSnapshot.of(entries, SampleData.settings, memo, now + 2_000, zone, false).notice)
        val logged = WidgetSamples.justLogged(night = true)
        assertEquals(ReceiptKind.SLEEP_STARTED, logged.receipt!!.kind)
        assertEquals(now + 2_000, memo.copy(receipt = logged.receipt).nextExpiry(now))
        assertEquals(now + 10_000, memo.copy(receipt = logged.receipt).nextExpiry(now + 2_000))
        assertNull(memo.nextExpiry(now + 2_000))
    }

    @Test
    fun theNightWindowBoundaries() {
        assertEquals(SampleData.at(19, 0), WidgetUpdater.nextNightBoundary(SampleData.NOW_1432, zone))
        assertEquals(SampleData.at(7, 0), WidgetUpdater.nextNightBoundary(SampleData.NOW_0312, zone))
        assertEquals(SampleData.at(7, 0, SampleData.TODAY.plusDays(1)), WidgetUpdater.nextNightBoundary(SampleData.at(19, 0), zone))
    }

    @Test
    fun deepLinksRoundTrip() {
        val launches = listOf(
            HomeLaunch.Home,
            HomeLaunch.Feed,
            HomeLaunch.Diaper,
            HomeLaunch.Receipt(ReceiptKind.WOKE_UP, "sleep-1"),
            HomeLaunch.Receipt(ReceiptKind.LOGGED, "diaper-2"),
        )
        launches.forEach { assertEquals(it, DeepLinks.parse(DeepLinks.uri(it))) }
        assertEquals("pooparazzi://home/feed", DeepLinks.uri(HomeLaunch.Feed).toString())
        assertNull("other links are ignored", DeepLinks.parse(Uri.parse("https://example.com/home/feed")))
        assertEquals("an unknown sheet opens Home", HomeLaunch.Home, DeepLinks.parse(Uri.parse("pooparazzi://home/nothing")))
        assertTrue(HomeLaunch.Feed.opensSheet)
        assertEquals("the old End feed link opens Home", HomeLaunch.Home, DeepLinks.parse(Uri.parse("pooparazzi://home/side")))
        assertTrue("a receipt is a sheet too", HomeLaunch.Receipt(ReceiptKind.LOGGED, "x").opensSheet)
    }
}
