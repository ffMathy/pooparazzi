package com.ffmathy.pooparazzi.core.sample

import com.ffmathy.pooparazzi.core.domain.epochMillis
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.settings.Settings
import java.time.LocalDate
import java.time.ZoneId

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

    val settings: Settings = Settings(babies = listOf(Baby(Baby.FIRST_ID, BABY_NAME)), updatedAtMillis = epochMillis(YESTERDAY, 9, 0, ZONE))

    /** The second baby of the twin samples. */
    const val TWIN_NAME = "Ida"
    const val TWIN_ID = "sample-baby-ida"

    /** Twins: Noa (the current baby, the one of [scenario]) and [TWIN_NAME]. */
    val twinSettings: Settings = settings.copy(babies = settings.babies + Baby(TWIN_ID, TWIN_NAME))

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
}
