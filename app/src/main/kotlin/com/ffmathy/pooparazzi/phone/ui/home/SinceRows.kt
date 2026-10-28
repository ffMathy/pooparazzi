package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.history.spokenDiaperKind
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** From this font scale on, rows may wrap and the Today grid becomes 2 × 2. */
internal const val LARGE_FONT_SCALE = 1.3f

/** The two since-rows (DESIGN_SPEC §3.3.1): the one-tap correction path into the edit sheet. */
@Composable
internal fun SinceRows(row1: SinceRow?, row2: SinceRow?, zone: ZoneId, formatters: Formatters, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SinceRowItem(row1, zone, formatters, onEdit)
        SinceRowItem(row2, zone, formatters, onEdit)
    }
}

/** What a since-row shows and says. */
private class SinceRowText(val icon: TintedIcon, val lead: String, val meta: String, val spoken: String)

@Composable
private fun SinceRowItem(row: SinceRow?, zone: ZoneId, formatters: Formatters, onEdit: (String) -> Unit) {
    if (row == null) {
        // Nothing logged yet: the band keeps its height so the layout never jumps.
        Spacer(Modifier.fillMaxWidth().height(48.dp))
        return
    }
    val colors = MaterialTheme.colorScheme
    val text = sinceRowText(row, zone, formatters)
    val editLabel = stringResource(CoreR.string.action_edit)
    val open = { onEdit(row.entryId) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = editLabel, role = Role.Button, onClick = open)
            .clearAndSetSemantics {
                contentDescription = text.spoken
                role = Role.Button
                onClick(label = editLabel) { open(); true }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        XIcon(text.icon.icon, contentDescription = null, tint = text.icon.tint, size = 20.dp)
        // One line, no wrapping (text slot 258 dp): long Danish leads shrink a little instead. At large font scales
        // the row may take a second line (DESIGN_SPEC §9.3: the area above the buttons scrolls).
        BasicText(
            text = buildAnnotatedString {
                append(text.lead)
                withStyle(SpanStyle(fontSize = (16f / 18f).em, fontWeight = FontWeight(450), color = colors.onSurfaceVariant)) {
                    append(text.meta)
                }
            },
            style = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
            maxLines = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 18.sp, stepSize = 0.5.sp),
            modifier = Modifier.weight(1f),
        )
        XIcon(XIcons.chevronRight, contentDescription = null, tint = colors.outline, size = 16.dp)
    }
}

@Composable
private fun sinceRowText(row: SinceRow, zone: ZoneId, formatters: Formatters): SinceRowText {
    val events = LocalXPalette.current.events
    val relative = formatters.relative
    val clock = formatters.clock
    return when (row) {
        is SinceRow.Awake -> statusRow(
            icon = TintedIcon(XIcons.awake, events.sleep.strong),
            status = stringResource(CoreR.string.status_awake),
            elapsedMillis = row.elapsedMillis,
            sinceMillis = row.sinceMillis,
            zone = zone,
            formatters = formatters,
        )
        is SinceRow.LastFed -> {
            val type = row.feed.feedType
            SinceRowText(
                icon = TintedIcon(XIcons.forFeedType(type), events.feed.strong),
                lead = stringResource(R.string.join_space, stringResource(CoreR.string.status_last_fed), relative.ago(row.elapsedMillis)),
                meta = dotted(clock.format(row.feed.startMillis, zone)),
                spoken = if (type == null) {
                    stringResource(
                        R.string.spoken_running,
                        stringResource(CoreR.string.spoken_last_feed),
                        relative.agoSpoken(row.elapsedMillis),
                        relative.at(row.feed.startMillis, zone),
                    )
                } else {
                    stringResource(
                        R.string.spoken_last_entry,
                        stringResource(CoreR.string.spoken_last_feed),
                        feedTypeLabel(type).lowercase(clock.locale),
                        relative.agoSpoken(row.elapsedMillis),
                        relative.at(row.feed.startMillis, zone),
                    )
                },
            )
        }
        is SinceRow.LastDiaper -> SinceRowText(
            icon = diaperIcon(row.kind, events),
            lead = stringResource(R.string.join_space, diaperStatusLabel(row.kind), relative.ago(row.elapsedMillis)),
            meta = dotted(clock.format(row.diaper.startMillis, zone)),
            spoken = if (row.kind == DiaperKind.PLAIN) {
                // "Last diaper, 48 minutes ago, at 13:44": a plain change has no kind to name.
                stringResource(
                    R.string.spoken_running,
                    stringResource(CoreR.string.spoken_last_diaper),
                    relative.agoSpoken(row.elapsedMillis),
                    relative.at(row.diaper.startMillis, zone),
                )
            } else {
                stringResource(
                    R.string.spoken_last_entry,
                    stringResource(CoreR.string.spoken_last_diaper),
                    spokenDiaperKind(row.kind).lowercase(clock.locale),
                    relative.agoSpoken(row.elapsedMillis),
                    relative.at(row.diaper.startMillis, zone),
                )
            },
        )
    }
}

@Composable
private fun statusRow(
    icon: TintedIcon,
    status: String,
    elapsedMillis: Long,
    sinceMillis: Long,
    zone: ZoneId,
    formatters: Formatters,
): SinceRowText {
    val since = formatters.relative.since(sinceMillis, zone)
    return SinceRowText(
        icon = icon,
        lead = stringResource(R.string.join_space, status, formatters.duration.format(elapsedMillis)),
        meta = dotted(since),
        spoken = stringResource(R.string.spoken_status_since, status, formatters.duration.spoken(elapsedMillis), since),
    )
}
