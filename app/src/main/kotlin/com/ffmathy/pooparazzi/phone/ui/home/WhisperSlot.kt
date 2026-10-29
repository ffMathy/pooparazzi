package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmathy.pooparazzi.core.domain.DayCounts
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** Card geometry (DESIGN_SPEC §3.3.1, §5.8). */
internal object WhisperDefaults {
    val SlotMinHeight = 128.dp
    val CardHeight = 120.dp
    val CardShape = RoundedCornerShape(28.dp)
}

/**
 * The fixed whisper slot: the Today grid, or a 2 s notice card in its place ("Undone", a guard). The card fades in over
 * 150 ms with no slide (DESIGN_SPEC §5.10) and is a polite live region. Receipts with Undo are a sheet (ReceiptSheet).
 */
@Composable
internal fun WhisperSlot(
    card: NoticeCardUi?,
    today: DayCounts,
    lightsDown: Boolean,
    zone: ZoneId,
    formatters: Formatters,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    // Keeps the last card on screen while it fades out.
    val lastCard = remember { mutableStateOf<NoticeCardUi?>(null) }
    if (card != null) SideEffect { lastCard.value = card }
    val shown = card ?: lastCard.value
    // When the area above the buttons has to scroll (short screen, large font), a new card scrolls fully into view.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(card?.key) { if (card != null) requester.bringIntoView() }
    Box(
        modifier
            .bringIntoViewRequester(requester)
            .heightIn(min = WhisperDefaults.SlotMinHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (card == null) TodayGrid(today, lightsDown, formatters, actions::onOpenHistory)
        AnimatedVisibility(visible = card != null, enter = EnterTransition.None, exit = fadeOut(tween(150))) {
            shown?.let { NoticeCard(it, zone, formatters) }
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
        // Four columns whose numbers and captions shrink to fit ("7h 57m" on a 360 dp screen); 2 × 2 from 150 % font.
        val columns = if (LocalDensity.current.fontScale >= TODAY_TWO_COLUMN_SCALE) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            numbers.zip(captions).chunked(columns).forEach { row ->
                // One size per line across the row, so the numbers (and the captions) share a baseline even when one
                // of them ("7h 57m", "måltider") has to shrink to fit its column.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cellWidth = (maxWidth - 8.dp * (columns - 1)) / columns
                    val numberStyle = fitAll(row.map { it.first }, MaterialTheme.typography.titleMedium, cellWidth, minSp = 12f)
                    val captionStyle = fitAll(row.map { it.second }, MaterialTheme.typography.labelMedium, cellWidth, minSp = 9f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (number, caption) ->
                            Column(Modifier.weight(1f)) {
                                Text(number, style = numberStyle, color = numberColor, maxLines = 1, softWrap = false)
                                Text(caption, style = captionStyle, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [style] at the largest size (stepping down 0.5 sp, not below [minSp]) at which every one of [texts] fits [width] on
 * one line, with the line height scaled along so the texts keep a common baseline.
 */
@Composable
private fun fitAll(texts: List<String>, style: TextStyle, width: androidx.compose.ui.unit.Dp, minSp: Float): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxPx = with(density) { width.roundToPx() }.coerceAtLeast(0)
    return remember(texts, style, maxPx, minSp, density) {
        var size = style.fontSize.value
        fun fits(sp: Float) = texts.all { text ->
            measurer.measure(text, style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false, density = density).size.width <= maxPx
        }
        while (size > minSp && !fits(size)) size = (size - 0.5f).coerceAtLeast(minSp)
        val scale = size / style.fontSize.value
        if (scale >= 1f) style
        else style.copy(fontSize = size.sp, lineHeight = if (style.lineHeight.isSp) (style.lineHeight.value * scale).sp else style.lineHeight)
    }
}

/** From this font scale the Today grid is 2 × 2. */
private const val TODAY_TWO_COLUMN_SCALE = 1.5f

/** The notice card: icon · title, then the guard line ("Already asleep since 14:32") for a guard. */
@Composable
private fun NoticeCard(card: NoticeCardUi, zone: ZoneId, formatters: Formatters) {
    val colors = MaterialTheme.colorScheme
    val events = LocalXPalette.current.events
    val appear = remember(card.key) { Animatable(0f) }
    LaunchedEffect(card.key) { appear.animateTo(1f, tween(150)) }
    val (icon, title) = when (val variant = card.variant) {
        is NoticeVariant.Guard -> when (variant.kind) {
            GuardKind.ASLEEP -> TintedIcon(XIcons.sleep, events.sleep.strong) to stringResource(CoreR.string.status_asleep)
            GuardKind.AWAKE -> TintedIcon(XIcons.awake, events.sleep.strong) to stringResource(CoreR.string.status_awake)
        }
        NoticeVariant.Undone -> TintedIcon(XIcons.undo, colors.onSurfaceVariant) to stringResource(CoreR.string.receipt_undone)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = WhisperDefaults.CardHeight)
            .graphicsLayer { alpha = appear.value }
            .clip(WhisperDefaults.CardShape)
            .background(colors.surfaceContainerHighest)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            XIcon(icon.icon, contentDescription = null, tint = icon.tint, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        }
        (card.variant as? NoticeVariant.Guard)?.let { guard ->
            val format = if (guard.kind == GuardKind.ASLEEP) CoreR.string.guard_already_asleep else CoreR.string.guard_already_awake
            Text(
                stringResource(format, formatters.clock.format(guard.sinceMillis, zone)),
                style = PhoneType.dataMedium,
                color = events.sleep.text,
            )
        }
    }
}
