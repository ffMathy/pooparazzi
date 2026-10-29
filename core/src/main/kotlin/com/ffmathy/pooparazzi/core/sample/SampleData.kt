package com.ffmathy.pooparazzi.core.sample

import com.ffmathy.pooparazzi.core.domain.epochMillis
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.settings.Settings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

/**
 * Reference scenario v2 (V1_ADDITIONS §6) for previews, screenshots and tests: baby Noa, Mon 26 Oct 2026,
 * `Europe/Copenhagen`. Ids are stable so screenshots and tests are deterministic.
 */
object SampleData {
    val ZONE: ZoneId = ZoneId.of("Europe/Copenhagen")
    val TODAY: LocalDate = LocalDate.of(2026, 10, 26)
    val YESTERDAY: LocalDate = TODAY.minusDays(1)

    /** The baby's name in samples only (production starts empty). */
    const val BABY_NAME = "Noa"

    /** Mon 26 Oct 2026 14:32 in Copenhagen. */
    val NOW_1432: Long = epochMillis(TODAY, 14, 32, ZONE)

    /** Mon 26 Oct 2026 03:12 in Copenhagen (night snapshot). */
    val NOW_0312: Long = epochMillis(TODAY, 3, 12, ZONE)

    /** Mon 26 Oct 2026 14:50 in Copenhagen (afternoon snapshot, 18 minutes after the 14:32 breast feed). */
    val NOW_1450: Long = epochMillis(TODAY, 14, 50, ZONE)

    /** Last sync times shown in the sync lines ("Synced with watch · 14:31" / "· 03:11"). */
    val SYNCED_1431: Long = epochMillis(TODAY, 14, 31, ZONE)
    val SYNCED_0311: Long = epochMillis(TODAY, 3, 11, ZONE)

    val settings: Settings = Settings(babyName = BABY_NAME, updatedAtMillis = epochMillis(YESTERDAY, 9, 0, ZONE))

    /** Epoch ms of [hour]:[minute] today (or on [day]) in [zone]. */
    fun at(hour: Int, minute: Int, day: LocalDate = TODAY, zone: ZoneId = ZONE): Long = epochMillis(day, hour, minute, zone)

    /** The full log of §6, as seen at 14:32 (no running entries). Breast feeds are instants with a side order. */
    fun scenario(zone: ZoneId = ZONE): List<Entry> {
        fun t(h: Int, m: Int, day: LocalDate = TODAY) = epochMillis(day, h, m, zone)
        return listOf(
            Entry.sleep(t(22, 40, YESTERDAY), t(1, 35), id = "sample-sleep-2240"),
            Entry.breastfeed(t(1, 40), BreastSide.LEFT_THEN_RIGHT, id = "sample-breast-0140"),
            Entry.diaper(t(1, 52), pee = true, poop = false, id = "sample-pee-0152"),
            Entry.sleep(t(2, 5), t(5, 20), id = "sample-sleep-0205"),
            Entry.feed(FeedType.FORMULA, t(5, 25), id = "sample-formula-0525"),
            Entry.diaper(t(5, 41), pee = true, poop = true, color = PoopColor.YELLOW, texture = PoopTexture.SEEDY, id = "sample-both-0541"),
            Entry.sleep(t(6, 10), t(7, 45), id = "sample-sleep-0610"),
            Entry.breastfeed(t(8, 0), BreastSide.RIGHT_THEN_LEFT, id = "sample-breast-0800"),
            Entry.diaper(t(8, 20), pee = false, poop = true, color = PoopColor.BROWN, texture = PoopTexture.MUSHY, id = "sample-poop-0820"),
            Entry.sleep(t(9, 30), t(10, 15), id = "sample-sleep-0930"),
            Entry.feed(FeedType.FOOD, t(10, 30), id = "sample-food-1030"),
            Entry.diaper(t(11, 5), pee = true, poop = false, id = "sample-pee-1105"),
            Entry.feed(FeedType.FORMULA, t(12, 22), id = "sample-formula-1222"),
            Entry.sleep(t(12, 40), t(13, 27), id = "sample-sleep-1240"),
            Entry.diaper(t(13, 44), pee = true, poop = false, id = "sample-pee-1344"),
        )
    }

    /** The breast feed of the 14:50 snapshot: logged at 14:32, Left then Right. */
    fun recentBreastfeed(zone: ZoneId = ZONE): Entry =
        Entry.breastfeed(epochMillis(TODAY, 14, 32, zone), BreastSide.LEFT_THEN_RIGHT, id = "sample-breast-1432")

    /** [scenario] plus the 14:32 breast feed (show at [NOW_1450]: "Last fed 18m · Breast"). */
    fun scenarioWithRecentFeed(zone: ZoneId = ZONE): List<Entry> = scenario(zone) + recentBreastfeed(zone)

    /** The log as it stood at 03:12: entries up to then, with the 02:05 sleep still running (show at [NOW_0312]). */
    fun scenarioAt0312(zone: ZoneId = ZONE): List<Entry> {
        val cutoff = epochMillis(TODAY, 3, 12, zone)
        return scenario(zone)
            .filter { it.startMillis <= cutoff }
            .map { if (it.id == "sample-sleep-0205") it.copy(endMillis = null, updatedAtMillis = it.startMillis) else it }
    }

    /**
     * Weeks of logging for Statistics: [days] full days before [YESTERDAY] (the first is [TODAY] − [days] − 1), then
     * yesterday until the 22:40 sleep, then [scenario]. Generated from a fixed seed, so it is the same on every run:
     * cycles of sleep, a feed and a diaper or two, with longer sleeps at night. Over the weeks the night sleeps grow
     * and poops become rarer, so the last 7 days differ from the last 30.
     */
    fun history(days: Int = 33, zone: ZoneId = ZONE): List<Entry> {
        val random = Random(HISTORY_SEED)
        val first = TODAY.minusDays(days.toLong() + 1)
        // Every generated cycle ends before the scenario's 22:40 sleep.
        val cutoff = epochMillis(YESTERDAY, 22, 30, zone)
        val entries = mutableListOf<Entry>()
        var t = epochMillis(first, 0, 20 + random.nextInt(30), zone)
        var cycles = 0
        while (true) {
            val time = Instant.ofEpochMilli(t).atZone(zone)
            // 0 on the first day, 1 on yesterday.
            val progress = Duration.between(first.atStartOfDay(zone), time).toMinutes() / ((days + 1) * 24.0 * 60)
            val night = time.hour >= 19 || time.hour < 6
            val id = "sample-history-$cycles"
            val cycle = mutableListOf<Entry>()
            var at = t + minutes(if (night) 120 + (progress * 70).toInt() + random.nextInt(60) else 35 + random.nextInt(70))
            cycle += Entry.sleep(t, at, id = "$id-sleep")
            at += minutes(3 + random.nextInt(10))
            cycle += when (random.nextInt(10)) {
                in 0..5 -> Entry.breastfeed(at, BreastSide.entries[random.nextInt(BreastSide.entries.size)], id = "$id-feed")
                in 6..7 -> Entry.feed(FeedType.FORMULA, at, id = "$id-feed")
                8 -> Entry.feed(if (night) FeedType.FORMULA else FeedType.FOOD, at, id = "$id-feed")
                else -> Entry.feed(null, at, id = "$id-feed")
            }
            at += minutes(10 + random.nextInt(15))
            val poop = random.nextDouble() < 0.42 - 0.2 * progress
            cycle += Entry.diaper(
                at,
                pee = !poop || random.nextInt(3) > 0,
                poop = poop,
                color = if (poop) HISTORY_COLORS[random.nextInt(HISTORY_COLORS.size)] else null,
                texture = if (poop) HISTORY_TEXTURES[random.nextInt(HISTORY_TEXTURES.size)] else null,
                id = "$id-diaper",
            )
            if (!night && random.nextInt(4) == 0) {
                at += minutes(25 + random.nextInt(30))
                cycle += Entry.diaper(at, pee = true, poop = false, id = "$id-pee")
            }
            if (at > cutoff) break
            entries += cycle
            cycles++
            t = at + minutes(if (night) 15 + random.nextInt(25) else 40 + random.nextInt(60))
        }
        return entries + scenario(zone)
    }

    private fun minutes(count: Int): Long = count * 60_000L

    private const val HISTORY_SEED = 2026_10_26L
    private val HISTORY_COLORS = listOf(PoopColor.YELLOW, PoopColor.YELLOW, PoopColor.BROWN, PoopColor.GREEN)
    private val HISTORY_TEXTURES = listOf(PoopTexture.SEEDY, PoopTexture.MUSHY, PoopTexture.MUSHY, PoopTexture.FORMED)
}
