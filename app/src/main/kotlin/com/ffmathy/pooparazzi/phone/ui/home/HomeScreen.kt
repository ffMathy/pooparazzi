package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.notify.rememberNotificationPermissionPrompt
import com.ffmathy.pooparazzi.phone.ui.edit.EditSheet
import com.ffmathy.pooparazzi.phone.ui.edit.EditViewModel
import com.ffmathy.pooparazzi.phone.ui.edit.rememberEditEffects
import com.ffmathy.pooparazzi.phone.ui.preview.HomeSamples
import com.ffmathy.pooparazzi.phone.ui.sheets.BabySwitcherUi
import com.ffmathy.pooparazzi.phone.ui.sheets.HomeSheets
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLight
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme

/** Phone layout constants (DESIGN_SPEC §3.3.1). */
internal object HomeDefaults {
    val Gutter = 20.dp
}

/**
 * Home wired to its view model and to navigation. Since-rows and the Adjust card's Edit chip open the edit sheet over
 * Home; a delete there shows the "… deleted" card with Undo.
 *
 * [launch] is a deep link from a widget or notification (DESIGN_SPEC §3.1): Home opens the sheet or undo card it asks
 * for and reports it through [onLaunchHandled]; when such a sheet closes, [onLaunchedSheetClosed]. The first sleep started here asks for the notification permission, so
 * the running sleep can show its ongoing notification (§3.3.1, §10.5).
 */
@Composable
fun HomeRoute(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    launch: HomeLaunch? = null,
    onLaunchHandled: (HomeLaunch) -> Unit = {},
    onLaunchedSheetClosed: () -> Unit = {},
) {
    val accessibility = LocalAccessibilityManager.current
    val timings = remember(accessibility) {
        val window = HomeTimings().undoWindowMillis
        HomeTimings(
            undoWindowMillis = accessibility?.calculateRecommendedTimeoutMillis(
                window,
                containsIcons = true,
                containsText = true,
                containsControls = true,
            ) ?: window,
        )
    }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(timings))
    val editViewModel: EditViewModel = viewModel(factory = EditViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val edit by editViewModel.state.collectAsStateWithLifecycle()
    rememberEditEffects(editViewModel, onDeleted = viewModel::onEntryDeleted)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel, haptics) {
        viewModel.effects.collect { haptics.performHapticFeedback(it.feedbackType()) }
    }
    val askForNotifications = rememberNotificationPermissionPrompt()
    LaunchedEffect(viewModel) {
        viewModel.timerStarted.collect { askForNotifications() }
    }
    val launchedSheetClosed by rememberUpdatedState(onLaunchedSheetClosed)
    LaunchedEffect(viewModel) {
        viewModel.launchedSheetClosed.collect { launchedSheetClosed() }
    }
    val handled by rememberUpdatedState(onLaunchHandled)
    LaunchedEffect(launch) {
        if (launch == null) return@LaunchedEffect
        // A sheet from a widget or notification replaces the edit sheet rather than stacking on it.
        if (launch.opensSheet) editViewModel.onDismiss()
        viewModel.onLaunch(launch)
        handled(launch)
    }
    val history by rememberUpdatedState(onOpenHistory)
    val settings by rememberUpdatedState(onOpenSettings)
    val actions = remember(viewModel, editViewModel) {
        object : HomeActions by viewModel {
            override fun onOpenHistory() = history()
            override fun onOpenSettings() = settings()
            override fun onEditEntry(entryId: String) = editViewModel.openEntry(entryId)
        }
    }
    val loaded = state
    if (loaded != null) {
        HomeScreen(loaded, actions)
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
    EditSheet(edit, editViewModel)
}

/** The Compose haptic for a [HomeHaptic] (also used by the edit sheet). */
internal fun HomeHaptic.feedbackType(): HapticFeedbackType = when (this) {
    HomeHaptic.CONFIRM -> HapticFeedbackType.Confirm
    HomeHaptic.TOGGLE_ON -> HapticFeedbackType.ToggleOn
    HomeHaptic.TOGGLE_OFF -> HapticFeedbackType.ToggleOff
    HomeHaptic.REJECT -> HapticFeedbackType.Reject
    HomeHaptic.SEGMENT_TICK -> HapticFeedbackType.SegmentTick
}

/**
 * The area above the buttons: [top] (top bar), the Lamp and [below] (since-rows and the whisper slot). The Lamp takes
 * the height the others leave in [viewport], up to [maxLampSize] and down to [HomeLampDefaults.floor], so a short
 * screen or a larger font shrinks the Lamp before anything has to scroll. Below the floor the area scrolls.
 */
@Composable
private fun HomeUpperArea(
    viewport: Dp,
    maxLampSize: Dp,
    top: @Composable () -> Unit,
    lamp: @Composable (Dp) -> Unit,
    below: @Composable () -> Unit,
) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val tops = subcompose(UpperSlot.TOP, top).map { it.measure(loose) }
        val belows = subcompose(UpperSlot.BELOW, below).map { it.measure(loose) }
        val topHeight = tops.sumOf { it.height }
        val belowHeight = belows.sumOf { it.height }
        val room = (viewport.roundToPx() - topHeight - belowHeight).toDp()
        val floor = HomeLampDefaults.floor(fontScale)
        val size = room.coerceIn(floor, maxLampSize.coerceAtLeast(floor))
        val lamps = subcompose(UpperSlot.LAMP) { lamp(size) }.map { it.measure(loose) }
        val lampHeight = lamps.maxOfOrNull { it.height } ?: 0
        val width = constraints.maxWidth
        layout(width, topHeight + lampHeight + belowHeight) {
            // The glow reaches past the Lamp: draw it behind the top bar and rows.
            lamps.forEach { it.placeRelative((width - it.width) / 2, topHeight, zIndex = -1f) }
            var y = 0
            tops.forEach { it.placeRelative(0, y); y += it.height }
            y = topHeight + lampHeight
            belows.forEach { it.placeRelative(0, y); y += it.height }
        }
    }
}

private enum class UpperSlot { TOP, LAMP, BELOW }

/**
 * Home (P1, DESIGN_SPEC §3.3.1): top bar, the Lamp, two since-rows, the whisper slot, Feed · Diaper and the Sleep
 * toggle, plus the logging sheets. Laid out from [windowInsets] (edge to edge): the area above the button block
 * scrolls when it does not fit (200 % font scale), and the button block stays pinned above the navigation inset.
 *
 * Lights-down (Night and something running) dims only the chrome: the name, sync line, Settings, the History pill
 * and the Today numbers (§8.2).
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val formatters = rememberFormatters(state.zone)
    val lightsDown = LocalNightLight.current.isNight && state.anythingRunning
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val insetHeight = with(density) { (windowInsets.getTop(this) + windowInsets.getBottom(this)).toDp() }
        val lampSize = HomeLampDefaults.size(maxHeight - insetHeight)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets),
        ) {
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val viewport = maxHeight
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    HomeUpperArea(
                        viewport = viewport,
                        maxLampSize = lampSize,
                        top = {
                            Column {
                                HomeTopBar(
                                    state.babyName, state.sync, state.zone, lightsDown, formatters, actions,
                                    babies = state.babies,
                                    babyId = state.babyId,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        },
                        lamp = { size ->
                            HomeLamp(
                                lamp = state.lamp,
                                zone = state.zone,
                                size = size,
                                formatters = formatters,
                                onAdjustStart = actions::onAdjustStart,
                            )
                        },
                        below = {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                SinceRows(
                                    row1 = state.row1,
                                    row2 = state.row2,
                                    zone = state.zone,
                                    formatters = formatters,
                                    onEdit = actions::onEditEntry,
                                    modifier = Modifier.padding(horizontal = HomeDefaults.Gutter),
                                )
                                Spacer(Modifier.height(4.dp))
                                WhisperSlot(
                                    card = state.card,
                                    today = state.today,
                                    lightsDown = lightsDown,
                                    zone = state.zone,
                                    formatters = formatters,
                                    actions = actions,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = HomeDefaults.Gutter),
                                )
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HomeButtons(
                state = state,
                formatters = formatters,
                actions = actions,
                modifier = Modifier.padding(horizontal = HomeDefaults.Gutter),
                // A short screen: the Lamp is already at its minimum, so the buttons give a little room too.
                compact = lampSize <= HomeLampDefaults.Min,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    HomeSheets(
        sheet = state.sheet,
        switcher = if (state.switchingBaby) BabySwitcherUi(state.babies, state.babyId) else null,
        zone = state.zone,
        formatters = formatters,
        actions = actions,
        windowInsets = windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
    )
}

@Preview(name = "Night · idle 14:32", widthDp = 390, heightDp = 844)
@Composable
private fun HomeNightPreview() {
    NightLightTheme(night = true) { HomeScreen(HomeSamples.idle(), HomeActions.None) }
}

@Preview(name = "Day · idle 14:32", widthDp = 390, heightDp = 844)
@Composable
private fun HomeDayPreview() {
    NightLightTheme(night = false) { HomeScreen(HomeSamples.idle(), HomeActions.None) }
}

@Preview(name = "Night · asleep 03:12", widthDp = 390, heightDp = 844)
@Composable
private fun HomeAsleepPreview() {
    NightLightTheme(night = true) { HomeScreen(HomeSamples.asleep(), HomeActions.None) }
}

@Preview(name = "Day · breast feed logged 14:50", widthDp = 390, heightDp = 844)
@Composable
private fun HomeBreastLoggedPreview() {
    NightLightTheme(night = false) { HomeScreen(HomeSamples.breastLogged(), HomeActions.None) }
}

@Preview(name = "Night · undo card", widthDp = 390, heightDp = 844)
@Composable
private fun HomeUndoCardPreview() {
    NightLightTheme(night = true) { HomeScreen(HomeSamples.justLogged(), HomeActions.None) }
}

@Preview(name = "Night · Danish", widthDp = 390, heightDp = 844, locale = "da")
@Composable
private fun HomeDanishPreview() {
    NightLightTheme(night = true) { HomeScreen(HomeSamples.idle(), HomeActions.None) }
}
