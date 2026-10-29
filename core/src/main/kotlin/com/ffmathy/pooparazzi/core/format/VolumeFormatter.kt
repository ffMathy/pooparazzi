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

/** How a formula amount is shown and picked: millilitres, or US fluid ounces where those are the custom. */
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
 * Formula amounts (stored in millilitres, [com.ffmathy.pooparazzi.core.model.Entry.amountMl]) in [unit]: "120 ml",
 * or "4 fl oz" / "4.5 fl oz" ("4,5 fl oz" in Danish) to the nearest half ounce. Also the amount slider's steps
 * ([steps], [stepOf], [amountAt]): step 0 is "no amount", then 10 ml (up to 300 ml) or ½ fl oz (up to 10 fl oz) each.
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
    fun format(amountMl: Int): String = when (unit) {
        VolumeUnit.MILLILITRES -> String.format(locale, millilitresPattern, number(BigDecimal.valueOf(amountMl.toLong())))
        VolumeUnit.FLUID_OUNCES -> {
            val halves = (amountMl / ML_PER_OUNCE * 2).roundToInt().coerceAtLeast(1)
            String.format(locale, ouncesPattern, number(BigDecimal.valueOf(halves * 5L, 1)))
        }
    }

    private fun number(value: BigDecimal): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(value)

    companion object {
        const val ML_PER_OUNCE = 29.5735
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
