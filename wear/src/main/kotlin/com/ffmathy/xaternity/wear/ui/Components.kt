package com.ffmathy.xaternity.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import com.ffmathy.xaternity.core.sync.SyncStatus
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.tokens.EventColors
import com.ffmathy.xaternity.core.ui.tokens.LocalXPalette
import com.ffmathy.xaternity.core.ui.type.WatchType
import com.ffmathy.xaternity.wear.theme.WatchAmbient
import com.ffmathy.xaternity.wear.theme.isAmbient

/** Watch shapes (DESIGN_SPEC §5.8): list buttons and status cards r 26, CompactButton r 16. */
object WatchShapes {
    val Button = RoundedCornerShape(26.dp)
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

/** The system TimeText (DESIGN_SPEC §3.5): 15 sp, weight 600, tabular, onSurface; ambient onSurfaceVariant. */
@Composable
fun XTimeText() {
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
    // Keeps the default backing pill: list content scrolls under the TimeText and must not collide with it.
    TimeText(timeSource = timeSource) { time -> timeTextCurvedText(time, style) }
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
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
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
            )
        },
    )
}

/**
 * A status card (DESIGN_SPEC §3.5.9): line 1 icon 18 + lead word (no digits), line 2 "{duration} · {clock}"
 * autosized down to 11 sp. TalkBack reads [spoken] (durations spoken in words, §9.5).
 */
@Composable
fun StatusCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    detail: String,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    val ambient = isAmbient()
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = spoken },
        shape = WatchShapes.Button,
        colors = tonalButtonColors(),
        border = ambientBorder(colors.outline),
        transformation = transformation,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XIcon(icon, contentDescription = null, tint = if (ambient) WatchAmbient.content else iconTint, size = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ambient) WatchAmbient.content else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AutoSizeLine(
                text = detail,
                style = WatchType.labelSmall,
                color = if (ambient) WatchAmbient.content else colors.onSurfaceVariant,
                minSize = 11,
            )
        }
    }
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
            modifier = if (contentDescription != null) Modifier.clearAndSetSemantics { } else Modifier,
        )
    }
}

/**
 * The sync line (DESIGN_SPEC §3.5.6, §4.5): `synced` / `offline` / `pending` 14 + 4 + bodySmall onSurfaceVariant.
 * Null [text] hides it (no peer). Long Danish lines step down in size rather than wrap.
 */
@Composable
fun SyncLine(status: SyncStatus, text: String?, modifier: Modifier = Modifier, maxWidth: Dp = 186.dp) {
    if (text == null) return
    val color = if (isAmbient()) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = when (status) {
        is SyncStatus.Synced -> XIcons.synced
        SyncStatus.Pending -> XIcons.pending
        else -> XIcons.offline
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        XIcon(icon, contentDescription = null, tint = color, size = 14.dp)
        Spacer(Modifier.width(4.dp))
        AutoSizeLine(
            text = text,
            style = WatchType.bodySmall,
            color = color,
            minSize = 9,
            modifier = Modifier.widthIn(max = maxWidth),
        )
    }
}

/** A small round swatch with the 1 dp outline ring (V1_ADDITIONS §3.1), for poop colour options. */
@Composable
fun Swatch(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(size)) {
        drawCircle(color)
        drawCircle(outline, style = Stroke(1.dp.toPx()))
    }
}

/** A header line for list screens (ListHeader style: titleMedium, centred). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 24.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isAmbient()) WatchAmbient.content else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** The event colours the watch uses (Night palette). */
@Composable
@ReadOnlyComposable
fun events() = LocalXPalette.current.events
