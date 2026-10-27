package com.ffmathy.xaternity.wear.main

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
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
import com.ffmathy.xaternity.core.format.DurationFormatter
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.format.SyncPeer
import com.ffmathy.xaternity.core.format.rememberFormatters
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.clockDurationMillis
import com.ffmathy.xaternity.core.model.diaperKind
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.icons.rememberBothIcon
import com.ffmathy.xaternity.core.ui.lamp.Lamp
import com.ffmathy.xaternity.core.ui.lamp.LampEvent
import com.ffmathy.xaternity.core.ui.lamp.LampGeometry
import com.ffmathy.xaternity.core.ui.lamp.LampVariant
import com.ffmathy.xaternity.core.ui.time.rememberReducedMotion
import com.ffmathy.xaternity.core.ui.tokens.EventColors
import com.ffmathy.xaternity.core.ui.type.WatchType
import com.ffmathy.xaternity.wear.R
import com.ffmathy.xaternity.wear.sample.WearSamples
import com.ffmathy.xaternity.wear.state.GuardKind
import com.ffmathy.xaternity.wear.state.MainMode
import com.ffmathy.xaternity.wear.state.MainUiState
import com.ffmathy.xaternity.wear.state.TimeField
import com.ffmathy.xaternity.wear.theme.WatchAmbient
import com.ffmathy.xaternity.wear.theme.XWearTheme
import com.ffmathy.xaternity.wear.theme.isAmbient
import com.ffmathy.xaternity.wear.ui.AutoSizeLine
import com.ffmathy.xaternity.wear.ui.DurationNumeral
import com.ffmathy.xaternity.wear.ui.StatusCard
import com.ffmathy.xaternity.wear.ui.SyncLine
import com.ffmathy.xaternity.wear.ui.WatchShapes
import com.ffmathy.xaternity.wear.ui.XCompactButton
import com.ffmathy.xaternity.wear.ui.XListButton
import com.ffmathy.xaternity.wear.ui.agoAndClock
import com.ffmathy.xaternity.wear.ui.ambientBorder
import com.ffmathy.xaternity.wear.ui.diaperStatusLabel
import com.ffmathy.xaternity.wear.ui.durationAndSince
import com.ffmathy.xaternity.wear.ui.eventButtonColors
import com.ffmathy.xaternity.wear.ui.events
import com.ffmathy.xaternity.wear.ui.feedTypeLabel
import com.ffmathy.xaternity.wear.ui.isLargeFont
import com.ffmathy.xaternity.wear.ui.isSmallWatch
import com.ffmathy.xaternity.wear.ui.labelAtTime
import com.ffmathy.xaternity.wear.ui.spokenAgo
import com.ffmathy.xaternity.wear.ui.spokenDurationSince
import com.ffmathy.xaternity.core.R as CoreR

/** What Main can do. Every callback is a user intent; the ViewModel decides the outcome. */
@Immutable
data class MainActions(
    val onFeed: () -> Unit = {},
    val onDiaper: () -> Unit = {},
    val onSleepToggle: () -> Unit = {},
    val onEndFeed: () -> Unit = {},
    /** Adjust a time of an entry (status cards, "Adjust start"). */
    val onAdjust: (entry: Entry, field: TimeField) -> Unit = { _, _ -> },
    val onHistory: () -> Unit = {},
)

/**
 * Main (DESIGN_SPEC §3.5.1–2, §10.2; V1_ADDITIONS §2.2): the first screen is the hero (status block + Feed | Diaper
 * when idle, the Lamp while a sleep or breastfeed runs) above a pinned EdgeButton that stays visible while the list
 * below scrolls. The list holds Adjust start, the buttons, status cards, History and the sync footer.
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

    ScreenScaffold(scrollState = listState, modifier = modifier) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            TransformingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = edgeReserve),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // The hero fills the first screen, so the list below starts under the fold (mockup "margin-top").
                item(key = "hero") {
                    // At least one screen tall; taller only when a large font scale needs it (then it scrolls).
                    Box(Modifier.fillMaxWidth().heightIn(min = screenHeight)) {
                        when (state.mode) {
                            MainMode.IDLE -> IdleHero(state, f, actions, small)
                            MainMode.ASLEEP, MainMode.FEEDING -> LampHero(state, f, small)
                        }
                    }
                }
                if (state.mode != MainMode.IDLE) {
                    val running = state.runningFeed ?: state.runningSleep
                    if (running != null) {
                        item(key = "adjust-start") {
                            XListButton(
                                label = stringResource(CoreR.string.action_adjust_start),
                                secondaryLabel = if (small) runningSince(state, f) else null,
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
                if (state.mode == MainMode.FEEDING) {
                    item(key = "sleep-toggle") {
                        SleepListButton(state, actions, SurfaceTransformation(spec), Modifier.transformedHeight(this, spec))
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
                            SyncLine(state.sync, syncText, maxWidth = 180.dp)
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
private fun IdleHero(state: MainUiState, f: Formatters, actions: MainActions, small: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = if (small) 20.dp else 30.dp),
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
    val label = if (lastFeed != null) labelAtTime(stringResource(CoreR.string.status_fed), lastFeed.startMillis, f)
    else stringResource(R.string.status_no_feed_yet)
    val spoken = if (lastFeed != null) {
        f.spokenAgo(stringResource(CoreR.string.spoken_last_feed), feedTypeLabel(lastFeed), lastFeed.startMillis, state.now)
    } else {
        label
    }
    Column(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.heightIn(min = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            XIcon(
                XIcons.forFeedType(lastFeed?.feedType ?: FeedType.FORMULA),
                contentDescription = null,
                tint = if (ambient) WatchAmbient.content else feed.strong,
                size = 16.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = textColor, maxLines = 1)
        }
        if (lastFeed != null) {
            val since = clockDurationMillis(lastFeed.startMillis, state.now)
            val numeralHeight = if (small) 30.dp else 36.dp
            Box(Modifier.heightIn(min = numeralHeight), contentAlignment = Alignment.Center) {
                val onSurface = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface
                if (DurationFormatter.isUnderAMinute(since)) {
                    Text(f.relative.justNow, style = WatchType.statusWord, color = onSurface, maxLines = 1)
                } else {
                    val scale = if (small) 30f / 36f else 1f
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
                        minNumberSize = 24.sp,
                    )
                }
            }
        }
    }
}

/**
 * Feed | Diaper (§3.5.1): two buttons 98 × 60 (small: 83 × 56), gap 6, r 26; icon 26 + 2 + label. Above 1.15 font
 * scale they stack as two 56 dp buttons.
 */
@Composable
private fun FeedDiaperGroup(actions: MainActions, small: Boolean, modifier: Modifier = Modifier) {
    val feed = events().feed
    val diaper = events().diaper
    val feedLabel = stringResource(CoreR.string.kind_feed)
    val diaperLabel = stringResource(CoreR.string.kind_diaper)
    val sidePadding = if (small) 10.dp else 13.dp
    if (isLargeFont()) {
        Column(modifier.fillMaxWidth().padding(horizontal = sidePadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EventTileButton(feedLabel, XIcons.feed, feed, actions.onFeed, Modifier.fillMaxWidth().height(56.dp), stacked = false)
            EventTileButton(diaperLabel, XIcons.diaper, diaper, actions.onDiaper, Modifier.fillMaxWidth().height(56.dp), stacked = false)
        }
        return
    }
    val feedInteraction = remember { MutableInteractionSource() }
    val diaperInteraction = remember { MutableInteractionSource() }
    ButtonGroup(
        modifier = modifier.fillMaxWidth().padding(horizontal = sidePadding).height(if (small) 56.dp else 60.dp),
        spacing = 6.dp,
    ) {
        EventTileButton(
            feedLabel, XIcons.feed, feed, actions.onFeed,
            Modifier.weight(1f).animateWidth(feedInteraction).fillMaxSize(),
            interactionSource = feedInteraction,
        )
        EventTileButton(
            diaperLabel, XIcons.diaper, diaper, actions.onDiaper,
            Modifier.weight(1f).animateWidth(diaperInteraction).fillMaxSize(),
            interactionSource = diaperInteraction,
        )
    }
}

@Composable
private fun EventTileButton(
    label: String,
    icon: ImageVector,
    colors: EventColors,
    onClick: () -> Unit,
    modifier: Modifier,
    stacked: Boolean = true,
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
        if (stacked) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                XIcon(icon, null, tint = iconTint, size = 26.dp)
                Spacer(Modifier.height(2.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1)
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                XIcon(icon, null, tint = iconTint, size = 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1)
            }
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
    val feeding = state.mode == MainMode.FEEDING
    val running = (if (feeding) state.runningFeed else state.runningSleep) ?: return
    val colors = if (feeding) events().feed else events().sleep
    val elapsed = clockDurationMillis(running.startMillis, state.now)
    val event = if (feeding) LampEvent.FEED else LampEvent.SLEEP
    val geometry = if (small) LampGeometry.WatchSmall else LampGeometry.Watch
    val top = if (small) 21.dp else 25.dp
    val title = stringResource(if (feeding) CoreR.string.status_breastfeeding else CoreR.string.status_asleep)
    val spoken = f.spokenDurationSince(title + ",", running.startMillis, state.now)
    val onSurface = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface

    Box(Modifier.fillMaxWidth().padding(top = top), contentAlignment = Alignment.TopCenter) {
        Lamp(
            event = event,
            running = true,
            elapsedMinutes = elapsed / 60_000L,
            variant = if (ambient) LampVariant.WATCH_AMBIENT else LampVariant.WATCH,
            geometry = geometry,
            reducedMotion = rememberReducedMotion(),
            identity = running.id,
            ambientColor = if (feeding) colors.strong else WatchAmbient.sleepRing,
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
                LampLabel(title, if (feeding) XIcons.breast else XIcons.sleep, colors, ambient)
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

/** "Asleep since 02:05" / "Breastfeeding since 14:32" for the small-watch Adjust start item. */
@Composable
private fun runningSince(state: MainUiState, f: Formatters): String? {
    val feeding = state.mode == MainMode.FEEDING
    val running = (if (feeding) state.runningFeed else state.runningSleep) ?: return null
    val word = stringResource(if (feeding) CoreR.string.status_breastfeeding else CoreR.string.status_asleep)
    return word + " " + f.relative.since(running.startMillis, f.zone)
}

// ---- Pinned EdgeButton -----------------------------------------------------------------------

/**
 * The pinned EdgeButton (§3.5.1–2, §10.2; V1_ADDITIONS §2.2): "Start sleep" (SLEEP container), "Woke up" (SLEEP
 * containerLit + border) or "End feed" (FEED containerLit + border). A rejected toggle shows two centred lines
 * ("Already / asleep") instead of the content for 2 s.
 */
@Composable
private fun PinnedEdgeButton(state: MainUiState, actions: MainActions, size: EdgeButtonSize, f: Formatters, modifier: Modifier) {
    val ambient = isAmbient()
    val feeding = state.mode == MainMode.FEEDING
    val asleep = state.mode == MainMode.ASLEEP
    val colors = if (feeding) events().feed else events().sleep
    val guard = if (feeding) state.endFeedGuard else state.sleepGuard
    val label = stringResource(
        when {
            feeding -> CoreR.string.action_end_feed
            asleep -> CoreR.string.action_woke_up
            else -> CoreR.string.action_start_sleep
        },
    )
    val name = when {
        feeding -> label
        asleep -> stringResource(R.string.cd_woke_up_end_sleep)
        else -> label
    }
    val guardText = guard?.let { stringResource(it.edgeText()) }
    val sleepState = sleepStateDescription(state, f).takeUnless { feeding }
    val container = when {
        ambient -> Color.Transparent
        feeding || asleep -> colors.containerLit
        else -> colors.container
    }
    val border = when {
        ambient -> BorderStroke(2.dp, if (feeding) colors.strong else WatchAmbient.sleepRing)
        feeding || asleep -> BorderStroke(1.5.dp, colors.strong)
        else -> null
    }
    val content = if (ambient) WatchAmbient.content else colors.onContainer
    EdgeButton(
        onClick = if (feeding) actions.onEndFeed else actions.onSleepToggle,
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
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = when {
                        feeding -> XIcons.breast
                        asleep -> XIcons.wake
                        else -> XIcons.sleep
                    }
                    XIcon(
                        icon,
                        null,
                        tint = if (ambient) WatchAmbient.content else colors.strong,
                        size = if (size == EdgeButtonSize.Small) 20.dp else 24.dp,
                    )
                    AutoSizeLine(label, MaterialTheme.typography.labelMedium, content, minSize = 12, modifier = Modifier.width(96.dp))
                }
            }
        }
    }
}

private fun GuardKind.edgeText(): Int = when (this) {
    GuardKind.ALREADY_ASLEEP -> R.string.edge_guard_already_asleep
    GuardKind.ALREADY_AWAKE -> R.string.edge_guard_already_awake
    GuardKind.ALREADY_FEEDING -> R.string.edge_guard_already_feeding
}

/** §9.4: "Asleep since 02:05" / "Awake since 13:27" as the sleep toggle's state. */
@Composable
private fun sleepStateDescription(state: MainUiState, f: Formatters): String? {
    state.runningSleep?.let { return stringResource(CoreR.string.status_asleep) + " " + f.relative.since(it.startMillis, f.zone) }
    val end = state.lastSleep?.endMillis ?: return null
    return stringResource(CoreR.string.status_awake) + " " + f.relative.since(end, f.zone)
}

/** The sleep toggle as a list button while a breastfeed holds the EdgeButton (V1_ADDITIONS §2.2). */
@Composable
private fun SleepListButton(state: MainUiState, actions: MainActions, transformation: SurfaceTransformation, modifier: Modifier) {
    val f = rememberFormatters(state.zone)
    val asleep = state.runningSleep != null
    val sleep = events().sleep
    val guardText = state.sleepGuard?.let { stringResource(it.edgeText()).replace('\n', ' ') }
    val label = guardText ?: stringResource(if (asleep) CoreR.string.action_woke_up else CoreR.string.action_start_sleep)
    val sleepState = sleepStateDescription(state, f)
    XListButton(
        label = label,
        onClick = actions.onSleepToggle,
        modifier = modifier
            .padding(horizontal = 13.dp)
            .semantics { if (sleepState != null) stateDescription = sleepState },
        icon = if (guardText == null) {
            { XIcon(if (asleep) XIcons.wake else XIcons.sleep, null, tint = iconTint(sleep.strong), size = 24.dp) }
        } else {
            null
        },
        colors = eventButtonColors(sleep, if (asleep) sleep.containerLit else sleep.container),
        border = if (isAmbient()) BorderStroke(2.dp, WatchAmbient.sleepRing) else if (asleep) BorderStroke(1.5.dp, sleep.strong) else null,
        transformation = transformation,
    )
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
)

/**
 * The status cards (§3.5.1–2, §3.5.9): idle shows Awake and the last diaper; asleep shows Last fed and the last
 * diaper; while a breastfeed runs, the running sleep (or Awake) and the last diaper. Each opens Adjust.
 */
@Composable
private fun statusCards(state: MainUiState, f: Formatters): List<CardModel> {
    val events = events()
    val cards = mutableListOf<CardModel>()
    val now = state.now
    val asleepWord = stringResource(CoreR.string.status_asleep)
    val awakeWord = stringResource(CoreR.string.status_awake)
    val lastFedWord = stringResource(CoreR.string.status_last_fed)
    val lastFeedSpoken = stringResource(CoreR.string.spoken_last_feed)
    val lastDiaperSpoken = stringResource(CoreR.string.spoken_last_diaper)
    val bothIcon = rememberBothIcon(events.pee.strong, events.poop.strong)

    if (state.mode == MainMode.FEEDING) {
        state.runningSleep?.let { sleep ->
            cards += CardModel(
                "sleep", sleep, TimeField.START, XIcons.sleep, events.sleep.strong, asleepWord,
                f.durationAndSince(sleep.startMillis, now), f.spokenDurationSince("$asleepWord,", sleep.startMillis, now),
            )
        }
    }
    if (state.mode == MainMode.ASLEEP) {
        state.lastFeed?.let { feed ->
            cards += CardModel(
                "last-feed", feed, TimeField.START, XIcons.forFeedType(feed.feedType ?: FeedType.FORMULA), events.feed.strong,
                lastFedWord, f.agoAndClock(feed.startMillis, now),
                f.spokenAgo(lastFeedSpoken, feedTypeLabel(feed), feed.startMillis, now),
            )
        }
    }
    if (state.runningSleep == null) {
        val lastSleep = state.lastSleep
        val end = lastSleep?.endMillis
        if (lastSleep != null && end != null) {
            cards += CardModel(
                "awake", lastSleep, TimeField.END, XIcons.awake, events.sleep.strong, awakeWord,
                f.durationAndSince(end, now), f.spokenDurationSince(awakeWord, end, now),
            )
        }
    }
    state.lastDiaper?.let { diaper ->
        val kind = diaper.diaperKind ?: DiaperKind.PEE
        val (icon, tint) = when (kind) {
            DiaperKind.PEE -> XIcons.pee to events.pee.strong
            DiaperKind.POOP -> XIcons.poop to events.poop.strong
            DiaperKind.BOTH -> bothIcon to Color.Unspecified
        }
        cards += CardModel(
            "diaper", diaper, TimeField.START, icon, tint, diaperStatusLabel(diaper),
            f.agoAndClock(diaper.startMillis, now),
            f.spokenAgo(lastDiaperSpoken, stringResource(Labels.diaperKind(kind)), diaper.startMillis, now),
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

@WearPreviewDevices
@Composable
private fun MainFeedingPreview() = XWearTheme { MainScreen(WearSamples.mainFeeding(), MainActions()) }
