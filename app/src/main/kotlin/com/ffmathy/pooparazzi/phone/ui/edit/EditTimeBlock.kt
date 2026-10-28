package com.ffmathy.pooparazzi.phone.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.components.ChipDefaults
import com.ffmathy.pooparazzi.phone.ui.history.relativeDayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import com.ffmathy.pooparazzi.core.R as CoreR

/** Edit-sheet geometry (DESIGN_SPEC §3.3.4). */
internal object EditDefaults {
    val Gutter = 24.dp
    val FieldHeight = 80.dp
    val FieldShape = RoundedCornerShape(20.dp)
    val NudgeMinutes = listOf(-5, -1, 1, 5)

    /** Save sits this far above the gesture inset (the mockup's footer at 652–708 of a 796 dp sheet). */
    val FooterBottom = 64.dp
}

/**
 * A time block (DESIGN_SPEC §3.3.4): the label (labelMedium) with the day chip at the end; the 80 dp time field
 * (`clock` 24, the time in displaySmall, "was 12:40" at the end after a change) that opens the time picker; and four
 * equal nudge chips "−5m" "−1m" "+1m" "+5m".
 */
@Composable
internal fun TimeBlock(
    label: String,
    millis: Long,
    was: Long?,
    today: LocalDate,
    zone: ZoneId,
    formatters: Formatters,
    onNudge: (Int) -> Unit,
    onTimeSet: (hour: Int, minute: Int) -> Unit,
    onDaySet: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val day = localDate(millis, zone)
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            DayChip(label, day, today, formatters, onDaySet)
        }
        Spacer(Modifier.height(1.dp))
        TimeField(label, millis, was, zone, formatters, onClick = { picking = true })
        Spacer(Modifier.height(8.dp))
        NudgeRow(label, formatters, onNudge)
    }
    if (picking) {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        TimeDialog(
            title = label,
            hour = time.hour,
            minute = time.minute,
            is24Hour = formatters.clock.is24Hour,
            onConfirm = { h, m ->
                picking = false
                onTimeSet(h, m)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The end block of a running interval: the label, then "Still asleep" in the field's place with an
 * "End now" button at its end. Changing the start keeps the timer running.
 */
@Composable
internal fun RunningBlock(label: String, stillText: String, onEndNow: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(1.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = EditDefaults.FieldHeight)
                .clip(EditDefaults.FieldShape)
                .background(colors.surfaceContainerHigh)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XIcon(XIcons.clock, contentDescription = null, tint = colors.onSurfaceVariant, size = 24.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                stillText,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label, $stillText" },
            )
            OutlinePill(stringResource(R.string.edit_end_now), onClick = onEndNow)
        }
    }
}

/** The 342 × 80 time field: `clock` 24 + 12 + "12:35" displaySmall, "was 12:40" dataMedium at the end. */
@Composable
private fun TimeField(label: String, millis: Long, was: Long?, zone: ZoneId, formatters: Formatters, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val time = formatters.clock.format(millis, zone)
    val wasText = was?.let { stringResource(R.string.edit_was, formatters.clock.format(it, zone)) }
    val spoken = listOfNotNull(label, time, wasText).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = EditDefaults.FieldHeight)
            .clip(EditDefaults.FieldShape)
            .background(colors.surfaceContainerHigh)
            .clickable(onClickLabel = stringResource(R.string.edit_change_time), role = Role.Button, onClick = onClick)
            // The click (above) stays; the texts inside are replaced by one spoken name.
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XIcon(XIcons.clock, contentDescription = null, tint = colors.onSurfaceVariant, size = 24.dp)
        Spacer(Modifier.width(12.dp))
        // The time takes the room "was 12:40" leaves; it shrinks only when that is too little (large font scales).
        BasicText(
            time,
            style = PhoneType.displaySmall.copy(color = colors.onSurface),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = PhoneType.displaySmall.fontSize, stepSize = 2.sp),
            modifier = Modifier.weight(1f),
        )
        if (wasText != null) {
            Spacer(Modifier.width(8.dp))
            Text(wasText, style = PhoneType.dataMedium, color = colors.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** "−5m" "−1m" "+1m" "+5m": four equal chips, gap 8; spoken "Fell asleep, 5 minutes earlier". */
@Composable
private fun NudgeRow(label: String, formatters: Formatters, onNudge: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EditDefaults.NudgeMinutes.forEach { minutes ->
            val spoken = label + ", " + formatters.relative.offsetSpoken(minutes)
            OutlinePill(
                text = formatters.duration.offset(minutes),
                onClick = { onNudge(minutes) },
                spoken = spoken,
                modifier = Modifier.weight(1f),
                fill = true,
            )
        }
    }
}

/**
 * The day chip: "Today ▾" (48 target / 40 pill, outline border, padding 0 12). Its menu offers Today, Yesterday and
 * "Earlier date…" (a date picker up to today).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayChip(blockLabel: String, day: LocalDate, today: LocalDate, formatters: Formatters, onDaySet: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    val name = relativeDayName(day, today, formatters)
    val spoken = stringResource(R.string.edit_spoken_day, blockLabel, name)
    Box {
        Box(
            Modifier
                .heightIn(min = ChipDefaults.TargetHeight)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.edit_change_day)) { menu = true }
                .clearAndSetSemantics { contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .heightIn(min = ChipDefaults.PillHeight)
                    .widthIn(min = 96.dp)
                    .clip(ChipDefaults.PillShape)
                    .border(1.dp, colors.outline, ChipDefaults.PillShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                Text(name, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                XIcon(XIcons.chevronDown, contentDescription = null, tint = colors.onSurfaceVariant, size = 16.dp)
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            DayMenuItem(stringResource(CoreR.string.nav_today), selected = day == today) {
                menu = false
                onDaySet(today)
            }
            DayMenuItem(stringResource(CoreR.string.nav_yesterday), selected = day == today.minusDays(1)) {
                menu = false
                onDaySet(today.minusDays(1))
            }
            DayMenuItem(stringResource(R.string.edit_earlier_date), selected = day < today.minusDays(1)) {
                menu = false
                datePicker = true
            }
        }
    }
    if (datePicker) {
        EarlierDateDialog(
            initial = day,
            today = today,
            onConfirm = {
                datePicker = false
                onDaySet(it)
            },
            onDismiss = { datePicker = false },
        )
    }
}

@Composable
private fun DayMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onSurface) },
        onClick = onClick,
        leadingIcon = {
            Box(Modifier.width(16.dp)) {
                if (selected) XIcon(XIcons.check, contentDescription = null, tint = colors.onSurface, size = 16.dp)
            }
        },
        modifier = Modifier.semantics { this.selected = selected },
    )
}

/** A 48 dp target holding a 40 dp outlined pill (r 20, labelLarge onSurface): nudges and "End now". */
@Composable
internal fun OutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spoken: String? = null,
    fill: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = ChipDefaults.TargetHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = ChipDefaults.PillHeight)
                .clip(ChipDefaults.PillShape)
                .border(1.dp, colors.outline, ChipDefaults.PillShape)
                .padding(horizontal = if (fill) 4.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // A pill with a fixed share of the row (nudges) shrinks its label rather than clip it at large font scales.
            val style = MaterialTheme.typography.labelLarge
            BasicText(
                text,
                style = style.copy(color = colors.onSurface),
                maxLines = 1,
                autoSize = if (fill) TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize, stepSize = 1.sp) else null,
            )
        }
    }
}

/**
 * The M3 time picker, opening on the clock dial with keyboard input one toggle away. Keyboard-first (DESIGN_SPEC
 * §10.4) read as "some picker" with two bare fields and a keyboard on a real phone, so the dial is the default now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    title: String,
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24Hour)
    // TimePickerDisplayMode can't go into a Bundle, so save "keyboard input?" as a Boolean (saving the mode itself
    // crashed the app the moment the dialog opened).
    var keyboard by rememberSaveable { mutableStateOf(false) }
    val mode = if (keyboard) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.action_ok)) } },
        title = { Text(title, style = MaterialTheme.typography.labelMedium) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = { keyboard = !keyboard },
                displayMode = mode,
            )
        },
    ) {
        if (mode == TimePickerDisplayMode.Picker) TimePicker(state) else TimeInput(state)
    }
}

/** "Earlier date…": the M3 date picker, up to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EarlierDateDialog(initial: LocalDate, today: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val lastSelectable = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= lastSelectable
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
        tonalElevation = 0.dp,
    ) {
        DatePicker(state)
    }
}
