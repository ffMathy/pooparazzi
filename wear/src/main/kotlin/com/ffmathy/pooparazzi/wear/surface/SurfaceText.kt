package com.ffmathy.pooparazzi.wear.surface

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicBool
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicDuration
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import java.time.Instant
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * A time-dependent text for surfaces the system renders (the Tile and complications): the [static] value at the time
 * the surface was built, and the [dynamic] expression the renderer re-evaluates on its own clock, so nothing in the
 * app has to tick (DESIGN_SPEC §2.1, §10.9, §10.10). [longest] is the widest text it is expected to grow to while it
 * ticks ("23h 59m ago"), for layout constraints.
 */
class TimeText(val static: String, val dynamic: DynamicString, val longest: String = static)

/**
 * The app's formatting grammar (DESIGN_SPEC §7, V1_ADDITIONS §5) for the Tile and complications, both as static text
 * ([Formatters]) and as ProtoLayout [DynamicString] expressions that follow the platform clock:
 *
 * - durations: "<1m", "48m", "2h 10m" (minutes zero-padded), "1d 3h"; Danish "2t 10m";
 * - time since: "48m ago" / "48m siden", "just now" under a minute;
 * - spoken forms for content descriptions: "2 hours 10 minutes ago" (§9.5).
 *
 * Durations count whole wall-clock minutes, like every duration the app shows ([clockDurationMillis]), so a tile line
 * always agrees with the clock times printed next to it.
 */
class SurfaceText(
    val formatters: Formatters,
    private val words: Words,
) {
    val zone: ZoneId get() = formatters.zone

    /** "2h 10m" since [fromMillis] ("<1m" under a minute). */
    fun duration(fromMillis: Long, now: Long): TimeText {
        val static = formatters.duration.format(clockDurationMillis(fromMillis, now))
        return TimeText(static, dynamicDuration(fromMillis), longestOf(static, widestDuration))
    }

    /** "2h 10m ago" since [fromMillis] ("just now" under a minute). */
    fun ago(fromMillis: Long, now: Long): TimeText {
        val static = formatters.relative.ago(clockDurationMillis(fromMillis, now))
        return TimeText(
            static,
            words.ago.wrap(agoBody(fromMillis), justNow(fromMillis)),
            longestOf(static, words.ago.fill(widestDuration), formatters.relative.justNow),
        )
    }

    /** Spoken "2 hours 10 minutes" since [fromMillis]. */
    fun spokenDuration(fromMillis: Long, now: Long): TimeText =
        TimeText(formatters.duration.spoken(clockDurationMillis(fromMillis, now)), dynamicSpokenDuration(fromMillis))

    /** Spoken "2 hours 10 minutes ago" since [fromMillis] ("just now" under a minute). */
    fun spokenAgo(fromMillis: Long, now: Long): TimeText =
        TimeText(
            formatters.relative.agoSpoken(clockDurationMillis(fromMillis, now)),
            words.ago.wrap(dynamicSpokenDuration(fromMillis), justNow(fromMillis)),
        )

    /** A [TimeText] placed into a one-argument resource template such as "Asleep %1$s" or "Last feed %1$s". */
    fun template(template: Template, value: TimeText): TimeText =
        TimeText(template.fill(value.static), template.wrap(value.dynamic), template.fill(value.longest))

    /** Clock time "14:32" (absolute, never changes). */
    fun clock(millis: Long): String = formatters.clock.format(millis, zone)

    /** "00h 00m": as wide as a duration under a day gets (tabular digits). */
    private val widestDuration: String get() = WIDE_NUMBER + words.hour + " " + WIDE_NUMBER + words.minute

    private fun longestOf(vararg candidates: String): String = candidates.maxBy { it.length }

    // ---- Dynamic expressions -------------------------------------------------------------------

    /** Wall-clock minutes from [fromMillis] to the platform time. */
    private fun elapsed(fromMillis: Long): DynamicDuration =
        DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(Math.floorDiv(fromMillis, MINUTE) * MINUTE))
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())

    private fun dynamicDuration(fromMillis: Long): DynamicString {
        val d = elapsed(fromMillis)
        val totalMinutes = d.toIntMinutes()
        return DynamicString.onCondition(totalMinutes.lt(1))
            .use(DynamicString.constant(LESS_THAN_ONE + words.minute))
            .elseUse(durationBody(d, totalMinutes))
    }

    /** The duration of [dynamicDuration] without the "<1m" case (under a minute, the callers say "just now"). */
    private fun agoBody(fromMillis: Long): DynamicString {
        val d = elapsed(fromMillis)
        return durationBody(d, d.toIntMinutes())
    }

    private fun durationBody(d: DynamicDuration, totalMinutes: DynamicInt32): DynamicString {
        val minutes = totalMinutes.format().concat(DynamicString.constant(words.minute))
        val hoursAndMinutes = d.toIntHours().format()
            .concat(DynamicString.constant(words.hour + " "))
            .concat(d.getMinutesPart().format(TWO_DIGITS))
            .concat(DynamicString.constant(words.minute))
        val daysAndHours = d.toIntDays().format()
            .concat(DynamicString.constant(words.day + " "))
            .concat(d.getHoursPart().format())
            .concat(DynamicString.constant(words.hour))
        return DynamicString.onCondition(totalMinutes.lt(MINUTES_PER_HOUR)).use(minutes)
            .elseUse(DynamicString.onCondition(totalMinutes.lt(MINUTES_PER_DAY)).use(hoursAndMinutes).elseUse(daysAndHours))
    }

    /** The "just now" override: while under a minute since [fromMillis], time-since reads "just now". */
    private fun justNow(fromMillis: Long): Pair<DynamicBool, String> =
        elapsed(fromMillis).toIntMinutes().lt(1) to formatters.relative.justNow

    private fun dynamicSpokenDuration(fromMillis: Long): DynamicString {
        val d = elapsed(fromMillis)
        val total = d.toIntMinutes()
        val hours = d.toIntHours()
        val minutesPart = d.getMinutesPart()
        val days = d.toIntDays()
        val hoursPart = d.getHoursPart()
        val hoursThenMinutes = DynamicString.onCondition(minutesPart.eq(0))
            .use(words.hours.of(hours))
            .elseUse(words.hours.of(hours).concat(DynamicString.constant(" ")).concat(words.minutes.of(minutesPart)))
        val daysThenHours = DynamicString.onCondition(hoursPart.eq(0))
            .use(words.days.of(days))
            .elseUse(words.days.of(days).concat(DynamicString.constant(" ")).concat(words.hours.of(hoursPart)))
        return DynamicString.onCondition(total.lt(1)).use(DynamicString.constant(words.lessThanAMinute))
            .elseUse(
                DynamicString.onCondition(total.lt(MINUTES_PER_HOUR)).use(words.minutes.of(total))
                    .elseUse(DynamicString.onCondition(total.lt(MINUTES_PER_DAY)).use(hoursThenMinutes).elseUse(daysThenHours)),
            )
    }

    /**
     * A one-argument resource template ("%1$s ago", "Asleep %1$s"), split around its placeholder so a dynamic value
     * can be placed inside it.
     */
    class Template(private val raw: String) {
        private val prefix = raw.substringBefore(PLACEHOLDER, missingDelimiterValue = "")
        private val suffix = raw.substringAfter(PLACEHOLDER, missingDelimiterValue = "")

        fun fill(value: String): String = prefix + value + suffix

        fun wrap(value: DynamicString): DynamicString =
            DynamicString.constant(prefix).concat(value).concat(DynamicString.constant(suffix))

        /** Like [wrap], but [override]'s text replaces the whole result while its condition holds ("just now"). */
        internal fun wrap(value: DynamicString, override: Pair<DynamicBool, String>): DynamicString =
            DynamicString.onCondition(override.first).use(DynamicString.constant(override.second)).elseUse(wrap(value))

        companion object {
            const val PLACEHOLDER = "%1\$s"

            fun of(resources: Resources, @StringRes id: Int) = Template(resources.getString(id))
        }
    }

    /** A spoken plural ("1 hour" / "2 hours"), from the shared `<plurals>` (English and Danish use one/other). */
    class Plural(private val one: String, private val other: String) {
        fun of(count: DynamicInt32): DynamicString =
            DynamicString.onCondition(count.eq(1)).use(fill(one, count)).elseUse(fill(other, count))

        private fun fill(template: String, count: DynamicInt32): DynamicString {
            val prefix = template.substringBefore(COUNT, missingDelimiterValue = "")
            val suffix = template.substringAfter(COUNT, missingDelimiterValue = template)
            return DynamicString.constant(prefix).concat(count.format()).concat(DynamicString.constant(suffix))
        }

        companion object {
            private const val COUNT = "%d"

            /** The raw (unformatted) quantity strings of [id]. */
            fun of(resources: Resources, @PluralsRes id: Int) =
                Plural(resources.getQuantityString(id, 1), resources.getQuantityString(id, OTHER_QUANTITY))
        }
    }

    /** The localised words the dynamic expressions are built from (core's shared resources). */
    class Words(
        val day: String,
        val hour: String,
        val minute: String,
        val ago: Template,
        val days: Plural,
        val hours: Plural,
        val minutes: Plural,
        val lessThanAMinute: String,
    ) {
        companion object {
            fun from(resources: Resources) = Words(
                day = resources.getString(CoreR.string.unit_day),
                hour = resources.getString(CoreR.string.unit_hour),
                minute = resources.getString(CoreR.string.unit_minute),
                ago = Template.of(resources, CoreR.string.rel_ago_format),
                days = Plural.of(resources, CoreR.plurals.spoken_days),
                hours = Plural.of(resources, CoreR.plurals.spoken_hours),
                minutes = Plural.of(resources, CoreR.plurals.spoken_minutes),
                lessThanAMinute = resources.getString(CoreR.string.spoken_less_than_minute),
            )
        }
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * 60
        private const val OTHER_QUANTITY = 5
        private const val LESS_THAN_ONE = "<1"
        private const val WIDE_NUMBER = "00"
        private val TWO_DIGITS = DynamicInt32.IntFormatter.Builder().setMinIntegerDigits(2).build()

        /** Text in [context]'s language (the app language synced from the phone) and the device 12/24 h setting. */
        fun from(context: Context, zone: ZoneId = ZoneId.systemDefault()) =
            SurfaceText(Formatters.from(context, zone), Words.from(context.resources))
    }
}
