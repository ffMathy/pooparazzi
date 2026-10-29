package com.ffmathy.pooparazzi.core.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * [tenths] / 10 with exactly one decimal in [locale] and no grouping: 67 is "6.7" in English and "6,7" in Danish,
 * 0 is "0.0". Exact (a [BigDecimal], never a Double), so the rounding already done in tenths is what is shown.
 */
fun formatTenths(tenths: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(BigDecimal.valueOf(tenths.toLong(), 1))
