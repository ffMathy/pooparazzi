package com.ffmathy.xaternity.core.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.diaperKind
import com.ffmathy.xaternity.core.ui.tokens.EventPalette
import com.ffmathy.xaternity.core.ui.tokens.LocalXPalette

/**
 * Shape-coded list avatars (DESIGN_SPEC §6.3): a 48 × 48 viewport drawn at 40 dp (phone) or 32 dp (watch).
 * The shape carries the event for colour-blind users: Sleep a circle, Feed a soft 9-lobe scallop, diapers a
 * rounded square. Filled with the event container, outlined (stroke 2 in viewport units) in the event strong
 * colour (every diaper in DIAPER strong), the event icon centred at 55 %.
 */
object XAvatars {
    const val VIEWPORT = 48f
    const val STROKE = 2f

    /** The icon is 55 % of the avatar (22 dp in 40, 18 dp in 32). */
    const val ICON_FRACTION = 0.55f

    /** Phone History rows. */
    val PhoneSize: Dp = 40.dp

    /** Watch Today rows. */
    val WatchSize: Dp = 32.dp

    /** Feed: the soft 9-lobe scallop, verbatim from §6.3. The lobes and stroke reach the viewport edge: do not clip. */
    const val FEED_SCALLOP =
        "M31.01 4.74A8.6 8.6 0 0 1 41.75 13.75A8.6 8.6 0 0 1 44.19 27.56A8.6 8.6 0 0 1 37.18 39.70A8.6 8.6 0 0 1 24.00 44.50" +
            "A8.6 8.6 0 0 1 10.82 39.70A8.6 8.6 0 0 1 3.81 27.56A8.6 8.6 0 0 1 6.25 13.75A8.6 8.6 0 0 1 16.99 4.74" +
            "A8.6 8.6 0 0 1 31.01 4.74Z"

    /** Sleep: circle, centre 24,24, r 22. */
    const val SLEEP_CIRCLE = "M24 2a22 22 0 1 0 0 44a22 22 0 1 0 0-44z"

    /** Pee / Poop / Both: rounded square x 2, y 2, 44 × 44, corner radius 14. */
    const val DIAPER_SQUARE = "M16 2h16a14 14 0 0 1 14 14v16a14 14 0 0 1-14 14H16a14 14 0 0 1-14-14V16a14 14 0 0 1 14-14z"

    /** Which shape an entry kind uses. */
    enum class Shape(val pathData: String) {
        CIRCLE(SLEEP_CIRCLE),
        SCALLOP(FEED_SCALLOP),
        ROUNDED_SQUARE(DIAPER_SQUARE),
    }

    fun shapeFor(kind: EntryKind): Shape = when (kind) {
        EntryKind.SLEEP -> Shape.CIRCLE
        EntryKind.FEED -> Shape.SCALLOP
        EntryKind.DIAPER -> Shape.ROUNDED_SQUARE
    }

    internal val scallop: Path by lazy { PathParser().parsePathString(FEED_SCALLOP).toPath() }
    internal val roundedSquare: Path by lazy {
        Path().apply { addRoundRect(RoundRect(2f, 2f, 46f, 46f, CornerRadius(14f))) }
    }
}

/**
 * The avatar of [entry] (DESIGN_SPEC §6.3) at [size]: [XAvatars.PhoneSize] in phone History, [XAvatars.WatchSize]
 * on the watch. Feeds show their type's icon (`breast`, `feed`, `food`); Both is split PEE | POOP container with
 * the two-tone icon. Decorative: the row's text carries the meaning.
 */
@Composable
fun EventAvatar(
    entry: Entry,
    modifier: Modifier = Modifier,
    size: Dp = XAvatars.PhoneSize,
    events: EventPalette = LocalXPalette.current.events,
) {
    val diaper = entry.diaperKind ?: DiaperKind.PLAIN
    val bothIcon = rememberBothIcon(events.pee.strong, events.poop.strong)
    val (icon, iconColor) = when (entry.kind) {
        EntryKind.SLEEP -> XIcons.sleep to events.sleep.strong
        EntryKind.FEED -> XIcons.forFeedType(entry.feedType) to events.feed.strong
        EntryKind.DIAPER -> when (diaper) {
            DiaperKind.PEE -> XIcons.pee to events.pee.strong
            DiaperKind.POOP -> XIcons.poop to events.poop.strong
            DiaperKind.BOTH -> bothIcon to Color.Unspecified
            DiaperKind.PLAIN -> XIcons.diaper to events.diaper.strong
        }
    }
    val colors = remember(entry.kind, diaper, events) { avatarColors(entry.kind, diaper, events) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            scale(this.size.minDimension / XAvatars.VIEWPORT, pivot = Offset.Zero) {
                drawAvatarShape(XAvatars.shapeFor(entry.kind), colors)
            }
        }
        XIcon(icon, contentDescription = null, tint = iconColor, size = size * XAvatars.ICON_FRACTION)
    }
}

private class AvatarColors(val fill: Color, val splitFill: Color?, val outline: Color)

private fun avatarColors(kind: EntryKind, diaper: DiaperKind, events: EventPalette): AvatarColors = when (kind) {
    EntryKind.SLEEP -> AvatarColors(events.sleep.container, null, events.sleep.strong)
    EntryKind.FEED -> AvatarColors(events.feed.container, null, events.feed.strong)
    EntryKind.DIAPER -> when (diaper) {
        DiaperKind.PEE -> AvatarColors(events.pee.container, null, events.diaper.strong)
        DiaperKind.POOP -> AvatarColors(events.poop.container, null, events.diaper.strong)
        DiaperKind.BOTH -> AvatarColors(events.bothStartContainer, events.bothEndContainer, events.diaper.strong)
        DiaperKind.PLAIN -> AvatarColors(events.diaper.container, null, events.diaper.strong)
    }
}

/** Draws in viewport units (48 × 48). */
private fun DrawScope.drawAvatarShape(shape: XAvatars.Shape, colors: AvatarColors) {
    val outline = Stroke(XAvatars.STROKE)
    when (shape) {
        XAvatars.Shape.CIRCLE -> {
            drawCircle(colors.fill, radius = 22f, center = Offset(24f, 24f))
            drawCircle(colors.outline, radius = 22f, center = Offset(24f, 24f), style = outline)
        }
        XAvatars.Shape.SCALLOP -> {
            drawPath(XAvatars.scallop, colors.fill)
            drawPath(XAvatars.scallop, colors.outline, style = outline)
        }
        XAvatars.Shape.ROUNDED_SQUARE -> {
            drawPath(XAvatars.roundedSquare, colors.fill)
            colors.splitFill?.let { right ->
                // Both: the right half (x ≥ 24) in POOP container, clipped to the rounded square.
                clipPath(XAvatars.roundedSquare) { drawRect(right, topLeft = Offset(24f, 0f), size = Size(24f, 48f)) }
            }
            drawPath(XAvatars.roundedSquare, colors.outline, style = outline)
        }
    }
}
