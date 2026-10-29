package com.ffmathy.pooparazzi.phone.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.SyncPeer
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.phone.ui.account.backupStatusText
import java.time.ZoneId

/**
 * One status icon in a top bar (DESIGN_SPEC §4.5): the glyph, what TalkBack reads ("Synced with watch · 14:31",
 * "Backed up · 14:31"), and in the error state the retry it offers.
 */
@Immutable
internal data class SyncIcon(
    val glyph: ImageVector,
    val description: String,
    val error: Boolean = false,
    val onRetry: (() -> Unit)? = null,
)

/**
 * The top bars' status icons (DESIGN_SPEC §4.5), in order: the watch unless sync is unavailable (no watch), then the
 * cloud while an account is signed in ([CloudStatus.Off] shows none: signed out, or a build without a backend).
 * Only the error states offer a retry: the watch's [onRetrySync], the backup's [onRetryBackup].
 */
@Composable
internal fun syncIcons(
    sync: SyncStatus,
    cloud: CloudStatus,
    zone: ZoneId,
    formatters: Formatters,
    onRetrySync: () -> Unit,
    onRetryBackup: () -> Unit,
): List<SyncIcon> = buildList {
    syncLineText(sync, zone, formatters)?.let { text ->
        val error = sync is SyncStatus.Error
        add(SyncIcon(watchGlyph(sync), text, error, if (error) onRetrySync else null))
    }
    if (cloud != CloudStatus.Off) {
        val text = backupStatusText(cloud, formatters.clock, zone)
        val retry = if (cloud == CloudStatus.Error) onRetryBackup else null
        // "Backup deleted" and "Backup paused": nothing is backed up (error colour), but only the Account screen starts
        // it again ("Back up now", "Agree and back up"), so they offer no retry.
        val warning = cloud == CloudStatus.Error || cloud == CloudStatus.Deleted || cloud == CloudStatus.Paused
        add(SyncIcon(cloudGlyph(cloud), text, warning, retry))
    }
}

/**
 * The [icons] as a row of 20 dp glyphs in onSurfaceVariant (error colour for an error), no visible text. An icon with
 * a retry becomes a 48 dp button, so in that state the row is 48 dp tall. Nothing shows without icons.
 */
@Composable
internal fun SyncIcons(icons: List<SyncIcon>, modifier: Modifier = Modifier) {
    if (icons.isEmpty()) return
    val targets = icons.any { it.onRetry != null }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        SyncGlyphs(icons)
        if (targets) SyncTargets(icons)
    }
}

/** The glyphs only; an icon with a retry is silent here, its [SyncTargets] button speaks for it. */
@Composable
internal fun SyncGlyphs(icons: List<SyncIcon>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val gap = Arrangement.spacedBy(gapOf(icons))
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = gap) {
        icons.forEach { icon ->
            XIcon(
                icon.glyph,
                contentDescription = if (icon.onRetry != null) null else icon.description,
                tint = if (icon.error) colors.error else colors.onSurfaceVariant,
                size = SyncGlyphSize,
            )
        }
    }
}

/**
 * The 48 dp retry buttons of [icons], each starting where its glyph in [SyncGlyphs] starts (laid out over the same
 * start), so a caller can also place them over other non-interactive content (Home puts them over the name).
 */
@Composable
internal fun SyncTargets(icons: List<SyncIcon>, modifier: Modifier = Modifier) {
    val pitch = SyncGlyphSize + gapOf(icons)
    Box(modifier.width(syncIconsWidth(icons))) {
        icons.forEachIndexed { i, icon ->
            val retry = icon.onRetry ?: return@forEachIndexed
            Box(
                Modifier
                    .offset(x = pitch * i)
                    .size(SyncTargetSize)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = retry)
                    .semantics { contentDescription = icon.description },
            )
        }
    }
}

/** How wide [SyncIcons] (or [SyncGlyphs] with its [SyncTargets]) is: 0 dp without icons. */
internal fun syncIconsWidth(icons: List<SyncIcon>): Dp {
    val pitch = SyncGlyphSize + gapOf(icons)
    return icons.indices.maxOfOrNull { i ->
        pitch * i + if (icons[i].onRetry != null) SyncTargetSize else SyncGlyphSize
    } ?: 0.dp
}

/** The glyphs' size: 20 dp, like the rows' icons (DESIGN_SPEC §6.1). */
internal val SyncGlyphSize: Dp = 20.dp
private val SyncTargetSize: Dp = 48.dp

/** 12 dp apart; when the first icon has a 48 dp button, the next glyph starts after it, so the buttons never overlap. */
private fun gapOf(icons: List<SyncIcon>): Dp =
    if (icons.size > 1 && icons.first().onRetry != null) SyncTargetSize - SyncGlyphSize else 12.dp

/** The phone's sentence for the watch sync (core's LabelFormatter), or null when there is no watch. */
internal fun syncLineText(sync: SyncStatus, zone: ZoneId, formatters: Formatters): String? =
    formatters.labels.syncLine(sync, SyncPeer.WATCH, formatters.clock, zone)

/** The watch with a tick, "…", a slash or "!" (DESIGN_SPEC §4.5). */
internal fun watchGlyph(sync: SyncStatus): ImageVector = when (sync) {
    is SyncStatus.Synced -> XIcons.watchSynced
    SyncStatus.Pending -> XIcons.watchPending
    SyncStatus.Error -> XIcons.watchError
    SyncStatus.Offline, SyncStatus.Unavailable -> XIcons.watchOffline
}

/** The cloud with a tick, an arrow, a slash or "!" (DESIGN_SPEC §4.5). */
internal fun cloudGlyph(cloud: CloudStatus): ImageVector = when (cloud) {
    is CloudStatus.Synced -> XIcons.synced
    CloudStatus.Syncing -> XIcons.pending
    CloudStatus.Offline, CloudStatus.Off -> XIcons.offline
    CloudStatus.Error, CloudStatus.Deleted, CloudStatus.Paused -> XIcons.cloudError
}
