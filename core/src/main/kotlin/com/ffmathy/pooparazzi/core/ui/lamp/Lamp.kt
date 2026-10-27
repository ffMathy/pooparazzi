package com.ffmathy.pooparazzi.core.ui.lamp

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.core.ui.tokens.XTokens
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Where the Lamp is drawn (DESIGN_SPEC §2.2–2.5). */
enum class LampVariant {
    /** Phone Night: static radial glow. */
    NIGHT,

    /** Phone Day: solid disc ("the shade"); the ember sits on a paper cut-out. */
    DAY,

    /** Watch, interactive: glow (only meaningful while running) + orbit. */
    WATCH,

    /** Watch ambient: no glow, no track fill, no tail; a 2 dp ring and a static dot. */
    WATCH_AMBIENT,
}

/** Which event's colours the Lamp body and orbit use. */
enum class LampEvent { FEED, SLEEP }

/**
 * Lamp geometry at its reference size, in dp. Everything scales by `size / referenceSize`.
 * The glow may extend past the Lamp's bounds (phone Ø360 around a Ø264 Lamp); it is not clipped.
 */
@Immutable
data class LampGeometry(
    val referenceSize: Dp,
    val ringRadius: Dp,
    val trackWidth: Dp,
    val emberDiameter: Dp,
    val glowRadius: Dp,
    val discRadius: Dp,
    val cutoutDiameter: Dp,
    val ambientRingWidth: Dp = 2.dp,
    val ambientDotDiameter: Dp = 6.dp,
) {
    companion object {
        /** Phone: Ø264, ring r 129 stroke 6, ember Ø10 (+ Ø14 Day cut-out), glow r 180, disc r 132. */
        val Phone = LampGeometry(264.dp, 129.dp, 6.dp, 10.dp, glowRadius = 180.dp, discRadius = 132.dp, cutoutDiameter = 14.dp)

        /** Watch: outer Ø126, ring r 60.5 stroke 5, ember Ø8, glow r 95. */
        val Watch = LampGeometry(126.dp, 60.5.dp, 5.dp, 8.dp, glowRadius = 95.dp, discRadius = 63.dp, cutoutDiameter = 12.dp)

        /** Small watches (< 225 dp): extent 93, ring r 42.5, track 4, ember Ø8. */
        val WatchSmall = LampGeometry(
            93.dp, 42.5.dp, 4.dp, 8.dp, glowRadius = 70.dp, discRadius = 46.5.dp, cutoutDiameter = 12.dp,
        )

        fun forVariant(variant: LampVariant): LampGeometry = when (variant) {
            LampVariant.NIGHT, LampVariant.DAY -> Phone
            LampVariant.WATCH, LampVariant.WATCH_AMBIENT -> Watch
        }
    }
}

/** Defaults per variant and event (DESIGN_SPEC §2.3, §5.5; V1_ADDITIONS §2). */
object LampDefaults {
    /** Phone idle FEED glow. */
    const val GLOW_PHONE_FEED = 0.55f

    /** Phone lights-down SLEEP glow. */
    const val GLOW_PHONE_SLEEP = 0.45f

    /** Watch running glow (sleep or breastfeed). */
    const val GLOW_WATCH = 0.60f

    /** Tail opacities from the farthest segment to the nearest (8 × 6°). */
    val TAIL_OPACITIES = floatArrayOf(0.06f, 0.12f, 0.20f, 0.30f, 0.42f, 0.56f, 0.72f, 0.88f)

    const val TAIL_SEGMENT_DEGREES = 6f

    /** The ember moves 6° per elapsed minute (one lap per hour). */
    const val STEP_DEGREES = 6f

    /** One ember step per minute: 600 ms, cubic-bezier(0.2, 0, 0, 1). */
    const val STEP_MILLIS = 600
    val StepEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun eventColors(variant: LampVariant, event: LampEvent): EventColors {
        val palette = if (variant == LampVariant.DAY) XTokens.DayEvents else XTokens.NightEvents
        return if (event == LampEvent.SLEEP) palette.sleep else palette.feed
    }

    fun glowAlpha(variant: LampVariant, event: LampEvent): Float = when (variant) {
        LampVariant.NIGHT -> if (event == LampEvent.SLEEP) GLOW_PHONE_SLEEP else GLOW_PHONE_FEED
        LampVariant.WATCH -> GLOW_WATCH
        LampVariant.DAY, LampVariant.WATCH_AMBIENT -> 0f
    }

    fun cutoutColor(variant: LampVariant): Color = when (variant) {
        LampVariant.DAY -> XTokens.Day.lampCut
        LampVariant.NIGHT -> XTokens.Night.lampCut
        LampVariant.WATCH, LampVariant.WATCH_AMBIENT -> XTokens.Watch.lampCut
    }

    /** The default diameter for [variant] (its geometry's reference size). */
    fun size(variant: LampVariant): Dp = LampGeometry.forVariant(variant).referenceSize
}

/**
 * The Lamp's graphics (DESIGN_SPEC §2): the glow (Night, watch) or disc (Day) in [event]'s container colour,
 * and — while [running] — the orbit: track, ember at (elapsed minutes mod 60) × 6° clockwise from 12 o'clock, and
 * the stepped 8-segment tail (truncated to the elapsed minutes under 8; none under a minute).
 *
 * It draws graphics only: lay the label, hero and sub-line out on top as real text. The canvas is cleared from
 * semantics. The ember moves only for the one 6° step per minute, with a 600 ms ease (§2.2, §5.10); every other
 * change (another event, a sleep starting or synced in, Adjust start, a breastfeed ending) puts it at its new
 * angle at once. It also jumps when [reducedMotion] or ambient. The accumulated angle is used, so 354° → 360°
 * never spins backwards.
 *
 * @param size the Lamp diameter (phone Ø264 reference, clamp 220–264; watch outer Ø126). Unspecified uses
 *   [geometry]'s reference size, so `geometry = LampGeometry.WatchSmall` alone draws the 93 dp small-watch Lamp.
 * @param identity what the orbit belongs to, e.g. the running entry's id. A new identity snaps the ember.
 */
@Composable
fun Lamp(
    event: LampEvent,
    running: Boolean,
    elapsedMinutes: Long,
    variant: LampVariant,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    colors: EventColors = LampDefaults.eventColors(variant, event),
    glowAlpha: Float = LampDefaults.glowAlpha(variant, event),
    cutoutColor: Color = LampDefaults.cutoutColor(variant),
    ambientColor: Color = colors.strong,
    geometry: LampGeometry = LampGeometry.forVariant(variant),
    reducedMotion: Boolean = false,
    identity: Any? = null,
) {
    val minutes = elapsedMinutes.coerceAtLeast(0)
    val jump = reducedMotion || variant == LampVariant.WATCH_AMBIENT
    val angle = rememberEmberAngle(minutes, event, running, identity, jump)
    Canvas(modifier.size(if (size.isSpecified) size else geometry.referenceSize).clearAndSetSemantics { }) {
        val scale = this.size.minDimension / geometry.referenceSize.toPx()
        drawLamp(
            running = running,
            angleDegrees = angle.value,
            tailSegments = if (jump) 0 else min(minutes, LampDefaults.TAIL_OPACITIES.size.toLong()).toInt(),
            variant = variant,
            colors = colors,
            glowAlpha = glowAlpha,
            cutoutColor = cutoutColor,
            ambientColor = ambientColor,
            geometry = geometry,
            scale = scale,
        )
    }
}

/** The Lamp for a [LampState]: FEED glow/disc when idle, SLEEP or FEED orbit while one runs. */
@Composable
fun Lamp(
    state: LampState,
    variant: LampVariant,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    geometry: LampGeometry = LampGeometry.forVariant(variant),
    reducedMotion: Boolean = false,
) {
    val event = if (state is LampState.SleepRunning) LampEvent.SLEEP else LampEvent.FEED
    Lamp(
        event = event,
        running = state !is LampState.FeedIdle,
        elapsedMinutes = if (state is LampState.FeedIdle) 0 else state.elapsedMinutes,
        variant = variant,
        modifier = modifier,
        size = size,
        geometry = geometry,
        reducedMotion = reducedMotion,
        identity = when (state) {
            is LampState.SleepRunning -> state.sleep.id
            is LampState.FeedRunning -> state.feed.id
            is LampState.FeedIdle -> null
        },
    )
}

/**
 * The ember angle in degrees (accumulated: elapsed minutes × 6). It animates only a single forward minute step
 * of the same orbit (same [event], [identity], still [running], motion allowed); any other change snaps.
 */
@Composable
internal fun rememberEmberAngle(
    elapsedMinutes: Long,
    event: LampEvent,
    running: Boolean,
    identity: Any?,
    jump: Boolean,
): State<Float> {
    val target = elapsedMinutes.coerceAtLeast(0) * LampDefaults.STEP_DEGREES
    // A new orbit (another event or entry, or the orbit appearing) starts at its own angle: no sweep.
    val angle = remember(event, running, identity) { Animatable(target) }
    LaunchedEffect(angle, target, jump) {
        val oneStep = running && !jump && target - angle.targetValue == LampDefaults.STEP_DEGREES
        if (oneStep) {
            angle.animateTo(target, tween(LampDefaults.STEP_MILLIS, easing = LampDefaults.StepEasing))
        } else {
            angle.snapTo(target)
        }
    }
    return angle.asState()
}

private fun DrawScope.drawLamp(
    running: Boolean,
    angleDegrees: Float,
    tailSegments: Int,
    variant: LampVariant,
    colors: EventColors,
    glowAlpha: Float,
    cutoutColor: Color,
    ambientColor: Color,
    geometry: LampGeometry,
    scale: Float,
) {
    val centre = center
    val ringRadius = geometry.ringRadius.toPx() * scale
    val trackWidth = geometry.trackWidth.toPx() * scale

    when (variant) {
        LampVariant.NIGHT, LampVariant.WATCH -> if (glowAlpha > 0f) drawGlow(colors.container, glowAlpha, geometry.glowRadius.toPx() * scale)
        LampVariant.DAY -> drawCircle(colors.container, radius = geometry.discRadius.toPx() * scale, center = centre)
        LampVariant.WATCH_AMBIENT -> Unit
    }
    if (!running) return

    val emberCentre = pointOnCircle(centre, ringRadius, angleDegrees)
    if (variant == LampVariant.WATCH_AMBIENT) {
        drawCircle(ambientColor, radius = ringRadius, center = centre, style = Stroke(geometry.ambientRingWidth.toPx() * scale))
        drawCircle(ambientColor, radius = geometry.ambientDotDiameter.toPx() * scale / 2, center = emberCentre)
        return
    }

    drawCircle(colors.track, radius = ringRadius, center = centre, style = Stroke(trackWidth))

    val arcTopLeft = Offset(centre.x - ringRadius, centre.y - ringRadius)
    val arcSize = Size(ringRadius * 2, ringRadius * 2)
    val opacities = LampDefaults.TAIL_OPACITIES
    for (k in 1..tailSegments) {
        val opacity = opacities[opacities.size - k]
        // Segment k spans [angle − 6k, angle − 6(k − 1)] clockwise from 12 o'clock; Compose angles start at 3 o'clock.
        drawArc(
            color = colors.ember.copy(alpha = opacity),
            startAngle = angleDegrees - LampDefaults.TAIL_SEGMENT_DEGREES * k - 90f,
            sweepAngle = LampDefaults.TAIL_SEGMENT_DEGREES,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = trackWidth, cap = StrokeCap.Butt),
        )
    }

    if (variant == LampVariant.DAY) {
        drawCircle(cutoutColor, radius = geometry.cutoutDiameter.toPx() * scale / 2, center = emberCentre)
    }
    drawCircle(colors.ember, radius = geometry.emberDiameter.toPx() * scale / 2, center = emberCentre)
}

/** Static radial glow: stops 0 → a, 0.6 → 0.6·a, 1 → 0, dithered to avoid banding on dark screens. */
private fun DrawScope.drawGlow(color: Color, alpha: Float, radius: Float) {
    if (radius <= 0f) return
    val shader = RadialGradient(
        center.x,
        center.y,
        radius,
        intArrayOf(
            color.copy(alpha = alpha).toArgb(),
            color.copy(alpha = alpha * 0.6f).toArgb(),
            color.copy(alpha = 0f).toArgb(),
        ),
        floatArrayOf(0f, 0.6f, 1f),
        Shader.TileMode.CLAMP,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { this.shader = shader }
    drawIntoCanvas { it.nativeCanvas.drawCircle(center.x, center.y, radius, paint) }
}

private fun pointOnCircle(centre: Offset, radius: Float, degreesFromTop: Float): Offset {
    val radians = Math.toRadians((degreesFromTop - 90f).toDouble())
    return Offset(centre.x + radius * cos(radians).toFloat(), centre.y + radius * sin(radians).toFloat())
}
