package com.ffmathy.pooparazzi.wear.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.wear.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
import androidx.wear.compose.material3.Button
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.MainMode
import com.ffmathy.pooparazzi.wear.state.MainUiState
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.AutoSizeLine
import com.ffmathy.pooparazzi.wear.ui.SpokenLabels
import com.ffmathy.pooparazzi.wear.ui.XCompactButton
import com.ffmathy.pooparazzi.wear.ui.XTimeText
import com.ffmathy.pooparazzi.wear.ui.ambientBorder
import com.ffmathy.pooparazzi.wear.ui.babyLabelOrNull
import com.ffmathy.pooparazzi.wear.ui.eventButtonColors
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.isSmallWatch
import com.ffmathy.pooparazzi.wear.ui.rememberBabyTimeText
import com.ffmathy.pooparazzi.wear.ui.spokenAgo
import com.ffmathy.pooparazzi.wear.ui.spokenFeedType
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
    /** The baby button (two or more babies): opens the baby picker. */
    val onBaby: () -> Unit = {},
)

/**
 * Main (DESIGN_SPEC §3.5.1): the app looks like the Tile. A title line ("Asleep 1h 07m", or the baby's name; with two
 * or more babies the baby picker), a Feed row and a Diaper row with the time since the last one, and the sleep toggle
 * as an EdgeButton. One fixed screen, nothing to scroll: history and every other detail are on the phone.
 */
@Composable
fun MainScreen(state: MainUiState, actions: MainActions, modifier: Modifier = Modifier) {
    val f = rememberFormatters(state.zone)
    val small = isSmallWatch()
    val babyLabel = babyLabelOrNull(state.babies, state.babyId)
    val babyTime = rememberBabyTimeText(babyLabel)
    val edgeSize = if (small) EdgeButtonSize.ExtraSmall else EdgeButtonSize.Small
    val events = events()
    // As on the Tile, small watches drop the title, unless it is the only way to switch babies.
    val showTitle = !small || babyLabel != null
    // The system TimeText grows with the font scale (it is not capped): the content moves down with it.
    val timeTextGrowth = TIME_TEXT_GROWTH_PER_SCALE * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)

    ScreenScaffold(modifier = modifier, timeText = { XTimeText(baby = babyTime) }) {
        // One fixed screen, as the Tile: past this font scale the labels would no longer fit their buttons.
        CappedFontScale(MAX_FONT_SCALE) { BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = maxWidth * SIDE_MARGIN
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = side,
                        end = side,
                        top = (if (small) 18.dp else 24.dp) + timeTextGrowth + (babyTime?.extraTop ?: 0.dp),
                        bottom = edgeSize.height + ROW_GAP,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                if (showTitle) Title(state, f, babyLabel, actions)
                val lastFeed = state.lastFeed
                EventRow(
                    label = stringResource(CoreR.string.kind_feed),
                    icon = XIcons.feed,
                    colors = events.feed,
                    since = lastFeed?.let { f.relative.ago(clockDurationMillis(it.startMillis, state.now)) },
                    none = stringResource(R.string.status_no_feed_yet),
                    spoken = lastFeed?.let {
                        f.spokenAgo(stringResource(CoreR.string.spoken_last_feed), spokenFeedType(it), it.startMillis, state.now)
                    },
                    loaded = state.loaded,
                    onClick = actions.onFeed,
                    modifier = Modifier.weight(1f),
                )
                val lastDiaper = state.lastDiaper
                EventRow(
                    label = stringResource(CoreR.string.kind_diaper),
                    icon = XIcons.diaper,
                    colors = events.diaper,
                    since = lastDiaper?.let { f.relative.ago(clockDurationMillis(it.startMillis, state.now)) },
                    none = stringResource(R.string.status_no_diaper_yet),
                    // "Pee and poop", never the status word "Both" or a "+".
                    spoken = lastDiaper?.let {
                        val kind = it.diaperKind ?: DiaperKind.PLAIN
                        f.spokenAgo(stringResource(CoreR.string.spoken_last_diaper), stringResource(SpokenLabels.diaperKind(kind)), it.startMillis, state.now)
                    },
                    loaded = state.loaded,
                    onClick = actions.onDiaper,
                    modifier = Modifier.weight(1f),
                )
            }
            SleepEdgeButton(state, actions, edgeSize, f, Modifier.align(Alignment.BottomCenter))
        } }
    }
}

/** [content] at the current font scale, but at most [max]. */
@Composable
private fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, max), content = content)
    }
}

/** Main's largest font scale (the system TimeText still follows the setting). */
private const val MAX_FONT_SCALE = 1.3f

/** The smallest font scale a row's text steps down to on a crowded screen (two babies on a small watch). */
private const val MIN_ROW_FONT_SCALE = 0.85f

/** The smallest font scale any text on Main steps down to; below it, it is cut with an ellipsis instead. */
private const val MIN_FIT_FONT_SCALE = 0.7f

/** A row's width that its text cannot use: the side padding, the icon and the gap after it. */
private val ROW_TEXT_INSET = 14.dp * 2 + 24.dp + 10.dp

/** The font scale at which [text] in [style] is exactly [maxWidth] wide on one line. */
@Composable
private fun widthScale(text: AnnotatedString, style: TextStyle, maxWidth: Dp): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, maxWidth, density.density) {
        val unscaled = Density(density.density, 1f)
        val width = measurer.measure(text, style, maxLines = 1, softWrap = false, density = unscaled).size.width
        if (width <= 0) Float.MAX_VALUE else with(unscaled) { maxWidth.toPx() } / width
    }
}

/** The rows' side margin, as a share of the screen width (the Tile's rows sit about this far in). */
private const val SIDE_MARGIN = 0.07f

private val ROW_GAP = 4.dp

/** How much the TimeText grows per +1.0 of font scale (measured at 200 %). */
private val TIME_TEXT_GROWTH_PER_SCALE = 10.dp

private val ROW_PADDING = 4.dp

private val EdgeButtonSize.height: Dp
    get() = when (this) {
        EdgeButtonSize.ExtraSmall -> 46.dp
        EdgeButtonSize.Small -> 56.dp
        EdgeButtonSize.Medium -> 70.dp
        else -> 96.dp
    }

// ---- Title -----------------------------------------------------------------------------------

/**
 * The title line, as on the Tile: "Asleep 1h 07m" while a sleep runs, otherwise the baby's name (nothing when unnamed).
 * With two or more babies it is a compact button that opens the baby picker.
 */
@Composable
private fun Title(state: MainUiState, f: Formatters, babyLabel: String?, actions: MainActions) {
    val running = state.runningSleep
    if (babyLabel != null) {
        val asleepText = running?.let { asleepTitle(it, state.now, f) }
        XCompactButton(
            label = asleepText ?: babyLabel,
            icon = if (running != null) XIcons.sleep else XIcons.profile,
            onClick = actions.onBaby,
            contentDescription = stringResource(CoreR.string.cd_baby_switch, babyLabel),
        )
        return
    }
    if (running != null) {
        AsleepLine(running, state.now, f)
        return
    }
    val name = state.baby.name.trim()
    // Keeps the rows where they are whether or not a name shows.
    Box(Modifier.heightIn(min = TITLE_HEIGHT), contentAlignment = Alignment.Center) {
        if (name.isNotEmpty()) {
            val ambient = isAmbient()
            AutoSizeLine(
                name,
                MaterialTheme.typography.titleSmall,
                if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface,
                minSize = 11,
                modifier = Modifier.widthIn(max = TITLE_MAX_WIDTH),
            )
        }
    }
}

@Composable
private fun asleepTitle(running: Entry, now: Long, f: Formatters): String =
    stringResource(R.string.tile_title_asleep, f.duration.format(clockDurationMillis(running.startMillis, now)))

/** "☾ Asleep 1h 07m" in the sleep colours, read as "Asleep, 1 hour 7 minutes, since 02:05". */
@Composable
private fun AsleepLine(running: Entry, now: Long, f: Formatters) {
    val ambient = isAmbient()
    val sleep = events().sleep
    val spoken = f.spokenRunning(stringResource(CoreR.string.status_asleep), running.startMillis, now)
    Row(
        Modifier.heightIn(min = TITLE_HEIGHT).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        XIcon(XIcons.sleep, null, tint = if (ambient) WatchAmbient.content else sleep.strong, size = 14.dp)
        Spacer(Modifier.width(4.dp))
        AutoSizeLine(
            asleepTitle(running, now, f),
            MaterialTheme.typography.titleSmall,
            if (ambient) WatchAmbient.content else sleep.text,
            minSize = 11,
            modifier = Modifier.widthIn(max = TITLE_MAX_WIDTH),
        )
    }
}

private val TITLE_HEIGHT = 24.dp
private val TITLE_MAX_WIDTH = 140.dp

// ---- Rows ------------------------------------------------------------------------------------

/**
 * A full-width event row, as the Tile's: icon, label, and the time since the last one ("2h 10m ago"). The rows share
 * the screen's height, so their text steps down (rather than clipping) where a large font or a small watch leaves
 * less room. The whole row is read as one sentence.
 */
@Composable
private fun EventRow(
    label: String,
    icon: ImageVector,
    colors: EventColors,
    since: String?,
    none: String,
    spoken: String?,
    loaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ambient = isAmbient()
    // Before the first read the row says nothing rather than flashing "No feeds yet".
    val secondary = since ?: none.takeIf { loaded }
    val description = listOfNotNull(label, spoken ?: secondary).joinToString(". ")
    val text = if (ambient) WatchAmbient.content else colors.onContainer
    val density = LocalDensity.current
    var rowSize by remember { mutableStateOf(DpSize.Zero) }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) } }
            .semantics { contentDescription = description },
        shape = WatchShapes.Button,
        colors = eventButtonColors(colors),
        border = ambientBorder(colors.strong),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = ROW_PADDING),
    ) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
            XIcon(icon, null, tint = if (ambient) WatchAmbient.content else colors.strong, size = 24.dp)
            Spacer(Modifier.width(10.dp))
            val labelStyle = MaterialTheme.typography.labelMedium
            val secondaryStyle = MaterialTheme.typography.labelSmall
            val oneLine = buildAnnotatedString {
                append(label)
                if (secondary != null) withStyle(secondaryStyle.toSpanStyle()) { append(DOT_SEPARATOR + secondary) }
            }
            // The rows share the screen's height: their text takes the largest font scale that fits, on two lines or,
            // where two would be too small (two babies on a small watch), on one ("Feed · 2h 10m ago").
            val known = rowSize.height > 0.dp
            val height = (rowSize.height - ROW_PADDING * 2).value
            val width = rowSize.width - ROW_TEXT_INSET
            val twoLines = if (!known) 1f else minOf(
                height / (labelStyle.lineHeight.value + secondaryStyle.lineHeight.value),
                widthScale(AnnotatedString(label), labelStyle, width),
                secondary?.let { widthScale(AnnotatedString(it), secondaryStyle, width) } ?: Float.MAX_VALUE,
            )
            val single = if (!known) 1f else minOf(height / labelStyle.lineHeight.value, widthScale(oneLine, labelStyle, width))
            val useOneLine = secondary != null && twoLines < MIN_ROW_FONT_SCALE && single > twoLines
            Column(Modifier.weight(1f)) {
                CappedFontScale((if (useOneLine) single else twoLines).coerceAtLeast(MIN_FIT_FONT_SCALE)) {
                    if (useOneLine) {
                        Text(oneLine, style = labelStyle, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(label, style = labelStyle, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (secondary != null) {
                            Text(secondary, style = secondaryStyle, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---- Sleep toggle (EdgeButton) ---------------------------------------------------------------

/**
 * The sleep toggle (§3.5.1–2): "Start sleep" (SLEEP container) or "Woke up" (SLEEP containerLit + border), the stock
 * collapsing EdgeButton. A rejected toggle shows two centred lines ("Already / asleep") instead of the label for 2 s.
 */
@Composable
private fun SleepEdgeButton(state: MainUiState, actions: MainActions, size: EdgeButtonSize, f: Formatters, modifier: Modifier) {
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
            // One line of "Start sleep" must clear the button's curve: the label steps down to fit its width.
            val labelWidth = if (size == EdgeButtonSize.ExtraSmall) EDGE_LABEL_WIDTH_EXTRA_SMALL else EDGE_LABEL_WIDTH
            val style = MaterialTheme.typography.labelMedium
            val fit = if (guardText != null) EDGE_MAX_FONT_SCALE else minOf(EDGE_MAX_FONT_SCALE, widthScale(AnnotatedString(label), style, labelWidth))
            CappedFontScale(fit.coerceAtLeast(MIN_FIT_FONT_SCALE)) {
                Text(
                    guardText ?: label,
                    style = style,
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = if (guardText != null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = if (guardText != null) EDGE_GUARD_WIDTH else labelWidth + 8.dp),
                )
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

/** The EdgeButton's content width at its top, where the label sits. */
private val EDGE_LABEL_WIDTH = 84.dp

/** The two-line guard ("Already / asleep") may use the button's full content width. */
private val EDGE_GUARD_WIDTH = 96.dp

/** The ExtraSmall EdgeButton's (small watches) content width at its top. */
private val EDGE_LABEL_WIDTH_EXTRA_SMALL = 60.dp

/** The EdgeButton label's largest font scale: "Start sleep" still fits one line of [EDGE_LABEL_WIDTH]. */
private const val EDGE_MAX_FONT_SCALE = 1.15f


// ---- Previews --------------------------------------------------------------------------------

@WearPreviewDevices
@Composable
private fun MainIdlePreview() = XWearTheme { MainScreen(WearSamples.mainIdle(), MainActions()) }

@WearPreviewDevices
@Composable
private fun MainAsleepPreview() = XWearTheme { MainScreen(WearSamples.mainAsleep(), MainActions()) }
