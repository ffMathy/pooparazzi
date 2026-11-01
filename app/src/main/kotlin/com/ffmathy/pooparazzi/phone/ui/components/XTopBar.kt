package com.ffmathy.pooparazzi.phone.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.ui.icons.XIcon

/**
 * A 64 dp top bar (DESIGN_SPEC §3.3.3): a 48 dp icon button 8 from the start (`back` 24, onSurfaceVariant) and the
 * title (titleLarge, a heading) at 64. [below] adds lines under the title, such as the status icons. [actions] adds
 * icon buttons at the end (History's `chart`): the row's end padding is then 8, so a 24 dp glyph in a 48 dp target
 * sits 20 from the edge, mirroring Back. [actions] comes before [below], so a trailing lambda still means [below].
 */
@Composable
internal fun XTopBar(
    title: String,
    navigationIcon: ImageVector,
    navigationLabel: String,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 8.dp, end = if (actions != null) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTarget(navigationIcon, navigationLabel, onNavigate)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            below?.invoke(this)
        }
        if (actions != null) {
            Spacer(Modifier.width(4.dp))
            actions(this)
        }
    }
}

/** A 48 dp round icon button drawn with an [XIcon] (no Material icon dependency). */
@Composable
internal fun IconTarget(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XIcon(icon, contentDescription = label, tint = tint, size = iconSize)
    }
}

/**
 * A 48 dp tall pill button with an [XIcon] and its [label] (History's "Statistics", Statistics' "History"):
 * `icon` 20 + 6 + labelLarge, both onSurfaceVariant, 12 inside each end. The label is the button's name, so the icon is not read.
 */
@Composable
internal fun LabelTarget(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XIcon(icon, contentDescription = null, tint = color, size = 20.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}
