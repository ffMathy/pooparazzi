package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.components.SyncGlyphs
import com.ffmathy.pooparazzi.phone.ui.components.SyncIcon
import com.ffmathy.pooparazzi.phone.ui.components.SyncIcons
import com.ffmathy.pooparazzi.phone.ui.components.SyncTargets
import com.ffmathy.pooparazzi.phone.ui.components.syncIcons
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * Home's top bar (DESIGN_SPEC §3.3.1): the baby's name, the status icons under it (the watch, and the cloud while
 * signed in; an icon in its error state is a retry button), the History, Statistics and Settings icon buttons. In lights-down the name and
 * icons use onSurfaceVariant.
 *
 * With two or more [babies] (BABIES_AND_BACKUP §2.4) the name is the current baby's label on a 48 dp button with a
 * chevron that opens the Switch baby sheet; the icons' retry targets then sit under it instead of over the name.
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
    babies: List<Baby> = listOf(Baby(Baby.FIRST_ID, babyName)),
    babyId: String = babies.first().id,
    cloud: CloudStatus = CloudStatus.Off,
) {
    val colors = MaterialTheme.colorScheme
    val chrome = if (lightsDown) colors.onSurfaceVariant else colors.onSurface
    val several = babies.size > 1
    val icons = syncIcons(sync, cloud, zone, formatters, actions::onRetrySync, actions::onRetryBackup)
    Box(modifier.fillMaxWidth()) {
        // Top-aligned: the name sits on the icon buttons' centre line (32 dp down) and the status icons hang below it.
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (several) {
                    val baby = babies.firstOrNull { it.id == babyId } ?: babies.first()
                    SwitcherColumn(
                        label = formatters.labels.baby(baby, babies),
                        named = baby.name.isNotBlank(),
                        chrome = chrome,
                        icons = icons,
                        actions = actions,
                    )
                } else {
                    Column(Modifier.padding(start = 20.dp, end = TopBarGap, top = 12.dp, bottom = 4.dp)) {
                        val name = babyName.ifBlank { stringResource(CoreR.string.baby_name_hint) }
                        // 40 dp, centred: the name shares the icon buttons' centre line.
                        Box(Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (babyName.isBlank()) colors.onSurfaceVariant else chrome,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        if (icons.isNotEmpty()) SyncGlyphs(icons)
                    }
                }
                if (!several && icons.any { it.onRetry != null }) {
                    // An icon in its error state ("Couldn't save · Tap to retry") is a 48 dp button over its glyph,
                    // reaching up over the name only.
                    SyncTargets(icons, Modifier.align(Alignment.BottomStart).padding(start = 20.dp))
                }
            }
            TopBarIcon(XIcons.history, stringResource(CoreR.string.nav_history), actions::onOpenHistory)
            // Statistics one tap from Home, so it is found without going through History first.
            TopBarIcon(XIcons.chart, stringResource(R.string.statistics_title), actions::onOpenStatistics)
            Box(
                Modifier
                    .padding(start = TopBarGap, end = 12.dp, top = 8.dp)
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

/**
 * The name as the switcher (two or more babies): the [label] and a 16 dp chevron on a 48 dp button
 * ("Noa. Switch baby"), then the status icons. In an error state the icons are their own 48 dp row under the button,
 * so the targets never overlap.
 */
@Composable
private fun SwitcherColumn(
    label: String,
    named: Boolean,
    chrome: Color,
    icons: List<SyncIcon>,
    actions: HomeActions,
) {
    val color = if (named) chrome else MaterialTheme.colorScheme.onSurfaceVariant
    val spoken = stringResource(CoreR.string.cd_baby_switch, label)
    Column(Modifier.padding(start = 12.dp, end = TopBarGap, top = 8.dp, bottom = 4.dp)) {
        Row(
            Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = actions::onSwitchBaby)
                .semantics {
                    contentDescription = spoken
                    heading()
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clearAndSetSemantics {},
            )
            Spacer(Modifier.width(4.dp))
            XIcon(XIcons.chevronDown, contentDescription = null, tint = color, size = 16.dp)
        }
        SyncIcons(icons, Modifier.padding(horizontal = 8.dp))
    }
}

/** A 48 dp icon button of the top bar (History, Statistics): the [icon] 24 in onSurfaceVariant, named [label]. */
@Composable
private fun TopBarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = TopBarGap, top = 8.dp)
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XIcon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 24.dp)
    }
}
