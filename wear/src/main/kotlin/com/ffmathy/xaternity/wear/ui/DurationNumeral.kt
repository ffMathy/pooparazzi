package com.ffmathy.xaternity.wear.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.ffmathy.xaternity.core.format.DurationPart

/**
 * A duration as number/unit pairs in two styles, baseline-aligned (DESIGN_SPEC §2.5, §3.5.1, §7 "hero split"):
 * [gapAfterNumber] after each number, [gapBetween] between groups, no space characters. An optional [trailing]
 * word follows after 4 dp ("ago"). When the whole line is wider than [maxWidth], the number size steps down by
 * 1 sp to [minNumberSize] (units keep their ratio). The line is read as [spoken] (e.g. "2 hours 10 minutes ago").
 */
@Composable
fun DurationNumeral(
    parts: List<DurationPart>,
    numberStyle: TextStyle,
    unitStyle: TextStyle,
    color: Color,
    spoken: String,
    modifier: Modifier = Modifier,
    gapAfterNumber: Dp = 2.dp,
    gapBetween: Dp = 6.dp,
    maxWidth: Dp = Dp.Infinity,
    minNumberSize: TextUnit = numberStyle.fontSize,
    trailing: String? = null,
    trailingStyle: TextStyle = TextStyle.Default,
    trailingColor: Color = color,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scale = remember(parts, numberStyle, unitStyle, maxWidth, minNumberSize, trailing, trailingStyle, density) {
        val max = numberStyle.fontSize.value
        val min = if (minNumberSize.isSpecified) minNumberSize.value else max
        var size = max
        while (true) {
            val factor = size / max
            val width = with(density) {
                var total = 0f
                parts.forEachIndexed { index, part ->
                    if (index > 0) total += gapBetween.toPx()
                    total += measurer.measure(part.number, numberStyle.scaled(factor)).size.width
                    total += gapAfterNumber.toPx()
                    total += measurer.measure(part.unit, unitStyle.scaled(factor)).size.width
                }
                if (trailing != null) total += TRAILING_GAP.toPx() + measurer.measure(trailing, trailingStyle).size.width
                total.toDp()
            }
            if (width <= maxWidth || size - 1f < min) return@remember factor
            size -= 1f
        }
        @Suppress("UNREACHABLE_CODE")
        1f
    }
    Row(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Spacer(Modifier.width(gapBetween))
            Text(part.number, style = numberStyle.scaled(scale), color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(gapAfterNumber))
            Text(part.unit, style = unitStyle.scaled(scale).baselineOnly(), color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
        if (trailing != null) {
            Spacer(Modifier.width(TRAILING_GAP))
            Text(trailing, style = trailingStyle.baselineOnly(), color = trailingColor, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

private val TRAILING_GAP = 4.dp

/**
 * The smaller texts beside the number sit on its baseline; their own (tall) line height would push the row below
 * the number's line box (an 18 sp unit on a 36 sp line ends ~6 dp lower), so they keep only the font's own height.
 */
private fun TextStyle.baselineOnly(): TextStyle = copy(lineHeight = TextUnit.Unspecified)

private fun TextStyle.scaled(factor: Float): TextStyle {
    if (factor == 1f) return this
    return copy(
        fontSize = (fontSize.value * factor).sp,
        lineHeight = if (lineHeight.isSpecified) (lineHeight.value * factor).sp else lineHeight,
    )
}
