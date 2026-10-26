package com.ffmathy.xaternity.core.domain

import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.diaperKind
import com.ffmathy.xaternity.core.model.isBreastfeed
import com.ffmathy.xaternity.core.model.isRunning
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * An interval clipped to a day, as fractions 0..1 of the day (DESIGN_SPEC §3.3.3, V1_ADDITIONS §4).
 * Nothing extends past now. [continuesBefore] / [continuesAfter] tell that the entry crosses the day edge
 * (or, for [continuesAfter], is still running).
 */
data class RibbonSpan(
    val entry: Entry,
    val start: Float,
    val end: Float,
    val running: Boolean,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/** An instant as a fraction 0..1 of the day. */
data class RibbonMark(val entry: Entry, val at: Float)

/** A diaper mark with its kind (ring = pee, dot = poop, ring + half = both). */
data class RibbonDiaperMark(val entry: Entry, val at: Float, val kind: DiaperKind)

/**
 * Geometry of the History day ribbon, in fractions of the local day. The phone multiplies by its plot
 * width (DESIGN_SPEC: x = 12 + 326 × fraction) and applies minimum widths itself.
 *
 * @property nowFraction the now-line, or null when [day] is not today.
 */
data class Ribbon(
    val bounds: DayBounds,
    val sleeps: List<RibbonSpan>,
    val breastfeeds: List<RibbonSpan>,
    val formula: List<RibbonMark>,
    val food: List<RibbonMark>,
    val diapers: List<RibbonDiaperMark>,
    val nowFraction: Float?,
) {
    val day: LocalDate get() = bounds.day

    fun fractionOf(millis: Long): Float = bounds.fractionOf(millis)

    /** Fraction of a wall-clock time on this day (e.g. 07:00 for night bands, 06/12/18 for axis labels). */
    fun fractionAt(time: LocalTime, zone: ZoneId): Float =
        fractionOf(day.atTime(time).atZone(zone).toInstant().toEpochMilli())

    companion object {
        fun build(entries: List<Entry>, day: LocalDate, zone: ZoneId, now: Long): Ribbon {
            val bounds = DayBounds.of(day, zone)
            val visibleEnd = minOf(bounds.endMillis, now)
            val sleeps = mutableListOf<RibbonSpan>()
            val breastfeeds = mutableListOf<RibbonSpan>()
            val formula = mutableListOf<RibbonMark>()
            val food = mutableListOf<RibbonMark>()
            val diapers = mutableListOf<RibbonDiaperMark>()

            fun span(entry: Entry): RibbonSpan? {
                val end = entry.endMillis ?: now
                val from = maxOf(entry.startMillis, bounds.startMillis)
                val to = minOf(end, visibleEnd)
                if (entry.startMillis >= visibleEnd || to < from || end <= bounds.startMillis) return null
                return RibbonSpan(
                    entry = entry,
                    start = bounds.fractionOf(from),
                    end = bounds.fractionOf(to),
                    running = entry.isRunning,
                    continuesBefore = entry.startMillis < bounds.startMillis,
                    continuesAfter = entry.isRunning || end > bounds.endMillis,
                )
            }

            for (entry in entries.sortedBy { it.startMillis }) {
                if (entry.deleted) continue
                val instantVisible = entry.startMillis in bounds && entry.startMillis <= now
                when (entry.kind) {
                    EntryKind.SLEEP -> span(entry)?.let(sleeps::add)
                    EntryKind.FEED -> when {
                        entry.isBreastfeed -> span(entry)?.let(breastfeeds::add)
                        !instantVisible -> Unit
                        entry.feedType == FeedType.FOOD -> food += RibbonMark(entry, bounds.fractionOf(entry.startMillis))
                        else -> formula += RibbonMark(entry, bounds.fractionOf(entry.startMillis))
                    }
                    EntryKind.DIAPER -> {
                        val kind = entry.diaperKind
                        if (instantVisible && kind != null) diapers += RibbonDiaperMark(entry, bounds.fractionOf(entry.startMillis), kind)
                    }
                }
            }
            return Ribbon(
                bounds = bounds,
                sleeps = sleeps,
                breastfeeds = breastfeeds,
                formula = formula,
                food = food,
                diapers = diapers,
                nowFraction = if (now in bounds) bounds.fractionOf(now) else null,
            )
        }
    }
}
