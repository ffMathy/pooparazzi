package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.BottleContents
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * One expected moment ("wakes in about 40m"): [atMillis] is the estimate, fixed for the whole stretch, so once it has
 * passed it is "now" rather than a new, later guess (an estimate never reads as a time in the past). [goneAtMillis] is when the current stretch has run so far past
 * every comparable one that logging has probably stopped, and the forecast is dropped. [samples] is how many past
 * stretches the estimate rests on.
 */
data class Expected(val atMillis: Long, val goneAtMillis: Long, val samples: Int) {
    /**
     * Time until it, rounded up to [STEP_MILLIS] ("in 55m": a week's typical gap, not a minute-exact promise); null
     * when it is near ("soon", under one step away) or due ([isDue]).
     */
    fun remainingMillis(now: Long): Long? {
        val left = atMillis - now
        if (left < STEP_MILLIS) return null
        return (left + STEP_MILLIS - 1) / STEP_MILLIS * STEP_MILLIS
    }

    /** Whether it has come ("now"): surfaces never say how long ago an estimate passed. */
    fun isDue(now: Long): Boolean = now >= atMillis

    /** [atMillis] to the nearest [STEP_MILLIS], for surfaces that show a clock time ("~15:25"). */
    val roundedAtMillis: Long get() = (atMillis + STEP_MILLIS / 2) / STEP_MILLIS * STEP_MILLIS

    companion object {
        const val STEP_MILLIS = 5 * 60_000L
    }
}

/**
 * What the past week says comes next (V1_ADDITIONS §9). [sleep] is the expected wake-up while a sleep runs and the
 * expected next sleep while awake. Each is null when the week has too few comparable stretches, or when they vary too
 * much to say anything useful (poop often does). When the gaps between poops say nothing but the baby reliably poops
 * soon after feeding, [poop] is the expected poop after the last feed instead (see [ForecastRules.AFTER_FEED_WINDOW_MILLIS]).
 */
data class Forecast(val sleep: Expected?, val feed: Expected?, val poop: Expected?) {
    val isEmpty: Boolean get() = sleep == null && feed == null && poop == null

    /** The estimate of [kind]. */
    operator fun get(kind: ForecastKind): Expected? = when (kind) {
        ForecastKind.FEED -> feed
        ForecastKind.POOP -> poop
        ForecastKind.SLEEP -> sleep
    }

    /** Only the estimates of [kinds]; the others are left out. */
    fun only(kinds: Set<ForecastKind>): Forecast = Forecast(
        sleep = sleep.takeIf { ForecastKind.SLEEP in kinds },
        feed = feed.takeIf { ForecastKind.FEED in kinds },
        poop = poop.takeIf { ForecastKind.POOP in kinds },
    )

    companion object {
        val NONE = Forecast(null, null, null)
    }
}

/**
 * The kinds of estimate a [Forecast] holds, as the parent turns them on and off: the phone's prediction notifications
 * have a channel each, and the watch's Ongoing Activity uses the kinds chosen in Settings › Watch. [SLEEP] is both the
 * next sleep (awake) and the wake-up (asleep).
 */
enum class ForecastKind { FEED, POOP, SLEEP }

/**
 * The [Forecast] for [summary], from the stretches of the last [ForecastRules.LOOKBACK_DAYS] days. [birthDate] is the
 * baby's, when the parent gave it: some rules depend on the baby's age, and without it they are left out.
 *
 * Babies' days are only loosely regular, and a sleep at night lasts longer than a nap at noon, so each estimate:
 *  1. takes the past week's stretches of the same kind (sleeps, wake windows, gaps between feeds, gaps between poops);
 *  2. keeps those that started at about the same time of day as the current one (within 2 h, widened to 4 h, then
 *     the whole day, until there are enough), except for poop, which follows no clock;
 *  3. gives up when the stretches are too few (under 5, or from fewer than 3 days) or spread too widely (interquartile range over 1.5 × the median), or
 *     when the current stretch is already twice the longest of them (logging has probably stopped);
 *  4. adds their median to the current stretch's start, the recent days counting more than the older ones
 *     ([ForecastRules.HALF_LIFE_DAYS]: a growth spurt changes the rhythm for a few days, and the estimate follows it
 *     sooner). The estimate stays put as the stretch goes on, so a late one reads "now" instead of moving on to a new
 *     guess.
 *
 * Poop that follows no gap falls back to [poopAfterFeed]: newborns often poop shortly after each feed.
 *
 * With a [birthDate], two rules follow the baby's age:
 *  - no sleep estimate before [ForecastRules.MIN_SLEEP_AGE_DAYS]: a newborn's sleep has no day–night rhythm yet, so
 *    the time of day says little;
 *  - from [ForecastRules.BREASTFED_POOP_AGE_DAYS], a breastfed baby ([isBreastfed]) gets no estimate from the gaps
 *    between poops: days without one are normal then, and an estimate would only worry. The poop after a feed still
 *    shows while it holds.
 */
fun forecast(summary: Summary, birthDate: LocalDate? = null): Forecast {
    val now = summary.now
    val zone = summary.zone
    val since = now - ForecastRules.LOOKBACK_DAYS * DAY_MILLIS
    val recent = summary.entries.filter { it.startMillis >= since }
    val sleeps = recent.filter { it.kind == EntryKind.SLEEP && it.endMillis != null && it.endMillis <= now }
    val feeds = recent.filter { it.kind == EntryKind.FEED }
    val poops = recent.filter { it.kind == EntryKind.DIAPER && it.poop }
    val ageDays = birthDate?.let { ChronoUnit.DAYS.between(it, localDate(now, zone)) }

    val running = summary.runningSleep
    val sleep = when {
        ageDays != null && ageDays < ForecastRules.MIN_SLEEP_AGE_DAYS -> null
        running != null -> {
            val durations = sleeps.map { Stretch(it.startMillis, it.endMillis!! - it.startMillis) }
            expect(durations, running.startMillis, now, zone, byTimeOfDay = true)
        }
        else -> summary.lastSleepEnd?.let { wokeAt -> expect(wakeWindows(sleeps), wokeAt, now, zone, byTimeOfDay = true) }
    }
    val feed = feeds.lastOrNull()?.let { expect(gaps(feeds), it.startMillis, now, zone, byTimeOfDay = true) }
    val byGaps = !(ageDays != null && ageDays >= ForecastRules.BREASTFED_POOP_AGE_DAYS && isBreastfed(feeds))
    val poop = (summary.entries.lastOrNull { it.kind == EntryKind.DIAPER && it.poop })
        ?.takeIf { byGaps }
        ?.let { expect(gaps(poops), it.startMillis, now, zone, byTimeOfDay = false) }
        ?: poopAfterFeed(feeds, poops, now, zone)
    return Forecast(sleep, feed, poop)
}

/**
 * Whether [feeds] are a breastfed baby's: at least one of them says what was given, and every one that does is a breast
 * feed or a bottle of breast milk. A bottle without its contents may have been formula, so it does not count as breast
 * milk; feeds logged with just the time say nothing either way.
 */
fun isBreastfed(feeds: List<Entry>): Boolean {
    val typed = feeds.filter { it.kind == EntryKind.FEED && it.feedType != null }
    return typed.isNotEmpty() && typed.all {
        it.feedType == FeedType.BREAST || (it.feedType == FeedType.BOTTLE && it.bottleContents == BottleContents.MILK)
    }
}

/** Tuning of [forecast]. */
object ForecastRules {
    /** How far back stretches count: a week is recent enough to follow a growing baby and long enough to average. */
    const val LOOKBACK_DAYS = 7L

    /** Fewer comparable stretches than this and no estimate is shown. */
    const val MIN_SAMPLES = 5

    /** The comparable stretches must start on at least this many days: one busy day is not yet a pattern. */
    const val MIN_DAYS = 3

    /** The time-of-day windows tried in turn (minutes either side); the last one is the whole day. */
    val TIME_OF_DAY_WINDOWS = listOf(120, 240, 12 * 60)

    /** Too spread out: the interquartile range is more than this times the median. */
    const val MAX_SPREAD = 1.5

    /** Gaps under this are one event logged twice (a feed per side), not a new stretch. */
    const val MIN_GAP_MILLIS = 10 * 60_000L

    /** Gaps over this are a forgotten log, not a stretch. */
    const val MAX_GAP_MILLIS = 12 * 60 * 60_000L

    /**
     * A poop logged within this long of a feed's start (and before the next feed) counts as following that feed. Long
     * enough to cover a diaper changed a while after the poop itself.
     */
    const val AFTER_FEED_WINDOW_MILLIS = 90 * 60_000L

    /** Poop is predicted after a feed only when at least this share of the week's feeds were followed by one. */
    const val AFTER_FEED_MIN_SHARE = 0.7

    /** The spread test's floor for poop after a feed: delays of a few minutes may differ by a few minutes. */
    const val AFTER_FEED_MIN_SPREAD_BASE_MILLIS = 15 * 60_000L

    /**
     * A stretch this many days old counts half as much as one from today, a stretch twice as old a quarter: recent
     * enough to follow a growth spurt within a few days, long enough that one odd day does not swing the estimate.
     */
    const val HALF_LIFE_DAYS = 3.0

    /** Below this age (6 weeks) there is no sleep estimate: the day–night rhythm is still forming. */
    const val MIN_SLEEP_AGE_DAYS = 42L

    /** From this age (6 weeks) a breastfed baby's poop is not estimated from its gaps, which may then run for days. */
    const val BREASTFED_POOP_AGE_DAYS = 42L
}

/** A past stretch: it began at [startMillis] and lasted [lengthMillis]. */
private data class Stretch(val startMillis: Long, val lengthMillis: Long)

/** How much a stretch that began at [startMillis] counts at [now]: halved every [ForecastRules.HALF_LIFE_DAYS]. */
private fun weight(startMillis: Long, now: Long): Double =
    0.5.pow(maxOf(0L, now - startMillis).toDouble() / DAY_MILLIS / ForecastRules.HALF_LIFE_DAYS)

/** Time awake from each sleep's end to the next sleep's start. */
private fun wakeWindows(sleeps: List<Entry>): List<Stretch> = sleeps.zipWithNext { a, b -> Stretch(a.endMillis!!, b.startMillis - a.endMillis) }
    .filter { it.lengthMillis in ForecastRules.MIN_GAP_MILLIS..ForecastRules.MAX_GAP_MILLIS }

/** Time between consecutive instants (feeds, poops). Poops may be days apart, so only feeds are capped. */
private fun gaps(instants: List<Entry>): List<Stretch> = instants.zipWithNext { a, b -> Stretch(a.startMillis, b.startMillis - a.startMillis) }
    .filter { it.lengthMillis >= ForecastRules.MIN_GAP_MILLIS && (instants.first().kind != EntryKind.FEED || it.lengthMillis <= ForecastRules.MAX_GAP_MILLIS) }

private fun expect(stretches: List<Stretch>, startMillis: Long, now: Long, zone: ZoneId, byTimeOfDay: Boolean): Expected? {
    fun enough(candidates: List<Stretch>) = candidates.size >= ForecastRules.MIN_SAMPLES &&
        candidates.map { localDate(it.startMillis, zone) }.distinct().size >= ForecastRules.MIN_DAYS
    val comparable = if (byTimeOfDay) {
        val minute = minuteOfDay(startMillis, zone)
        ForecastRules.TIME_OF_DAY_WINDOWS.asSequence()
            .map { window -> stretches.filter { circularDistance(minuteOfDay(it.startMillis, zone), minute) <= window } }
            .firstOrNull(::enough)
    } else {
        stretches.takeIf(::enough)
    } ?: return null
    val weighted = comparable.map { it.lengthMillis to weight(it.startMillis, now) }
    val lengths = comparable.map { it.lengthMillis }.sorted()
    val median = weightedQuantile(weighted, 0.5)
    if (weightedQuantile(weighted, 0.75) - weightedQuantile(weighted, 0.25) > ForecastRules.MAX_SPREAD * median) return null
    // Far longer than any stretch of the week: logging has stopped (or this one was forgotten), so say nothing.
    val goneAt = startMillis + STALE_FACTOR * lengths.last()
    if (now > goneAt) return null
    return Expected(startMillis + median.toLong(), goneAtMillis = goneAt, samples = lengths.size)
}

/**
 * The poop expected after the last feed, when most of the week's feeds were followed by a poop within
 * [ForecastRules.AFTER_FEED_WINDOW_MILLIS] (the gastrocolic reflex; strongest in the first weeks, so this mostly fades
 * as the baby grows). Feeds under [ForecastRules.MIN_GAP_MILLIS] after the previous one are the same feed. Null once a
 * poop has been logged since the last feed, when too few feeds were followed by one (under [ForecastRules.MIN_SAMPLES]
 * from [ForecastRules.MIN_DAYS] days, or under [ForecastRules.AFTER_FEED_MIN_SHARE] of them), when the delays vary
 * too much, or once the window, or twice the longest delay, has passed.
 */
private fun poopAfterFeed(feeds: List<Entry>, poops: List<Entry>, now: Long, zone: ZoneId): Expected? {
    val starts = feeds.map { it.startMillis }.sorted().fold(mutableListOf<Long>()) { kept, t ->
        if (kept.isEmpty() || t - kept.last() >= ForecastRules.MIN_GAP_MILLIS) kept += t
        kept
    }
    val current = starts.lastOrNull() ?: return null
    if (poops.any { it.startMillis >= current }) return null
    // Every earlier feed's window has closed: it ends at the next feed, or the window's length after the feed.
    val followed = starts.zipWithNext { start, next ->
        val end = minOf(start + ForecastRules.AFTER_FEED_WINDOW_MILLIS, next)
        start to poops.firstOrNull { it.startMillis in start until end }?.let { it.startMillis - start }
    }
    val hits = followed.mapNotNull { (start, delay) -> delay?.let { Stretch(start, it) } }
    if (hits.size < ForecastRules.MIN_SAMPLES) return null
    if (hits.map { localDate(it.startMillis, zone) }.distinct().size < ForecastRules.MIN_DAYS) return null
    if (hits.size < ForecastRules.AFTER_FEED_MIN_SHARE * followed.size) return null
    val weighted = hits.map { it.lengthMillis to weight(it.startMillis, now) }
    val delays = hits.map { it.lengthMillis }.sorted()
    val median = weightedQuantile(weighted, 0.5)
    val spreadBase = maxOf(median, ForecastRules.AFTER_FEED_MIN_SPREAD_BASE_MILLIS.toDouble())
    if (weightedQuantile(weighted, 0.75) - weightedQuantile(weighted, 0.25) > ForecastRules.MAX_SPREAD * spreadBase) return null
    val goneAt = current + minOf(STALE_FACTOR * delays.last(), ForecastRules.AFTER_FEED_WINDOW_MILLIS)
    if (now > goneAt) return null
    return Expected(current + median.toLong(), goneAtMillis = goneAt, samples = delays.size)
}

/** The [q] quantile of [sorted], interpolating between neighbours. */
internal fun quantile(sorted: List<Long>, q: Double): Double {
    val position = (sorted.size - 1) * q
    val low = sorted[position.toInt()]
    val high = sorted[minOf(position.toInt() + 1, sorted.size - 1)]
    return low + (high - low) * (position - position.toInt())
}

/**
 * The [q] quantile of [values] (each a value and its weight), interpolating between neighbours. Each value sits at the
 * middle of its share of the total weight, measured from the first value's middle to the last's, so with equal weights
 * this is [quantile] exactly; a heavier value pulls the quantiles towards itself.
 */
internal fun weightedQuantile(values: List<Pair<Long, Double>>, q: Double): Double {
    val sorted = values.sortedBy { it.first }
    if (sorted.size == 1) return sorted.first().first.toDouble()
    val first = sorted.first().second / 2
    val span = sorted.sumOf { it.second } - first - sorted.last().second / 2
    if (span <= 0.0) return quantile(sorted.map { it.first }, q)
    var below = 0.0
    val positions = sorted.map { (_, w) -> ((below + w / 2 - first) / span).also { below += w } }
    val at = positions.indexOfFirst { it >= q }.coerceAtLeast(1)
    val (p0, p1) = positions[at - 1] to positions[at]
    val (v0, v1) = sorted[at - 1].first.toDouble() to sorted[at].first.toDouble()
    return if (p1 <= p0) v1 else v0 + (v1 - v0) * ((q - p0) / (p1 - p0)).coerceIn(0.0, 1.0)
}

private fun minuteOfDay(millis: Long, zone: ZoneId): Int = Instant.ofEpochMilli(millis).atZone(zone).let { it.hour * 60 + it.minute }

/** Minutes between two times of day, the short way round midnight. */
private fun circularDistance(a: Int, b: Int): Int = abs(a - b).let { minOf(it, 24 * 60 - it) }

private const val STALE_FACTOR = 2
private const val DAY_MILLIS = 24 * 60 * 60_000L
