package com.ffmathy.xaternity.wear.logged

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.xaternity.core.format.DOT_SEPARATOR
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.format.SyncPeer
import com.ffmathy.xaternity.core.format.rememberFormatters
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.diaperKind
import com.ffmathy.xaternity.core.sync.SyncStatus
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.time.rememberReducedMotion
import com.ffmathy.xaternity.wear.R
import com.ffmathy.xaternity.wear.sample.WearSamples
import com.ffmathy.xaternity.wear.state.Receipt
import com.ffmathy.xaternity.wear.state.ReceiptKind
import com.ffmathy.xaternity.wear.theme.WatchAmbient
import com.ffmathy.xaternity.wear.theme.XWearTheme
import com.ffmathy.xaternity.wear.theme.isAmbient
import com.ffmathy.xaternity.wear.ui.AutoSizeLine
import com.ffmathy.xaternity.wear.ui.SpokenLabels
import com.ffmathy.xaternity.wear.ui.SyncLine
import com.ffmathy.xaternity.wear.ui.events
import com.ffmathy.xaternity.wear.ui.feedTypeLabel
import com.ffmathy.xaternity.wear.ui.keepSidesTogether
import com.ffmathy.xaternity.wear.ui.rememberCooldown
import com.ffmathy.xaternity.wear.ui.spokenText
import kotlinx.coroutines.delay
import com.ffmathy.xaternity.core.R as CoreR

/** The Logged window before it returns to Main (DESIGN_SPEC §5.12: 5 s, stretched by the accessibility timeout). */
const val LOGGED_WINDOW_MILLIS = 5_000

/** Undo and dismiss ignore taps this long after Logged appears (§5.9), unless opened from the Tile. */
const val LOGGED_INERT_MILLIS = 1_000

/**
 * Logged (DESIGN_SPEC §3.5.6; V1_ADDITIONS §2.2, §3.2): the check circle in the event container, the title with
 * the time in the event colour, an optional second line, the sync line and a Small Undo EdgeButton, under a 270°
 * neutral bezel arc that drains over the window. Any tap outside Undo dismisses; both are inert for the first
 * second. When the window ends, [onTimeout] runs. No TimeText.
 */
@Composable
fun LoggedScreen(
    receipt: Receipt,
    sync: SyncStatus,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = rememberFormatters(receipt.zone)
    val ambient = isAmbient()
    val window = rememberRecommendedTimeout(LOGGED_WINDOW_MILLIS)
    val cooldown = rememberCooldown(millis = LOGGED_INERT_MILLIS, fromAlpha = 0.4f, enabled = !receipt.fromTile, key = receipt.serial)
    val remaining = remember(receipt.serial) { Animatable(1f) }
    val reducedMotion = rememberReducedMotion()
    val timeout by rememberUpdatedState(onTimeout)
    val inspection = LocalInspectionMode.current
    val dismissLabel = stringResource(R.string.logged_dismiss)
    LaunchedEffect(receipt.serial, window) {
        if (inspection) return@LaunchedEffect
        if (reducedMotion) {
            // No drain transition: the arc steps once a second.
            val steps = (window / 1000).coerceAtLeast(1)
            repeat(steps) { step ->
                delay(1000)
                remaining.snapTo(1f - (step + 1f) / steps)
            }
        } else {
            remaining.animateTo(0f, tween(window, easing = LinearEasing))
        }
        timeout()
    }

    // A large font scale grows the title: tighten the top so the sync line stays clear of Undo. A second line
    // ("Left → Right · 18m") moves the block up a little for the same reason (§3.5.6: sync line at y 148–162).
    val compact = LocalConfiguration.current.fontScale > 1.15f
    val line2 = receiptLine2(receipt, f)
    val top = when {
        compact -> 0.08f
        line2 != null -> 0.12f
        else -> 0.149f
    }
    ScreenScaffold(timeText = {}, modifier = modifier) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val height = maxHeight
            // Dismiss target: the whole screen except Undo.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = cooldown.ready,
                        role = Role.Button,
                        onClick = onDismiss,
                    )
                    .semantics { contentDescription = dismissLabel },
            )
            if (!ambient) DrainArc(remaining.value, MaterialTheme.colorScheme.outline)

            // Status layer above the dismiss target: no pointer input, so taps fall through. Announced politely.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = height * top, start = 8.dp, end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ReceiptCircle(receipt, ambient, size = if (compact) 48.dp else 64.dp)
                Spacer(Modifier.height(if (compact || line2 != null) 4.dp else 8.dp))
                ReceiptTitle(receipt, f, ambient)
                line2?.let { line ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        line.shown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { contentDescription = line.spoken },
                    )
                }
                val syncText = if (sync == SyncStatus.Pending) null else f.labels.syncLine(sync, SyncPeer.PHONE, f.clock, f.zone)
                if (syncText != null) {
                    Spacer(Modifier.height(2.dp))
                    // The round screen is wider higher up, where a large font scale moves the line; lower down it
                    // keeps clear of the drain arc (Danish "Synkroniseret med telefon · 14:31" steps down a size).
                    SyncLine(sync, syncText, maxWidth = if (compact) 200.dp else 166.dp)
                }
            }
            UndoEdgeButton(
                onUndo = onUndo,
                enabled = cooldown.ready,
                ambient = ambient,
                modifier = Modifier.align(Alignment.BottomCenter).alpha(cooldown.alpha),
            )
        }
    }
}

/** 270° bezel arc, open at the bottom (gap centred on 6 o'clock), r = screen − 3, stroke 2, outline, round caps. */
@Composable
private fun DrainArc(fraction: Float, color: Color) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension / 2f - 3.dp.toPx()
        val topLeft = Offset(center.x - radius, center.y - radius)
        // Starts at the lower left (135°, clockwise from 3 o'clock) and runs over the top; the tip retreats as time passes.
        drawArc(
            color = color,
            startAngle = 135f,
            sweepAngle = 270f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Ø64 circle in the event container (Both: split PEE | POOP), `check` 32 in the event strong colour. */
@Composable
private fun ReceiptCircle(receipt: Receipt, ambient: Boolean, size: Dp) {
    val events = events()
    val kind = receipt.entry.diaperKind
    val (fill, split, iconColor) = when (receipt.kind) {
        ReceiptKind.FEED_LOGGED, ReceiptKind.FEED_ENDED, ReceiptKind.FEED_CANCELLED ->
            Triple(events.feed.container, null, events.feed.strong)
        ReceiptKind.WOKE_UP, ReceiptKind.SLEEP_CANCELLED -> Triple(events.sleep.container, null, events.sleep.strong)
        ReceiptKind.DIAPER_LOGGED -> when (kind) {
            DiaperKind.POOP -> Triple(events.poop.container, null, events.poop.strong)
            DiaperKind.BOTH -> Triple(events.bothStartContainer, events.bothEndContainer, events.pee.strong)
            else -> Triple(events.pee.container, null, events.pee.strong)
        }
    }
    val icon: ImageVector = when (receipt.kind) {
        ReceiptKind.SLEEP_CANCELLED -> XIcons.wake
        ReceiptKind.FEED_CANCELLED -> XIcons.breast
        else -> XIcons.check
    }
    val outline = if (ambient) WatchAmbient.content else null
    Box(Modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (outline != null) {
                drawCircle(outline, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(fill)
                if (split != null) {
                    drawArc(split, startAngle = -90f, sweepAngle = 180f, useCenter = true)
                }
            }
        }
        XIcon(icon, null, tint = if (ambient) WatchAmbient.content else iconColor, size = size / 2)
    }
}

/** "Formula at 14:32" with the time in the event colour; time-free titles for ended and cancelled intervals. */
@Composable
private fun ReceiptTitle(receipt: Receipt, f: Formatters, ambient: Boolean) {
    val events = events()
    val entry = receipt.entry
    val onSurface = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface
    val title: AnnotatedString = when (receipt.kind) {
        ReceiptKind.FEED_LOGGED ->
            withTime(feedTypeLabel(entry, compact = true), entry.startMillis, f, events.feed.strong, ambient)
        ReceiptKind.DIAPER_LOGGED ->
            withTime(stringResource(Labels.diaperKind(entry.diaperKind ?: DiaperKind.PLAIN)), entry.startMillis, f, events.diaper.text, ambient)
        ReceiptKind.WOKE_UP ->
            withTime(stringResource(CoreR.string.action_woke_up), entry.endMillis ?: entry.startMillis, f, events.sleep.text, ambient)
        ReceiptKind.FEED_ENDED -> AnnotatedString(stringResource(CoreR.string.receipt_feed_ended))
        ReceiptKind.FEED_CANCELLED -> AnnotatedString(stringResource(CoreR.string.receipt_feed_cancelled))
        ReceiptKind.SLEEP_CANCELLED -> AnnotatedString(stringResource(CoreR.string.receipt_sleep_cancelled))
    }
    // "Pee and poop at 14:32": never the "+" (DESIGN_SPEC §9.4).
    val spoken = if (receipt.kind == ReceiptKind.DIAPER_LOGGED) {
        stringResource(
            CoreR.string.receipt_at_time,
            stringResource(SpokenLabels.diaperKind(entry.diaperKind ?: DiaperKind.PLAIN)),
            f.clock.format(entry.startMillis, f.zone),
        )
    } else {
        title.text
    }
    BasicText(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        style = MaterialTheme.typography.titleLarge.copy(color = onSurface, textAlign = TextAlign.Center, hyphens = Hyphens.Auto),
        maxLines = 2,
        autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 20.sp),
    )
}

/** "%1$s at %2$s" with the time span coloured (§3.5.6). */
@Composable
private fun withTime(label: String, millis: Long, f: Formatters, timeColor: Color, ambient: Boolean): AnnotatedString {
    val time = f.clock.format(millis, f.zone)
    val text = stringResource(CoreR.string.receipt_at_time, label, time)
    val start = text.lastIndexOf(time)
    return buildAnnotatedString {
        if (start < 0 || ambient) {
            append(text)
        } else {
            append(text.substring(0, start))
            withStyle(SpanStyle(color = timeColor)) { append(time) }
            append(text.substring(start + time.length))
        }
    }
}

/** A line as shown ("Left → Right · 18m") and as read ("Left then right, 18 minutes"). */
private class Line(val shown: String, val spoken: String)

/** Line 2: "Slept 1h 07m", "Left → Right · 18m", "Already logged". */
@Composable
private fun receiptLine2(receipt: Receipt, f: Formatters): Line? {
    val entry = receipt.entry
    val end = entry.endMillis
    if (receipt.duplicate) return stringResource(R.string.logged_already_logged).let { Line(it, it) }
    return when (receipt.kind) {
        ReceiptKind.WOKE_UP, ReceiptKind.SLEEP_CANCELLED -> end?.let {
            Line(
                stringResource(CoreR.string.receipt_slept, f.duration.between(entry.startMillis, it)),
                stringResource(CoreR.string.receipt_slept, f.duration.spokenBetween(entry.startMillis, it)),
            )
        }
        ReceiptKind.FEED_ENDED, ReceiptKind.FEED_CANCELLED -> {
            val side = entry.breastSide
            val shown = listOfNotNull(
                side?.let { stringResource(Labels.side(it)).keepSidesTogether() },
                end?.let { f.duration.between(entry.startMillis, it) },
            )
            val spoken = listOfNotNull(
                side?.let { stringResource(SpokenLabels.side(it)) },
                end?.let { f.duration.spokenBetween(entry.startMillis, it) },
            )
            if (shown.isEmpty()) null else Line(shown.joinToString(DOT_SEPARATOR), spokenText().all(spoken))
        }
        ReceiptKind.FEED_LOGGED, ReceiptKind.DIAPER_LOGGED -> null
    }
}

/** Undo: Small EdgeButton (56), `surfaceContainer`, `undo` 20 + 6 + "Undo" (§3.5.6). Exposed as disabled while inert. */
@Composable
private fun UndoEdgeButton(onUndo: () -> Unit, enabled: Boolean, ambient: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val content = if (ambient) WatchAmbient.content else scheme.onSurface
    EdgeButton(
        onClick = onUndo,
        modifier = modifier,
        buttonSize = EdgeButtonSize.Small,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (ambient) Color.Transparent else scheme.surfaceContainer,
            contentColor = content,
            disabledContainerColor = if (ambient) Color.Transparent else scheme.surfaceContainer,
            disabledContentColor = content,
        ),
        border = if (ambient) BorderStroke(2.dp, scheme.outline) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            XIcon(XIcons.undo, null, tint = content, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            AutoSizeLine(
                stringResource(CoreR.string.action_undo),
                MaterialTheme.typography.labelMedium,
                content,
                minSize = 11,
                modifier = Modifier.widthIn(max = 64.dp),
            )
        }
    }
}

/** [base] stretched by the user's accessibility timeout ("Time to take action"), for content with controls. */
@Composable
fun rememberRecommendedTimeout(base: Int): Int {
    val context = LocalContext.current
    return remember(context, base) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        manager?.getRecommendedTimeoutMillis(
            base,
            AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
        ) ?: base
    }
}

/** Undone (DESIGN_SPEC §3.5.7): `undo` 32 onSurfaceVariant and "Undone"; 1.5 s, then [onDone]. No TimeText. */
@Composable
fun UndoneScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val done by rememberUpdatedState(onDone)
    val inspection = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (inspection) return@LaunchedEffect
        delay(UNDONE_MILLIS)
        done()
    }
    val ambient = isAmbient()
    ScreenScaffold(timeText = {}, modifier = modifier) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(top = maxHeight * 0.219f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                XIcon(XIcons.undo, null, tint = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant, size = 32.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(CoreR.string.receipt_undone),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** "Undone" shows for 1.5 s (DESIGN_SPEC §5.12). */
const val UNDONE_MILLIS = 1_500L

@WearPreviewDevices
@Composable
private fun LoggedFeedEndedPreview() = XWearTheme {
    LoggedScreen(WearSamples.receiptFeedEnded(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {})
}

@WearPreviewDevices
@Composable
private fun LoggedDiaperPreview() = XWearTheme {
    LoggedScreen(WearSamples.receiptDiaper(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {})
}

@WearPreviewDevices
@Composable
private fun UndonePreview() = XWearTheme { UndoneScreen(onDone = {}) }
