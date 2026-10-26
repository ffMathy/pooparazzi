package com.ffmathy.xaternity.core.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.R
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.sample.SampleData.TODAY
import com.ffmathy.xaternity.core.sample.SampleData.ZONE
import com.ffmathy.xaternity.core.settings.AppLanguage
import com.ffmathy.xaternity.core.settings.Appearance
import com.ffmathy.xaternity.core.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

private const val MINUTE = 60_000L
private val context: Context get() = ApplicationProvider.getApplicationContext()
private fun entry(id: String) = SampleData.scenarioWithRunningFeed().first { it.id == id }

/** The resource-backed formatters in English (V1_ADDITIONS §5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rGB")
class EnglishResourceFormattersTest {
    private val f by lazy { Formatters.from(context, ZONE) }

    @Test
    fun durationsFromResources() {
        assertEquals("2h 10m", f.duration.format(130 * MINUTE))
        assertEquals("1h 07m", f.duration.format(67 * MINUTE))
        assertEquals("48m", f.duration.format(48 * MINUTE))
        assertEquals("<1m", f.duration.format(0))
        assertEquals("1d 3h", f.duration.format(27 * 60 * MINUTE))
        assertEquals("2 hours 10 minutes", f.duration.spoken(130 * MINUTE))
        assertEquals("1 hour 7 minutes", f.duration.spoken(67 * MINUTE))
        assertEquals("less than a minute", f.duration.spoken(0))
    }

    @Test
    fun relativeAndClock() {
        assertTrue(f.clock.is24Hour)
        assertEquals("2h 10m ago", f.relative.ago(130 * MINUTE))
        assertEquals("just now", f.relative.ago(10_000))
        assertEquals("since 13:27", f.relative.since(SampleData.at(13, 27), ZONE))
        assertEquals("at 14:32", f.relative.at(SampleData.NOW_1432, ZONE))
        assertEquals("Mon 26 Oct", f.clock.dayMonth(TODAY))
    }

    @Test
    fun labels() {
        val l = f.labels
        assertEquals("Feed", l.kind(EntryKind.FEED))
        assertEquals("Formula", l.feedType(FeedType.FORMULA))
        assertEquals("Breast", l.feedType(FeedType.BREAST))
        assertEquals("Food", l.feedType(FeedType.FOOD))
        assertEquals("Left → Right", l.side(BreastSide.LEFT_THEN_RIGHT))
        assertEquals("Right", l.side(BreastSide.RIGHT))
        assertEquals("Pee", l.diaperKind(DiaperKind.PEE))
        assertEquals("Poop", l.diaperKind(DiaperKind.POOP))
        assertEquals("Pee + poop", l.diaperKind(DiaperKind.BOTH))
        assertEquals("Both", l.diaperKindStatus(DiaperKind.BOTH))
        assertEquals("Pale", l.poopColor(PoopColor.PALE))
        assertEquals("Seedy", l.poopTexture(PoopTexture.SEEDY))
        assertEquals("Night", l.appearance(Appearance.NIGHT))
        assertEquals("Dansk", l.language(AppLanguage.DA))
        assertEquals("feed", l.feedsCaption(1))
        assertEquals("feeds", l.feedsCaption(5))
    }

    @Test
    fun historyLines() {
        val l = f.labels
        val both = entry("sample-both-0541")
        assertEquals("Pee + poop", l.historyTitle(both, f.duration, SampleData.NOW_1432))
        assertEquals("05:41 · Yellow · Seedy", l.diaperDetail(both, f.clock, ZONE))
        assertEquals("13:44", l.diaperDetail(entry("sample-pee-1344"), f.clock, ZONE))

        val breast = entry("sample-breast-0140")
        assertEquals("Breast · Left → Right", l.historyTitle(breast, f.duration, SampleData.NOW_1432))
        assertEquals(
            "01:40–01:58 · 18m",
            l.historySupporting(breast, TODAY, ZONE, SampleData.NOW_1432, f.clock, f.duration, f.relative, spacedRange = false),
        )
        val running = SampleData.runningBreastfeed()
        assertEquals("Breast · running", l.historyTitle(running, f.duration, SampleData.NOW_1450))
        assertEquals("since 14:32", l.historySupporting(running, TODAY, ZONE, SampleData.NOW_1450, f.clock, f.duration, f.relative))

        val sleep = entry("sample-sleep-1240")
        assertEquals("Sleep · 47m", l.historyTitle(sleep, f.duration, SampleData.NOW_1432))
        assertEquals("12:40 – 13:27", l.historySupporting(sleep, TODAY, ZONE, SampleData.NOW_1432, f.clock, f.duration, f.relative))
        assertEquals(
            "Sun 22:40 – 01:35",
            l.historySupporting(entry("sample-sleep-2240"), TODAY, ZONE, SampleData.NOW_1432, f.clock, f.duration, f.relative),
        )
        assertEquals("12:22", l.historySupporting(entry("sample-formula-1222"), TODAY, ZONE, SampleData.NOW_1432, f.clock, f.duration, f.relative))
    }

    @Test
    fun syncLines() {
        val l = f.labels
        assertEquals("Synced with watch · 14:31", l.syncLine(SyncStatus.Synced(SampleData.SYNCED_1431), SyncPeer.WATCH, f.clock, ZONE))
        assertEquals("Synced with phone · 14:31", l.syncLine(SyncStatus.Synced(SampleData.SYNCED_1431), SyncPeer.PHONE, f.clock, ZONE))
        assertEquals("Syncing with watch…", l.syncLine(SyncStatus.Pending, SyncPeer.WATCH, f.clock, ZONE))
        assertEquals("Syncing with phone…", l.syncLine(SyncStatus.Pending, SyncPeer.PHONE, f.clock, ZONE))
        assertEquals("Saved on phone · will sync", l.syncLine(SyncStatus.Offline, SyncPeer.WATCH, f.clock, ZONE))
        assertEquals("Saved on watch · will sync", l.syncLine(SyncStatus.Offline, SyncPeer.PHONE, f.clock, ZONE))
        assertEquals("Couldn't save · Tap to retry", l.syncLine(SyncStatus.Error, SyncPeer.WATCH, f.clock, ZONE))
        assertEquals("Saved on watch · will sync", l.syncLine(SyncStatus.Error, SyncPeer.PHONE, f.clock, ZONE))
        assertNull(l.syncLine(SyncStatus.Unavailable, SyncPeer.WATCH, f.clock, ZONE))
    }
}

/** The same formatters in Danish. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "da-rDK")
class DanishResourceFormattersTest {
    private val f by lazy { Formatters.from(context, ZONE) }

    @Test
    fun durationsFromResources() {
        assertEquals("2t 10m", f.duration.format(130 * MINUTE))
        assertEquals("1t 07m", f.duration.format(67 * MINUTE))
        assertEquals("48m", f.duration.format(48 * MINUTE))
        assertEquals("<1m", f.duration.format(0))
        assertEquals("1d 3t", f.duration.format(27 * 60 * MINUTE))
        assertEquals("2 timer 10 minutter", f.duration.spoken(130 * MINUTE))
        assertEquals("1 time 7 minutter", f.duration.spoken(67 * MINUTE))
        assertEquals("1 dag", f.duration.spoken(24 * 60 * MINUTE))
        assertEquals("under et minut", f.duration.spoken(0))
    }

    @Test
    fun relativeAndClock() {
        assertTrue("Danish always uses 24 h", f.clock.is24Hour)
        assertEquals(Locale.forLanguageTag("da-DK"), f.clock.locale)
        assertEquals("2t 10m siden", f.relative.ago(130 * MINUTE))
        assertEquals("lige nu", f.relative.ago(0))
        assertEquals("siden kl. 13:27", f.relative.since(SampleData.at(13, 27), ZONE))
        assertEquals("kl. 14:32", f.relative.at(SampleData.NOW_1432, ZONE))
        assertEquals("siden", f.relative.agoWord)
        assertEquals("man. 26. okt.", f.clock.dayMonth(TODAY))
    }

    @Test
    fun glossary() {
        val l = f.labels
        assertEquals("Måltid", l.kind(EntryKind.FEED))
        assertEquals("Søvn", l.kind(EntryKind.SLEEP))
        assertEquals("Ble", l.kind(EntryKind.DIAPER))
        assertEquals("Amning", l.feedType(FeedType.BREAST))
        assertEquals("Modermælkserstatning", l.feedType(FeedType.FORMULA))
        assertEquals("Modermælks­erstatning", l.feedType(FeedType.FORMULA, compact = true))
        assertEquals("Mad", l.feedType(FeedType.FOOD))
        assertEquals("Venstre → Højre", l.side(BreastSide.LEFT_THEN_RIGHT))
        assertEquals("Højre → Venstre", l.side(BreastSide.RIGHT_THEN_LEFT))
        assertEquals("Tis + bæ", l.diaperKind(DiaperKind.BOTH))
        assertEquals(
            listOf("Gul", "Grøn", "Brun", "Sort", "Rød", "Bleg"),
            PoopColor.entries.map(l::poopColor),
        )
        assertEquals(
            listOf("Vandig", "Kornet", "Grødet", "Formet", "Hård"),
            PoopTexture.entries.map(l::poopTexture),
        )
        assertEquals("måltid", l.feedsCaption(1))
        assertEquals("måltider", l.feedsCaption(5))
        assertEquals("bæ", l.poopCaption())
        val res = context.resources
        assertEquals("Afslut amning", res.getString(R.string.action_end_feed))
        assertEquals("Hvilken side?", res.getString(R.string.side_question))
        assertEquals("Ammer", res.getString(R.string.status_breastfeeding))
        assertEquals("Sidste måltid", res.getString(R.string.status_last_fed))
        assertEquals("Ret start", res.getString(R.string.action_adjust_start))
        assertEquals("Tidligere?", res.getString(R.string.action_earlier_question))
        assertEquals("Indstillinger", res.getString(R.string.nav_settings))
    }

    @Test
    fun historyAndSync() {
        val l = f.labels
        assertEquals("05:41 · Gul · Kornet", l.diaperDetail(entry("sample-both-0541"), f.clock, ZONE))
        assertEquals("Amning · Venstre → Højre", l.historyTitle(entry("sample-breast-0140"), f.duration, SampleData.NOW_1432))
        assertEquals("Amning · i gang", l.historyTitle(SampleData.runningBreastfeed(), f.duration, SampleData.NOW_1450))
        assertEquals("Søvn · 47m", l.historyTitle(entry("sample-sleep-1240"), f.duration, SampleData.NOW_1432))
        assertEquals(
            "Synkroniseret med ur · 14:31",
            l.syncLine(SyncStatus.Synced(SampleData.SYNCED_1431), SyncPeer.WATCH, f.clock, ZONE),
        )
        assertEquals(
            "Synkroniseret med telefon · 14:31",
            l.syncLine(SyncStatus.Synced(SampleData.SYNCED_1431), SyncPeer.PHONE, f.clock, ZONE),
        )
        assertEquals("Gemt på uret · synkes senere", l.syncLine(SyncStatus.Offline, SyncPeer.PHONE, f.clock, ZONE))
        assertEquals("Gemt lokalt · synkes senere", l.syncLine(SyncStatus.Offline, SyncPeer.WATCH, f.clock, ZONE))
        assertEquals("Synkroniserer med ur…", l.syncLine(SyncStatus.Pending, SyncPeer.WATCH, f.clock, ZONE))
        assertEquals("Ikke gemt · Prøv igen", l.syncLine(SyncStatus.Error, SyncPeer.WATCH, f.clock, ZONE))
    }
}

/** A US English device uses the 12-hour clock with AM/PM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class TwelveHourClockTest {
    @Test
    fun usesAmPm() {
        val clock = ClockFormatter.from(context)
        if (!clock.is24Hour) {
            assertEquals("1:44 PM", clock.format(SampleData.at(13, 44), ZONE).replace(' ', ' ').replace(' ', ' '))
        } else {
            assertEquals("13:44", clock.format(SampleData.at(13, 44), ZONE))
        }
    }
}

/** Durations printed next to clock times agree with them, whatever the seconds were. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rGB")
class OffMinuteLabelsTest {
    private val f by lazy { Formatters.from(context, ZONE) }
    private val second = 1_000L

    @Test
    fun sinceRowAndHistoryAgreeWithTheClock() {
        val now = SampleData.at(14, 32) + 10 * second
        val pee = com.ffmathy.xaternity.core.model.Entry.diaper(SampleData.at(13, 44) + 30 * second, pee = true, poop = false, id = "p")
        val summary = com.ffmathy.xaternity.core.domain.summarize(listOf(pee), now, ZONE)
        assertEquals("48m ago", f.relative.ago(summary.sinceLastDiaperMillis!!))
        assertEquals("13:44", f.clock.format(pee.startMillis, ZONE))

        val sleep = com.ffmathy.xaternity.core.model.Entry.sleep(SampleData.at(12, 40) + 50 * second, SampleData.at(13, 27) + 10 * second, id = "s")
        assertEquals("Sleep · 47m", f.labels.historyTitle(sleep, f.duration, now))
        assertEquals("12:40 – 13:27", f.labels.historySupporting(sleep, TODAY, ZONE, now, f.clock, f.duration, f.relative))
        val breast = com.ffmathy.xaternity.core.model.Entry.breastfeed(
            SampleData.at(12, 0) + 40 * second, SampleData.at(12, 18) + 5 * second, BreastSide.LEFT, id = "b",
        )
        assertEquals("12:00 – 12:18 · 18m", f.labels.historySupporting(breast, TODAY, ZONE, now, f.clock, f.duration, f.relative))
        assertEquals("1h 07m", f.duration.between(SampleData.at(2, 5) + 40 * second, SampleData.at(3, 12) + 20 * second))
    }
}

/** Danish clock times are always 24 h, even on a device set to 12 h (V1_ADDITIONS §5, DESIGN_SPEC §7). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "da-rDK")
class DanishOnTwelveHourDeviceTest {
    @Test
    fun staysTwentyFourHour() {
        android.provider.Settings.System.putString(context.contentResolver, android.provider.Settings.System.TIME_12_24, "12")
        assertTrue(android.text.format.DateFormat.is24HourFormat(context).not())
        val clock = ClockFormatter.from(context)
        assertTrue(clock.is24Hour)
        assertEquals("14:32", clock.format(SampleData.NOW_1432, ZONE))
    }

    @Test
    fun twelveHourPatternsAlwaysUseAColon() {
        assertEquals("h:mm a", ClockFormatter.withColon("h.mm a"))
        assertEquals("h:mm 'h.'", ClockFormatter.withColon("h.mm 'h.'"))
        val clock = ClockFormatter(is24Hour = false, locale = Locale.forLanguageTag("da-DK"), pattern12 = "h.mm a")
        assertTrue(clock.format(SampleData.NOW_1432, ZONE).startsWith("2:32"))
    }
}

/** English on a 12-hour device keeps AM/PM with a colon. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class EnglishOnTwelveHourDeviceTest {
    @Test
    fun usesAmPm() {
        android.provider.Settings.System.putString(context.contentResolver, android.provider.Settings.System.TIME_12_24, "12")
        val clock = ClockFormatter.from(context)
        assertTrue(!clock.is24Hour)
        assertEquals("2:32 PM", clock.format(SampleData.NOW_1432, ZONE).replace(' ', ' ').replace(' ', ' '))
    }
}
