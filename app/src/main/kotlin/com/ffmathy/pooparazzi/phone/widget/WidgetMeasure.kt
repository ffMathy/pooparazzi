package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Measures widget text in the device font. RemoteViews cannot measure or auto-size, but the launcher draws the
 * widget with the same system font, so the app can decide before rendering whether a label fits on one line, which
 * fact fits in row 1 and which labels fit in the 3×1 buttons. Font scale is included (non-linear on Android 14+).
 */
internal class WidgetMeasure(context: Context) {
    private val metrics = context.resources.displayMetrics
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    /** The width of [text] on one line at [size] (sp), in dp. */
    fun width(text: String, size: TextUnit): Dp {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.value, metrics)
        return (paint.measureText(text) / metrics.density).dp
    }

    fun fits(text: String, size: TextUnit, available: Dp): Boolean = width(text, size) <= available
}
