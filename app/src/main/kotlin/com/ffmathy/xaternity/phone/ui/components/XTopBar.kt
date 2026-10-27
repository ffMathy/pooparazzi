package com.ffmathy.xaternity.phone.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import com.ffmathy.xaternity.core.ui.icons.XIcon

/**
 * A 64 dp top bar (DESIGN_SPEC §3.3.3): a 48 dp icon button 8 from the start (`back` 24, onSurfaceVariant) and the
 * title (titleLarge, a heading) at 64. [below] adds lines under the title, such as the sync line.
 */
@Composable
internal fun XTopBar(
    title: String,
    navigationIcon: ImageVector,
    navigationLabel: String,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 8.dp, end = 16.dp),
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
