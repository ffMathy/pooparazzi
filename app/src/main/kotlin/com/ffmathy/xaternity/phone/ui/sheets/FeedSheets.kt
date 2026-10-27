package com.ffmathy.xaternity.phone.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.tokens.LocalXPalette
import com.ffmathy.xaternity.core.ui.type.PhoneType
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.ui.components.OffsetChip
import com.ffmathy.xaternity.phone.ui.home.HomeSheetUi
import com.ffmathy.xaternity.phone.ui.home.OffsetChipUi
import com.ffmathy.xaternity.phone.ui.home.feedTypeLabel
import com.ffmathy.xaternity.phone.ui.home.titleWithTime
import java.time.ZoneId
import com.ffmathy.xaternity.core.R as CoreR

/**
 * The Feed sheet (V1_ADDITIONS §2.1): "Feed · 14:32" stamped on the first tap, Now · −5m · −10m · −15m, and three
 * full-width rows. Breast starts a timer; Formula and Food log an instant at stamp + offset.
 */
@Composable
internal fun FeedSheetContent(
    sheet: HomeSheetUi.Feed,
    zone: ZoneId,
    formatters: Formatters,
    onOffset: (Int) -> Unit,
    onType: (FeedType) -> Unit,
    onCancel: () -> Unit,
) {
    val readiness = rememberPickerReadiness()
    val title = titleWithTime(CoreR.string.kind_feed, formatters.clock.format(sheet.time, zone))
    SheetColumn(title) {
        SheetHeader(title, onCancel)
        Spacer(Modifier.height(12.dp))
        SheetChipRow(sheet.chips, formatters, readiness, onOffset)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.alpha(readiness.alpha), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedType.entries.forEach { type ->
                FeedTypeRow(
                    type = type,
                    supporting = if (type == FeedType.BREAST) stringResource(CoreR.string.feed_breast_starts_timer) else null,
                    enabled = readiness.ready,
                    onClick = { onType(type) },
                )
            }
        }
    }
}

/** A 72 dp row, r 28, FEED container: icon 32 FEED strong, headlineSmall label, optional bodyMedium line. */
@Composable
private fun FeedTypeRow(type: FeedType, supporting: String?, enabled: Boolean, onClick: () -> Unit) {
    val feed = LocalXPalette.current.events.feed
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(feed.container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        XIcon(XIcons.forFeedType(type), contentDescription = null, tint = feed.strong, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                feedTypeLabel(type, compact = true),
                style = MaterialTheme.typography.headlineSmall.copy(hyphens = Hyphens.Auto),
                color = feed.onContainer,
                maxLines = 2,
            )
            supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = feed.onContainer) }
        }
    }
}

/**
 * The Side sheet (V1_ADDITIONS §2.1): "Which side?", "Breastfeeding · 14:32–14:50 · 18m" (follows the chips), end
 * chips Now · −5m · −10m (disabled before the start) and a 2 × 2 grid of sides. One tap ends the feed; Cancel keeps it
 * running.
 */
@Composable
internal fun SideSheetContent(
    sheet: HomeSheetUi.Side,
    zone: ZoneId,
    formatters: Formatters,
    onOffset: (Int) -> Unit,
    onSide: (BreastSide) -> Unit,
    onCancel: () -> Unit,
) {
    val readiness = rememberPickerReadiness()
    val colors = MaterialTheme.colorScheme
    val title = stringResource(CoreR.string.side_question)
    val end = maxOf(sheet.time, sheet.startMillis)
    val range = formatters.clock.range(sheet.startMillis, end, zone, spaced = false)
    val sub = stringResource(
        R.string.join_dot,
        stringResource(R.string.join_dot, stringResource(CoreR.string.status_breastfeeding), range),
        formatters.duration.between(sheet.startMillis, end),
    )
    SheetColumn(title) {
        SheetHeader(title, onCancel)
        Text(sub, style = PhoneType.dataMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SheetChipRow(sheet.chips, formatters, readiness, onOffset)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.alpha(readiness.alpha), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                BreastSide.LEFT to BreastSide.RIGHT,
                BreastSide.LEFT_THEN_RIGHT to BreastSide.RIGHT_THEN_LEFT,
            ).forEach { (first, second) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SideTile(first, readiness.ready, { onSide(first) }, Modifier.weight(1f))
                    SideTile(second, readiness.ready, { onSide(second) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** A 165 × 96 tile, r 28, FEED container: the side glyph with its L/R letters (from text) over the side label. */
@Composable
private fun SideTile(side: BreastSide, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val feed = LocalXPalette.current.events.feed
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(feed.container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(width = 32.dp, height = 44.dp).clearAndSetSemantics { }) {
            XIcon(XIcons.forSide(side), contentDescription = null, tint = feed.strong, size = 32.dp)
            // The circles sit at x 6.5 and 17.5 of 24: the letters go under them.
            SideLetter(stringResource(CoreR.string.side_letter_left), centreX = 32f * 6.5f / 24f)
            SideLetter(stringResource(CoreR.string.side_letter_right), centreX = 32f * 17.5f / 24f)
        }
        BasicText(
            stringResource(Labels.side(side)),
            style = MaterialTheme.typography.headlineSmall.copy(color = feed.onContainer, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 24.sp, stepSize = 0.5.sp),
        )
    }
}

@Composable
private fun SideLetter(letter: String, centreX: Float) {
    val feed = LocalXPalette.current.events.feed
    Box(
        Modifier
            .offset(x = (centreX - 8f).dp, y = 30.dp)
            .width(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(letter, style = MaterialTheme.typography.labelSmall, color = feed.onContainer, maxLines = 1)
    }
}

/** A sheet's scrolling content column, labelled as a pane by [title]. */
@Composable
internal fun SheetColumn(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { paneTitle = title }
            .then(SheetContentPadding)
            .padding(bottom = 24.dp),
    ) { content() }
}

/** The single-select offset chips of a sheet (radio semantics). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetChipRow(chips: List<OffsetChipUi>, formatters: Formatters, readiness: PickerReadiness, onOffset: (Int) -> Unit) {
    // Wraps only at large font scales.
    FlowRow(
        Modifier
            .selectableGroup()
            .alpha(readiness.alpha),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            OffsetChip(chip, formatters, onClick = { onOffset(chip.minutes) }, role = Role.RadioButton, acceptsTaps = readiness.ready)
        }
    }
}
