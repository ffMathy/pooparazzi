package com.ffmathy.xaternity.phone.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmathy.xaternity.core.domain.DayCounts
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.time.rememberReducedMotion
import com.ffmathy.xaternity.core.ui.tokens.LocalXPalette
import com.ffmathy.xaternity.core.ui.type.PhoneType
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.ui.components.ChipDefaults
import com.ffmathy.xaternity.phone.ui.components.OffsetChip
import com.ffmathy.xaternity.phone.ui.components.XChip
import java.time.ZoneId
import kotlinx.coroutines.delay
import com.ffmathy.xaternity.core.R as CoreR

/** Card geometry (DESIGN_SPEC §3.3.1, §5.8). */
internal object WhisperDefaults {
    val SlotMinHeight = 128.dp
    val CardHeight = 120.dp
    val CardShape = RoundedCornerShape(28.dp)
}

/**
 * The fixed whisper slot: the Today grid, or the undo card in its place. The card fades in over 150 ms with no slide
 * (DESIGN_SPEC §5.10) and is a polite live region.
 */
@Composable
internal fun WhisperSlot(
    card: UndoCardUi?,
    today: DayCounts,
    lightsDown: Boolean,
    zone: ZoneId,
    formatters: Formatters,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    // Keeps the last card on screen while it fades out.
    val lastCard = remember { mutableStateOf<UndoCardUi?>(null) }
    if (card != null) SideEffect { lastCard.value = card }
    val shown = card ?: lastCard.value
    Box(modifier.heightIn(min = WhisperDefaults.SlotMinHeight), contentAlignment = Alignment.Center) {
        if (card == null) TodayGrid(today, lightsDown, formatters, actions::onOpenHistory)
        AnimatedVisibility(visible = card != null, enter = EnterTransition.None, exit = fadeOut(tween(150))) {
            shown?.let { UndoCard(it, zone, formatters, actions) }
        }
    }
}

/** "Today" + 5 feeds · 4 pee · 2 poop · 7h 57m sleep; the whole block opens History. */
@Composable
private fun TodayGrid(today: DayCounts, lightsDown: Boolean, formatters: Formatters, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val numberColor = if (lightsDown) colors.onSurfaceVariant else colors.onSurface
    val todayLabel = stringResource(CoreR.string.nav_today)
    val sleep = formatters.duration.format(today.sleepMinutes * MINUTE_MILLIS)
    val captions = listOf(
        pluralStringResource(CoreR.plurals.count_feeds, today.feeds),
        stringResource(CoreR.string.count_pee),
        stringResource(CoreR.string.count_poop),
        stringResource(CoreR.string.count_sleep),
    )
    val numbers = listOf(today.feeds.toString(), today.pee.toString(), today.poop.toString(), sleep)
    val spoken = stringResource(
        R.string.spoken_today,
        todayLabel,
        numbers[0], captions[0], numbers[1], captions[1], numbers[2], captions[2],
        formatters.duration.spoken(today.sleepMinutes * MINUTE_MILLIS), captions[3],
    )
    val historyLabel = stringResource(CoreR.string.nav_history)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = WhisperDefaults.CardHeight)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = historyLabel, role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = historyLabel) { onOpen(); true }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(todayLabel, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            XIcon(XIcons.chevronRight, contentDescription = null, tint = colors.outline, size = 16.dp)
        }
        // Four columns; 2 × 2 at large font scales so "7h 57m" never clips.
        val columns = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            numbers.zip(captions).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (number, caption) ->
                        Column(Modifier.weight(1f)) {
                            Text(number, style = MaterialTheme.typography.titleMedium, color = numberColor, maxLines = 1, softWrap = false)
                            Text(caption, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The undo card: row 1 icon · title (time-free) · Undo/Done; row 2 one of the variants; the drain line shows the
 * remaining window (anchored at the start, hidden for "Undone").
 */
@Composable
private fun UndoCard(card: UndoCardUi, zone: ZoneId, formatters: Formatters, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    val appear = remember(card.key) { Animatable(0f) }
    LaunchedEffect(card.key) { appear.animateTo(1f, tween(150)) }
    val content = cardContent(card.variant, formatters)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = WhisperDefaults.CardHeight)
            .graphicsLayer { alpha = appear.value }
            .clip(WhisperDefaults.CardShape)
            .background(colors.surfaceContainerHighest)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(top = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XIcon(content.icon.icon, contentDescription = null, tint = content.icon.tint, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                // Long Danish titles ("Modermælks­erstatning registreret") wrap at the soft hyphen, then shrink.
                BasicText(
                    content.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.onSurface,
                        lineHeight = if (content.sub != null) 22.sp else 24.sp,
                        hyphens = Hyphens.Auto,
                    ),
                    maxLines = if (content.sub != null) 1 else 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 18.sp, stepSize = 0.5.sp),
                )
                content.sub?.let {
                    Text(it, style = PhoneType.dataSmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            when (card.action) {
                CardAction.UNDO -> CardTextButton(XIcons.undo, stringResource(CoreR.string.action_undo), actions::onUndo)
                CardAction.DONE -> CardTextButton(XIcons.check, stringResource(CoreR.string.action_done), actions::onCardDone)
                CardAction.NONE -> Unit
            }
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            CardRowContent(card.row, zone, formatters, actions)
        }
        Spacer(Modifier.height(6.dp))
        if (card.variant != CardVariant.Undone) {
            DrainLine(card)
        } else {
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** 2 dp, solid outline, 16 dp insets, anchored at the start; width = the remaining fraction of the window. */
@Composable
private fun DrainLine(card: UndoCardUi) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    val remaining = remember(card.key, card.windowKey) { Animatable(1f) }
    LaunchedEffect(card.key, card.windowKey, reducedMotion) {
        remaining.snapTo(1f)
        if (reducedMotion) {
            // No drain transition: step once a second.
            val steps = (card.timeoutMillis / 1_000L).coerceAtLeast(1L)
            for (step in 1..steps) {
                delay(1_000L)
                remaining.snapTo(1f - step.toFloat() / steps)
            }
        } else {
            remaining.animateTo(0f, tween(card.timeoutMillis.toInt(), easing = LinearEasing))
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(2.dp),
    ) {
        Box(
            Modifier
                .width(maxWidth * remaining.value)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.outline),
        )
    }
}

@Composable
private fun CardTextButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        XIcon(icon, contentDescription = null, tint = colors.primary, size = 18.dp)
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.primary, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardRowContent(row: CardRow, zone: ZoneId, formatters: Formatters, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    val events = LocalXPalette.current.events
    when (row) {
        CardRow.None -> Unit
        is CardRow.Earlier -> FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(CoreR.string.action_earlier_question),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            row.chips.forEach { chip -> OffsetChip(chip, formatters, onClick = { actions.onCardOffset(chip.minutes) }) }
        }
        is CardRow.RunningSleep -> FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XChip(
                selected = false,
                onClick = actions::onCardEndSleep,
                label = stringResource(CoreR.string.action_end_sleep),
                enabled = row.endSleepEnabled,
                role = Role.Button,
                leading = { XIcon(XIcons.wake, contentDescription = null, tint = colors.onSurface, size = 18.dp) },
            )
            row.chips.forEach { chip -> OffsetChip(chip, formatters, onClick = { actions.onCardOffset(chip.minutes) }) }
        }
        is CardRow.Adjust -> FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.chips.forEach { chip -> OffsetChip(chip, formatters, onClick = { actions.onCardOffset(chip.minutes) }) }
            EditChip(onClick = { actions.onEditEntry(row.editEntryId) })
        }
        is CardRow.Guard -> {
            val (icon, tint, textColor, format) = when (row.kind) {
                GuardKind.ASLEEP -> GuardStyle(XIcons.sleep, events.sleep.strong, events.sleep.text, CoreR.string.guard_already_asleep)
                GuardKind.AWAKE -> GuardStyle(XIcons.awake, events.sleep.strong, events.sleep.text, CoreR.string.guard_already_awake)
                GuardKind.FEEDING -> GuardStyle(XIcons.breast, events.feed.strong, events.feed.text, CoreR.string.guard_already_feeding)
            }
            MessageRow(icon, tint, stringResource(format, formatters.clock.format(row.sinceMillis, zone)), textColor)
        }
        is CardRow.Dedupe -> {
            val icon = entryIcon(row.entry, events)
            MessageRow(
                icon.icon,
                icon.tint,
                stringResource(CoreR.string.guard_already_logged, formatters.clock.format(row.entry.startMillis, zone)),
                colors.onSurfaceVariant,
            )
        }
    }
}

private data class GuardStyle(val icon: ImageVector, val tint: Color, val text: Color, val format: Int)

@Composable
private fun MessageRow(icon: ImageVector, tint: Color, text: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XIcon(icon, contentDescription = null, tint = tint, size = 18.dp)
        Text(text, style = PhoneType.dataMedium, color = textColor, maxLines = 2)
    }
}

/** Icon-only Edit chip: 48 × 40 visual, `edit` 18, outline border, "Edit start time". */
@Composable
private fun EditChip(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.cd_edit_start_time)
    Box(
        Modifier
            .height(ChipDefaults.TargetHeight)
            .width(48.dp)
            .clip(ChipDefaults.PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(ChipDefaults.PillHeight)
                .width(48.dp)
                .border(1.dp, colors.outline, ChipDefaults.PillShape),
            contentAlignment = Alignment.Center,
        ) {
            XIcon(XIcons.edit, contentDescription = null, tint = colors.onSurface, size = 18.dp)
        }
    }
}

/** Row 1 of a card: icon, title and optional sub-line. */
private class CardContent(val icon: TintedIcon, val title: String, val sub: String?)

@Composable
private fun cardContent(variant: CardVariant, formatters: Formatters): CardContent {
    val events = LocalXPalette.current.events
    val colors = MaterialTheme.colorScheme
    val duration = formatters.duration
    val breast = TintedIcon(XIcons.breast, events.feed.strong)
    val wake = TintedIcon(XIcons.wake, events.sleep.strong)
    return when (variant) {
        is CardVariant.FeedLogged -> CardContent(
            TintedIcon(XIcons.forFeedType(variant.type), events.feed.strong),
            stringResource(CoreR.string.receipt_logged, feedTypeLabel(variant.type, compact = true)),
            null,
        )
        is CardVariant.DiaperLogged -> CardContent(
            diaperIcon(variant.kind, events),
            stringResource(CoreR.string.receipt_logged, diaperLabel(variant.kind)),
            null,
        )
        CardVariant.FeedStarted -> CardContent(breast, stringResource(CoreR.string.receipt_feed_started), null)
        is CardVariant.FeedEnded -> CardContent(
            breast,
            stringResource(CoreR.string.receipt_feed_ended),
            stringResource(R.string.join_dot, stringResource(Labels.side(variant.side)), duration.format(variant.durationMillis)),
        )
        is CardVariant.FeedCancelled -> CardContent(
            breast,
            stringResource(CoreR.string.receipt_feed_cancelled),
            stringResource(R.string.join_dot, stringResource(Labels.side(variant.side)), duration.format(0L)),
        )
        CardVariant.SleepStarted -> CardContent(TintedIcon(XIcons.sleep, events.sleep.strong), stringResource(CoreR.string.receipt_sleep_started), null)
        is CardVariant.WokeUp -> CardContent(
            wake,
            stringResource(CoreR.string.action_woke_up),
            stringResource(CoreR.string.receipt_slept, duration.format(variant.sleptMillis)),
        )
        CardVariant.SleepCancelled -> CardContent(
            wake,
            stringResource(CoreR.string.receipt_sleep_cancelled),
            stringResource(CoreR.string.receipt_slept, duration.format(0L)),
        )
        is CardVariant.AdjustStart -> CardContent(
            if (variant.sleep) TintedIcon(XIcons.sleep, events.sleep.strong) else breast,
            stringResource(CoreR.string.action_adjust_start),
            null,
        )
        is CardVariant.Notice -> when (variant.guard) {
            GuardKind.ASLEEP -> CardContent(TintedIcon(XIcons.sleep, events.sleep.strong), stringResource(CoreR.string.status_asleep), null)
            GuardKind.AWAKE -> CardContent(TintedIcon(XIcons.awake, events.sleep.strong), stringResource(CoreR.string.status_awake), null)
            GuardKind.FEEDING -> CardContent(breast, stringResource(CoreR.string.status_breastfeeding), null)
        }
        CardVariant.Undone -> CardContent(TintedIcon(XIcons.undo, colors.onSurfaceVariant), stringResource(CoreR.string.receipt_undone), null)
    }
}
