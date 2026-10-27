package com.ffmathy.xaternity.wear.complication

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.DynamicComplicationText
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import com.ffmathy.xaternity.core.domain.Summary
import com.ffmathy.xaternity.core.domain.summarize
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.ui.icons.XDrawables
import com.ffmathy.xaternity.wear.surface.SurfaceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** The complication data (DESIGN_SPEC §3.7, §10.10) for the reference scenario. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComplicationContentTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private val idle: Summary get() = summarize(SampleData.scenario(), SampleData.NOW_1432, SampleData.ZONE)
    private val asleep: Summary get() = summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, SampleData.ZONE)

    private fun content(sdk: Int = Build.VERSION_CODES.BAKLAVA, static: Boolean = false) =
        ComplicationContent(context, SurfaceText.from(context, SampleData.ZONE), sdk, static)

    private fun ComplicationParts.Companion.read(complication: XComplication, summary: Summary, sdk: Int = Build.VERSION_CODES.BAKLAVA) =
        checkNotNull(of(content(sdk).build(complication, summary, tapAction = null), context, Instant.ofEpochMilli(summary.now)))

    @Test
    fun sinceFeedShowsTheDurationWithTheFeedTypeIcon() {
        val data = content().build(XComplication.SINCE_FEED, idle, tapAction = null) as ShortTextComplicationData
        assertTrue(data.text is DynamicComplicationText)
        assertEquals(XDrawables.feed, data.monochromaticImage?.image?.resId)
        val parts = ComplicationParts.read(XComplication.SINCE_FEED, idle)
        assertEquals("2h 10m", parts.text)
        assertEquals("Last feed 2 hours 10 minutes ago", parts.description)
    }

    @Test
    fun lastDiaperShowsTheKindGlyphAndATitleForFacesWithoutImages() {
        val data = content().build(XComplication.LAST_DIAPER, idle, tapAction = null) as ShortTextComplicationData
        assertEquals(XDrawables.pee, data.monochromaticImage?.image?.resId)
        assertEquals(XDrawables.pee, data.smallImage?.image?.resId)
        val parts = ComplicationParts.read(XComplication.LAST_DIAPER, idle)
        assertEquals("48m", parts.text)
        assertEquals("Pee", parts.title)
        assertEquals("Last diaper, pee, 48 minutes ago", parts.description)
    }

    @Test
    fun sleepShowsAwakeOrAsleep() {
        val awake = ComplicationParts.read(XComplication.SLEEP, idle)
        assertEquals("1h 05m", awake.text)
        assertEquals(XDrawables.awake, awake.icon?.resId)
        assertEquals("Awake 1 hour 5 minutes, since 13:27", awake.description)

        val sleeping = ComplicationParts.read(XComplication.SLEEP, asleep)
        assertEquals("1h 07m", sleeping.text)
        assertEquals(XDrawables.sleepRunning, sleeping.icon?.resId)
        assertEquals("Asleep 1 hour 7 minutes, since 02:05", sleeping.description)
    }

    @Test
    fun longTextCombinesSleepAndTheLastFeed() {
        val awake = ComplicationParts.read(XComplication.SLEEP_LONG, idle)
        assertEquals("Awake 1h 05m", awake.text)
        assertEquals("since 13:27", awake.title)

        val sleeping = ComplicationParts.read(XComplication.SLEEP_LONG, asleep)
        assertEquals("Asleep 1h 07m", sleeping.text)
        assertEquals("Fed 1h 32m ago", sleeping.title)
        assertTrue(content().build(XComplication.SLEEP_LONG, asleep, null) is LongTextComplicationData)
    }

    @Test
    fun beforeWearOs4TheSystemTimeDifferenceIsUsed() {
        val data = content(sdk = Build.VERSION_CODES.S).build(XComplication.SINCE_FEED, idle, tapAction = null) as ShortTextComplicationData
        assertTrue(data.text is TimeDifferenceComplicationText)
    }

    @Test
    fun beforeWearOs4TheLongTitleStillSaysAgo() {
        // The system time difference fills only "1h 32m": the "ago" must come from the template.
        val sleeping = ComplicationParts.read(XComplication.SLEEP_LONG, asleep, sdk = Build.VERSION_CODES.S)
        assertEquals("Fed 1h 32m ago", sleeping.title)
    }

    @Test
    fun theLastDiaperIsReadInWords() {
        val both = summarize(
            SampleData.scenario() + Entry.diaper(SampleData.at(14, 0), pee = true, poop = true, id = "both-1400"),
            SampleData.NOW_1432,
            SampleData.ZONE,
        )
        val parts = ComplicationParts.read(XComplication.LAST_DIAPER, both)
        assertEquals("Both", parts.title)
        // Never the status word "Both" (DESIGN_SPEC §9.4).
        assertTrue(parts.description, parts.description.orEmpty().startsWith("Last diaper, pee and poop, "))
    }

    @Test
    fun staticDataIsPlainTextAtTheSummaryTime() {
        val data = content(static = true).build(XComplication.SINCE_FEED, idle, tapAction = null) as ShortTextComplicationData
        assertEquals("2h 10m", data.text.getTextAt(context.resources, Instant.now()).toString())
    }

    @Test
    fun withNothingLoggedTheComplicationsSayNoValue() {
        val empty = summarize(emptyList(), SampleData.NOW_1432, SampleData.ZONE)
        for (complication in XComplication.entries) {
            val data = content().build(complication, empty, tapAction = null)
            assertEquals(complication.type, data.type)
        }
        assertEquals("--", ComplicationParts.read(XComplication.SINCE_FEED, empty).text)
        assertEquals("No feeds yet", ComplicationParts.read(XComplication.SINCE_FEED, empty).description)
    }

    @Test
    fun theTypesMatchTheManifest() {
        assertEquals(ComplicationType.SHORT_TEXT, XComplication.SINCE_FEED.type)
        assertEquals(ComplicationType.LONG_TEXT, XComplication.SLEEP_LONG.type)
        assertTrue(ComplicationContent.noData() is NoDataComplicationData)
    }

    @Test
    fun tapsOpenMainAndNeverLog() {
        val intent = shadowIntent(XComplicationService.tapAction(context, XComplication.LAST_DIAPER))
        assertEquals("main", intent.getStringExtra(com.ffmathy.xaternity.wear.MainActivity.EXTRA_OPEN))
    }

    private fun shadowIntent(pendingIntent: android.app.PendingIntent) =
        org.robolectric.Shadows.shadowOf(pendingIntent).savedIntent
}
