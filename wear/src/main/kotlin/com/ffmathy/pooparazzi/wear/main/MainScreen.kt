package com.ffmathy.pooparazzi.wear.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.DurationFormatter
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.SyncPeer
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.icons.rememberBothIcon
import com.ffmathy.pooparazzi.core.ui.lamp.Lamp
import com.ffmathy.pooparazzi.core.ui.lamp.LampEvent
import com.ffmathy.pooparazzi.core.ui.lamp.LampGeometry
import com.ffmathy.pooparazzi.core.ui.lamp.LampVariant
import com.ffmathy.pooparazzi.core.ui.time.rememberReducedMotion
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.core.ui.type.WatchType
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.MainMode
import com.ffmathy.pooparazzi.wear.state.MainUiState
import com.ffmathy.pooparazzi.wear.state.TimeField
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.AutoSizeLine
import com.ffmathy.pooparazzi.wear.ui.DurationNumeral
import com.ffmathy.pooparazzi.wear.ui.StatusCard
import com.ffmathy.pooparazzi.wear.ui.SyncIcon
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
import com.ffmathy.pooparazzi.wear.ui.XCompactButton
import com.ffmathy.pooparazzi.wear.ui.XListButton
import com.ffmathy.pooparazzi.wear.ui.XTimeText
import com.ffmathy.pooparazzi.wear.ui.agoAndClock
import com.ffmathy.pooparazzi.wear.ui.ambientBorder
import com.ffmathy.pooparazzi.wear.ui.babyLabelOrNull
import com.ffmathy.pooparazzi.wear.ui.rememberBabyTimeText
import com.ffmathy.pooparazzi.wear.ui.diaperStatusLabel
import com.ffmathy.pooparazzi.wear.ui.durationAndSince
import com.ffmathy.pooparazzi.wear.ui.eventButtonColors
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.feedSideSuffix
import com.ffmathy.pooparazzi.wear.ui.spokenFeedType
import com.ffmathy.pooparazzi.wear.ui.isSmallWatch
import com.ffmathy.pooparazzi.wear.ui.labelAtTime
import com.ffmathy.pooparazzi.wear.ui.SpokenLabels
import com.ffmathy.pooparazzi.wear.ui.spokenAgo
import com.ffmathy.pooparazzi.wear.ui.spokenDurationSince
import com.ffmathy.pooparazzi.wear.ui.spokenRunning
import com.ffmathy.pooparazzi.wear.ui.statusSince
import com.ffmathy.pooparazzi.core.R as CoreR

/** What Main can do. Every callback is a user intent; the ViewModel decides the outcome. */
@Immutable
data class MainActions(
    val onFeed: () -> Unit = {},
    val onDiaper: () -> Unit = {},
    /**
     * The sleep toggle, with what it showed: the running sleep behind "Woke up", or null for "Start sleep" (so a tap
     * that crossed a change made on the phone is not taken the other way).
     */
    val onSleepToggle: (shownSleepId: String?) -> Unit = {},
    /** Adjust a time of an entry (status cards, "Adjust start"). */
    val onAdjust: (entry: Entry, field: TimeField) -> Unit = { _, _ -> },
    val onHistory: () -> Unit = {},
    /** The baby button (two or more babies): opens the baby picker. */
    val onBaby: () -> Unit = {},
)

/**
 * Main (DESIGN_SPEC §3.5.1–2, §10.2; V1_ADDITIONS §2.2): the first screen is the hero (status block + Feed | Diaper
 * when idle, the Lamp while a sleep runs) above a pinned EdgeButton that stays visible while the list
 * below scrolls. The list holds Adjust start, the buttons, status cards, History and the sync footer. With two or
 * more babies the time text names the current one ("Noa · 14:32") and a baby button right after the hero opens the
 * baby picker (BABIES_AND_BACKUP §2.5).
 */
@Composable
fun MainScreen(state: MainUiState, actions: MainActions, modifier: Modifier = Modifier) {
    val f = rememberFormatters(state.zone)
    val small = isSmallWatch()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val edgeSize = if (small && state.mode == MainMode.IDLE) EdgeButtonSize.Small else EdgeButtonSize.Medium
    val edgeReserve = if (edgeSize == EdgeButtonSize.Small) 70.dp else 84.dp
    val cards = statusCards(state, f)
    val syncText = f.labels.syncLine(state.sync, SyncPeer.PHONE, f.clock, f.zone)
    val babyLabel = babyLabelOrNull(state.babies, state.babyId)
    val babyTime = rememberBabyTimeText(babyLabel)

    // Unscrolled, only the hero is under the time: no backing pill there (no notch in the Lamp's glow).
    val atTop by remember(listState) { derivedStateOf { !listState.canScrollBackward } }
    ScreenScaffold(scrollState = listState, modifier = modifier, timeText = { XTimeText(masked = !atTop, baby = babyTime) }) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            TransformingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Comes to rest on an item, never half under the time.
                flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
                contentPadding = PaddingValues(bottom = edgeReserve),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // The hero fills the first screen, so the list below starts under the fold (mockup "margin-top").
                item(key = "hero") {
                    // At least one screen tall; taller only when a large font scale needs it (then it scrolls). The
                    // idle hero's own content keeps clear of the pinned EdgeButton: what does not fit above it
                    // scrolls up from under it instead of resting there.
                    val idle = state.mode == MainMode.IDLE
                    Box(Modifier.fillMaxWidth().heightIn(min = screenHeight).padding(bottom = if (idle) edgeReserve else 0.dp)) {
                        when (state.mode) {
                            MainMode.IDLE -> IdleHero(state, f, actions, small, babyTime?.extraTop ?: 0.dp)
                            MainMode.ASLEEP -> LampHero(state, f, small)
                        }
                    }
                }
                if (babyLabel != null) {
                    item(key = "baby") {
                        Box(Modifier.fillMaxWidth().transformedHeight(this, spec).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                            XCompactButton(
                                label = babyLabel,
                                icon = XIcons.profile,
                                onClick = actions.onBaby,
                                contentDescription = stringResource(CoreR.string.cd_baby_switch, babyLabel),
                                transformation = SurfaceTransformation(spec),
                            )
                        }
                    }
                }
                if (state.mode != MainMode.IDLE) {
                    val running = state.runningSleep
                    if (running != null) {
                        item(key = "adjust-start") {
                            XListButton(
                                label = stringResource(CoreR.string.action_adjust_start),
                                // The expected wake-up as a clock time ("Wakes ~05:25", short enough for one line; V1_ADDITIONS
                                // §9); small watches put it under "Asleep since 02:05".
                                secondaryLabel = listOfNotNull(if (small) runningSince(state, f) else null, wakeForecastClock(state, f))
                                    .joinToString("\n").ifEmpty { null },
                                onClick = { actions.onAdjust(running, TimeField.START) },
                                icon = { XIcon(XIcons.clock, null, tint = iconTint(MaterialTheme.colorScheme.onSurface), size = 26.dp) },
                                border = ambientBorder(MaterialTheme.colorScheme.outline),
                                transformation = SurfaceTransformation(spec),
                                modifier = Modifier.transformedHeight(this, spec).padding(horizontal = 30.dp),
                            )
                        }
                    }
                    item(key = "feed-diaper") {
                        FeedDiaperGroup(actions, small, Modifier.transformedHeight(this, spec))
                    }
                }
                cards.forEach { card ->
                    item(key = card.key) {
                        StatusCard(
                            icon = card.icon,
                            iconTint = card.tint,
                            title = card.title,
                            detail = card.detail,
                            spoken = card.spoken,
                            forecast = card.forecast,
                            onClick = { actions.onAdjust(card.entry, card.field) },
                            transformation = SurfaceTransformation(spec),
                            modifier = Modifier.transformedHeight(this, spec).padding(horizontal = 24.dp),
                        )
                    }
                }
                item(key = "history") {
                    Box(Modifier.fillMaxWidth().transformedHeight(this, spec), contentAlignment = Alignment.Center) {
                        XCompactButton(
                            label = stringResource(CoreR.string.nav_history),
                            icon = XIcons.history,
                            onClick = actions.onHistory,
                            transformation = SurfaceTransformation(spec),
                        )
                    }
                }
                if (syncText != null) {
                    item(key = "sync") {
                        Box(Modifier.fillMaxWidth().transformedHeight(this, spec), contentAlignment = Alignment.Center) {
                            SyncIcon(state.sync, syncText)
                        }
                    }
                }
            }
            PinnedEdgeButton(state, actions, edgeSize, f, Modifier.align(Alignment.BottomCenter))
        }
    }
}

// ---- Hero: idle ------------------------------------------------------------------------------

@Composable
private fun IdleHero(state: MainUiState, f: Formatters, actions: MainActions, small: Boolean, babyTimeDrop: Dp) {
    // The system TimeText grows with the font scale; the status label moves down with it instead of touching it, and
    // further down by what a baby's name in the time text adds at the line's ends.
    val timeTextGrowth = TIME_TEXT_GROWTH_PER_SCALE * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
    Column(
        Modifier.fillMaxWidth().padding(top = (if (small) 20.dp else 30.dp) + timeTextGrowth + babyTimeDrop),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Before the first read the block stays empty rather than flashing "No feeds yet".
        if (state.loaded) StatusBlock(state, f, small) else Spacer(Modifier.height(if (small) 46.dp else 52.dp))
        Spacer(Modifier.height(if (small) 4.dp else 6.dp))
        FeedDiaperGroup(actions, small)
    }
}

/** The idle status block (§3.5.1): `feed`-type icon + "Fed at 12:22", then "2h 10m ago". */
@Composable
private fun StatusBlock(state: MainUiState, f: Formatters, small: Boolean) {
    val ambient = isAmbient()
    val feed = events().feed
    val lastFeed = state.lastFeed
    val textColor = if (ambient) WatchAmbient.content else feed.text
    // "Fed at 12:22 · L → R": the breast order is what the next feed starts from.
    val label = if (lastFeed != null) labelAtTime(stringResource(CoreR.string.status_fed), lastFeed.startMillis, f) + feedSideSuffix(lastFeed)
    else stringResource(R.string.status_no_feed_yet)
    val spoken = if (lastFeed != null) {
        f.spokenAgo(stringResource(CoreR.string.spoken_last_feed), spokenFeedType(lastFeed), lastFeed.startMillis, state.now)
    } else {
        label
    }
    Column(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.heightIn(min = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            XIcon(
                XIcons.forFeedType(lastFeed?.feedType),
                contentDescription = null,
                tint = if (ambient) WatchAmbient.content else feed.strong,
                size = 16.dp,
            )
            Spacer(Modifier.width(4.dp))
            // Steps down rather than running past the round edge at very large font scales.
            AutoSizeLine(label, MaterialTheme.typography.titleSmall, textColor, minSize = 8, modifier = Modifier.widthIn(max = if (small) STATUS_LABEL_MAX_WIDTH_SMALL else STATUS_LABEL_MAX_WIDTH))
        }
        if (lastFeed != null) {
            val since = clockDurationMillis(lastFeed.startMillis, state.now)
            val numeralHeight = if (small) 30.dp else 36.dp
            // The numeral is decorative (its spoken form is the block's description) and already large: it keeps its
            // 1.0× size at every font scale, so a large scale grows the words, not the hero past the EdgeButton.
            val fixed = LocalDensity.current.fontScale
            // Exactly the numeral's line height (§3.5.1: y 46–82), whatever the font's own ascent and descent: the
            // numeral is centred on it and may overhang it, so the buttons below never move.
            Box(Modifier.height(numeralHeight), contentAlignment = Alignment.Center) {
                val onSurface = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface
                val unbounded = Modifier.wrapContentHeight(unbounded = true)
                if (DurationFormatter.isUnderAMinute(since)) {
                    Text(f.relative.justNow, style = WatchType.statusWord, color = onSurface, maxLines = 1, modifier = unbounded)
                } else {
                    val scale = (if (small) 30f / 36f else 1f) / fixed
                    DurationNumeral(
                        parts = f.duration.parts(since),
                        numberStyle = WatchType.statusNumeral.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) },
                        unitStyle = WatchType.statusUnit.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) },
                        color = onSurface,
                        spoken = spoken,
                        trailing = f.relative.agoWord,
                        trailingStyle = MaterialTheme.typography.bodyMedium,
                        trailingColor = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxWidth = 180.dp,
                        minNumberSize = (24f / fixed).sp,
                        modifier = unbounded,
                    )
                }
            }
        }
    }
}

/**
 * Feed | Diaper (§3.5.1): two buttons 98 × 60 (small: 83 × 56), gap 6, r 26; icon 26 + 2 + label. They stay side by
 * side at every font scale: a label that does not fit its half steps down and wraps (two lines, hyphenated). Stacking
 * them (§3.5.10's rule above 1.15) pushed Diaper under the pinned EdgeButton, which V1_ADDITIONS §5 rules out.
 */
@Composable
private fun FeedDiaperGroup(actions: MainActions, small: Boolean, modifier: Modifier = Modifier) {
    val feed = events().feed
    val diaper = events().diaper
    val feedLabel = stringResource(CoreR.string.kind_feed)
    val diaperLabel = stringResource(CoreR.string.kind_diaper)
    val sidePadding = if (small) 10.dp else 13.dp
    val height = if (small) 56.dp else 60.dp
    val labelStyle = groupLabelStyle(listOf(feedLabel, diaperLabel), sidePadding, height)
    val feedInteraction = remember { MutableInteractionSource() }
    val diaperInteraction = remember { MutableInteractionSource() }
    ButtonGroup(
        modifier = modifier.fillMaxWidth().padding(horizontal = sidePadding).height(if (small) 56.dp else 60.dp),
        spacing = 6.dp,
        // Only the side padding above (§3.5.1: 98 dp buttons, 13 dp margins); the group's default adds 5.2 % more.
        contentPadding = PaddingValues(0.dp),
    ) {
        EventTileButton(
            feedLabel, XIcons.feed, feed, actions.onFeed,
            Modifier.weight(1f).animateWidth(feedInteraction).fillMaxSize(),
            labelStyle = labelStyle,
            interactionSource = feedInteraction,
        )
        EventTileButton(
            diaperLabel, XIcons.diaper, diaper, actions.onDiaper,
            Modifier.weight(1f).animateWidth(diaperInteraction).fillMaxSize(),
            labelStyle = labelStyle,
            interactionSource = diaperInteraction,
        )
    }
}

/**
 * How much the status block moves down per +1.0 of font scale, as the TimeText grows (measured at 200 %: the time
 * ends at 35 dp, the label starts at 41 dp).
 */
private val TIME_TEXT_GROWTH_PER_SCALE = 8.dp

/**
 * The status label's room at y 30–46 on the reference screen (the chord there, less the 20 dp icon). A long label
 * such as "Fed at 14:32 · L → R" steps down to fit it.
 */
private val STATUS_LABEL_MAX_WIDTH = 140.dp

/** [STATUS_LABEL_MAX_WIDTH] on a small (192 dp) watch, whose label sits at y 20–36. */
private val STATUS_LABEL_MAX_WIDTH_SMALL = 108.dp

/** Whether [text] fits on one line of [width] in [style] at the current font scale. */
@Composable
private fun fitsOneLine(text: String, style: TextStyle, width: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, density, width) { with(density) { measurer.measure(text, style, maxLines = 1).size.width.toDp() } <= width }
}

/**
 * [style] stepped down (to 12 sp at most) until [text] fits [width] × [height] in [maxLines] without breaking a word,
 * so a long label wraps instead of being cut by the button's curve.
 */
@Composable
private fun fittingStyle(text: String, style: TextStyle, width: Dp, height: Dp, maxLines: Int): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, density, width, height, maxLines) {
        val constraints = with(density) { Constraints(maxWidth = width.roundToPx()) }
        val maxHeight = with(density) { height.toPx() }
        fun sized(size: Float) = style.copy(fontSize = size.sp, lineHeight = (size * LINE_RATIO).sp)
        fun fits(candidate: TextStyle): Boolean {
            val result = measurer.measure(text, candidate, maxLines = maxLines, constraints = constraints, softWrap = true)
            val wordsWhole = text.split(' ').all { measurer.measure(it, candidate, maxLines = 1).size.width <= constraints.maxWidth }
            return !result.hasVisualOverflow && result.size.height <= maxHeight && wordsWhole
        }
        val floor = minOf(MIN_LABEL_SP, style.fontSize.value)
        generateSequence(style.fontSize.value) { it - 1f }
            .takeWhile { it > floor }
            .map(::sized)
            .firstOrNull(::fits)
            ?: sized(floor)
    }
}

private const val LINE_RATIO = 1.15f
private const val MIN_LABEL_SP = 12f

/** The EdgeButton's content width (Medium: ≈ 99 dp, §5.8). */
private val EDGE_LABEL_WIDTH = 96.dp

/** Room for a two-line label inside the Medium EdgeButton's content area (≈ 56 dp tall) above its curve. */
private val EDGE_TWO_LINES_HEIGHT = 46.dp

/**
 * The label style both Feed | Diaper buttons share: labelMedium while every label fits its half on one line (always
 * at 1.0×), otherwise stepped down until each fits two lines under the icon (large font scales).
 */
@Composable
private fun groupLabelStyle(labels: List<String>, sidePadding: Dp, buttonHeight: Dp): TextStyle {
    val style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val width = (screenWidth - sidePadding * 2 - GROUP_GAP) / 2 - LABEL_SIDE_PADDING * 2
    val height = buttonHeight - GROUP_ICON - GROUP_ICON_GAP - GROUP_VERTICAL_PADDING * 2
    // The smallest of the labels' fitting sizes, so both buttons read alike.
    return labels.map { fittingStyle(it, style, width, height, maxLines = 2) }.minBy { it.fontSize.value }
}

private val GROUP_GAP = 6.dp
private val LABEL_SIDE_PADDING = 8.dp
private val GROUP_ICON = 26.dp
private val GROUP_ICON_GAP = 2.dp
private val GROUP_VERTICAL_PADDING = 2.dp

@Composable
private fun EventTileButton(
    label: String,
    icon: ImageVector,
    colors: EventColors,
    onClick: () -> Unit,
    modifier: Modifier,
    labelStyle: TextStyle,
    interactionSource: MutableInteractionSource? = null,
) {
    val ambient = isAmbient()
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = WatchShapes.Button,
        colors = eventButtonColors(colors),
        border = ambientBorder(colors.strong),
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource,
    ) {
        val iconTint = if (ambient) WatchAmbient.content else colors.strong
        val textColor = if (ambient) WatchAmbient.content else colors.onContainer
        Column(
            Modifier.fillMaxSize().padding(horizontal = LABEL_SIDE_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            XIcon(icon, null, tint = iconTint, size = GROUP_ICON)
            Spacer(Modifier.height(GROUP_ICON_GAP))
            Text(label, style = labelStyle, color = textColor, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

// ---- Hero: running (Lamp) --------------------------------------------------------------------

/**
 * The watch Lamp (§2.5): Ø126 centred at y 88 with the label row (y 51–67), the numeral (y 71–105) and
 * "since 02:05" (y 108–124). Small watches: the 93 dp Lamp with the numeral only (§3.5.10).
 */
@Composable
private fun LampHero(state: MainUiState, f: Formatters, small: Boolean) {
    val ambient = isAmbient()
    val running = state.runningSleep ?: return
    val colors = events().sleep
    val elapsed = clockDurationMillis(running.startMillis, state.now)
    val geometry = if (small) LampGeometry.WatchSmall else LampGeometry.Watch
    val top = if (small) 21.dp else 25.dp
    val title = stringResource(CoreR.string.status_asleep)
    val spoken = f.spokenRunning(title, running.startMillis, state.now)
    val onSurface = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface

    Box(Modifier.fillMaxWidth().padding(top = top), contentAlignment = Alignment.TopCenter) {
        Lamp(
            event = LampEvent.SLEEP,
            running = true,
            elapsedMinutes = elapsed / 60_000L,
            variant = if (ambient) LampVariant.WATCH_AMBIENT else LampVariant.WATCH,
            geometry = geometry,
            reducedMotion = rememberReducedMotion(),
            identity = running.id,
            ambientColor = WatchAmbient.sleepRing,
        )
        val lampSize = geometry.referenceSize
        if (small) {
            // Numeral only, centred in the Lamp; "Asleep since 02:05" moves to the Adjust start item.
            Box(
                Modifier.height(lampSize).fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
                contentAlignment = Alignment.Center,
            ) {
                LampNumeral(elapsed, f, onSurface, spoken, maxWidth = 76.dp, minSize = 24f)
            }
        } else {
            // Read once as "Asleep, 1 hour 7 minutes, since 02:05" (§9.4); never a live region on the ticking value.
            Column(
                Modifier
                    .padding(top = 26.dp)
                    .clearAndSetSemantics { contentDescription = spoken },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LampLabel(title, XIcons.sleep, colors, ambient)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.height(34.dp), contentAlignment = Alignment.Center) {
                    LampNumeral(elapsed, f, onSurface, spoken, maxWidth = 106.dp, minSize = 28f)
                }
                Spacer(Modifier.height(3.dp))
                // Danish "siden kl. 14:32" is wider than "since 02:05": keep it inside the ring's chord.
                AutoSizeLine(
                    text = f.relative.since(running.startMillis, f.zone),
                    style = WatchType.bodyMedium,
                    color = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                    minSize = 11,
                    modifier = Modifier.widthIn(max = LAMP_SUBLINE_WIDTH).clearAndSetSemantics { },
                )
            }
        }
    }
}

/** The Lamp label row: icon 14 + 4 + titleSmall in the event's text colour, kept inside the ring's chord. */
@Composable
private fun LampLabel(title: String, icon: ImageVector, colors: EventColors, ambient: Boolean) {
    Row(
        Modifier.heightIn(min = 16.dp).clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        XIcon(icon, null, tint = if (ambient) WatchAmbient.content else colors.strong, size = 14.dp)
        Spacer(Modifier.width(4.dp))
        AutoSizeLine(
            text = title,
            style = WatchType.titleSmall,
            color = if (ambient) WatchAmbient.content else colors.text,
            minSize = 11,
            modifier = Modifier.widthIn(max = LAMP_LABEL_TEXT_WIDTH),
        )
    }
}

/** The ring's inner chord at the label row is about 92 dp; the text gets what the icon leaves. */
private val LAMP_LABEL_TEXT_WIDTH: Dp = 74.dp

/** The ring's inner chord at the sub-line (y 108–124) is about 91 dp. */
private val LAMP_SUBLINE_WIDTH: Dp = 86.dp

@Composable
private fun LampNumeral(elapsed: Long, f: Formatters, color: Color, spoken: String, maxWidth: Dp, minSize: Float) {
    if (DurationFormatter.isUnderAMinute(elapsed)) {
        Text(f.relative.justNow, style = WatchType.lampWord, color = color, maxLines = 1, textAlign = TextAlign.Center)
    } else {
        DurationNumeral(
            parts = f.duration.parts(elapsed),
            numberStyle = WatchType.lampNumeral,
            unitStyle = WatchType.lampUnit,
            color = color,
            spoken = spoken,
            maxWidth = maxWidth,
            minNumberSize = minSize.sp,
        )
    }
}

/**
 * What the last week expects next for sleep (V1_ADDITIONS §9): "Wakes in 2h 15m" while asleep, "Next sleep in 55m"
 * while awake, "… soon" when due, "Next sleep 24m ago" once passed; null when the week cannot predict it. [spoken] says
 * the duration in words.
 */
@Composable
private fun sleepForecastLine(state: MainUiState, f: Formatters, spoken: Boolean = false): String? {
    val expected = state.sleepForecast ?: return null
    val asleep = state.runningSleep != null
    fun amount(millis: Long) = if (spoken) f.duration.spoken(millis) else f.duration.format(millis)
    expected.agoMillis(state.now)?.let { ago ->
        return stringResource(if (asleep) CoreR.string.forecast_wakes_ago else CoreR.string.forecast_next_sleep_ago, amount(ago))
    }
    val remaining = expected.remainingMillis(state.now)
        ?: return stringResource(if (asleep) CoreR.string.forecast_wakes_soon else CoreR.string.forecast_next_sleep_soon)
    return stringResource(if (asleep) CoreR.string.forecast_wakes_in else CoreR.string.forecast_next_sleep_in, amount(remaining))
}

/**
 * "Wakes ~05:25" while asleep (a clock time, so the Danish fits on one line; it stays once passed), "Wakes soon" in the
 * last minutes before it; null when the week cannot predict it.
 */
@Composable
private fun wakeForecastClock(state: MainUiState, f: Formatters): String? {
    val expected = state.sleepForecast?.takeIf { state.runningSleep != null } ?: return null
    if (expected.remainingMillis(state.now) == null && expected.atMillis > state.now) return stringResource(CoreR.string.forecast_wakes_soon)
    return stringResource(CoreR.string.forecast_wakes_at, f.clock.format(expected.roundedAtMillis, f.zone))
}

/** "Asleep since 02:05" for the small-watch Adjust start item. */
@Composable
private fun runningSince(state: MainUiState, f: Formatters): String? {
    val running = state.runningSleep ?: return null
    return f.statusSince(stringResource(CoreR.string.status_asleep), running.startMillis)
}

// ---- Pinned EdgeButton -----------------------------------------------------------------------

/**
 * The pinned EdgeButton (§3.5.1–2, §10.2; V1_ADDITIONS §2.2): "Start sleep" (SLEEP container) or "Woke up" (SLEEP
 * containerLit + border). A rejected toggle shows two centred lines ("Already / asleep") instead of the content for 2 s.
 */
@Composable
private fun PinnedEdgeButton(state: MainUiState, actions: MainActions, size: EdgeButtonSize, f: Formatters, modifier: Modifier) {
    val ambient = isAmbient()
    val asleep = state.mode == MainMode.ASLEEP
    val colors = events().sleep
    val label = stringResource(if (asleep) CoreR.string.action_woke_up else CoreR.string.action_start_sleep)
    val name = if (asleep) stringResource(R.string.cd_woke_up_end_sleep) else label
    val guardText = state.sleepGuard?.let { stringResource(it.edgeText()) }
    val sleepState = sleepStateDescription(state, f)
    val container = when {
        ambient -> Color.Transparent
        asleep -> colors.containerLit
        else -> colors.container
    }
    val border = when {
        ambient -> BorderStroke(2.dp, WatchAmbient.sleepRing)
        asleep -> BorderStroke(1.5.dp, colors.strong)
        else -> null
    }
    val content = if (ambient) WatchAmbient.content else colors.onContainer
    EdgeButton(
        onClick = { actions.onSleepToggle(state.runningSleep?.id) },
        modifier = modifier.semantics {
            contentDescription = guardText?.replace('\n', ' ') ?: name
            if (sleepState != null) stateDescription = sleepState
        },
        buttonSize = size,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        border = border,
    ) {
        Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            if (guardText != null) {
                Text(
                    guardText,
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            } else if (fitsOneLine(label, MaterialTheme.typography.labelMedium, EDGE_LABEL_WIDTH)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = if (asleep) XIcons.wake else XIcons.sleep
                    XIcon(
                        icon,
                        null,
                        tint = if (ambient) WatchAmbient.content else colors.strong,
                        size = if (size == EdgeButtonSize.Small) 20.dp else 24.dp,
                    )
                    AutoSizeLine(label, MaterialTheme.typography.labelMedium, content, minSize = 12, modifier = Modifier.width(EDGE_LABEL_WIDTH))
                }
            } else {
                // Very large text (e.g. 200 %): the label wraps onto two lines in place of the icon, never clipped.
                val style = fittingStyle(label, MaterialTheme.typography.labelMedium, EDGE_LABEL_WIDTH, EDGE_TWO_LINES_HEIGHT, maxLines = 2)
                Text(label, style = style, color = content, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.width(EDGE_LABEL_WIDTH))
            }
        }
    }
}

private fun GuardKind.edgeText(): Int = when (this) {
    GuardKind.ALREADY_ASLEEP -> R.string.edge_guard_already_asleep
    GuardKind.ALREADY_AWAKE -> R.string.edge_guard_already_awake
}

/** §9.4: "Asleep since 02:05" / "Awake since 13:27" as the sleep toggle's state. */
@Composable
private fun sleepStateDescription(state: MainUiState, f: Formatters): String? {
    state.runningSleep?.let { return f.statusSince(stringResource(CoreR.string.status_asleep), it.startMillis) }
    val end = state.lastSleep?.endMillis ?: return null
    return f.statusSince(stringResource(CoreR.string.status_awake), end)
}

// ---- Status cards ----------------------------------------------------------------------------

private class CardModel(
    val key: String,
    val entry: Entry,
    val field: TimeField,
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val detail: String,
    val spoken: String,
    val forecast: String? = null,
)

/**
 * The status cards (§3.5.1–2, §3.5.9): idle shows Awake and the last diaper; asleep shows Last fed and the last
 * diaper. Each opens Adjust.
 */
@Composable
private fun statusCards(state: MainUiState, f: Formatters): List<CardModel> {
    val events = events()
    val cards = mutableListOf<CardModel>()
    val now = state.now
    val awakeWord = stringResource(CoreR.string.status_awake)
    val lastFedWord = stringResource(CoreR.string.status_last_fed)
    val lastFeedSpoken = stringResource(CoreR.string.spoken_last_feed)
    val lastDiaperSpoken = stringResource(CoreR.string.spoken_last_diaper)
    val bothIcon = rememberBothIcon(events.pee.strong, events.poop.strong)

    val feedForecast = state.feedForecast
    if (state.mode == MainMode.ASLEEP) {
        state.lastFeed?.let { feed ->
            // The expected next feed as the card's third line (V1_ADDITIONS §9).
            val next = feedForecast?.let { expected ->
                expected.agoMillis(now)
                    ?.let { stringResource(CoreR.string.forecast_next_feed_ago, f.duration.format(it)) to stringResource(CoreR.string.forecast_next_feed_ago, f.duration.spoken(it)) }
                    ?: expected.remainingMillis(now)
                    ?.let { stringResource(CoreR.string.forecast_next_feed_in, f.duration.format(it)) to stringResource(CoreR.string.forecast_next_feed_in, f.duration.spoken(it)) }
                    ?: stringResource(CoreR.string.forecast_next_feed_soon).let { it to it }
            }
            val spoken = f.spokenAgo(lastFeedSpoken, spokenFeedType(feed), feed.startMillis, now)
            cards += CardModel(
                "last-feed", feed, TimeField.START, XIcons.forFeedType(feed.feedType), events.feed.strong,
                lastFedWord, f.agoAndClock(feed.startMillis, now) + feedSideSuffix(feed),
                next?.let { stringResource(R.string.spoken_pair, spoken, it.second) } ?: spoken,
                next?.first,
            )
        }
    } else {
        // Idle, the hero shows the last feed; the expected next one gets its own card, first (V1_ADDITIONS §9). It opens
        // the last feed, like the hero.
        val feed = state.lastFeed
        if (feed != null && feedForecast != null) {
            val title = stringResource(CoreR.string.forecast_next_feed)
            val remaining = feedForecast.remainingMillis(now)
            val ago = feedForecast.agoMillis(now)
            val clock = f.clock.format(feedForecast.roundedAtMillis, f.zone)
            val detail = ago?.let { stringResource(CoreR.string.forecast_ago_at, f.duration.format(it), clock) }
                ?: remaining?.let { stringResource(CoreR.string.forecast_in_at, f.duration.format(it), clock) }
                ?: stringResource(CoreR.string.forecast_soon_word)
            val spoken = ago?.let { stringResource(CoreR.string.forecast_next_feed_ago, f.duration.spoken(it)) }
                ?: remaining?.let { stringResource(CoreR.string.forecast_next_feed_in, f.duration.spoken(it)) }
                ?: stringResource(CoreR.string.forecast_next_feed_soon)
            cards += CardModel("next-feed", feed, TimeField.START, XIcons.feed, events.feed.strong, title, detail, spoken)
        }
    }
    if (state.runningSleep == null) {
        val lastSleep = state.lastSleep
        val end = lastSleep?.endMillis
        if (lastSleep != null && end != null) {
            val forecast = sleepForecastLine(state, f)
            val spoken = f.spokenDurationSince(awakeWord, end, now)
            cards += CardModel(
                "awake", lastSleep, TimeField.END, XIcons.awake, events.sleep.strong, awakeWord,
                f.durationAndSince(end, now),
                sleepForecastLine(state, f, spoken = true)?.let { stringResource(R.string.spoken_pair, spoken, it) } ?: spoken,
                forecast,
            )
        }
    }
    state.lastDiaper?.let { diaper ->
        val kind = diaper.diaperKind ?: DiaperKind.PLAIN
        val (icon, tint) = when (kind) {
            DiaperKind.PEE -> XIcons.pee to events.pee.strong
            DiaperKind.POOP -> XIcons.poop to events.poop.strong
            DiaperKind.BOTH -> bothIcon to Color.Unspecified
            DiaperKind.PLAIN -> XIcons.diaper to events.diaper.strong
        }
        cards += CardModel(
            "diaper", diaper, TimeField.START, icon, tint, diaperStatusLabel(diaper),
            f.agoAndClock(diaper.startMillis, now),
            // "Pee and poop", never the status word "Both" or a "+".
            f.spokenAgo(lastDiaperSpoken, stringResource(SpokenLabels.diaperKind(kind)), diaper.startMillis, now),
        )
    }
    return cards
}

@Composable
private fun iconTint(color: Color): Color = if (isAmbient()) WatchAmbient.content else color

// ---- Previews --------------------------------------------------------------------------------

@WearPreviewDevices
@Composable
private fun MainIdlePreview() = XWearTheme { MainScreen(WearSamples.mainIdle(), MainActions()) }

@WearPreviewDevices
@Composable
private fun MainAsleepPreview() = XWearTheme { MainScreen(WearSamples.mainAsleep(), MainActions()) }
