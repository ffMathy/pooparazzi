package com.ffmathy.pooparazzi.core.format

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Immutable
import com.ffmathy.pooparazzi.core.R
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a bottle amount is shown and picked: millilitres, or US fluid ounces where those are the custom. */
enum class VolumeUnit {
    MILLILITRES, FLUID_OUNCES;

    companion object {
        /** The regions that measure a baby's bottle in fluid ounces (the US, Liberia and Myanmar). */
        private val OUNCE_REGIONS = setOf("US", "LR", "MM")

        /** The unit of [region] (an ISO 3166 country code): fluid ounces in [OUNCE_REGIONS], else millilitres. */
        fun forRegion(region: String?): VolumeUnit =
            if (region?.uppercase(Locale.ROOT) in OUNCE_REGIONS) FLUID_OUNCES else MILLILITRES
    }
}

/**
 * Bottle amounts (stored in millilitres, [com.ffmathy.pooparazzi.core.model.Entry.amountMl]) in [unit]: "120 ml",
 * or "4 fl oz" / "4.5 fl oz" ("4,5 fl oz" in Danish) to the nearest half ounce. Also the amount slider's steps
 * ([steps], [stepOf], [amountAt]): step 0 is "no amount", then 10 ml (up to 300 ml) or ½ fl oz (up to 10 fl oz) each,
 * and the watch's amount chips ([quickAmounts]).
 */
@Immutable
class VolumeFormatter(
    val unit: VolumeUnit,
    private val locale: Locale,
    private val millilitresPattern: String = "%1\$s ml",
    private val ouncesPattern: String = "%1\$s fl oz",
) {
    /** The last slider step; [amountAt] of it is the largest amount the slider offers. */
    val steps: Int get() = if (unit == VolumeUnit.MILLILITRES) MAX_ML / ML_STEP else MAX_HALF_OUNCES

    /** The amount in millilitres at slider [step] (clamped to 0‥[steps]); null at step 0 (no amount). */
    fun amountAt(step: Int): Int? {
        val clamped = step.coerceIn(0, steps)
        if (clamped == 0) return null
        return if (unit == VolumeUnit.MILLILITRES) clamped * ML_STEP else (clamped * ML_PER_OUNCE / 2).roundToInt()
    }

    /** The slider step nearest to [amountMl] (0 for none). */
    fun stepOf(amountMl: Int?): Int {
        if (amountMl == null || amountMl <= 0) return 0
        val nearest = (0..steps).minBy { step -> abs((amountAt(step) ?: 0) - amountMl) }
        return nearest.coerceAtLeast(1)
    }

    /** "120 ml" or "4.5 fl oz". */
    fun format(amountMl: Int): String = String.format(locale, pattern, numberOf(amountMl))

    /** The number of [format] without its unit: "120", "4.5" ("4,5" in Danish). */
    fun numberOf(amountMl: Int): String = when (unit) {
        VolumeUnit.MILLILITRES -> number(BigDecimal.valueOf(amountMl.toLong()))
        VolumeUnit.FLUID_OUNCES -> {
            val halves = (amountMl / ML_PER_OUNCE * 2).roundToInt().coerceAtLeast(1)
            number(BigDecimal.valueOf(halves * 5L, 1))
        }
    }

    /** The unit of [format] on its own: "ml" or "fl oz". */
    val unitSymbol: String get() = pattern.replace(NUMBER_PLACEHOLDER, "").trim()

    /**
     * The watch's three amount chips, smallest first: [QUICK_DELTA_ML_STEPS] steps (30 ml) or [QUICK_DELTA_OUNCE_STEPS]
     * steps (1 fl oz) apart, centred on the step of [lastMl] (the last bottle's amount), or on 90 ml / 3 fl oz without
     * one, and kept inside the scale so all three exist: 60 · 90 · 120 ml and 2 · 3 · 4 fl oz by default.
     */
    fun quickAmounts(lastMl: Int?): List<Int> {
        val delta = if (unit == VolumeUnit.MILLILITRES) QUICK_DELTA_ML_STEPS else QUICK_DELTA_OUNCE_STEPS
        val default = stepOf(if (unit == VolumeUnit.MILLILITRES) QUICK_DEFAULT_ML else (QUICK_DEFAULT_OUNCES * ML_PER_OUNCE).roundToInt())
        val centre = (stepOf(lastMl).takeIf { it > 0 } ?: default).coerceIn(1 + delta, steps - delta)
        return listOf(centre - delta, centre, centre + delta).map { checkNotNull(amountAt(it)) }
    }

    private val pattern: String get() = if (unit == VolumeUnit.MILLILITRES) millilitresPattern else ouncesPattern

    private fun number(value: BigDecimal): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(value)

    companion object {
        const val ML_PER_OUNCE = 29.5735
        private const val NUMBER_PLACEHOLDER = "%1\$s"
        private const val QUICK_DELTA_ML_STEPS = 3
        private const val QUICK_DELTA_OUNCE_STEPS = 2
        private const val QUICK_DEFAULT_ML = 90
        private const val QUICK_DEFAULT_OUNCES = 3
        private const val ML_STEP = 10
        private const val MAX_ML = 300
        private const val MAX_HALF_OUNCES = 20

        /**
         * The formatter for the app's region and language. The in-app languages carry no region ("da", "en"), so
         * then the device's region decides: a Danish-language app on a US phone still measures in ounces.
         */
        fun from(context: Context): VolumeFormatter {
            val region = listOfNotNull(
                context.resources.configuration.locales[0]?.country,
                Resources.getSystem().configuration.locales[0]?.country,
            ).firstOrNull { it.isNotEmpty() }
            return VolumeFormatter(
                unit = VolumeUnit.forRegion(region),
                locale = ClockFormatter.locale(context),
                millilitresPattern = context.getString(R.string.volume_ml),
                ouncesPattern = context.getString(R.string.volume_fl_oz),
            )
        }
    }
}
