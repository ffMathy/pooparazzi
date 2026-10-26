package com.ffmathy.xaternity.core.format

import android.content.Context
import com.ffmathy.xaternity.core.R
import java.time.ZoneId
import java.util.Locale

/**
 * Localised relative phrases (V1_ADDITIONS §5): "ago" / "siden", "%s ago" / "%s siden",
 * "since %s" / "siden kl. %s", "just now" / "lige nu", "at %s" / "kl. %s".
 * Formats use Android `%s` / `%1$s` placeholders.
 */
class RelativePhrases(
    val ago: String,
    val agoFormat: String,
    val sinceFormat: String,
    val justNow: String,
    val atFormat: String,
    val earlierFormat: String,
    val laterFormat: String,
) {
    companion object {
        fun from(context: Context): RelativePhrases {
            val res = context.resources
            return RelativePhrases(
                ago = res.getString(R.string.rel_ago),
                agoFormat = res.getString(R.string.rel_ago_format),
                sinceFormat = res.getString(R.string.rel_since_format),
                justNow = res.getString(R.string.rel_just_now),
                atFormat = res.getString(R.string.rel_at_format),
                earlierFormat = res.getString(R.string.spoken_earlier_format),
                laterFormat = res.getString(R.string.spoken_later_format),
            )
        }
    }
}

/** Time-since and since/at phrases built from [DurationFormatter] and [ClockFormatter]. */
class RelativeFormatter(
    private val duration: DurationFormatter,
    private val clock: ClockFormatter,
    private val phrases: RelativePhrases,
    private val locale: Locale = clock.locale,
) {
    /** The bare word for the Lamp sub-line: "ago" / "siden" (as in "ago · 12:22"). */
    val agoWord: String get() = phrases.ago

    val justNow: String get() = phrases.justNow

    /** "48m ago" / "48m siden"; "just now" under a minute (never "0m ago"). */
    fun ago(elapsedMillis: Long): String =
        if (DurationFormatter.isUnderAMinute(elapsedMillis)) phrases.justNow else fill(phrases.agoFormat, duration.format(elapsedMillis))

    /** "48 minutes ago"; "just now" under a minute. */
    fun agoSpoken(elapsedMillis: Long): String =
        if (DurationFormatter.isUnderAMinute(elapsedMillis)) phrases.justNow else fill(phrases.agoFormat, duration.spoken(elapsedMillis))

    /** "since 13:27" / "siden kl. 13:27". */
    fun since(millis: Long, zone: ZoneId): String = fill(phrases.sinceFormat, clock.format(millis, zone))

    /** "at 14:32" / "kl. 14:32". */
    fun at(millis: Long, zone: ZoneId): String = fill(phrases.atFormat, clock.format(millis, zone))

    /** Spoken offset chip: "5 minutes earlier" / "5 minutes later". */
    fun offsetSpoken(minutes: Int): String {
        val amount = duration.spoken(kotlin.math.abs(minutes) * 60_000L)
        return fill(if (minutes < 0) phrases.earlierFormat else phrases.laterFormat, amount)
    }

    private fun fill(format: String, arg: String): String = String.format(locale, format, arg)
}
