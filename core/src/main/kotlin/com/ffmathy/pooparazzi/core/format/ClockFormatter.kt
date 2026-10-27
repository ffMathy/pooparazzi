package com.ffmathy.pooparazzi.core.format

import android.content.Context
import android.text.format.DateFormat
import com.ffmathy.pooparazzi.core.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Clock times and dates (DESIGN_SPEC §7): "13:44" when the device uses 24 h (and always in Danish), otherwise
 * the locale's short time with AM/PM ("1:44 PM"), always with a colon. Ranges use an en dash: "12:40 – 13:27" on the phone,
 * "12:40–13:27" on the watch.
 *
 * @param pattern12 the 12-hour pattern; defaults to the locale's short time pattern.
 * @param dayMonthPattern pattern for [dayMonth] ("EEE d MMM" → "Mon 26 Oct"; Danish "EEE d. MMM").
 */
class ClockFormatter(
    val is24Hour: Boolean,
    val locale: Locale,
    pattern12: String? = null,
    dayMonthPattern: String = "EEE d MMM",
) {
    private val time: DateTimeFormatter = if (is24Hour) {
        DateTimeFormatter.ofPattern("HH:mm", locale)
    } else {
        DateTimeFormatter.ofPattern(withColon(pattern12 ?: shortTimePattern(locale)), locale)
    }
    private val weekday = DateTimeFormatter.ofPattern("EEE", locale)
    private val dayMonth = DateTimeFormatter.ofPattern(dayMonthPattern, locale)

    /** "14:32" / "2:32 PM". */
    fun format(millis: Long, zone: ZoneId): String = time.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "12:40 – 13:27" ([spaced], phone) or "12:40–13:27" (watch). */
    fun range(startMillis: Long, endMillis: Long, zone: ZoneId, spaced: Boolean = true): String =
        format(startMillis, zone) + (if (spaced) " $EN_DASH " else EN_DASH) + format(endMillis, zone)

    /**
     * A range listed under [day]: prefixed with the start's weekday when it starts on another day
     * ("Sun 22:40 – 01:35"), otherwise as [range].
     */
    fun rangeOnDay(startMillis: Long, endMillis: Long, day: LocalDate, zone: ZoneId, spaced: Boolean = true): String {
        val startDay = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val range = range(startMillis, endMillis, zone, spaced)
        return if (startDay == day) range else weekdayShort(startMillis, zone) + " " + range
    }

    /** Abbreviated weekday: "Sun" / "søn.". */
    fun weekdayShort(millis: Long, zone: ZoneId): String = weekday.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "Mon 26 Oct" / "man. 26. okt.". */
    fun dayMonth(date: LocalDate): String = dayMonth.format(date)

    companion object {
        /** The en dash used in ranges (U+2013). */
        const val EN_DASH = "–"

        fun shortTimePattern(locale: Locale): String =
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)

        /**
         * The locale dates, 12-hour markers and lower-casing use: the UI locale of [context] when the app ships its
         * language, otherwise the language the strings fell back to (English). A Swedish or German device then reads
         * "Today · Mon 26 Oct", not English text around "mån 26 okt.".
         */
        fun locale(context: Context): Locale {
            val ui = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val shipped = context.getString(R.string.format_language)
            return if (ui.language == shipped) ui else Locale.forLanguageTag(shipped)
        }

        /**
         * Whether clock times use 24 h: always in Danish (V1_ADDITIONS §5), otherwise the device setting.
         * A Danish UI on a 12-hour device would otherwise print "2.32 PM", a style §7 never allows.
         */
        fun uses24Hour(context: Context, locale: Locale = locale(context)): Boolean =
            locale.language == "da" || DateFormat.is24HourFormat(context)

        /** Follows the device 12/24 h setting (24 h always in Danish) and the context's locale. */
        fun from(context: Context): ClockFormatter {
            val locale = locale(context)
            return ClockFormatter(
                is24Hour = uses24Hour(context, locale),
                locale = locale,
                pattern12 = DateFormat.getBestDateTimePattern(locale, "hm"),
                dayMonthPattern = context.getString(R.string.pattern_weekday_day_month),
            )
        }

        /** A clock time always has a colon (§7: never "13.44"): turns "h.mm a" into "h:mm a", leaving quoted text alone. */
        internal fun withColon(pattern: String): String = buildString {
            var quoted = false
            for (c in pattern) {
                if (c == '\'') quoted = !quoted
                append(if (c == '.' && !quoted) ':' else c)
            }
        }
    }
}
