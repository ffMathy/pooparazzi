package com.ffmathy.xaternity.wear.adjust

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.xaternity.core.format.DOT_SEPARATOR
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.rememberFormatters
import com.ffmathy.xaternity.core.model.isBreastfeed
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.wear.R
import com.ffmathy.xaternity.wear.sample.WearSamples
import com.ffmathy.xaternity.wear.state.AdjustGroup
import com.ffmathy.xaternity.wear.state.AdjustUiState
import com.ffmathy.xaternity.wear.state.SetTimeUiState
import com.ffmathy.xaternity.wear.state.TimeField
import com.ffmathy.xaternity.wear.theme.WatchAmbient
import com.ffmathy.xaternity.wear.theme.XWearTheme
import com.ffmathy.xaternity.wear.theme.isAmbient
import com.ffmathy.xaternity.wear.ui.ScreenTitle
import com.ffmathy.xaternity.wear.ui.WatchShapes
import com.ffmathy.xaternity.wear.ui.XCompactButton
import com.ffmathy.xaternity.wear.ui.ambientBorder
import com.ffmathy.xaternity.wear.ui.entryWord
import com.ffmathy.xaternity.wear.ui.rememberCooldown
import com.ffmathy.xaternity.wear.ui.spokenOffset
import com.ffmathy.xaternity.wear.ui.spokenText
import com.ffmathy.xaternity.wear.ui.tonalButtonColors
import java.time.Instant
import com.ffmathy.xaternity.core.R as CoreR

@Immutable
data class AdjustActions(
    /** A choice: move [field] to [at], the time the button shows (absolute, so a repeat never adds up). */
    val onOption: (field: TimeField, at: Long) -> Unit = { _, _ -> },
    val onSetTime: (field: TimeField) -> Unit = {},
)

/**
 * Adjust (DESIGN_SPEC §3.5.5): tonal buttons "−15m · 01:50", "−5m · 02:00", "+5m · 02:10" relative to the entry's
 * time; a choice that would land in the future (or put an end before its start) is disabled. A completed interval
 * opened from Today shows its start and its end; Today also adds "Set time". Swipe right cancels. 500 ms cooldown.
 */
@Composable
fun AdjustScreen(state: AdjustUiState, actions: AdjustActions, modifier: Modifier = Modifier) {
    val f = rememberFormatters(state.zone)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    val single = state.groups.size == 1
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "title") { ScreenTitle(adjustTitle(state, f), Modifier.transformedHeight(this, spec)) }
            state.groups.forEach { group ->
                if (!single) {
                    item(key = "header-${group.field}") {
                        Text(
                            groupHeader(group, f),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAmbient()) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).transformedHeight(this, spec),
                        )
                    }
                }
                group.options.forEach { option ->
                    item(key = "${group.field}${option.minutes}") {
                        val label = f.duration.offset(option.minutes) + DOT_SEPARATOR + f.clock.format(option.at, f.zone)
                        val spoken = f.spokenOffset(option.minutes, option.at)
                        Button(
                            onClick = { actions.onOption(group.field, option.at) },
                            enabled = option.enabled && cooldown.ready,
                            shape = WatchShapes.Button,
                            colors = tonalButtonColors(),
                            border = ambientBorder(MaterialTheme.colorScheme.outline),
                            transformation = SurfaceTransformation(spec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .transformedHeight(this, spec)
                                .alpha(if (option.enabled) cooldown.alpha else 1f)
                                .semantics { contentDescription = spoken },
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                                )
                            },
                        )
                    }
                }
                if (state.allowSetTime) {
                    item(key = "set-${group.field}") {
                        Box(Modifier.fillMaxWidth().transformedHeight(this, spec), contentAlignment = Alignment.Center) {
                            XCompactButton(
                                label = stringResource(R.string.action_set_time),
                                icon = XIcons.clock,
                                onClick = { actions.onSetTime(group.field) },
                                enabled = cooldown.ready,
                                contentDescription = spokenText().pair(stringResource(R.string.action_set_time), groupHeader(group, f)),
                                modifier = Modifier.alpha(cooldown.alpha),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Adjust start" for a running interval; "Pee · 13:44" for an instant; "Woke up · 13:27" / "Feed ended · 08:14" for
 * an end; the entry word ("Sleep") above Start / End groups.
 */
@Composable
private fun adjustTitle(state: AdjustUiState, f: Formatters): String {
    val entry = state.entry
    val group = state.groups.singleOrNull() ?: return entryWord(entry)
    return when {
        group.field == TimeField.START && entry.isRunning -> stringResource(CoreR.string.action_adjust_start)
        group.field == TimeField.START && entry.endMillis == null -> entryWord(entry) + DOT_SEPARATOR + f.clock.format(group.base, f.zone)
        group.field == TimeField.START -> stringResource(R.string.adjust_start_at, f.clock.format(group.base, f.zone))
        entry.isBreastfeed -> stringResource(CoreR.string.receipt_feed_ended) + DOT_SEPARATOR + f.clock.format(group.base, f.zone)
        else -> stringResource(CoreR.string.action_woke_up) + DOT_SEPARATOR + f.clock.format(group.base, f.zone)
    }
}

@Composable
private fun groupHeader(group: AdjustGroup, f: Formatters): String = stringResource(
    if (group.field == TimeField.START) R.string.adjust_start_at else R.string.adjust_end_at,
    f.clock.format(group.base, f.zone),
)

/**
 * Set time (DESIGN_SPEC §3.5.5): the Wear time picker, 24 h when the clock is 24 h (always in Danish), starting at
 * the current value. The picked time lands on the value's day, or the day before when that would be in the future.
 */
@Composable
fun SetTimeScreen(state: SetTimeUiState, onPicked: (java.time.LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val f = rememberFormatters(state.zone)
    val initial = Instant.ofEpochMilli(state.base).atZone(state.zone).toLocalTime().withSecond(0).withNano(0)
    TimePicker(
        initialTime = initial,
        onTimePicked = onPicked,
        modifier = modifier,
        timePickerType = if (f.clock.is24Hour) TimePickerType.HoursMinutes24H else TimePickerType.HoursMinutesAmPm12H,
    )
}

@WearPreviewDevices
@Composable
private fun AdjustPreview() = XWearTheme { AdjustScreen(WearSamples.adjustRunningSleep(), AdjustActions()) }
