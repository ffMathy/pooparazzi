package com.ffmathy.xaternity.wear.tile

import android.graphics.Paint
import android.graphics.Typeface
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters

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
        /** The first variant of "[label] [time] · [undo]" that fits [maxWidthDp] as [measure] reads it. */
        fun fitting(label: String, time: String, separator: String, undo: String, maxWidthDp: Float, measure: TileTextMeasure): LoggedTitleParts =
            entries.firstOrNull { parts -> parts.widthDp(label, time, separator, undo, measure) <= maxWidthDp } ?: NO_TIME

        private fun LoggedTitleParts.widthDp(label: String, clock: String, separator: String, undo: String, measure: TileTextMeasure): Float {
            val lead = if (time) "$label $clock$separator" else "$label$separator"
            var width = measure.titleWidthDp(lead) + measure.titleWidthDp(undo, bold = true)
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
 * An estimate of how wide the renderer draws one line of tile title text (Material 3 `titleSmall`: 14 sp, weight 500,
 * 0.4 sp tracking), from the platform's default typeface at the same size, weight and tracking plus a small margin.
 * The renderer's own font (the system's) can differ slightly; the margin covers that.
 */
class TileTextMeasure(fontScale: Float) {
    private val scale = fontScale.takeIf { it > 0f } ?: 1f
    private val regular = paint(TITLE_WEIGHT)
    private val bold = paint(BOLD_WEIGHT)

    /** The width in dp of [text] in the title style. */
    fun titleWidthDp(text: String, bold: Boolean = false): Float =
        (if (bold) this.bold else regular).measureText(text) * SAFETY

    private fun paint(weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, weight, false)
        // Measured in dp: 1 sp is fontScale dp.
        textSize = TITLE_SMALL_SP * scale
        letterSpacing = TITLE_SMALL_TRACKING_SP / TITLE_SMALL_SP
    }

    companion object {
        const val TITLE_ICON_DP = 14f
        private const val TITLE_SMALL_SP = 14f
        private const val TITLE_SMALL_TRACKING_SP = 0.4f
        private const val TITLE_WEIGHT = 500
        private const val BOLD_WEIGHT = 700
        private const val SAFETY = 1.05f

        /** Material 3 `primaryLayout` insets its title by 14.5 % of the screen width on each side. */
        private const val TITLE_SIDE_MARGIN = 0.145f

        /** The title slot's width on [device]. */
        fun titleSlotWidthDp(device: DeviceParameters): Float = device.screenWidthDp * (1f - 2f * TITLE_SIDE_MARGIN)
    }
}
