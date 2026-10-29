package com.ffmathy.pooparazzi.wear.today

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
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
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
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.isBreastfeed
import com.ffmathy.pooparazzi.core.model.isInterval
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.ui.icons.EventAvatar
import com.ffmathy.pooparazzi.core.ui.icons.XAvatars
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.TodayUiState
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.AutoSizeLine
import com.ffmathy.pooparazzi.wear.ui.ScreenTitle
import com.ffmathy.pooparazzi.wear.ui.SpokenLabels
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
import com.ffmathy.pooparazzi.wear.ui.ambientBorder
import com.ffmathy.pooparazzi.wear.ui.entryWord
import com.ffmathy.pooparazzi.wear.ui.keepSidesTogether
import com.ffmathy.pooparazzi.wear.ui.spokenEntryWord
import com.ffmathy.pooparazzi.wear.ui.spokenText
import com.ffmathy.pooparazzi.wear.ui.tonalButtonColors
import com.ffmathy.pooparazzi.core.R as CoreR

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
    TransformingLazyColumn(
        state = listState,
        contentPadding = contentPadding,
        // Comes to rest on a row, never half under the time.
        flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
    ) {
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
        entry, state.day, f.zone, state.now, f.clock, f.duration, f.relative, spacedRange = false, volume = f.volume,
    )
    val spoken = spokenText().thenAction(rowSpoken(entry, state, f, supporting), stringResource(R.string.action_adjust))
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
            RowTitle(title, MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto), Modifier.clearAndSetSemantics { })
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

/**
 * A row title on one line when it fits; otherwise broken at its first "·" with the separator dropped ("Breast" /
 * "Left → Right"), so it never dangles at a line end ("Breast ·" / "Left → Right"). Both forms are laid out and the
 * one that fits is placed, so the buttons' intrinsic measurement keeps working.
 */
@Composable
private fun RowTitle(title: String, style: TextStyle, modifier: Modifier) {
    val split = title.indexOf(DOT_SEPARATOR)
    if (split < 0) {
        Text(title, style = style, maxLines = 2, modifier = modifier)
        return
    }
    val broken = title.substring(0, split) + "\n" + title.substring(split + DOT_SEPARATOR.length)
    Layout(
        contents = listOf(
            { Text(title, style = style, maxLines = 1, softWrap = false) },
            { Text(broken, style = style, maxLines = 2) },
        ),
        modifier = modifier,
        measurePolicy = OneLineOrBroken,
    )
}

/** Places the first content (one line) when its full width fits, otherwise the second (broken at the separator). */
private object OneLineOrBroken : MultiContentMeasurePolicy {
    private fun <T : IntrinsicMeasurable> choose(contents: List<List<T>>, maxWidth: Int): T {
        val oneLine = contents[0].single()
        return if (oneLine.maxIntrinsicWidth(Constraints.Infinity) <= maxWidth) oneLine else contents[1].single()
    }

    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val placeable = choose(measurables, constraints.maxWidth).measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        choose(measurables, width).maxIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        choose(measurables, width).minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int =
        measurables[0].single().maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int =
        measurables[1].single().minIntrinsicWidth(height)
}

/** The row read aloud: words for durations (§9.5), sides and ranges in words, facts joined by the templates. */
@Composable
private fun rowSpoken(entry: Entry, state: TodayUiState, f: Formatters, supporting: String): String {
    val spoken = spokenText()
    val parts = mutableListOf(spokenEntryWord(entry))
    when {
        entry.isRunning -> {
            parts += stringResource(CoreR.string.status_running)
            parts += f.relative.since(entry.startMillis, f.zone)
        }
        entry.isInterval -> {
            parts += f.duration.spoken(clockDurationMillis(entry.startMillis, entry.endMillis ?: state.now))
            parts += spoken.range(f, entry.startMillis, entry.endMillis ?: state.now, state.day)
        }
        else -> {
            // A breast feed: "Breast, left then right, 01:40".
            if (entry.isBreastfeed) entry.breastSide?.let { parts += stringResource(SpokenLabels.side(it)) }
            parts += supporting.split(DOT_SEPARATOR)
        }
    }
    return spoken.all(parts)
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
