package com.ffmathy.pooparazzi.phone.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.domain.Ribbon
import com.ffmathy.pooparazzi.core.domain.RibbonSpan
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.phone.ui.theme.NightRule
import java.time.LocalTime
import java.time.ZoneId

/** Ribbon geometry inside its 350 × 100 card (DESIGN_SPEC §3.3.3, §6.4; V1_ADDITIONS §4). */
internal object RibbonDefaults {
    val Height: Dp = 100.dp
    val Shape = RoundedCornerShape(20.dp)

    /** Plot x = 12 + (width − 24) × fraction of the day. */
    const val PLOT_INSET = 12f

    const val BAND_TOP = 8f
    const val BAND_BOTTOM = 74f
    const val BAND_RADIUS = 6f

    const val SLEEP_TOP = 13f
    const val FEED_TOP = 34f
    const val LANE_HEIGHT = 14f
    const val BAR_RADIUS = 3f
    const val BAR_MIN_WIDTH = 6f

    const val TICK_WIDTH = 4f
    const val TICK_RADIUS = 2f

    /** Food: the feed tick with a small square cap on top (shape stays the redundant cue). */
    const val FOOD_CAP = 8f

    const val DIAPER_CENTRE = 60f
    const val PEE_RADIUS = 5f
    const val PEE_STROKE = 2f
    const val POOP_RADIUS = 6f

    const val NOW_LINE_WIDTH = 2f
    const val AXIS_TOP = 78f
}

/**
 * The day ribbon (glance-only, not tappable): night bands, then three lanes — sleep bars; feeds (formula ticks, food
 * ticks with a square cap, breast ticks with a round foot); diapers (pee ring, poop dot, both = ring + solid right half) — and the
 * now-line in onSurface. Nothing is drawn after now. Axis labels "00 06 12 18 24". TalkBack reads [summary] instead.
 */
@Composable
internal fun HistoryRibbon(ribbon: Ribbon, zone: ZoneId, summary: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalXPalette.current
    val events = palette.events
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val card = colors.surfaceContainer
    val nightBand = palette.colors.ribbonNightBand
    val nowColor = colors.onSurface
    // 100 dp at the reference size; the axis labels' line grows with the font scale and the card with it.
    val axisLine = with(LocalDensity.current) { axisStyle.lineHeight.toDp() }
    val height = maxOf(RibbonDefaults.Height, RibbonDefaults.AXIS_TOP.dp + axisLine + 6.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RibbonDefaults.Shape)
            .background(card)
            .clearAndSetSemantics { contentDescription = summary },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            val inset = RibbonDefaults.PLOT_INSET * d
            val plotWidth = size.width - 2 * inset
            fun x(fraction: Float) = inset + plotWidth * fraction
            fun dp(value: Float) = value * d

            // Night bands: the default night window (19:00–07:00), split at midnight.
            val nightEnd = ribbon.fractionAt(NightRule.DEFAULT_NIGHT_END, zone)
            val nightStart = ribbon.fractionAt(NightRule.DEFAULT_NIGHT_START, zone)
            band(x(0f), x(nightEnd), nightBand, d)
            band(x(nightStart), x(1f), nightBand, d)

            // Core's ribbon already ends every span at now and leaves out later instants.
            ribbon.sleeps.forEach { bar(it, ::x, dp(RibbonDefaults.SLEEP_TOP), events.sleep.strong, d) }
            ribbon.breastfeeds.forEach { breastTick(x(it.at), events.feed.strong, d) }
            ribbon.formula.forEach { tick(x(it.at), events.feed.strong, d) }
            ribbon.food.forEach { foodTick(x(it.at), events.feed.strong, d) }
            ribbon.diapers.forEach { mark ->
                val centre = Offset(x(mark.at), dp(RibbonDefaults.DIAPER_CENTRE))
                when (mark.kind) {
                    DiaperKind.PEE -> peeRing(centre, events.pee.strong, card, d)
                    DiaperKind.POOP -> drawCircle(events.poop.strong, dp(RibbonDefaults.POOP_RADIUS), centre)
                    DiaperKind.BOTH -> {
                        peeRing(centre, events.pee.strong, card, d)
                        // The right half-disc in POOP strong over the ring.
                        val r = dp(RibbonDefaults.POOP_RADIUS)
                        drawArc(
                            color = events.poop.strong,
                            startAngle = -90f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(centre.x - r, centre.y - r),
                            size = Size(2 * r, 2 * r),
                        )
                    }
                    // A plain change (neither pee nor poop): a small DIAPER strong square, unlike every other mark.
                    DiaperKind.PLAIN -> {
                        val half = dp(RibbonDefaults.POOP_RADIUS) * 0.8f
                        drawRect(events.diaper.strong, topLeft = Offset(centre.x - half, centre.y - half), size = Size(2 * half, 2 * half))
                    }
                }
            }

            ribbon.nowFraction?.let { fraction ->
                val w = dp(RibbonDefaults.NOW_LINE_WIDTH)
                drawRect(
                    nowColor,
                    topLeft = Offset(x(fraction) - w / 2, dp(RibbonDefaults.BAND_TOP)),
                    size = Size(w, dp(RibbonDefaults.BAND_BOTTOM - RibbonDefaults.BAND_TOP)),
                )
            }

            axisLabels(ribbon, zone, measurer, axisStyle, ::x, inset, d)
        }
    }
}

private fun DrawScope.band(from: Float, to: Float, color: Color, d: Float) {
    if (to <= from) return
    drawRoundRect(
        color,
        topLeft = Offset(from, RibbonDefaults.BAND_TOP * d),
        size = Size(to - from, (RibbonDefaults.BAND_BOTTOM - RibbonDefaults.BAND_TOP) * d),
        cornerRadius = CornerRadius(RibbonDefaults.BAND_RADIUS * d),
    )
}

/** A 14 dp bar, rx 3; at least 6 dp wide, widened around its centre (never a pill wider than tall). */
private fun DrawScope.bar(span: RibbonSpan, x: (Float) -> Float, top: Float, color: Color, d: Float) {
    var left = x(span.start)
    var right = x(span.end)
    val min = RibbonDefaults.BAR_MIN_WIDTH * d
    if (right - left < min) {
        val centre = (left + right) / 2
        left = centre - min / 2
        right = centre + min / 2
    }
    drawRoundRect(
        color,
        topLeft = Offset(left, top),
        size = Size(right - left, RibbonDefaults.LANE_HEIGHT * d),
        cornerRadius = CornerRadius(RibbonDefaults.BAR_RADIUS * d),
    )
}

/** Formula: a 4 × 14 tick, rx 2, centred on the time. */
private fun DrawScope.tick(centre: Float, color: Color, d: Float) {
    val w = RibbonDefaults.TICK_WIDTH * d
    drawRoundRect(
        color,
        topLeft = Offset(centre - w / 2, RibbonDefaults.FEED_TOP * d),
        size = Size(w, RibbonDefaults.LANE_HEIGHT * d),
        cornerRadius = CornerRadius(RibbonDefaults.TICK_RADIUS * d),
    )
}

/** Food: the tick with an 8 × 6 square cap at its top, a "T" that tells it from formula without colour. */
private fun DrawScope.foodTick(centre: Float, color: Color, d: Float) {
    tick(centre, color, d)
    val cap = RibbonDefaults.FOOD_CAP * d
    drawRoundRect(
        color,
        topLeft = Offset(centre - cap / 2, RibbonDefaults.FEED_TOP * d),
        size = Size(cap, cap * 0.75f),
        cornerRadius = CornerRadius(1.5f * d),
    )
}

/** Breast: the tick with a round foot (a disc Ø 8 at its bottom), an upside-down lollipop unlike formula and food. */
private fun DrawScope.breastTick(centre: Float, color: Color, d: Float) {
    tick(centre, color, d)
    val r = RibbonDefaults.FOOD_CAP * d / 2
    drawCircle(color, r, Offset(centre, (RibbonDefaults.FEED_TOP + RibbonDefaults.LANE_HEIGHT) * d - r))
}

/** Pee: a ring r 5, stroke 2, filled with the card colour. */
private fun DrawScope.peeRing(centre: Offset, color: Color, fill: Color, d: Float) {
    val r = RibbonDefaults.PEE_RADIUS * d
    drawCircle(fill, r, centre)
    drawCircle(color, r, centre, style = Stroke(RibbonDefaults.PEE_STROKE * d))
}

/** "00" start-aligned, "06" "12" "18" centred on their hour, "24" end-aligned (labelSmall onSurfaceVariant). */
private fun DrawScope.axisLabels(
    ribbon: Ribbon,
    zone: ZoneId,
    measurer: TextMeasurer,
    style: TextStyle,
    x: (Float) -> Float,
    inset: Float,
    d: Float,
) {
    val top = RibbonDefaults.AXIS_TOP * d
    listOf(0, 6, 12, 18, 24).forEach { hour ->
        val label = measurer.measure(hour.toString().padStart(2, '0'), style)
        val w = label.size.width.toFloat()
        val left = when (hour) {
            0 -> inset
            24 -> size.width - inset - w
            else -> x(ribbon.fractionAt(LocalTime.of(hour, 0), zone)) - w / 2
        }
        drawText(label, topLeft = Offset(left, top))
    }
}
