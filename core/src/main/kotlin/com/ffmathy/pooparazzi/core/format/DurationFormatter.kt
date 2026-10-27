package com.ffmathy.pooparazzi.core.format

import android.content.Context
import com.ffmathy.pooparazzi.core.R
import com.ffmathy.pooparazzi.core.model.clockDurationMillis

/**
 * Localised units for [DurationFormatter]. Unit letters for display ("h"/"t", "m", "d") and spoken forms
 * for TalkBack ("2 hours", "2 timer"). Build with [from] in the apps; tests can pass plain strings.
 */
class DurationUnits(
    val day: String,
    val hour: String,
    val minute: String,
    val spokenDays: (Long) -> String,
    val spokenHours: (Long) -> String,
    val spokenMinutes: (Long) -> String,
    val spokenLessThanMinute: String,
) {
    companion object {
        fun from(context: Context): DurationUnits {
            val res = context.resources
            return DurationUnits(
                day = res.getString(R.string.unit_day),
                hour = res.getString(R.string.unit_hour),
                minute = res.getString(R.string.unit_minute),
                spokenDays = { n -> res.getQuantityString(R.plurals.spoken_days, n.toQuantity(), n) },
                spokenHours = { n -> res.getQuantityString(R.plurals.spoken_hours, n.toQuantity(), n) },
                spokenMinutes = { n -> res.getQuantityString(R.plurals.spoken_minutes, n.toQuantity(), n) },
                spokenLessThanMinute = res.getString(R.string.spoken_less_than_minute),
            )
        }

        private fun Long.toQuantity(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }
}

/** One number/unit pair of a duration, for the hero composition (number and unit in different styles). */
data class DurationPart(val number: String, val unit: String)

/**
 * The duration grammar (DESIGN_SPEC §7): unit letters, whole minutes floored, minutes zero-padded when hours
 * are shown, `<1m` under a minute, days from 24 h. Examples: "48m", "2h 10m", "1h 07m", "1d 3h", "<1m";
 * Danish "2t 10m", "1d 3t".
 */
class DurationFormatter(private val units: DurationUnits) {

    /** "2h 10m". Negative durations count as 0 ("<1m"). */
    fun format(millis: Long): String = parts(millis).joinToString(" ") { it.number + it.unit }

    /**
     * The number/unit pairs: 130 min → ("2","h"), ("10","m"); 48 min → ("48","m"); under a minute → ("<1","m").
     * Callers that show "just now" under a minute check [isUnderAMinute] first.
     */
    fun parts(millis: Long): List<DurationPart> {
        val minutes = wholeMinutes(millis)
        return when {
            minutes < 1 -> listOf(DurationPart("<1", units.minute))
            minutes < MINUTES_PER_HOUR -> listOf(DurationPart(minutes.toString(), units.minute))
            minutes < MINUTES_PER_DAY -> listOf(
                DurationPart((minutes / MINUTES_PER_HOUR).toString(), units.hour),
                DurationPart((minutes % MINUTES_PER_HOUR).toString().padStart(2, '0'), units.minute),
            )
            else -> listOf(
                DurationPart((minutes / MINUTES_PER_DAY).toString(), units.day),
                DurationPart(((minutes % MINUTES_PER_DAY) / MINUTES_PER_HOUR).toString(), units.hour),
            )
        }
    }

    /** The TalkBack form: "2 hours 10 minutes", "48 minutes", "1 day 3 hours", "less than a minute". */
    fun spoken(millis: Long): String {
        val minutes = wholeMinutes(millis)
        return when {
            minutes < 1 -> units.spokenLessThanMinute
            minutes < MINUTES_PER_HOUR -> units.spokenMinutes(minutes)
            minutes < MINUTES_PER_DAY -> {
                val h = minutes / MINUTES_PER_HOUR
                val m = minutes % MINUTES_PER_HOUR
                if (m == 0L) units.spokenHours(h) else units.spokenHours(h) + " " + units.spokenMinutes(m)
            }
            else -> {
                val d = minutes / MINUTES_PER_DAY
                val h = (minutes % MINUTES_PER_DAY) / MINUTES_PER_HOUR
                if (h == 0L) units.spokenDays(d) else units.spokenDays(d) + " " + units.spokenHours(h)
            }
        }
    }

    /**
     * The displayed duration between two instants, in whole clock minutes ([clockDurationMillis]): use it for
     * receipts such as "Slept 1h 07m" and "Feed ended · 18m", so they match the clock times around them.
     */
    fun between(fromMillis: Long, toMillis: Long): String = format(clockDurationMillis(fromMillis, toMillis))

    /** The spoken form of [between]. */
    fun spokenBetween(fromMillis: Long, toMillis: Long): String = spoken(clockDurationMillis(fromMillis, toMillis))

    /** An offset chip label: "−5m" (U+2212 minus), "+5m", "0m". */
    fun offset(minutes: Int): String = when {
        minutes < 0 -> MINUS + (-minutes) + units.minute
        minutes > 0 -> "+" + minutes + units.minute
        else -> "0" + units.minute
    }

    companion object {
        /** The minus sign used in offsets (U+2212). */
        const val MINUS = "−"
        private const val MINUTES_PER_HOUR = 60L
        private const val MINUTES_PER_DAY = 24 * 60L

        fun wholeMinutes(millis: Long): Long = millis.coerceAtLeast(0L) / 60_000L

        fun isUnderAMinute(millis: Long): Boolean = wholeMinutes(millis) < 1

        fun from(context: Context): DurationFormatter = DurationFormatter(DurationUnits.from(context))
    }
}
