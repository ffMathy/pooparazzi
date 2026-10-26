package com.ffmathy.xaternity.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A local calendar day as a half-open epoch-ms range [startMillis, endMillis). Built from the zone's
 * start-of-day, so DST days are 23 or 25 hours long.
 */
data class DayBounds(val day: LocalDate, val startMillis: Long, val endMillis: Long) {
    val lengthMillis: Long get() = endMillis - startMillis

    operator fun contains(millis: Long): Boolean = millis in startMillis until endMillis

    /** Position of [millis] in the day as 0..1 (clamped). */
    fun fractionOf(millis: Long): Float =
        ((millis - startMillis).toDouble() / lengthMillis).coerceIn(0.0, 1.0).toFloat()

    /** The overlap of [from, to) with this day in ms (0 when they do not overlap). */
    fun overlapMillis(from: Long, to: Long): Long =
        (minOf(to, endMillis) - maxOf(from, startMillis)).coerceAtLeast(0L)

    companion object {
        fun of(day: LocalDate, zone: ZoneId): DayBounds = DayBounds(
            day = day,
            startMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            endMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        /** The day containing [millis] in [zone]. */
        fun containing(millis: Long, zone: ZoneId): DayBounds = of(localDate(millis, zone), zone)
    }
}

/** The local date of [millis] in [zone]. */
fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** The local time of day of [millis] in [zone]. */
fun localTime(millis: Long, zone: ZoneId): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

/** Epoch ms of [day] at [hour]:[minute] in [zone]. */
fun epochMillis(day: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
    day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
