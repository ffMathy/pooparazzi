package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * One expected moment ("wakes in about 40m"): [atMillis] is the estimate, fixed for the whole stretch, so once it has
 * passed it is "24m ago" rather than a new, later guess. [goneAtMillis] is when the current stretch has run so far past
 * every comparable one that logging has probably stopped, and the forecast is dropped. [samples] is how many past
 * stretches the estimate rests on.
 */
data class Expected(val atMillis: Long, val goneAtMillis: Long, val samples: Int) {
    /**
     * Time until it, rounded up to [STEP_MILLIS] ("in 55m": a week's typical gap, not a minute-exact promise); null
     * when it is due ("soon", under one step away) or past ([agoMillis]).
     */
    fun remainingMillis(now: Long): Long? {
        val left = atMillis - now
        if (left < STEP_MILLIS) return null
        return (left + STEP_MILLIS - 1) / STEP_MILLIS * STEP_MILLIS
    }

    /** How long ago it should have happened ("24m ago"); null until a full minute after it (until then it is "soon"). */
    fun agoMillis(now: Long): Long? = (now - atMillis).takeIf { it >= MINUTE_MILLIS }

    /** [atMillis] to the nearest [STEP_MILLIS], for surfaces that show a clock time ("~15:25"). */
    val roundedAtMillis: Long get() = (atMillis + STEP_MILLIS / 2) / STEP_MILLIS * STEP_MILLIS

    companion object {
        const val STEP_MILLIS = 5 * 60_000L
        private const val MINUTE_MILLIS = 60_000L
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

    companion object {
        val NONE = Forecast(null, null, null)
    }
}

/**
 * The [Forecast] for [summary], from the stretches of the last [ForecastRules.LOOKBACK_DAYS] days.
 *
 * Babies' days are only loosely regular, and a sleep at night lasts longer than a nap at noon, so each estimate:
 *  1. takes the past week's stretches of the same kind (sleeps, wake windows, gaps between feeds, gaps between poops);
 *  2. keeps those that started at about the same time of day as the current one (within 2 h, widened to 4 h, then
 *     the whole day, until there are enough), except for poop, which follows no clock;
 *  3. gives up when the stretches are too few (under 5, or from fewer than 3 days) or spread too widely (interquartile range over 1.5 × the median), or
 *     when the current stretch is already twice the longest of them (logging has probably stopped);
 *  4. adds their median to the current stretch's start. The estimate stays put as the stretch goes on, so a late one
 *     reads "24m ago" instead of moving on to a new guess.
 *
 * Poop that follows no gap falls back to [poopAfterFeed]: newborns often poop shortly after each feed.
 */
fun forecast(summary: Summary): Forecast {
    val now = summary.now
    val zone = summary.zone
    val since = now - ForecastRules.LOOKBACK_DAYS * DAY_MILLIS
    val recent = summary.entries.filter { it.startMillis >= since }
    val sleeps = recent.filter { it.kind == EntryKind.SLEEP && it.endMillis != null && it.endMillis <= now }
    val feeds = recent.filter { it.kind == EntryKind.FEED }
    val poops = recent.filter { it.kind == EntryKind.DIAPER && it.poop }

    val running = summary.runningSleep
    val sleep = if (running != null) {
        val durations = sleeps.map { Stretch(it.startMillis, it.endMillis!! - it.startMillis) }
        expect(durations, running.startMillis, now, zone, byTimeOfDay = true)
    } else {
        summary.lastSleepEnd?.let { wokeAt -> expect(wakeWindows(sleeps), wokeAt, now, zone, byTimeOfDay = true) }
    }
    val feed = feeds.lastOrNull()?.let { expect(gaps(feeds), it.startMillis, now, zone, byTimeOfDay = true) }
    val poop = (summary.entries.lastOrNull { it.kind == EntryKind.DIAPER && it.poop })
        ?.let { expect(gaps(poops), it.startMillis, now, zone, byTimeOfDay = false) }
        ?: poopAfterFeed(feeds, poops, now, zone)
    return Forecast(sleep, feed, poop)
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
}

/** A past stretch: it began at [startMillis] and lasted [lengthMillis]. */
private data class Stretch(val startMillis: Long, val lengthMillis: Long)

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
    val lengths = comparable.map { it.lengthMillis }.sorted()
    val median = quantile(lengths, 0.5)
    if (quantile(lengths, 0.75) - quantile(lengths, 0.25) > ForecastRules.MAX_SPREAD * median) return null
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
    val delays = hits.map { it.lengthMillis }.sorted()
    val median = quantile(delays, 0.5)
    val spreadBase = maxOf(median, ForecastRules.AFTER_FEED_MIN_SPREAD_BASE_MILLIS.toDouble())
    if (quantile(delays, 0.75) - quantile(delays, 0.25) > ForecastRules.MAX_SPREAD * spreadBase) return null
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

private fun minuteOfDay(millis: Long, zone: ZoneId): Int = Instant.ofEpochMilli(millis).atZone(zone).let { it.hour * 60 + it.minute }

/** Minutes between two times of day, the short way round midnight. */
private fun circularDistance(a: Int, b: Int): Int = abs(a - b).let { minOf(it, 24 * 60 - it) }

private const val STALE_FACTOR = 2
private const val DAY_MILLIS = 24 * 60 * 60_000L
