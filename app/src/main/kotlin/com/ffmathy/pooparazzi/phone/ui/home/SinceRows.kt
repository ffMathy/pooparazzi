package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.model.FeedType
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

/** The smallest since-row text before it wraps to a second line. */
private const val MIN_ROW_SP = 14f

/** The largest since-row text; both rows step down from here together. */
private const val MAX_ROW_SP = 18f

/**
 * The two since-rows (DESIGN_SPEC §3.3.1): the one-tap correction path into the edit sheet. Both rows share one text
 * size: the largest (18 sp, stepping down 0.5 sp to 14 sp) at which each fits one line, so "Last fed …" and "Pee …"
 * never differ. A row that does not fit even at 14 sp (a narrow screen, a larger font) takes a second line at that
 * shared size.
 */
@Composable
internal fun SinceRows(row1: SinceRow?, row2: SinceRow?, zone: ZoneId, formatters: Formatters, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val texts = listOf(row1, row2).map { row -> row?.let { sinceRowText(it, zone, formatters) } }
    val annotated = texts.map { text ->
        text?.let {
            buildAnnotatedString {
                append(it.lead)
                withStyle(SpanStyle(fontSize = (16f / 18f).em, fontWeight = FontWeight(450), color = colors.onSurfaceVariant)) {
                    append(it.meta)
                }
            }
        }
    }
    val style = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        // The text slot: the row minus its 16 dp insets, the 20 dp icon, the 16 dp chevron and two 12 dp gaps.
        val slot = with(density) { (maxWidth - 32.dp - 20.dp - 16.dp - 24.dp).roundToPx() }.coerceAtLeast(0)
        val fontSp = remember(annotated, style, slot, measurer, density) {
            fun fits(text: AnnotatedString, sp: Float) =
                measurer.measure(text, style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false, density = density).size.width <= slot
            // A row that needs two lines even at 14 sp wraps anyway: it does not pull the other row down.
            val singleLine = annotated.filterNotNull().filter { fits(it, MIN_ROW_SP) }
            var sp = MAX_ROW_SP
            while (sp > MIN_ROW_SP && singleLine.any { !fits(it, sp) }) sp -= 0.5f
            sp
        }
        val wrapAllowed = density.fontScale >= LARGE_FONT_SCALE
        Column {
            listOf(row1, row2).forEachIndexed { index, row ->
                val text = texts[index]
                val line = annotated[index]
                if (row == null || text == null || line == null) {
                    // Nothing logged yet: the band keeps its height so the layout never jumps.
                    Spacer(Modifier.fillMaxWidth().height(48.dp))
                } else {
                    val oneLine = remember(line, style, fontSp, slot, measurer, density) {
                        measurer.measure(line, style.copy(fontSize = fontSp.sp), maxLines = 1, softWrap = false, density = density).size.width <= slot
                    }
                    SinceRowItem(row, text, line, style.copy(fontSize = fontSp.sp, lineHeight = (fontSp * 24f / 18f).sp), maxLines = if (oneLine && !wrapAllowed) 1 else 2, onEdit = onEdit)
                }
            }
        }
    }
}

/** What a since-row shows and says. */
private class SinceRowText(val icon: TintedIcon, val lead: String, val meta: String, val spoken: String)

@Composable
private fun SinceRowItem(row: SinceRow, text: SinceRowText, line: AnnotatedString, style: TextStyle, maxLines: Int, onEdit: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
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
        BasicText(text = line, style = style, maxLines = maxLines, modifier = Modifier.weight(1f))
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
            // The breast order rides after the time ("· 01:40 · L → R"), short so the row stays on one line: it is what
            // the next feed starts from.
            val side = row.feed.breastSide?.takeIf { type == FeedType.BREAST }?.let { stringResource(Labels.sideShort(it)) }
            val spokenDetail = spokenFeedDetail(row.feed, clock.locale)
            SinceRowText(
                icon = TintedIcon(XIcons.forFeedType(type), events.feed.strong),
                lead = stringResource(R.string.join_space, stringResource(CoreR.string.status_last_fed), relative.ago(row.elapsedMillis)),
                meta = dotted(clock.format(row.feed.startMillis, zone)) + (side?.let(::dotted) ?: ""),
                spoken = if (spokenDetail == null) {
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
                        spokenDetail,
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
