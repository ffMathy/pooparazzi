package com.ffmathy.xaternity.phone.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ffmathy.xaternity.core.domain.DayCounts
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.rememberFormatters
import com.ffmathy.xaternity.core.sync.SyncStatus
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.ui.components.IconTarget
import com.ffmathy.xaternity.phone.ui.components.SyncLine
import com.ffmathy.xaternity.phone.ui.components.XTopBar
import com.ffmathy.xaternity.phone.ui.components.syncLineText
import com.ffmathy.xaternity.phone.ui.edit.EditSheet
import com.ffmathy.xaternity.phone.ui.edit.EditViewModel
import com.ffmathy.xaternity.phone.ui.edit.rememberEditEffects
import com.ffmathy.xaternity.phone.ui.home.LARGE_FONT_SCALE
import com.ffmathy.xaternity.phone.ui.preview.HistorySamples
import com.ffmathy.xaternity.phone.ui.theme.NightLightTheme
import java.time.ZoneId
import com.ffmathy.xaternity.core.R as CoreR

/** Layout constants (DESIGN_SPEC §3.3.3). */
internal object HistoryDefaults {
    val Gutter = 20.dp
    val FabHeight = 56.dp
    val FabShape = RoundedCornerShape(16.dp)
    val FabMargin = 16.dp
}

/** History wired to its view models: the day, deletes with Undo, and the edit sheet (edit a row, add a past entry). */
@Composable
fun HistoryRoute(onBack: () -> Unit) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
    val editViewModel: EditViewModel = viewModel(factory = EditViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val edit by editViewModel.state.collectAsStateWithLifecycle()
    rememberEditEffects(editViewModel, onDeleted = viewModel::onEntryDeleted)
    val back by rememberUpdatedState(onBack)
    val actions = remember(viewModel, editViewModel) {
        object : HistoryActions by viewModel {
            override fun onBack() = back()
            override fun onEdit(entryId: String) = editViewModel.openEntry(entryId)
            override fun onAddEntry() = editViewModel.openNew(viewModel.addEntryDefaultTime())
        }
    }
    val loaded = state
    if (loaded != null) {
        HistoryScreen(loaded, actions)
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
    EditSheet(edit, editViewModel)
}

/**
 * History (P3, DESIGN_SPEC §3.3.3, V1_ADDITIONS §4): the top bar with the sync line, the day switcher, the day ribbon,
 * the stats, and the day's entries newest first. Rows open the edit sheet and swipe to reveal Delete (with an Undo
 * snackbar); the "Add entry" FAB adds a past entry. Laid out edge to edge from [windowInsets].
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    actions: HistoryActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = MaterialTheme.colorScheme
    val formatters = rememberFormatters(state.zone)
    val density = LocalDensity.current
    val bottomInset = with(density) { windowInsets.getBottom(this).toDp() }
    val snackbars = remember { SnackbarHostState() }
    ReceiptSnackbar(state.receipt, snackbars, actions)
    val listState = rememberLazyListState()
    // Another day starts at its top.
    LaunchedEffect(state.day) { if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0) }

    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            HistoryTopBar(state.sync, state.zone, formatters, actions)
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                state = listState,
                contentPadding = PaddingValues(
                    start = HistoryDefaults.Gutter,
                    end = HistoryDefaults.Gutter,
                    top = 8.dp,
                    bottom = bottomInset + HistoryDefaults.FabMargin * 2 + HistoryDefaults.FabHeight,
                ),
            ) {
                item(key = "switcher") { DaySwitcher(state, formatters, actions) }
                item(key = "ribbon") {
                    val dayName = relativeDayName(state.day, state.today, formatters)
                    HistoryRibbon(
                        ribbon = state.ribbon,
                        zone = state.zone,
                        summary = countsSpoken(dayName, state.counts, formatters),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item(key = "stats") { Stats(state.counts, formatters, Modifier.padding(top = 12.dp)) }
                item(key = "heading") {
                    Text(
                        relativeDayName(state.day, state.today, formatters),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 20.dp, bottom = 10.dp)
                            .semantics { heading() },
                    )
                }
                if (state.entries.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
                    HistoryRow(
                        entry = entry,
                        title = historyTitle(entry, state.now, formatters),
                        supporting = historySupporting(entry, state.day, state.zone, state.now, formatters),
                        spoken = historySpoken(entry, state.day, state.zone, state.now, formatters),
                        first = index == 0,
                        last = index == state.entries.lastIndex,
                        onEdit = { actions.onEdit(entry.id) },
                        onDelete = { actions.onDelete(entry.id) },
                        modifier = Modifier
                            .animateItem()
                            .padding(bottom = if (index == state.entries.lastIndex) 0.dp else HistoryRowDefaults.Gap),
                    )
                }
            }
        }
        SnackbarHost(
            snackbars,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + HistoryDefaults.FabMargin * 2 + HistoryDefaults.FabHeight)
                .padding(horizontal = HistoryDefaults.Gutter - 8.dp),
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurface,
                actionColor = colors.primary,
                actionContentColor = colors.primary,
            )
        }
        AddEntryFab(
            onClick = actions::onAddEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(end = HistoryDefaults.Gutter, bottom = HistoryDefaults.FabMargin),
        )
    }
}

/** Back, "History" and the sync line (a 48 dp retry target in the error state). */
@Composable
private fun HistoryTopBar(sync: SyncStatus, zone: ZoneId, formatters: Formatters, actions: HistoryActions) {
    XTopBar(
        title = stringResource(CoreR.string.nav_history),
        navigationIcon = XIcons.back,
        navigationLabel = stringResource(R.string.cd_back),
        onNavigate = actions::onBack,
    ) {
        if (sync is SyncStatus.Error) {
            val label = syncLineText(sync, zone, formatters).orEmpty()
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = actions::onRetrySync)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.CenterStart,
            ) { SyncLine(sync, zone, formatters) }
        } else {
            SyncLine(sync, zone, formatters)
        }
    }
}

/** ‹ "Today · Mon 26 Oct" › — "Next day" is disabled on today (outlineVariant). */
@Composable
private fun DaySwitcher(state: HistoryUiState, formatters: Formatters, actions: HistoryActions) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTarget(
            XIcons.chevronLeft,
            stringResource(R.string.history_previous_day),
            actions::onPreviousDay,
            enabled = state.canGoBack,
            tint = if (state.canGoBack) colors.onSurfaceVariant else colors.outlineVariant,
        )
        Text(
            dayLabel(state.day, state.today, formatters),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconTarget(
            XIcons.chevronRight,
            stringResource(R.string.history_next_day),
            actions::onNextDay,
            enabled = state.canGoForward,
            tint = if (state.canGoForward) colors.onSurfaceVariant else colors.outlineVariant,
        )
    }
}

/** feeds · pee · poop · sleep: numbers titleLarge, captions labelSmall (4 equal columns, 16 inset). */
@Composable
private fun Stats(counts: DayCounts, formatters: Formatters, modifier: Modifier = Modifier) {
    val labels = formatters.labels
    val sleep = counts.sleepMinutes * MINUTE_MILLIS
    val stats = listOf(
        Triple(counts.feeds.toString(), labels.feedsCaption(counts.feeds), null),
        Triple(counts.pee.toString(), labels.peeCaption(), null),
        Triple(counts.poop.toString(), labels.poopCaption(), null),
        Triple(formatters.duration.format(sleep), labels.sleepCaption(), formatters.duration.spoken(sleep)),
    )
    // Four columns; two rows of two at large font scales (as Home's Today grid), so "7h 57m" stays on screen.
    val columns = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else 4
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (number, caption, spoken) -> Stat(number, caption, spoken, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Stat(number: String, caption: String, spokenNumber: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.semantics(mergeDescendants = true) { spokenNumber?.let { contentDescription = "$it $caption" } },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // "7h 57m" is wider than its quarter of the row: like the mockup's grid, it may run into the gutter.
        Text(
            number,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
        )
        Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 2)
    }
}

/** "Add entry": 56 tall, r 16, primaryContainer, `plus` 24 + 8 + labelLarge onPrimaryContainer. No shadow (§5.9). */
@Composable
private fun AddEntryFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = HistoryDefaults.FabHeight)
            .clip(HistoryDefaults.FabShape)
            .background(colors.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XIcon(XIcons.plus, contentDescription = null, tint = colors.onPrimaryContainer, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.history_add_entry), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
    }
}

/** "Pee deleted · Undo" for the last delete; the result goes back to the view model. */
@Composable
private fun ReceiptSnackbar(receipt: DeletedReceipt?, host: SnackbarHostState, actions: HistoryActions) {
    receipt ?: return
    val message = stringResource(CoreR.string.receipt_deleted, deletedLabel(receipt.entry))
    val undo = stringResource(CoreR.string.action_undo)
    val current by rememberUpdatedState(actions)
    LaunchedEffect(receipt.key) {
        val result = host.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) current.onUndoDelete(receipt) else current.onReceiptDismissed(receipt)
    }
}

@Preview(name = "Day · 14:32", widthDp = 390, heightDp = 1340)
@Composable
private fun HistoryDayPreview() {
    NightLightTheme(night = false) { HistoryScreen(HistorySamples.today(), HistoryActions.None) }
}

@Preview(name = "Night · 14:32", widthDp = 390, heightDp = 844)
@Composable
private fun HistoryNightPreview() {
    NightLightTheme(night = true) { HistoryScreen(HistorySamples.today(), HistoryActions.None) }
}

@Preview(name = "Night · yesterday · Danish", widthDp = 390, heightDp = 844, locale = "da")
@Composable
private fun HistoryYesterdayPreview() {
    NightLightTheme(night = true) { HistoryScreen(HistorySamples.yesterday(), HistoryActions.None) }
}
