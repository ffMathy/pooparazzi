package com.ffmathy.xaternity.wear.today

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.xaternity.core.format.DOT_SEPARATOR
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.format.rememberFormatters
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.clockDurationMillis
import com.ffmathy.xaternity.core.model.isBreastfeed
import com.ffmathy.xaternity.core.model.isInterval
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.ui.icons.EventAvatar
import com.ffmathy.xaternity.core.ui.icons.XAvatars
import com.ffmathy.xaternity.wear.R
import com.ffmathy.xaternity.wear.sample.WearSamples
import com.ffmathy.xaternity.wear.state.TodayUiState
import com.ffmathy.xaternity.wear.theme.WatchAmbient
import com.ffmathy.xaternity.wear.theme.XWearTheme
import com.ffmathy.xaternity.wear.theme.isAmbient
import com.ffmathy.xaternity.wear.ui.AutoSizeLine
import com.ffmathy.xaternity.wear.ui.ScreenTitle
import com.ffmathy.xaternity.wear.ui.WatchShapes
import com.ffmathy.xaternity.wear.ui.ambientBorder
import com.ffmathy.xaternity.wear.ui.entryWord
import com.ffmathy.xaternity.wear.ui.keepSidesTogether
import com.ffmathy.xaternity.wear.ui.tonalButtonColors
import com.ffmathy.xaternity.core.R as CoreR

/**
 * Today (DESIGN_SPEC §3.5.8): "Today" and today's entries newest first, each with its shape-coded avatar (32 dp),
 * a title and an absolute time (watch ranges without spaces). A row opens Adjust for that entry. The stock
 * collapsing EdgeButton "More on phone" opens phone History, when the phone can be reached.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    phoneAvailable: Boolean,
    onRow: (Entry) -> Unit,
    onMoreOnPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    if (phoneAvailable) {
        ScreenScaffold(
            scrollState = listState,
            modifier = modifier,
            edgeButton = { MoreOnPhoneButton(onMoreOnPhone) },
        ) { contentPadding -> TodayList(state, listState, spec, contentPadding, onRow) }
    } else {
        ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
            TodayList(state, listState, spec, contentPadding, onRow)
        }
    }
}

@Composable
private fun TodayList(
    state: TodayUiState,
    listState: TransformingLazyColumnState,
    spec: TransformationSpec,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onRow: (Entry) -> Unit,
) {
    val f = rememberFormatters(state.zone)
    val ambient = isAmbient()
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
        item(key = "header") {
            ScreenTitle(stringResource(CoreR.string.nav_today), Modifier.transformedHeight(this, spec))
        }
        if (state.loaded && state.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.today_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).transformedHeight(this, spec),
                )
            }
        }
        state.entries.forEach { entry ->
            item(key = entry.id) {
                TodayRow(
                    entry = entry,
                    state = state,
                    f = f,
                    onClick = { onRow(entry) },
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier.transformedHeight(this, spec),
                )
            }
        }
    }
}

/** A Today row (§3.5.8): button ≥ 60 dp, r 26, `surfaceContainer`; avatar 32, label over the secondary line. */
@Composable
private fun TodayRow(
    entry: Entry,
    state: TodayUiState,
    f: Formatters,
    onClick: () -> Unit,
    transformation: SurfaceTransformation,
    modifier: Modifier,
) {
    val ambient = isAmbient()
    val title = rowTitle(entry, state.now, f)
    val supporting = f.labels.historySupporting(
        entry, state.day, f.zone, state.now, f.clock, f.duration, f.relative, spacedRange = false,
    )
    val spoken = rowSpoken(entry, state, f, supporting) + ". " + stringResource(R.string.action_adjust)
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .semantics { contentDescription = spoken },
        shape = WatchShapes.Button,
        colors = tonalButtonColors(),
        border = ambientBorder(MaterialTheme.colorScheme.outline),
        transformation = transformation,
        icon = { EventAvatar(entry, Modifier.clearAndSetSemantics { }, size = XAvatars.WatchSize) },
        label = {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto),
                maxLines = 2,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        secondaryLabel = {
            AutoSizeLine(
                text = supporting,
                style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Start),
                color = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                minSize = 11,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
    )
}

/** History row titles (V1_ADDITIONS §4), with the compact (hyphenatable) Formula word for the narrow rows. */
@Composable
private fun rowTitle(entry: Entry, now: Long, f: Formatters): String =
    if (entry.kind == EntryKind.FEED && !entry.isBreastfeed) entryWord(entry, compact = true)
    else f.labels.historyTitle(entry, f.duration, now).keepSidesTogether()

/** The row read aloud: words for durations (§9.5) and commas between facts. */
@Composable
private fun rowSpoken(entry: Entry, state: TodayUiState, f: Formatters, supporting: String): String {
    val parts = mutableListOf(entryWord(entry))
    when {
        entry.isRunning -> {
            parts += stringResource(CoreR.string.status_running)
            parts += f.relative.since(entry.startMillis, f.zone)
        }
        entry.isInterval -> {
            entry.breastSide?.let { parts += stringResource(Labels.side(it)) }
            parts += f.duration.spoken(clockDurationMillis(entry.startMillis, entry.endMillis ?: state.now))
            parts += f.clock.rangeOnDay(entry.startMillis, entry.endMillis ?: state.now, state.day, f.zone, spaced = false)
        }
        else -> parts += supporting.split(DOT_SEPARATOR)
    }
    return parts.joinToString(", ")
}

/** "More on phone": the stock collapsing EdgeButton (collapsed at the top of the list). */
@Composable
private fun MoreOnPhoneButton(onClick: () -> Unit) {
    val ambient = isAmbient()
    val scheme = MaterialTheme.colorScheme
    EdgeButton(
        onClick = onClick,
        buttonSize = EdgeButtonSize.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (ambient) Color.Transparent else scheme.primaryContainer,
            contentColor = if (ambient) WatchAmbient.content else scheme.onPrimaryContainer,
        ),
        border = ambientBorder(scheme.outline),
    ) {
        Text(
            stringResource(R.string.action_more_on_phone),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@WearPreviewDevices
@Composable
private fun TodayPreview() = XWearTheme { TodayScreen(WearSamples.today(), phoneAvailable = true, onRow = {}, onMoreOnPhone = {}) }
