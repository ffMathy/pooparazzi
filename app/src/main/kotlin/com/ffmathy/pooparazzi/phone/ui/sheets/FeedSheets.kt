package com.ffmathy.pooparazzi.phone.ui.sheets

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.components.OffsetChipRow
import com.ffmathy.pooparazzi.phone.ui.history.spokenSideCapitalised
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.OffsetChipUi
import com.ffmathy.pooparazzi.phone.ui.home.feedTypeLabel
import com.ffmathy.pooparazzi.phone.ui.home.titleWithTime
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** What the Feed sheet can ask for. */
internal class FeedSheetCallbacks(
    val onOffset: (Int) -> Unit,
    val onType: (FeedType?) -> Unit,
    val onTick: (Breast) -> Unit,
    val onLogBreast: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The Feed sheet (V1_ADDITIONS §2.1): "Feed · 14:32" stamped on the first tap, Now · −5m · −10m · −15m, three
 * full-width type rows and an outlined "Just the time" row. Formula and Food log an instant at stamp + offset;
 * "Just the time" logs a feed with no type (the type is optional). Breast is an instant too: it opens the sheet's
 * Breast step ([BreastStep]) for the optional side order, then "Log feed".
 */
@Composable
internal fun FeedSheetContent(
    sheet: HomeSheetUi.Feed,
    zone: ZoneId,
    formatters: Formatters,
    callbacks: FeedSheetCallbacks,
) {
    val readiness = rememberPickerReadiness()
    val time = formatters.clock.format(sheet.time, zone)
    val title = titleWithTime(if (sheet.breast) CoreR.string.feed_type_breast else CoreR.string.kind_feed, time)
    SheetColumn(title) {
        SheetHeader(title, callbacks.onCancel)
        Spacer(Modifier.height(12.dp))
        SheetChipRow(sheet.chips, formatters, readiness, callbacks.onOffset)
        Spacer(Modifier.height(12.dp))
        if (sheet.breast) {
            BreastStep(sheet, readiness, callbacks)
        } else {
            Column(Modifier.alpha(readiness.alpha), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedType.entries.forEach { type ->
                    FeedTypeRow(
                        type = type,
                        supporting = if (type == FeedType.BREAST) stringResource(CoreR.string.feed_breast_sides_next) else null,
                        enabled = readiness.ready,
                        onClick = { callbacks.onType(type) },
                    )
                }
                FeedTypeRow(type = null, supporting = null, enabled = readiness.ready, onClick = { callbacks.onType(null) })
            }
        }
    }
}

/**
 * The Feed sheet's Breast step (V1_ADDITIONS §2.1): the time stays on the chips above; two checkboxes Left and Right
 * whose tick order is the order recorded (badges "1" / "2" and a summary line "Left → Right"), and "Log feed". The
 * side is optional: logging with nothing ticked records only the time.
 */
@Composable
private fun BreastStep(sheet: HomeSheetUi.Feed, readiness: PickerReadiness, callbacks: FeedSheetCallbacks) {
    val colors = MaterialTheme.colorScheme
    Text(stringResource(CoreR.string.side_order_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    BreastSideCheckboxes(sheet.ticks, callbacks.onTick, enabled = readiness.ready, modifier = Modifier.alpha(readiness.alpha))
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SideSummary(sheet.ticks, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        SaveButton(
            enabled = readiness.ready,
            onClick = callbacks.onLogBreast,
            label = stringResource(CoreR.string.action_log_feed),
        )
    }
}

/**
 * A 72 dp row, r 28, FEED container: icon 32 FEED strong, headlineSmall label, optional bodyMedium line. The untyped
 * row ([type] null, "Just the time") is outlined instead of filled, with the `clock` icon.
 */
@Composable
private fun FeedTypeRow(type: FeedType?, supporting: String?, enabled: Boolean, onClick: () -> Unit) {
    val feed = LocalXPalette.current.events.feed
    val shape = RoundedCornerShape(28.dp)
    val untyped = type == null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .then(if (untyped) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier.background(feed.container))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        XIcon(if (untyped) XIcons.clock else XIcons.forFeedType(type), contentDescription = null, tint = feed.strong, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                if (untyped) stringResource(CoreR.string.feed_type_none) else feedTypeLabel(type, compact = true),
                style = MaterialTheme.typography.headlineSmall.copy(hyphens = Hyphens.Auto),
                color = if (untyped) MaterialTheme.colorScheme.onSurface else feed.onContainer,
                maxLines = 2,
            )
            supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = feed.onContainer) }
        }
    }
}

/**
 * Left and Right as two checkbox tiles side by side (V1_ADDITIONS §2.1), shared by the Feed sheet's Breast step and the edit sheet.
 * [ticks] is the tick order: a checked tile shows its place ("1" / "2") in a badge. `Role.Checkbox`; TalkBack reads
 * "Left, first, checkbox, checked".
 */
@Composable
internal fun BreastSideCheckboxes(ticks: List<Breast>, onTick: (Breast) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Breast.entries.forEach { breast ->
            val place = ticks.indexOf(breast).takeIf { it >= 0 }?.plus(1)
            SideCheckboxTile(breast, place, { onTick(breast) }, enabled, Modifier.weight(1f))
        }
    }
}

/** "Left → Right" for the ticked order, or "No side" while nothing is ticked (live region: read when it changes). */
@Composable
internal fun SideSummary(ticks: List<Breast>, modifier: Modifier = Modifier) {
    val side = BreastSide.fromOrder(ticks)
    val text = side?.let { stringResource(Labels.side(it)) } ?: stringResource(CoreR.string.side_none)
    val spoken = side?.let { spokenSideCapitalised(it) } ?: text
    val feed = LocalXPalette.current.events.feed
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (side == null) MaterialTheme.colorScheme.onSurfaceVariant else feed.text,
        modifier = modifier.semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/**
 * A checkbox tile, min 112 dp tall, r 28 (the Diaper sheet's tiles in FEED colours). Off: FEED container, 1 dp outline,
 * empty checkbox. On: FEED lit fill, 2 dp FEED strong border, checked checkbox, and the order badge [place] top-start.
 */
@Composable
private fun SideCheckboxTile(breast: Breast, place: Int?, onToggle: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val feed = LocalXPalette.current.events.feed
    val shape = RoundedCornerShape(28.dp)
    val checked = place != null
    val side = if (breast == Breast.LEFT) BreastSide.LEFT else BreastSide.RIGHT
    val label = stringResource(Labels.side(side))
    val placeSpoken = place?.let { stringResource(if (it == 1) R.string.spoken_side_first else R.string.spoken_side_second) }
    Box(
        modifier
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(if (checked) feed.containerLit else feed.container)
            .border(if (checked) BorderStroke(2.dp, feed.strong) else BorderStroke(1.dp, colors.outline), shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            // The label and glyph letters are replaced by "Left, first"; the toggle, role and state stay.
            .clearAndSetSemantics {
                contentDescription = if (placeSpoken != null) "$label, $placeSpoken" else label
            },
    ) {
        if (place != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(feed.strong),
                contentAlignment = Alignment.Center,
            ) {
                Text(place.toString(), style = MaterialTheme.typography.labelLarge, color = feed.containerLit, maxLines = 1)
            }
        }
        XIcon(
            if (checked) XIcons.checkboxChecked else XIcons.checkbox,
            contentDescription = null,
            tint = if (checked) feed.strong else colors.outline,
            size = 24.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp),
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(width = 32.dp, height = 44.dp)) {
                XIcon(XIcons.forSide(side), contentDescription = null, tint = feed.strong, size = 32.dp)
                // The circles sit at x 6.5 and 17.5 of 24: the letters go under them.
                SideLetter(stringResource(CoreR.string.side_letter_left), centreX = 32f * 6.5f / 24f)
                SideLetter(stringResource(CoreR.string.side_letter_right), centreX = 32f * 17.5f / 24f)
            }
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall,
                color = feed.onContainer,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
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

/** The single-select offset chips of a sheet (radio semantics): one row of equal chips at every width and font scale. */
@Composable
internal fun SheetChipRow(chips: List<OffsetChipUi>, formatters: Formatters, readiness: PickerReadiness, onOffset: (Int) -> Unit) {
    OffsetChipRow(
        chips,
        formatters,
        onOffset,
        modifier = Modifier
            .selectableGroup()
            .alpha(readiness.alpha),
        role = Role.RadioButton,
        acceptsTaps = readiness.ready,
    )
}
