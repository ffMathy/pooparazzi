package com.ffmathy.pooparazzi.wear.main

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.DurationFormatter
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.glance.GlanceText
import com.ffmathy.pooparazzi.wear.glance.GlanceTitle
import com.ffmathy.pooparazzi.wear.glance.WatchGlance
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.MainMode
import com.ffmathy.pooparazzi.wear.state.MainUiState
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.AutoSizeLine
import com.ffmathy.pooparazzi.wear.ui.SpokenLabels
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
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
    // The rows need two lines, not three: the sleep toggle gets the height they leave, unless the baby picker's
    // button already takes it (two or more babies) or the watch is small.
    val edgeSize = if (small || babyLabel != null) EdgeButtonSize.Small else EdgeButtonSize.Medium
    val events = events()
    val glance = state.glance
    val resources = LocalResources.current
    val words = remember(resources, f) { GlanceText(resources, f) }
    // Each row's largest fitting font scale: both rows use the smaller, so Feed and Diaper read alike.
    val rowFits = remember { mutableStateMapOf<String, Float>() }
    val rowScale = rowFits.values.minOrNull()
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
                if (showTitle) Title(state, f, words, babyLabel, actions)
                val lastFeed = glance.lastFeed
                val nextFeed = glance.nextFeed?.takeIf { lastFeed != null }
                EventRow(
                    label = stringResource(CoreR.string.kind_feed),
                    icon = XIcons.feed,
                    colors = events.feed,
                    since = lastFeed?.let { sinceText(it.startMillis, state.now, f) },
                    none = stringResource(R.string.status_no_feed_yet),
                    spoken = lastFeed?.let {
                        f.spokenAgo(stringResource(CoreR.string.spoken_last_feed), spokenFeedType(it), it.startMillis, state.now)
                    },
                    next = nextFeed?.let { words.nextShort(it, glance.now) },
                    nextSpoken = nextFeed?.let(words::nextFeedSpoken),
                    loaded = state.loaded,
                    onClick = actions.onFeed,
                    sharedScale = rowScale,
                    onFit = { rowFits["feed"] = it },
                    modifier = Modifier.weight(1f),
                )
                val lastDiaper = glance.lastDiaper
                val nextPoop = glance.nextPoop?.takeIf { lastDiaper != null }
                EventRow(
                    label = stringResource(CoreR.string.kind_diaper),
                    icon = XIcons.diaper,
                    colors = events.diaper,
                    since = lastDiaper?.let { sinceText(it.startMillis, state.now, f) },
                    none = stringResource(R.string.status_no_diaper_yet),
                    // "Pee and poop", never the status word "Both" or a "+".
                    spoken = lastDiaper?.let {
                        val kind = it.diaperKind ?: DiaperKind.PLAIN
                        f.spokenAgo(stringResource(CoreR.string.spoken_last_diaper), stringResource(SpokenLabels.diaperKind(kind)), it.startMillis, state.now)
                    },
                    next = nextPoop?.let { words.nextShort(it, glance.now) },
                    nextSpoken = nextPoop?.let(words::nextPoopSpoken),
                    loaded = state.loaded,
                    onClick = actions.onDiaper,
                    sharedScale = rowScale,
                    onFit = { rowFits["diaper"] = it },
                    modifier = Modifier.weight(1f),
                )
            }
            SleepEdgeButton(state, actions, edgeSize, f, words, Modifier.align(Alignment.BottomCenter))
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
private const val MIN_ROW_FONT_SCALE = 0.8f

/** The smallest font scale any text on Main steps down to; below it, it is cut with an ellipsis instead. */
private const val MIN_FIT_FONT_SCALE = 0.7f

/** The gap between a row's icon and its times. */
private val ICON_GAP = 12.dp

/** A row's width that its text cannot use: the side padding, the icon and the gap after it. */
private val ROW_TEXT_INSET = 14.dp * 2 + 24.dp + ICON_GAP

/** How wide [text] in [style] is on one line at font scale 1. */
@Composable
private fun textWidth(text: AnnotatedString, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, density.density) {
        val unscaled = Density(density.density, 1f)
        val width = measurer.measure(text, style, maxLines = 1, softWrap = false, density = unscaled).size.width
        with(unscaled) { width.toDp() }
    }
}

/** The font scale at which [text] in [style] is exactly [maxWidth] wide on one line. */
@Composable
private fun widthScale(text: AnnotatedString, style: TextStyle, maxWidth: Dp): Float {
    val width = textWidth(text, style)
    return if (width <= 0.dp) Float.MAX_VALUE else maxWidth / width
}

/** A line led by a small icon that says what its time is: `history` the time since, `upcoming` the expected next. */
@Immutable
private data class IconLine(val icon: ImageVector?, val text: String) {
    /** The icon inline, one em square, then a thin space and the text. */
    fun annotated(): AnnotatedString = buildAnnotatedString {
        if (icon != null) {
            appendInlineContent(icon.name, "\u25CB")
            append(THIN_SPACE)
        }
        append(text)
    }

    /** What [annotated] measures as: an em space stands in for the icon. */
    fun measured(): AnnotatedString = AnnotatedString(if (icon != null) EM_SPACE + THIN_SPACE + text else text)
}

private const val EM_SPACE = "\u2003"
private const val THIN_SPACE = "\u2009"

/** One line of [text], its inline icons drawn in [color] at the text's size. */
@Composable
private fun IconLineText(text: AnnotatedString, style: TextStyle, color: Color) {
    val icons = listOf(XIcons.history, XIcons.upcoming)
    val inline = icons.associate { icon ->
        icon.name to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.Center)) {
            XIcon(icon, contentDescription = null, tint = color, modifier = Modifier.fillMaxSize())
        }
    }
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, inlineContent = inline)
}

/** The time since, after the `history` icon: "2h 10m", or "just now" under a minute. */
private fun sinceText(fromMillis: Long, now: Long, f: Formatters): String {
    val elapsed = clockDurationMillis(fromMillis, now)
    return if (DurationFormatter.isUnderAMinute(elapsed)) f.relative.justNow else f.duration.format(elapsed)
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
 * The title line, from the glance the Tile shows too ([WatchGlance.title]): "Asleep 1h 07m" while a sleep runs,
 * otherwise the baby's name, or the last diaper when the baby has none ("Pee 48m ago"). With two or more babies it is
 * a compact button that opens the baby picker.
 */
@Composable
private fun Title(state: MainUiState, f: Formatters, words: GlanceText, babyLabel: String?, actions: MainActions) {
    val glance = state.glance
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (babyLabel != null) {
            val running = glance.runningSleep
            XCompactButton(
                label = running?.let { asleepTitle(it, glance.now, f) } ?: babyLabel,
                icon = if (running != null) XIcons.sleep else XIcons.profile,
                onClick = actions.onBaby,
                contentDescription = stringResource(CoreR.string.cd_baby_switch, babyLabel),
            )
        } else {
            // Keeps the rows where they are whether or not the title says anything.
            Box(Modifier.heightIn(min = TITLE_HEIGHT), contentAlignment = Alignment.Center) {
                when (val title = glance.title) {
                    is GlanceTitle.Asleep -> AsleepLine(title.sleep, glance.now, f)
                    is GlanceTitle.Name -> TitleText(title.name, spoken = null)
                    is GlanceTitle.LastDiaper -> {
                        val kind = title.diaper.diaperKind ?: DiaperKind.PLAIN
                        val ago = f.relative.ago(clockDurationMillis(title.diaper.startMillis, glance.now))
                        TitleText(
                            stringResource(R.string.tile_title_last_diaper, stringResource(Labels.diaperKindStatus(kind)), ago),
                            spoken = stringResource(
                                R.string.tile_cd_last_diaper,
                                stringResource(SpokenLabels.diaperKind(kind)).lowercase(f.clock.locale),
                                f.relative.agoSpoken(clockDurationMillis(title.diaper.startMillis, glance.now)),
                            ),
                        )
                    }
                    GlanceTitle.None -> Unit
                }
            }
        }
    }
}

/** The baby's name or the last diaper, in the title line. */
@Composable
private fun TitleText(text: String, spoken: String?) {
    AutoSizeLine(
        text,
        MaterialTheme.typography.titleSmall,
        if (isAmbient()) WatchAmbient.content else MaterialTheme.colorScheme.onSurface,
        minSize = 11,
        modifier = Modifier.widthIn(max = TITLE_MAX_WIDTH).then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier),
    )
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
        Modifier.clearAndSetSemantics { contentDescription = spoken },
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
 * A full-width event row, as the Tile's: the event's icon (no label: the icon says it, and the row is read with its
 * name), then the time since the last one after `history` ("⟲ 2h 10m") over the expected next one after `upcoming`
 * ("⟳ 55m"), so the lines need no "ago" / "next in". The rows share the screen's height, so their text steps down
 * (rather than clipping) where a large font or a small watch leaves less room. The whole row is read as one sentence.
 */
@Composable
private fun EventRow(
    label: String,
    icon: ImageVector,
    colors: EventColors,
    since: String?,
    none: String,
    spoken: String?,
    next: String?,
    nextSpoken: String?,
    loaded: Boolean,
    onClick: () -> Unit,
    /** The font scale both rows use (the smaller of their [onFit]s); null until both are measured. */
    sharedScale: Float?,
    /** Reports the largest font scale at which this row's text fits. */
    onFit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ambient = isAmbient()
    // Before the first read the row says nothing rather than flashing "No feeds yet".
    val secondary = since?.let { IconLine(XIcons.history, it) } ?: none.takeIf { loaded }?.let { IconLine(null, it) }
    val nextLine = next?.let { IconLine(XIcons.upcoming, it) }
    val description = listOfNotNull(label, listOfNotNull(spoken ?: secondary?.text, nextSpoken).joinToString(", ").ifEmpty { null }).joinToString(". ")
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
        val style = MaterialTheme.typography.labelMedium
        // No label: the icon says what the row is (TalkBack reads it). After it, the time since over the estimate, at
        // the size a label had. The text takes the largest font scale that fits; where both lines would be too small
        // (two babies on a small watch) the estimate goes.
        val known = rowSize.height > 0.dp
        val height = (rowSize.height - ROW_PADDING * 2).value
        val width = rowSize.width - ROW_TEXT_INSET
        val lines = listOfNotNull(secondary, nextLine)
        val layouts = buildList {
            add(lines)
            if (nextLine != null) add(lines.dropLast(1))
        }.map { shown ->
            val fit = if (!known || shown.isEmpty()) {
                1f
            } else {
                minOf(
                    height / (shown.size * style.lineHeight.value),
                    width / shown.maxOf { textWidth(it.measured(), style) },
                )
            }
            shown to fit
        }
        val (shown, fit) = layouts.firstOrNull { it.second >= MIN_ROW_FONT_SCALE } ?: layouts.last()
        SideEffect { onFit(fit) }
        CappedFontScale(minOf(fit, sharedScale ?: fit).coerceAtLeast(MIN_FIT_FONT_SCALE)) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
                XIcon(icon, null, tint = if (ambient) WatchAmbient.content else colors.strong, size = 24.dp)
                Spacer(Modifier.width(ICON_GAP))
                Column(Modifier.weight(1f)) {
                    shown.forEach { line -> IconLineText(line.annotated(), style, text) }
                }
            }
        }
    }
}

// ---- Sleep toggle (EdgeButton) ---------------------------------------------------------------

/**
 * The sleep toggle (§3.5.1–2): "Start sleep" (SLEEP container) or "Woke up" (SLEEP containerLit + border), the stock
 * collapsing EdgeButton, with the expected next sleep or wake-up under the word ("⟳ 50m"). It is Medium wherever
 * the rows leave room for it. A rejected toggle shows two centred lines ("Already / asleep") instead of the label for
 * 2 s.
 */
@Composable
private fun SleepEdgeButton(state: MainUiState, actions: MainActions, size: EdgeButtonSize, f: Formatters, words: GlanceText, modifier: Modifier) {
    val ambient = isAmbient()
    val asleep = state.mode == MainMode.ASLEEP
    val colors = events().sleep
    val label = stringResource(if (asleep) CoreR.string.action_woke_up else CoreR.string.action_start_sleep)
    val next = state.glance.nextSleep
    val action = if (asleep) stringResource(R.string.cd_woke_up_end_sleep) else label
    // Read after the action: the expected wake-up or next sleep.
    val name = next?.let { stringResource(R.string.spoken_pair, action, words.nextSleepSpoken(it, asleep)) } ?: action
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
            // EdgeButton pads its content well inside its top edge (99 dp of a Medium button, 75 dp of a Small one on a
            // small watch): the word's line may use the button's width there, the estimate's a little less, where the
            // button's curve begins. Each line steps down on its own until it fits.
            val wide = if (size == EdgeButtonSize.Medium) EDGE_WIDTH_MEDIUM else EDGE_WIDTH_SMALL
            val style = MaterialTheme.typography.labelMedium
            val nextText = next?.takeIf { guardText == null }?.let { IconLine(XIcons.upcoming, words.nextShort(it, state.now)) }
            val nextStyle = MaterialTheme.typography.labelSmall
            val labelFit = if (guardText != null) EDGE_MAX_FONT_SCALE else minOf(EDGE_MAX_FONT_SCALE, widthScale(AnnotatedString(label), style, wide))
            val nextFit = nextText?.let { minOf(EDGE_MAX_FONT_SCALE, widthScale(it.measured(), nextStyle, wide - EDGE_LOWER_LINE_INSET)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CappedFontScale(labelFit.coerceAtLeast(MIN_FIT_FONT_SCALE)) {
                    Text(
                        guardText ?: label,
                        style = style,
                        color = content,
                        textAlign = TextAlign.Center,
                        maxLines = if (guardText != null) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (guardText != null) Modifier.widthIn(max = EDGE_GUARD_WIDTH) else Modifier.requiredWidthIn(max = wide),
                    )
                }
                if (nextText != null && nextFit != null) {
                    CappedFontScale(nextFit.coerceAtLeast(MIN_FIT_FONT_SCALE)) {
                        IconLineText(nextText.annotated(), nextStyle, content)
                    }
                }
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

/** The width the sleep toggle's word may use at the top of a Medium EdgeButton. */
private val EDGE_WIDTH_MEDIUM = 120.dp

/** The width the sleep toggle's word may use at the top of a Small EdgeButton. */
private val EDGE_WIDTH_SMALL = 96.dp

/** How much narrower the EdgeButton is at its second line than at its first (its curve). */
private val EDGE_LOWER_LINE_INSET = 16.dp

/** The two-line guard ("Already / asleep") may use the button's full content width. */
private val EDGE_GUARD_WIDTH = 96.dp

/** The sleep toggle's largest font scale. */
private const val EDGE_MAX_FONT_SCALE = 1.15f


// ---- Previews --------------------------------------------------------------------------------

@WearPreviewDevices
@Composable
private fun MainIdlePreview() = XWearTheme { MainScreen(WearSamples.mainIdle(), MainActions()) }

@WearPreviewDevices
@Composable
private fun MainAsleepPreview() = XWearTheme { MainScreen(WearSamples.mainAsleep(), MainActions()) }
