package com.ffmathy.xaternity.phone.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.rememberFormatters
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.domain.localDate
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.tokens.LocalXPalette
import com.ffmathy.xaternity.core.ui.type.PhoneType
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.ui.components.ConnectedChoiceGroup
import com.ffmathy.xaternity.phone.ui.components.IconTarget
import com.ffmathy.xaternity.phone.ui.history.dayLabel
import com.ffmathy.xaternity.phone.ui.history.spokenSide
import com.ffmathy.xaternity.phone.ui.preview.EditSamples
import com.ffmathy.xaternity.phone.ui.home.feedbackType
import com.ffmathy.xaternity.phone.ui.sheets.DiaperToggleTiles
import com.ffmathy.xaternity.phone.ui.sheets.PoopDetails
import com.ffmathy.xaternity.phone.ui.sheets.SaveButton
import com.ffmathy.xaternity.phone.ui.sheets.SectionHeading
import com.ffmathy.xaternity.phone.ui.sheets.XModalSheet
import com.ffmathy.xaternity.phone.ui.theme.NightLightTheme
import com.ffmathy.xaternity.core.R as CoreR

/**
 * Collects [viewModel]'s haptics and results while the hosting screen is shown. [onDeleted] receives the entry as it
 * was before a delete from the sheet, so the host can offer Undo.
 */
@Composable
fun rememberEditEffects(viewModel: EditViewModel, onDeleted: (Entry) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val deleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(viewModel, haptics) {
        viewModel.haptics.collect { haptics.performHapticFeedback(it.feedbackType()) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event is EditEvent.Deleted) deleted(event.previous) }
    }
}

/**
 * The edit sheet (P4, DESIGN_SPEC §3.3.4, V1_ADDITIONS §4): a full-height modal sheet starting 24 below the status bar,
 * open while [state] is non-null. It edits or deletes a sleep, feed or diaper, or adds a past entry.
 *
 * @param windowInsets the screen's insets: the sheet stops 24 dp below their top and keeps Save above their bottom.
 */
@Composable
fun EditSheet(state: EditUiState?, actions: EditActions, windowInsets: WindowInsets = WindowInsets.safeDrawing) {
    XModalSheet(
        value = state,
        onDismissRequest = actions::onDismiss,
        onClosed = actions::onClosed,
        windowInsets = windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        sheetModifier = Modifier
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top))
            .padding(top = 24.dp),
        handleTop = 10.dp,
    ) { shown ->
        EditSheetContent(shown, actions)
    }
}

/** The sheet's content, without the modal window (previews). */
@Composable
internal fun ColumnScope.EditSheetContent(state: EditUiState, actions: EditActions) {
    val formatters = rememberFormatters(state.zone)
    val title = editTitle(state)
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .semantics { paneTitle = title },
    ) {
        EditHeader(title, showDelete = state.mode == EditMode.EDIT, actions)
        val scroll = rememberScrollState()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = EditDefaults.Gutter),
        ) {
            Text(
                dayLabel(localDate(state.draft.start, state.zone), state.today, formatters),
                style = PhoneType.dataMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.mode == EditMode.ADD) {
                Spacer(Modifier.height(12.dp))
                ConnectedChoiceGroup(
                    options = EntryKind.entries,
                    selected = state.draft.kind,
                    onSelect = actions::onKind,
                    label = { stringResource(Labels.kind(it)) },
                )
            }
            Spacer(Modifier.height(9.dp))
            when (state.draft.kind) {
                EntryKind.SLEEP -> SleepForm(state, formatters, actions)
                EntryKind.FEED -> FeedForm(state, formatters, actions)
                EntryKind.DIAPER -> DiaperForm(state, formatters, actions)
            }
            ValidationSlot(state, formatters, actions)
        }
        // A hairline over the footer while the form continues below it (decorative: outlineVariant).
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (scroll.canScrollForward) MaterialTheme.colorScheme.outlineVariant else Color.Transparent),
        )
        EditFooter(state.canSave, actions)
    }
}

/** "Edit sleep" / "Edit feed" / "Edit diaper" / "Add past entry". */
@Composable
private fun editTitle(state: EditUiState): String = when (state.mode) {
    EditMode.ADD -> stringResource(R.string.edit_title_add)
    EditMode.EDIT -> when (state.draft.kind) {
        EntryKind.SLEEP -> stringResource(R.string.edit_title_sleep)
        EntryKind.FEED -> stringResource(R.string.edit_title_feed)
        EntryKind.DIAPER -> stringResource(R.string.edit_title_diaper)
    }
}

/** Header (48): Close (`close` 24), the title (titleLarge), and "Delete" (`delete` 18 + 6 + labelLarge, error). */
@Composable
private fun EditHeader(title: String, showDelete: Boolean, actions: EditActions) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTarget(XIcons.close, stringResource(R.string.cd_close), actions::onDismiss)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (showDelete) {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = actions::onDelete)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                XIcon(XIcons.delete, contentDescription = null, tint = colors.error, size = 18.dp)
                Text(stringResource(CoreR.string.action_delete), style = MaterialTheme.typography.labelLarge, color = colors.error)
            }
        }
    }
}

/** "Fell asleep" and "Woke up" (or "Still asleep" + End now), then "Duration 52m · was 47m". */
@Composable
private fun SleepForm(state: EditUiState, formatters: Formatters, actions: EditActions) {
    IntervalBlocks(
        state = state,
        formatters = formatters,
        actions = actions,
        startLabel = stringResource(R.string.edit_fell_asleep),
        endLabel = stringResource(CoreR.string.action_woke_up),
        stillLabel = stringResource(R.string.edit_still_asleep),
    )
    Spacer(Modifier.height(24.dp))
    DurationRow(state, formatters, sleep = true)
}

/**
 * The type (Breast | Formula | Food). A breastfeed has "Started" and "Ended" (or "Still feeding" + End now), the side
 * (a connected group of the four; picked once the feed has ended) and its duration; formula and food one time block.
 */
@Composable
private fun FeedForm(state: EditUiState, formatters: Formatters, actions: EditActions) {
    val draft = state.draft
    SectionLabel(stringResource(R.string.edit_type))
    ConnectedChoiceGroup(
        options = FeedType.entries,
        selected = draft.feedType,
        onSelect = actions::onFeedType,
        label = { stringResource(Labels.feedType(it, compact = true)) },
        // Danish "Modermælks­erstatning" needs the room to break at its soft hyphen, beside the selection check.
        weight = { if (it == FeedType.FORMULA) 1.5f else 1f },
    )
    Spacer(Modifier.height(9.dp))
    if (draft.isBreastfeed) {
        IntervalBlocks(
            state = state,
            formatters = formatters,
            actions = actions,
            startLabel = stringResource(R.string.edit_started),
            endLabel = stringResource(R.string.edit_ended),
            stillLabel = stringResource(R.string.edit_still_feeding),
        )
        if (draft.end != null) {
            SectionLabel(stringResource(R.string.edit_side))
            SideGroup(draft.side, actions::onSide)
        }
        Spacer(Modifier.height(24.dp))
        DurationRow(state, formatters, sleep = false)
    } else {
        InstantBlock(state, formatters, actions)
    }
}

/** One time block, the Pee and Poop toggles, and Colour / Texture / the hint while Poop is on. */
@Composable
private fun DiaperForm(state: EditUiState, formatters: Formatters, actions: EditActions) {
    val draft = state.draft
    InstantBlock(state, formatters, actions)
    Spacer(Modifier.height(16.dp))
    DiaperToggleTiles(pee = draft.pee, poop = draft.poop, onPee = actions::onPee, onPoop = actions::onPoop, enabled = true)
    PoopDetails(
        visible = draft.poop,
        color = draft.color,
        texture = draft.texture,
        onColor = actions::onPoopColor,
        onTexture = actions::onPoopTexture,
        acceptsTaps = true,
    )
}

@Composable
private fun InstantBlock(state: EditUiState, formatters: Formatters, actions: EditActions) {
    TimeBlock(
        label = stringResource(R.string.edit_time),
        millis = state.draft.start,
        was = state.wasStart,
        today = state.today,
        zone = state.zone,
        formatters = formatters,
        onNudge = { actions.onNudge(TimeField.START, it) },
        onTimeSet = { h, m -> actions.onTimeSet(TimeField.START, h, m) },
        onDaySet = { actions.onDaySet(TimeField.START, it) },
    )
}

@Composable
private fun IntervalBlocks(
    state: EditUiState,
    formatters: Formatters,
    actions: EditActions,
    startLabel: String,
    endLabel: String,
    stillLabel: String,
) {
    val draft = state.draft
    TimeBlock(
        label = startLabel,
        millis = draft.start,
        was = state.wasStart,
        today = state.today,
        zone = state.zone,
        formatters = formatters,
        onNudge = { actions.onNudge(TimeField.START, it) },
        onTimeSet = { h, m -> actions.onTimeSet(TimeField.START, h, m) },
        onDaySet = { actions.onDaySet(TimeField.START, it) },
    )
    Spacer(Modifier.height(9.dp))
    val end = draft.end
    if (end == null) {
        RunningBlock(endLabel, stillLabel, actions::onEndNow)
    } else {
        TimeBlock(
            label = endLabel,
            millis = end,
            was = state.wasEnd,
            today = state.today,
            zone = state.zone,
            formatters = formatters,
            onNudge = { actions.onNudge(TimeField.END, it) },
            onTimeSet = { h, m -> actions.onTimeSet(TimeField.END, h, m) },
            onDaySet = { actions.onDaySet(TimeField.END, it) },
        )
    }
}

/** Left | Right over Left → Right | Right → Left: one connected group (2 × 2 so Danish labels fit). */
@Composable
private fun SideGroup(selected: BreastSide?, onSelect: (BreastSide) -> Unit) {
    ConnectedChoiceGroup(
        modifier = Modifier.testTag(EditTags.SIDES),
        options = listOf(BreastSide.LEFT, BreastSide.RIGHT, BreastSide.LEFT_THEN_RIGHT, BreastSide.RIGHT_THEN_LEFT),
        selected = selected,
        onSelect = onSelect,
        label = { stringResource(Labels.side(it)) },
        spokenLabel = { spokenSide(it) },
        columns = 2,
    )
}

/** `sleep` / `breast` 20 in the event's strong colour + "Duration 52m" titleMedium + " · was 47m" dataMedium. */
@Composable
private fun DurationRow(state: EditUiState, formatters: Formatters, sleep: Boolean) {
    val duration = state.durationMillis ?: return
    val colors = MaterialTheme.colorScheme
    val events = LocalXPalette.current.events
    val shown = stringResource(R.string.edit_duration, formatters.duration.format(duration))
    val was = state.wasDurationMillis?.let { stringResource(R.string.edit_was, formatters.duration.format(it)) }
    val spoken = listOfNotNull(
        stringResource(R.string.edit_duration, formatters.duration.spoken(duration)),
        state.wasDurationMillis?.let { stringResource(R.string.edit_was, formatters.duration.spoken(it)) },
    ).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XIcon(
            if (sleep) XIcons.sleep else XIcons.breast,
            contentDescription = null,
            tint = if (sleep) events.sleep.strong else events.feed.strong,
            size = 20.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(MaterialTheme.typography.titleMedium.toSpanStyle().copy(color = colors.onSurface)) { append(shown) }
                if (was != null) {
                    withStyle(PhoneType.dataMedium.toSpanStyle().copy(color = colors.onSurfaceVariant)) { append(" · $was") }
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * The validation slot (48): empty unless a rule fires. The error colour only when the rule blocks Save. Lines with a
 * time use the digit style (GSF), prose lines Atkinson (DESIGN_SPEC §5.7).
 */
@Composable
private fun ValidationSlot(state: EditUiState, formatters: Formatters, actions: EditActions) {
    val colors = MaterialTheme.colorScheme
    val issue = state.issue
    Row(
        Modifier
            .fillMaxWidth()
            // Tagged with its padding, so scrolling to the slot reaches the end of the form.
            .testTag(EditTags.VALIDATION)
            .padding(top = 12.dp, bottom = 8.dp)
            .heightIn(min = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (issue == null) return@Row
        val color = if (issue.blocksSave) colors.error else colors.onSurfaceVariant
        val (text, hasDigits) = when (issue) {
            is EditIssue.EndBeforeStart -> stringResource(
                if (issue.kind == EntryKind.SLEEP) R.string.edit_error_wake_before_sleep else R.string.edit_error_feed_end_before_start,
            ) to false
            is EditIssue.InFuture -> stringResource(R.string.edit_error_future, formatters.clock.format(issue.at, state.zone)) to true
            is EditIssue.Overlaps -> stringResource(
                if (issue.other.kind == EntryKind.SLEEP) R.string.edit_warning_overlap_sleep else R.string.edit_warning_overlap_feed,
                formatters.clock.format(issue.other.startMillis, state.zone),
            ) to true
            EditIssue.SaveFailed -> stringResource(R.string.edit_error_save_failed) to false
        }
        Text(
            text,
            style = if (hasDigits) PhoneType.dataMedium else MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (issue is EditIssue.InFuture) {
            Spacer(Modifier.width(8.dp))
            OutlinePill(stringResource(R.string.edit_use_yesterday), onClick = actions::onUseYesterday)
        }
    }
}

/** Test tags of the sheet's parts. */
object EditTags {
    const val SIDES = "edit_sides"
    const val VALIDATION = "edit_validation"
}

/** Footer: "Cancel" (labelLarge onSurfaceVariant) at the start, Save (160 × 56) at the end, above the gesture inset. */
@Composable
private fun EditFooter(canSave: Boolean, actions: EditActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = EditDefaults.Gutter, end = EditDefaults.Gutter, top = 8.dp, bottom = EditDefaults.FooterBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 100.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = actions::onDismiss)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(CoreR.string.action_cancel), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        SaveButton(enabled = canSave, onClick = actions::onSave)
    }
}

/** A section label: labelMedium onSurfaceVariant, a heading. */
@Composable
private fun SectionLabel(text: String) = SectionHeading(text, Modifier.padding(bottom = 4.dp))

/** The sheet's content as it stands in the 796 dp sheet of the reference device (no modal window in previews). */
@Composable
private fun EditPreviewFrame(night: Boolean, state: EditUiState) {
    NightLightTheme(night = night) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(796.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(top = 24.dp),
        ) { EditSheetContent(state, EditActions.None) }
    }
}

@Preview(name = "Edit sleep · Night", widthDp = 390, heightDp = 796)
@Composable
private fun EditSleepPreview() = EditPreviewFrame(night = true, state = EditSamples.sleepNudged())

@Preview(name = "Edit breastfeed · Day", widthDp = 390, heightDp = 796)
@Composable
private fun EditBreastfeedPreview() = EditPreviewFrame(night = false, state = EditSamples.breastfeed())

@Preview(name = "Edit diaper · Night · Danish", widthDp = 390, heightDp = 796, locale = "da")
@Composable
private fun EditDiaperPreview() = EditPreviewFrame(night = true, state = EditSamples.diaperPoop())

@Preview(name = "Add past entry · Day", widthDp = 390, heightDp = 796)
@Composable
private fun AddPastEntryPreview() = EditPreviewFrame(night = false, state = EditSamples.addPastEntry())

@Preview(name = "Validation · Night", widthDp = 390, heightDp = 796)
@Composable
private fun EditValidationPreview() = EditPreviewFrame(night = true, state = EditSamples.sleepEndBeforeStart())
