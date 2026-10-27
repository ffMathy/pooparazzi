package com.ffmathy.xaternity.phone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.SyncPeer
import com.ffmathy.xaternity.core.sync.SyncStatus
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.type.PhoneType
import java.time.ZoneId
import com.ffmathy.xaternity.core.R as CoreR

/**
 * Home's top bar (DESIGN_SPEC §3.3.1): the baby's name, the sync line (a retry button in the error state), the History
 * pill and Settings. In lights-down the text and icons use onSurfaceVariant and the pill loses its fill.
 */
@Composable
internal fun HomeTopBar(
    babyName: String,
    sync: SyncStatus,
    zone: ZoneId,
    lightsDown: Boolean,
    formatters: Formatters,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val chrome = if (lightsDown) colors.onSurfaceVariant else colors.onSurface
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pill = historyPillStyle(formatters.labels.syncLine(sync, SyncPeer.WATCH, formatters.clock, zone), maxWidth)
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp),
            ) {
                Column(Modifier.padding(start = 20.dp, end = TopBarGap, top = 6.dp, bottom = 4.dp)) {
                    val name = babyName.ifBlank { stringResource(CoreR.string.baby_name_hint) }
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (babyName.isBlank()) colors.onSurfaceVariant else chrome,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    SyncLine(sync, zone, formatters)
                }
                if (sync is SyncStatus.Error) {
                    // "Couldn't save · Tap to retry" is a 48 dp button over the sync line; it overlaps only the name.
                    val label = formatters.labels.syncLine(sync, SyncPeer.WATCH, formatters.clock, zone).orEmpty()
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(role = Role.Button, onClick = actions::onRetrySync)
                            .semantics { contentDescription = label },
                    )
                }
            }
            HistoryPill(pill, lightsDown, actions::onOpenHistory)
            Box(
                Modifier
                    .padding(start = TopBarGap, end = 12.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = actions::onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                XIcon(XIcons.settings, contentDescription = stringResource(CoreR.string.nav_settings), tint = colors.onSurfaceVariant, size = 24.dp)
            }
        }
    }
}

private val TopBarGap = 4.dp

/** How much of the History pill fits beside the sync line. */
private enum class PillStyle { FULL, TEXT, ICON }

/**
 * The full pill ("History" with its icon) unless the sync line would not fit beside it. Danish "Synkroniseret med ur ·
 * 14:31" next to "Historik" needs about 20 dp more than the 390 dp reference has, so the pill first drops its icon and,
 * at large font scales, its label (TalkBack still reads "History").
 */
@Composable
private fun historyPillStyle(syncLine: String?, maxWidth: Dp): PillStyle {
    if (syncLine == null) return PillStyle.FULL
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val label = stringResource(CoreR.string.nav_history)
    return remember(syncLine, label, maxWidth, density) {
        with(density) {
            val sync = measurer.measure(syncLine, PhoneType.syncLabel, maxLines = 1).size.width.toDp() + 14.dp + 6.dp
            val text = measurer.measure(label, PhoneType.labelLarge, maxLines = 1).size.width.toDp()
            // Start 20, the gaps either side of the pill, Settings 48 and its 12 end margin.
            val left = maxWidth - 20.dp - TopBarGap * 2 - 48.dp - 12.dp
            when {
                left - (12.dp + 18.dp + 6.dp + text + 14.dp) >= sync -> PillStyle.FULL
                left - (14.dp + text + 14.dp) >= sync -> PillStyle.TEXT
                else -> PillStyle.ICON
            }
        }
    }
}

/** "Synced with watch · 14:31" and friends (DESIGN_SPEC §4.5); hidden when sync is unavailable. Error uses the error colour. */
@Composable
private fun SyncLine(sync: SyncStatus, zone: ZoneId, formatters: Formatters) {
    val colors = MaterialTheme.colorScheme
    val text = formatters.labels.syncLine(sync, SyncPeer.WATCH, formatters.clock, zone) ?: return
    val icon = when (sync) {
        is SyncStatus.Synced -> XIcons.synced
        SyncStatus.Pending -> XIcons.pending
        else -> XIcons.offline
    }
    val color = if (sync is SyncStatus.Error) colors.error else colors.onSurfaceVariant
    Row(
        Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        XIcon(icon, contentDescription = null, tint = color, size = 14.dp)
        val lines = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else 1
        Text(text, style = PhoneType.syncLabel, color = color, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

/** The History pill: 48 target / 40 visual, r 20, `history` 18 + 6 + "History" (narrower when [style] says so). */
@Composable
private fun HistoryPill(style: PillStyle, lightsDown: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val label = stringResource(CoreR.string.nav_history)
    Box(
        Modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (style == PillStyle.ICON) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(40.dp)
                .widthIn(min = 40.dp)
                .clip(shape)
                .background(if (lightsDown) Color.Transparent else colors.surfaceContainerHigh)
                .padding(
                    start = when (style) {
                        PillStyle.FULL -> 12.dp
                        PillStyle.TEXT -> 14.dp
                        PillStyle.ICON -> 0.dp
                    },
                    end = if (style == PillStyle.ICON) 0.dp else 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            if (style != PillStyle.TEXT) {
                XIcon(XIcons.history, contentDescription = null, tint = colors.onSurfaceVariant, size = 18.dp)
            }
            if (style != PillStyle.ICON) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (lightsDown) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}
