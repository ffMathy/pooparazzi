package com.ffmathy.pooparazzi.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.foundation.sizeIn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import com.ffmathy.pooparazzi.core.format.StringResolver
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.type.WatchType
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.core.R as CoreR

/** Watch shapes (DESIGN_SPEC §5.8): list buttons, status cards and tiles r 26, inline choice chips r 20, CompactButton r 16. */
object WatchShapes {
    val Button = RoundedCornerShape(26.dp)
    val Chip = RoundedCornerShape(20.dp)
    val Compact = RoundedCornerShape(16.dp)
}

/** True below 225 dp (e.g. 192 dp round), where §3.5.10's small-watch rules apply. */
@Composable
@ReadOnlyComposable
fun isSmallWatch(): Boolean = LocalConfiguration.current.screenWidthDp < SMALL_WATCH_DP

/** True when the font scale needs stacked layouts (§3.5.10: above 1.15). */
@Composable
@ReadOnlyComposable
fun isLargeFont(): Boolean = LocalConfiguration.current.fontScale > 1.15f

const val SMALL_WATCH_DP = 225

/** Previews and screenshots pin the TimeText to the scenario's clock ("14:32"). Null shows the real time. */
val LocalFixedTimeText = staticCompositionLocalOf<String?> { null }

/**
 * The system TimeText (DESIGN_SPEC §3.5): 15 sp, weight 600, tabular, onSurface; ambient onSurfaceVariant.
 *
 * [masked] keeps Wear's backing pill, which hides whatever list content rests under the time (lists also come to
 * rest on an item and shrink and fade items at the edge). Main drops it while its hero is at rest at the top, where
 * nothing scrolls under the time and the pill would cut a dark notch into the Lamp's glow.
 *
 * [baby] (two or more babies, [rememberBabyTimeText]) leads the time on one curved line, "Noa · 14:32", so every
 * screen says which baby a tap logs for (BABIES_AND_BACKUP §2.5). A long label is cut with an ellipsis; the time
 * itself is never cut.
 */
@Composable
fun XTimeText(masked: Boolean = true, baby: BabyTimeText? = LocalBabyTimeText.current) {
    val fixed = LocalFixedTimeText.current
    val color = if (isAmbient()) WatchAmbient.content else MaterialTheme.colorScheme.onSurface
    val style = CurvedTextStyle(WatchType.timeText.copy(color = color))
    val timeSource = if (fixed != null) {
        remember(fixed) {
            object : TimeSource {
                @Composable
                override fun currentTime(): String = fixed
            }
        }
    } else {
        TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat())
    }
    TimeText(
        backgroundColor = if (masked) TimeTextDefaults.backgroundColor() else Color.Transparent,
        timeSource = timeSource,
        maxSweepAngle = baby?.lineMaxSweep ?: TimeTextDefaults.MaxSweepAngle,
    ) { time ->
        if (baby != null) {
            basicCurvedText(baby.label, style, CurvedModifier.sizeIn(maxSweepDegrees = baby.labelMaxSweep), overflow = TextOverflow.Ellipsis)
            timeTextSeparator(style)
        }
        timeTextCurvedText(time, style)
    }
}

/**
 * The baby's label in the time text (two or more babies) and what it takes: the most the label may sweep before it
 * is cut, the line's own limit, and [extraTop], how much lower the line's ends reach than the time alone does. Screen
 * titles and Main's hero move down by [extraTop], so the longer line never touches them (small watches, large fonts).
 */
@Immutable
class BabyTimeText(val label: String, val labelMaxSweep: Float, val lineMaxSweep: Float, val extraTop: Dp)

/** The time text's baby label for the screens below it; null with one baby (each screen then looks as before). */
val LocalBabyTimeText = compositionLocalOf<BabyTimeText?> { null }

/**
 * Measures [label] ([babyLabelOrNull]) against the widest time this watch shows, at the time text's radius, the
 * screen's size and the font scale. Null for a null label.
 */
@Composable
fun rememberBabyTimeText(label: String?): BabyTimeText? {
    if (label == null) return null
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widestTime = LocalFixedTimeText.current ?: android.text.format.DateFormat.format(TimeTextDefaults.timeFormat(), WIDEST_TIME).toString()
    return remember(label, widestTime, density, configuration) {
        fun widthDp(text: String) = with(density) { measurer.measure(text, WatchType.timeText).size.width.toDp().value }
        val heightDp = with(density) { measurer.measure(widestTime, WatchType.timeText).size.height.toDp().value }
        val radius = (minOf(configuration.screenWidthDp, configuration.screenHeightDp) / 2f - TIME_TEXT_EDGE_DP - heightDp / 2f)
            .coerceAtLeast(1f)
        fun sweep(widthDp: Float) = Math.toDegrees((widthDp / radius).toDouble()).toFloat()
        val timeOnly = sweep(widthDp(widestTime))
        val timeAndSeparator = sweep(widthDp(widestTime) + widthDp(TIME_TEXT_SEPARATOR) + SEPARATOR_PADDING_DP)
        // At least a couple of letters ("No…") beside even a very wide time (large fonts).
        val shortest = sweep(widthDp(label.take(2) + ELLIPSIS))
        val lineMax = maxOf(TimeTextDefaults.MaxSweepAngle, timeAndSeparator + shortest + TIME_TEXT_SWEEP_MARGIN)
        val labelMax = lineMax - timeAndSeparator - TIME_TEXT_SWEEP_MARGIN
        val line = timeAndSeparator + minOf(sweep(widthDp(label)), labelMax)
        fun drop(sweep: Float) = radius * (1 - kotlin.math.cos(Math.toRadians(sweep / 2.0))).toFloat()
        BabyTimeText(label, labelMax, lineMax, kotlin.math.ceil(drop(line) - drop(timeOnly)).coerceAtLeast(0f).dp)
    }
}

/** A time as wide as the time text gets: two-digit hours, tabular digits. */
private val WIDEST_TIME: java.util.Calendar = java.util.Calendar.getInstance().apply { set(2026, 0, 1, 10, 58) }

/** What `timeTextSeparator` draws, and the angular padding on both of its sides. */
private const val TIME_TEXT_SEPARATOR = "·"
private const val SEPARATOR_PADDING_DP = 8f
private const val ELLIPSIS = "…"

/** The time text's distance from the screen edge (its content padding). */
private const val TIME_TEXT_EDGE_DP = 4f

/** Room kept free for the backing pill's own padding, so the line never reaches the time text's limit. */
private const val TIME_TEXT_SWEEP_MARGIN = 8f

/**
 * The current baby's label ("Noa", or "Baby 2" for an unnamed one) when there are two or more [babies], for the time
 * text and the baby button; null with one baby, so a single baby's watch looks exactly as before (BABIES_AND_BACKUP
 * §2.2).
 */
@Composable
@ReadOnlyComposable
fun babyLabelOrNull(babies: List<Baby>, babyId: String): String? {
    if (babies.size < 2) return null
    val baby = babies.firstOrNull { it.id == babyId } ?: babies.first()
    return LabelFormatter(StringResolver.of(LocalResources.current)).baby(baby, babies)
}

/** Container colours of an event button, resolved for ambient (outline in the strong colour, no fill). */
@Composable
fun eventButtonColors(event: EventColors, container: Color = event.container) =
    if (isAmbient()) {
        ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = WatchAmbient.content,
            disabledContainerColor = Color.Transparent,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = event.onContainer,
            secondaryContentColor = event.onContainer,
            iconColor = event.strong,
            disabledContainerColor = container.copy(alpha = 0.38f),
        )
    }

/** The neutral tonal fill of list buttons and status cards (`surfaceContainer`, §5.4). */
@Composable
fun tonalButtonColors() = if (isAmbient()) {
    ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = WatchAmbient.content)
} else {
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurface,
    )
}

/** Ambient outline for a button whose fill was dropped (2 dp, §10.6). */
@Composable
fun ambientBorder(color: Color): BorderStroke? = if (isAmbient()) BorderStroke(2.dp, color) else null

/**
 * A full-width list button (≥ 52 dp, r 26): icon at the start, label, optional secondary label. Labels wrap (two
 * lines, hyphenated) so long Danish words never clip.
 */
@Composable
fun XListButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    icon: (@Composable () -> Unit)? = null,
    colors: ButtonColors = tonalButtonColors(),
    border: BorderStroke? = null,
    enabled: Boolean = true,
    transformation: SurfaceTransformation? = null,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
    /** What TalkBack reads instead of the labels, where they show symbols ("Left → Right"). */
    contentDescription: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        enabled = enabled,
        shape = WatchShapes.Button,
        colors = colors,
        border = border,
        transformation = transformation,
        icon = icon?.let { { it() } },
        secondaryLabel = secondaryLabel?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        label = {
            Text(
                label,
                style = labelStyle.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (contentDescription != null) Modifier.clearAndSetSemantics { } else Modifier,
            )
        },
    )
}

/** One centred line that steps its size down (to [minSize] sp) instead of clipping. */
@Composable
fun AutoSizeLine(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minSize: Int = 10,
    maxLines: Int = 1,
) {
    val maxSize = style.fontSize.value
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(textAlign = TextAlign.Center),
        color = ColorProducer { color },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize.sp, maxFontSize = maxSize.sp, stepSize = 0.5.sp),
    )
}

/**
 * A CompactButton (§5.8, §5.9): 32 dp visual inside a 48 dp target, r 16, `surfaceContainer`; icon 20 + 4 + label
 * in labelSmall 13 sp weight 600.
 */
@Composable
fun XCompactButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    transformation: SurfaceTransformation? = null,
) {
    val ambient = isAmbient()
    val content = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface
    CompactButton(
        onClick = onClick,
        modifier = modifier.then(
            if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier,
        ),
        enabled = enabled,
        shape = WatchShapes.Compact,
        colors = tonalButtonColors(),
        border = ambientBorder(MaterialTheme.colorScheme.outline),
        transformation = transformation,
        icon = { XIcon(icon, contentDescription = null, tint = content, size = 20.dp) },
    ) {
        Text(
            label,
            style = WatchType.labelSmall.copy(fontWeight = FontWeight(600)),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (contentDescription != null) Modifier.clearAndSetSemantics { } else Modifier,
        )
    }
}

/**
 * The sync status (DESIGN_SPEC §3.5.6, §4.5): a 20 dp phone with its state on the screen (tick, "…", slash) in
 * onSurfaceVariant, no visible text; TalkBack reads [text] ("Synced with phone · 14:31"). Null [text] hides it (no
 * peer). The watch shows no error state: [SyncStatus.Error] reads, and looks, like saved on the watch.
 */
@Composable
fun SyncIcon(status: SyncStatus, text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    val color = if (isAmbient()) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant
    XIcon(phoneGlyph(status), contentDescription = text, tint = color, modifier = modifier, size = 20.dp)
}

/** The phone with a tick, "…" or a slash (DESIGN_SPEC §4.5). */
fun phoneGlyph(status: SyncStatus): ImageVector = when (status) {
    is SyncStatus.Synced -> XIcons.phoneSynced
    SyncStatus.Pending -> XIcons.phonePending
    SyncStatus.Offline, SyncStatus.Error, SyncStatus.Unavailable -> XIcons.phoneOffline
}

/**
 * A small round swatch with the 1 dp outline ring (V1_ADDITIONS §3.1), for poop colour options. In ambient it is the
 * ring alone (1.5 dp, no fill, §10.6).
 */
@Composable
fun Swatch(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val outline = MaterialTheme.colorScheme.outline
    val ambient = isAmbient()
    Canvas(modifier.size(size)) {
        if (ambient) {
            val stroke = 1.5.dp.toPx()
            drawCircle(WatchAmbient.content, radius = (this.size.minDimension - stroke) / 2, style = Stroke(stroke))
        } else {
            drawCircle(color)
            drawCircle(outline, style = Stroke(1.dp.toPx()))
        }
    }
}

/** A header line for list screens (ListHeader style: titleMedium, centred), a TalkBack heading. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    // Clear of a time text that names the baby (its line reaches lower at its ends).
    val extraTop = LocalBabyTimeText.current?.extraTop ?: 0.dp
    Box(modifier.fillMaxWidth().padding(top = extraTop).heightIn(min = 24.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isAmbient()) WatchAmbient.content else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** The event colours the watch uses (Night palette). */
@Composable
@ReadOnlyComposable
fun events() = LocalXPalette.current.events

/** The pinned Save's size (§3.5.10, as Main's sleep toggle): Medium (70 dp), Small (56 dp) below 225 dp. */
@Composable
@ReadOnlyComposable
fun saveButtonSize(): EdgeButtonSize = if (isSmallWatch()) EdgeButtonSize.Small else EdgeButtonSize.Medium

/**
 * The top padding of a picker list (Side, Bottle, Diaper) under the scaffold's [scaffoldTop]: 8 dp tighter on watches of
 * 225 dp and up at normal font sizes, so more shows above the pinned Save at rest; the full padding on small watches;
 * and more with large fonts, where the time text grows but the scaffold's padding does not.
 */
@Composable
@ReadOnlyComposable
fun pickerListTopPadding(scaffoldTop: Dp): Dp {
    val fontScale = LocalConfiguration.current.fontScale
    val tighter = if (isSmallWatch() || isLargeFont()) 0.dp else 8.dp
    val extra = TIME_TEXT_LINE * (fontScale - 1f).coerceAtLeast(0f)
    return (scaffoldTop - tighter + extra).coerceAtLeast(0.dp)
}

/** The time text's line height at font scale 1, by which a larger font pushes it down. */
private val TIME_TEXT_LINE = 18.dp

/** The list's bottom padding above a pinned Save of [size], so the last item scrolls clear of it. */
fun saveListBottomPadding(size: EdgeButtonSize): Dp = if (size == EdgeButtonSize.Small) 70.dp else 84.dp

/**
 * Save (V1_ADDITIONS §2.2, §3.2): the pinned EdgeButton in `primaryContainer`. Medium: `check` 24 over "Save";
 * Small (below 225 dp): `check` 20, 6 dp and "Save" on one line that steps down rather than cutting (as Done); above a
 * 1.15 font scale the label alone, with the check's room.
 */
@Composable
fun SaveEdgeButton(
    enabled: Boolean,
    onClick: () -> Unit,
    ambient: Boolean,
    modifier: Modifier = Modifier,
    size: EdgeButtonSize = EdgeButtonSize.Medium,
) {
    val scheme = MaterialTheme.colorScheme
    val content = when {
        ambient -> WatchAmbient.content
        enabled -> scheme.onPrimaryContainer
        else -> scheme.onPrimaryContainer.copy(alpha = 0.38f)
    }
    EdgeButton(
        onClick = onClick,
        modifier = modifier,
        buttonSize = size,
        enabled = enabled,
        colors = if (ambient) {
            // Black, the screen's own colour, not a fill: the list behind it must not show through the outline.
            ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = WatchAmbient.content, disabledContainerColor = Color.Black)
        } else {
            ButtonDefaults.buttonColors(
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                disabledContainerColor = scheme.primaryContainer.copy(alpha = 0.38f),
                disabledContentColor = scheme.onPrimaryContainer.copy(alpha = 0.38f),
            )
        },
        border = if (ambient) BorderStroke(2.dp, scheme.outline) else null,
    ) {
        val label = stringResource(CoreR.string.action_save)
        if (size == EdgeButtonSize.Small) {
            // At large fonts the label takes the check's room too, so "Save" is never cut.
            val withIcon = !isLargeFont()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (withIcon) {
                    XIcon(XIcons.check, null, tint = content, size = 20.dp, modifier = Modifier.clearAndSetSemantics { })
                    Spacer(Modifier.width(6.dp))
                }
                AutoSizeLine(label, MaterialTheme.typography.labelMedium, content, minSize = 11, modifier = Modifier.widthIn(max = if (withIcon) 64.dp else 92.dp))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                XIcon(XIcons.check, null, tint = content, size = 24.dp, modifier = Modifier.clearAndSetSemantics { })
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
