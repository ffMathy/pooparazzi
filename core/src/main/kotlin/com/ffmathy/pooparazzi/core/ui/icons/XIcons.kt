package com.ffmathy.pooparazzi.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.FeedType

/**
 * The icon set (DESIGN_SPEC §6) as Compose vectors: 24 × 24 viewport, stroke 2 in viewport units, round caps and
 * joins, no fill unless noted. Every vector is drawn in black so a tint (e.g. `XIcon(tint = …)` or Material
 * `Icon(tint = …)`) recolours it. [both] is two-tone: use [bothTwoTone] with `tint = Color.Unspecified` for the
 * coloured form.
 *
 * The geometry comes from [XIconSpecs], which also generates the vector drawables in [XDrawables].
 */
object XIcons {
    val sleep: ImageVector by lazy { XIconSpecs.sleep.toImageVector() }
    val wake: ImageVector by lazy { XIconSpecs.wake.toImageVector() }
    val feed: ImageVector by lazy { XIconSpecs.feed.toImageVector() }
    val diaper: ImageVector by lazy { XIconSpecs.diaper.toImageVector() }
    val pee: ImageVector by lazy { XIconSpecs.pee.toImageVector() }
    val poop: ImageVector by lazy { XIconSpecs.poop.toImageVector() }

    /** Single-colour form of `both` (follows the tint). For the two-tone form use [bothTwoTone]. */
    val both: ImageVector by lazy { XIconSpecs.both.toImageVector() }
    val history: ImageVector by lazy { XIconSpecs.history.toImageVector() }
    val edit: ImageVector by lazy { XIconSpecs.edit.toImageVector() }
    val undo: ImageVector by lazy { XIconSpecs.undo.toImageVector() }
    val delete: ImageVector by lazy { XIconSpecs.delete.toImageVector() }
    val profile: ImageVector by lazy { XIconSpecs.profile.toImageVector() }
    val settings: ImageVector by lazy { XIconSpecs.settings.toImageVector() }
    val phone: ImageVector by lazy { XIconSpecs.phone.toImageVector() }
    val synced: ImageVector by lazy { XIconSpecs.synced.toImageVector() }
    val pending: ImageVector by lazy { XIconSpecs.pending.toImageVector() }
    val offline: ImageVector by lazy { XIconSpecs.offline.toImageVector() }
    val check: ImageVector by lazy { XIconSpecs.check.toImageVector() }
    val plus: ImageVector by lazy { XIconSpecs.plus.toImageVector() }
    val back: ImageVector by lazy { XIconSpecs.back.toImageVector() }
    val chevronLeft: ImageVector by lazy { XIconSpecs.chevronLeft.toImageVector() }
    val chevronRight: ImageVector by lazy { XIconSpecs.chevronRight.toImageVector() }
    val chevronDown: ImageVector by lazy { XIconSpecs.chevronDown.toImageVector() }
    val close: ImageVector by lazy { XIconSpecs.close.toImageVector() }
    val clock: ImageVector by lazy { XIconSpecs.clock.toImageVector() }
    val awake: ImageVector by lazy { XIconSpecs.awake.toImageVector() }

    /** Ring (stroked) + filled crescent: "a sleep is running" on glance surfaces. */
    val sleepRunning: ImageVector by lazy { XIconSpecs.sleepRunning.toImageVector() }

    // ---- v1 additions (V1_ADDITIONS §7) -------------------------------------------------------

    /** Breastfeeding: a parent's head and shoulder cradling a baby's head at the chest. */
    val breast: ImageVector by lazy { XIconSpecs.breast.toImageVector() }

    /** Food (solids): a bowl with a spoon. */
    val food: ImageVector by lazy { XIconSpecs.food.toImageVector() }

    /** Two breasts as circles; the filled one is the side. Letters come from text, never the glyph. */
    val sideLeft: ImageVector by lazy { XIconSpecs.sideLeft.toImageVector() }
    val sideRight: ImageVector by lazy { XIconSpecs.sideRight.toImageVector() }

    /** Left first (filled), then right, with an arrow arcing from left to right. */
    val sideLeftRight: ImageVector by lazy { XIconSpecs.sideLeftRight.toImageVector() }

    /** Right first (filled), then left, with an arrow arcing from right to left. */
    val sideRightLeft: ImageVector by lazy { XIconSpecs.sideRightLeft.toImageVector() }

    /** Empty checkbox (rounded square). */
    val checkbox: ImageVector by lazy { XIconSpecs.checkbox.toImageVector() }

    /** Checked checkbox: a filled rounded square with the tick knocked out (even-odd), same outer bounds as [checkbox]. */
    val checkboxChecked: ImageVector by lazy { XIconSpecs.checkboxChecked.toImageVector() }

    /** Info: a circled "i" (health hint). */
    val info: ImageVector by lazy { XIconSpecs.info.toImageVector() }

    /** Chart: three uneven bars on a baseline (Statistics). */
    val chart: ImageVector by lazy { XIconSpecs.chart.toImageVector() }

    // ---- Sync status (DESIGN_SPEC §4.5) --------------------------------------------------------

    /** The watch with its sync state on the face: tick, "…", slash, "!" (the phone's top bars). */
    val watchSynced: ImageVector by lazy { XIconSpecs.watchSynced.toImageVector() }
    val watchPending: ImageVector by lazy { XIconSpecs.watchPending.toImageVector() }
    val watchOffline: ImageVector by lazy { XIconSpecs.watchOffline.toImageVector() }
    val watchError: ImageVector by lazy { XIconSpecs.watchError.toImageVector() }

    /** The phone with its sync state on the screen: tick, "…", slash (the watch's footer). */
    val phoneSynced: ImageVector by lazy { XIconSpecs.phoneSynced.toImageVector() }
    val phonePending: ImageVector by lazy { XIconSpecs.phonePending.toImageVector() }
    val phoneOffline: ImageVector by lazy { XIconSpecs.phoneOffline.toImageVector() }

    /** The backup failed: a cloud with "!" (with [synced], [pending] and [offline] the backup's states). */
    val cloudError: ImageVector by lazy { XIconSpecs.cloudError.toImageVector() }

    // ---- Lookups ------------------------------------------------------------------------------

    /** Type glyph; an untyped feed (null) uses the generic `feed` bottle. */
    fun forFeedType(type: FeedType?): ImageVector = when (type) {
        null -> feed
        FeedType.BREAST -> breast
        FeedType.FORMULA -> feed
        FeedType.FOOD -> food
    }

    /** Kind glyph: `pee`, `poop`, (single-colour) `both`, or `diaper` for a plain change. */
    fun forDiaperKind(kind: DiaperKind): ImageVector = when (kind) {
        DiaperKind.PEE -> pee
        DiaperKind.POOP -> poop
        DiaperKind.BOTH -> both
        DiaperKind.PLAIN -> diaper
    }

    fun forSide(side: BreastSide): ImageVector = when (side) {
        BreastSide.LEFT -> sideLeft
        BreastSide.RIGHT -> sideRight
        BreastSide.LEFT_THEN_RIGHT -> sideLeftRight
        BreastSide.RIGHT_THEN_LEFT -> sideRightLeft
    }

    /** Two-tone `both`: droplet in [pee] (PEE strong), mound in [poop] (POOP strong). Draw with no tint. */
    fun bothTwoTone(pee: Color, poop: Color): ImageVector =
        XIconSpecs.both.toImageVector(name = "bothTwoTone", colors = listOf(pee, poop))

    /** Every icon by its spec name (previews, render checks). */
    val all: Map<String, ImageVector>
        get() = linkedMapOf(
            "sleep" to sleep, "wake" to wake, "feed" to feed, "diaper" to diaper, "pee" to pee, "poop" to poop,
            "both" to both, "history" to history, "edit" to edit, "undo" to undo, "delete" to delete, "profile" to profile,
            "settings" to settings, "phone" to phone, "synced" to synced, "pending" to pending, "offline" to offline,
            "check" to check, "plus" to plus, "back" to back, "chevronLeft" to chevronLeft, "chevronRight" to chevronRight,
            "chevronDown" to chevronDown, "close" to close, "clock" to clock, "awake" to awake,
            "sleepRunning" to sleepRunning, "breast" to breast, "food" to food, "sideLeft" to sideLeft,
            "sideRight" to sideRight, "sideLeftRight" to sideLeftRight, "sideRightLeft" to sideRightLeft,
            "checkbox" to checkbox, "checkboxChecked" to checkboxChecked, "info" to info, "chart" to chart,
            "watchSynced" to watchSynced, "watchPending" to watchPending, "watchOffline" to watchOffline,
            "watchError" to watchError, "phoneSynced" to phoneSynced, "phonePending" to phonePending,
            "phoneOffline" to phoneOffline, "cloudError" to cloudError,
        )

    private const val STROKE = 2f

    /** Builds the vector; [colors] gives each path its own colour (default black, so a tint recolours it). */
    internal fun XIconSpec.toImageVector(name: String = this.name, colors: List<Color> = emptyList()): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEachIndexed { index, path ->
            val color = SolidColor(colors.getOrElse(index) { Color.Black })
            builder.addPath(
                pathData = addPathNodes(path.pathData),
                pathFillType = if (path.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = if (path.fill) color else null,
                stroke = if (path.stroke) color else null,
                strokeLineWidth = if (path.stroke) STROKE else 0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
