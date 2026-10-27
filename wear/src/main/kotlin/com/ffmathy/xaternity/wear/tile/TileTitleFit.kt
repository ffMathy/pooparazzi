package com.ffmathy.xaternity.wear.tile

import android.graphics.Paint
import android.graphics.Typeface
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.material3.Typography

/**
 * Which parts the just-logged title keeps (DESIGN_SPEC §3.6: `check` + "Pee 14:32 · " + `undo` + "Undo"). The full
 * title fits the ≈ 161 dp title slot in English, but not every language: Danish "✓ Begge 14:32 · ↶ Fortryd" would be
 * cut to "Fortr…". Parts are dropped in this order until the title fits, so "Undo" (what the background tap does)
 * always stays whole: the undo glyph (the word says it too), then the check glyph, then the time.
 */
enum class LoggedTitleParts(val check: Boolean, val undoGlyph: Boolean, val time: Boolean) {
    FULL(check = true, undoGlyph = true, time = true),
    NO_UNDO_GLYPH(check = true, undoGlyph = false, time = true),
    TEXT_ONLY(check = false, undoGlyph = false, time = true),
    NO_TIME(check = false, undoGlyph = false, time = false),
    ;

    companion object {
        /**
         * The first variant of "[label] [time] · [undo]" that fits [maxWidthDp] as [measure] reads it. A confirmation
         * without a time ("Sleep cancelled") passes a null [time].
         */
        fun fitting(label: String, time: String?, separator: String, undo: String, maxWidthDp: Float, measure: TileTextMeasure): LoggedTitleParts =
            entries.firstOrNull { parts -> parts.widthDp(label, time, separator, undo, measure) <= maxWidthDp } ?: NO_TIME

        private fun LoggedTitleParts.widthDp(label: String, clock: String?, separator: String, undo: String, measure: TileTextMeasure): Float {
            val lead = if (time && clock != null) "$label $clock$separator" else "$label$separator"
            var width = measure.widthDp(lead) + measure.widthDp(undo, bold = true)
            if (check) width += TileTextMeasure.TITLE_ICON_DP + CHECK_GAP_DP
            if (undoGlyph) width += TileTextMeasure.TITLE_ICON_DP + UNDO_GAP_DP
            return width
        }

        /** Gaps after the glyphs (DESIGN_SPEC §3.6: check 14 + 4, undo 14 + 3). */
        const val CHECK_GAP_DP = 4f
        const val UNDO_GAP_DP = 3f
    }
}

/**
 * The just-logged confirmation as Row A's secondary label, on watches below 225 dp where the title is hidden
 * (DESIGN_SPEC §3.6: the feedback is then the secondary-label change). The longest of "Pee 14:32 · Undo",
 * "Pee · Undo", "Pee 14:32" and "Pee" that fits the row; buttons never relabel or move.
 */
object RowAConfirmation {
    fun text(label: String, labelWithTime: String?, separator: String, undo: String, maxWidthDp: Float, measure: TileTextMeasure): String {
        val candidates = listOfNotNull(
            labelWithTime?.let { it + separator + undo },
            label + separator + undo,
            labelWithTime,
        )
        return candidates.firstOrNull { measure.widthDp(it) <= maxWidthDp } ?: label
    }
}

/**
 * How Row B labels its buttons (Pee | Poop | Both): the Material 3 `labelSmall` the design asks for, one step smaller
 * where a word would touch the rounded edges (Danish "Begge" on 192 dp watches or at large font scales), and only the
 * icons where even that does not fit. The content descriptions name each button either way ("Log pee and poop").
 */
enum class RowBLabels(val typography: Int?) {
    LABEL_SMALL(Typography.LABEL_SMALL),
    BODY_SMALL(Typography.BODY_SMALL),
    ICONS_ONLY(null),
    ;

    companion object {
        /** The first style in which every one of [labels] fits [maxWidthDp] at [fontScale]. */
        fun fitting(labels: List<String>, maxWidthDp: Float, fontScale: Float): RowBLabels {
            val label = TileTextMeasure.labelSmall(fontScale)
            if (labels.all { label.widthDp(it) <= maxWidthDp }) return LABEL_SMALL
            val body = TileTextMeasure.bodySmall(fontScale)
            if (labels.all { body.widthDp(it) <= maxWidthDp }) return BODY_SMALL
            return ICONS_ONLY
        }
    }
}

/**
 * An estimate of how wide the renderer draws one line of tile text in a Material 3 type style (by default
 * `titleSmall`: 14 sp, weight 500, 0.4 sp tracking), from the platform's default typeface at the same size, weight,
 * width and tracking plus a small margin. The renderer's own font (the system's) can differ slightly; the margin
 * covers that.
 */
class TileTextMeasure(
    fontScale: Float,
    sizeSp: Float = TITLE_SMALL_SP,
    weight: Int = TITLE_WEIGHT,
    trackingSp: Float = TRACKING_SP,
    /** The style's width axis as a factor. */
    widthFactor: Float = 1f,
    /** Extra room over the measured width, for the renderer's own font. */
    private val safety: Float = SAFETY,
) {
    private val scale = fontScale.takeIf { it > 0f } ?: 1f
    private val regular = paint(sizeSp, weight, trackingSp, widthFactor)
    private val bold = paint(sizeSp, BOLD_WEIGHT, trackingSp, widthFactor)

    /** The width in dp of [text] in this style. */
    fun widthDp(text: String, bold: Boolean = false): Float =
        (if (bold) this.bold else regular).measureText(text) / MEASURE_SCALE * safety

    /** The width in dp of [text] in the title style. */
    fun titleWidthDp(text: String, bold: Boolean = false): Float = widthDp(text, bold)

    private fun paint(sizeSp: Float, weight: Int, trackingSp: Float, widthFactor: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, weight, false)
            // Measured in dp (1 sp is fontScale dp), magnified so that glyph hinting cannot round the widths.
            textSize = sizeSp * scale * MEASURE_SCALE
            letterSpacing = trackingSp / sizeSp
            textScaleX = widthFactor
        }

    companion object {
        const val TITLE_ICON_DP = 14f
        private const val TITLE_SMALL_SP = 14f
        private const val TRACKING_SP = 0.4f
        private const val TITLE_WEIGHT = 500
        private const val BOLD_WEIGHT = 700
        private const val SAFETY = 1.05f
        private const val MEASURE_SCALE = 10f

        /**
         * Material 3 `labelSmall` (Row A's secondary label, Row B): 13 sp, weight 500. Calibrated against rendered
         * tiles ("Poop" 30 dp, "Begge" 37 dp, "2t 10m siden" 80 dp): the platform font measures 2–5 % wider than the
         * renderer draws, which is margin enough, so no extra safety.
         */
        fun labelSmall(fontScale: Float) = TileTextMeasure(fontScale, sizeSp = 13f, weight = 500, safety = 1f)

        /** Material 3 `bodySmall`: 12 sp, weight 500 (same calibration as [labelSmall]). */
        fun bodySmall(fontScale: Float) = TileTextMeasure(fontScale, sizeSp = 12f, weight = 500, safety = 1f)

        /** Material 3 `primaryLayout` insets its title by 14.5 % of the screen width on each side. */
        private const val TITLE_SIDE_MARGIN = 0.145f

        /**
         * `primaryLayout`'s default side margins around the main slot with an edge button: 7.1 % of the width with a
         * title, 12 % without; plus about 2 dp the renderer keeps on each side (measured: 191 dp at 227, 144 dp at 192).
         */
        private const val MAIN_SIDE_MARGIN_WITH_TITLE = 0.071f
        private const val MAIN_SIDE_MARGIN_WITHOUT_TITLE = 0.12f
        private const val MAIN_SIDE_INSET_DP = 2f

        /** Row A's text column: the button less its 16 dp start padding, 24 dp icon, 6 dp gap and 12 dp end padding. */
        private const val ROW_A_TEXT_INSET_DP = 16f + 24f + 6f + 12f

        /** The title slot's width on [device]. */
        fun titleSlotWidthDp(device: DeviceParameters): Float = device.screenWidthDp * (1f - 2f * TITLE_SIDE_MARGIN)

        /** The main slot's width on [device], with or without the title slot. */
        fun mainSlotWidthDp(device: DeviceParameters, withTitle: Boolean): Float {
            val margin = if (withTitle) MAIN_SIDE_MARGIN_WITH_TITLE else MAIN_SIDE_MARGIN_WITHOUT_TITLE
            return device.screenWidthDp * (1f - 2f * margin) - 2f * MAIN_SIDE_INSET_DP
        }

        /** The width of Row A's secondary label. */
        fun rowATextWidthDp(device: DeviceParameters, withTitle: Boolean): Float = mainSlotWidthDp(device, withTitle) - ROW_A_TEXT_INSET_DP

        /**
         * The label width inside one Row B button that stays clear of its rounded edges: the label sits in the lower
         * half of the round-ended button, where the curve takes about 15 % of the width on each side, more as a larger
         * font pushes the label lower (calibrated on rendered tiles); never less than [horizontalPaddingDp].
         */
        fun rowBLabelWidthDp(device: DeviceParameters, withTitle: Boolean, gapDp: Float, horizontalPaddingDp: Float): Float {
            val button = (mainSlotWidthDp(device, withTitle) - 2f * gapDp) / 3f
            val curve = ROW_B_CURVE_INSET + ROW_B_CURVE_INSET_PER_SCALE * (device.fontScale - 1f).coerceAtLeast(0f)
            return button - 2f * maxOf(horizontalPaddingDp, button * curve)
        }

        private const val ROW_B_CURVE_INSET = 0.15f
        private const val ROW_B_CURVE_INSET_PER_SCALE = 0.1f
    }
}
