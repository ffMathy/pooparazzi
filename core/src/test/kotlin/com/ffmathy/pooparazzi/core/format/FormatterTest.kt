package com.ffmathy.pooparazzi.core.format

import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sample.SampleData.TODAY
import com.ffmathy.pooparazzi.core.sample.SampleData.YESTERDAY
import com.ffmathy.pooparazzi.core.sample.SampleData.ZONE
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

private const val MINUTE = 60_000L
private fun minutes(n: Long) = n * MINUTE

/** Units as in values/strings.xml and values-da/strings.xml (the resource-backed factory is tested with Robolectric). */
internal val EnglishUnits = DurationUnits(
    day = "d", hour = "h", minute = "m",
    spokenDays = { if (it == 1L) "$it day" else "$it days" },
    spokenHours = { if (it == 1L) "$it hour" else "$it hours" },
    spokenMinutes = { if (it == 1L) "$it minute" else "$it minutes" },
    spokenLessThanMinute = "less than a minute",
)

internal val DanishUnits = DurationUnits(
    day = "d", hour = "t", minute = "m",
    spokenDays = { if (it == 1L) "$it dag" else "$it dage" },
    spokenHours = { if (it == 1L) "$it time" else "$it timer" },
    spokenMinutes = { if (it == 1L) "$it minut" else "$it minutter" },
    spokenLessThanMinute = "under et minut",
)

internal val EnglishPhrases = RelativePhrases("ago", "%1\$s ago", "since %1\$s", "just now", "at %1\$s", "%1\$s earlier", "%1\$s later")
internal val DanishPhrases = RelativePhrases("siden", "%1\$s siden", "siden kl. %1\$s", "lige nu", "kl. %1\$s", "%1\$s tidligere", "%1\$s senere")

class DurationFormatterTest {
    private val en = DurationFormatter(EnglishUnits)
    private val da = DurationFormatter(DanishUnits)

    @Test
    fun englishGrammar() {
        assertEquals("2h 10m", en.format(minutes(130)))
        assertEquals("1h 07m", en.format(minutes(67)))
        assertEquals("48m", en.format(minutes(48)))
        assertEquals("<1m", en.format(59_999L))
        assertEquals("1m", en.format(60_000L))
        assertEquals("10h 45m", en.format(minutes(645)))
        assertEquals("2h 00m", en.format(minutes(120)))
        assertEquals("23h 59m", en.format(minutes(24 * 60 - 1)))
        assertEquals("1d 3h", en.format(minutes(27 * 60 + 59)))
        assertEquals("1d 0h", en.format(minutes(24 * 60)))
    }

    @Test
    fun danishGrammar() {
        assertEquals("2t 10m", da.format(minutes(130)))
        assertEquals("1t 07m", da.format(minutes(67)))
        assertEquals("48m", da.format(minutes(48)))
        assertEquals("<1m", da.format(0L))
        assertEquals("1d 3t", da.format(minutes(27 * 60)))
    }

    @Test
    fun floorsSecondsAndClampsNegatives() {
        assertEquals("48m", en.format(minutes(48) + 59_999L))
        assertEquals("<1m", en.format(-5_000L))
    }

    @Test
    fun heroParts() {
        assertEquals(listOf(DurationPart("2", "h"), DurationPart("10", "m")), en.parts(minutes(130)))
        assertEquals(listOf(DurationPart("48", "m")), en.parts(minutes(48)))
        assertEquals(listOf(DurationPart("1", "t"), DurationPart("07", "m")), da.parts(minutes(67)))
        assertEquals(listOf(DurationPart("<1", "m")), en.parts(0))
    }

    @Test
    fun spoken() {
        assertEquals("2 hours 10 minutes", en.spoken(minutes(130)))
        assertEquals("1 hour 7 minutes", en.spoken(minutes(67)))
        assertEquals("48 minutes", en.spoken(minutes(48)))
        assertEquals("1 minute", en.spoken(minutes(1)))
        assertEquals("2 hours", en.spoken(minutes(120)))
        assertEquals("1 day 3 hours", en.spoken(minutes(27 * 60)))
        assertEquals("less than a minute", en.spoken(10_000L))
        assertEquals("2 timer 10 minutter", da.spoken(minutes(130)))
        assertEquals("1 time 7 minutter", da.spoken(minutes(67)))
        assertEquals("under et minut", da.spoken(0))
    }

    @Test
    fun offsets() {
        assertEquals("−5m", en.offset(-5))
        assertEquals("+5m", en.offset(5))
        assertEquals("−15m", da.offset(-15))
    }
}

class ClockAndRelativeFormatterTest {
    private val clock24 = ClockFormatter(is24Hour = true, locale = Locale.UK)
    private val clockDa = ClockFormatter(is24Hour = true, locale = Locale.forLanguageTag("da-DK"), dayMonthPattern = "EEE d. MMM")
    private val clock12 = ClockFormatter(is24Hour = false, locale = Locale.US)

    private fun normalize(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun clockTimes() {
        assertEquals("13:44", clock24.format(SampleData.at(13, 44), ZONE))
        assertEquals("03:12", clock24.format(SampleData.NOW_0312, ZONE))
        assertEquals("13:44", clockDa.format(SampleData.at(13, 44), ZONE))
        assertEquals("1:44 PM", normalize(clock12.format(SampleData.at(13, 44), ZONE)))
    }

    @Test
    fun ranges() {
        val start = SampleData.at(12, 40)
        val end = SampleData.at(13, 27)
        assertEquals("12:40 – 13:27", clock24.range(start, end, ZONE))
        assertEquals("12:40–13:27", clock24.range(start, end, ZONE, spaced = false))
        assertEquals("12:40 – 13:27", clock24.rangeOnDay(start, end, TODAY, ZONE))
        val sunday = SampleData.at(22, 40, YESTERDAY)
        assertEquals("Sun 22:40 – 01:35", clock24.rangeOnDay(sunday, SampleData.at(1, 35), TODAY, ZONE))
    }

    @Test
    fun dates() {
        assertEquals("Mon 26 Oct", clock24.dayMonth(TODAY))
        assertEquals("man. 26. okt.", clockDa.dayMonth(TODAY))
    }

    @Test
    fun relativeEnglish() {
        val rel = RelativeFormatter(DurationFormatter(EnglishUnits), clock24, EnglishPhrases)
        assertEquals("48m ago", rel.ago(minutes(48)))
        assertEquals("2h 10m ago", rel.ago(minutes(130)))
        assertEquals("just now", rel.ago(30_000L))
        assertEquals("48 minutes ago", rel.agoSpoken(minutes(48)))
        assertEquals("since 13:27", rel.since(SampleData.at(13, 27), ZONE))
        assertEquals("at 14:32", rel.at(SampleData.NOW_1432, ZONE))
        assertEquals("ago", rel.agoWord)
        assertEquals("5 minutes earlier", rel.offsetSpoken(-5))
        assertEquals("5 minutes later", rel.offsetSpoken(5))
    }

    @Test
    fun relativeDanish() {
        val rel = RelativeFormatter(DurationFormatter(DanishUnits), clockDa, DanishPhrases)
        assertEquals("48m siden", rel.ago(minutes(48)))
        assertEquals("2t 10m siden", rel.ago(minutes(130)))
        assertEquals("lige nu", rel.ago(0))
        assertEquals("siden kl. 13:27", rel.since(SampleData.at(13, 27), ZONE))
        assertEquals("kl. 14:32", rel.at(SampleData.NOW_1432, ZONE))
        assertEquals("2 timer 10 minutter siden", rel.agoSpoken(minutes(130)))
        assertEquals("5 minutter tidligere", rel.offsetSpoken(-5))
    }
}
