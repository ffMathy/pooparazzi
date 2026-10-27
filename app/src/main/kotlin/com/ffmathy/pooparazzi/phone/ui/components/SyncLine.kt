package com.ffmathy.pooparazzi.phone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.SyncPeer
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.ui.home.LARGE_FONT_SCALE
import java.time.ZoneId

/**
 * "Synced with watch · 14:31" and friends (DESIGN_SPEC §4.5): icon 14 + 6 + syncLabel. Hidden when sync is
 * unavailable. The error state uses the error colour; the caller makes it a 48 dp retry target.
 */
@Composable
internal fun SyncLine(sync: SyncStatus, zone: ZoneId, formatters: Formatters, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val text = syncLineText(sync, zone, formatters) ?: return
    val color = if (sync is SyncStatus.Error) colors.error else colors.onSurfaceVariant
    Row(
        modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        XIcon(syncIcon(sync), contentDescription = null, tint = color, size = 14.dp)
        val lines = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else 1
        Text(text, style = PhoneType.syncLabel, color = color, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

/** The phone's sync line copy, or null when the line is hidden. */
internal fun syncLineText(sync: SyncStatus, zone: ZoneId, formatters: Formatters): String? =
    formatters.labels.syncLine(sync, SyncPeer.WATCH, formatters.clock, zone)

/** `synced`, `pending` or `offline` (DESIGN_SPEC §4.5). */
internal fun syncIcon(sync: SyncStatus) = when (sync) {
    is SyncStatus.Synced -> XIcons.synced
    SyncStatus.Pending -> XIcons.pending
    else -> XIcons.offline
}
