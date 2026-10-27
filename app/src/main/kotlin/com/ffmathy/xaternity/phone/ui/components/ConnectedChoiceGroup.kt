package com.ffmathy.xaternity.phone.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.phone.ui.theme.LocalNightLightMotion

/** Geometry of the connected choice group. */
internal object ConnectedGroupDefaults {
    val MinHeight: Dp = 48.dp
    val Gap: Dp = 2.dp

    /** Outer corners of the group (as the History list group, DESIGN_SPEC §5.8). */
    val OuterCorner: Dp = 20.dp

    /** Corners between neighbours. */
    val InnerCorner: Dp = 6.dp

    /** A selected button rounds all its corners, so the selection reads by shape as well as by fill. */
    val SelectedCorner: Dp = 24.dp
}

/**
 * A single-select connected button group (V1_ADDITIONS §4: "Breast | Formula | Food", the four sides; Settings:
 * "Auto | Night | Day"). Neutral chrome, never an event hue, in the chips' language (DESIGN_SPEC §3.3.1): every button
 * has a 1 dp outline border and a transparent fill; the selected one fills primaryContainer, keeps the border, gains a
 * leading `check` 16 and fully rounded corners (shape, check and fill all tell the selection).
 *
 * Buttons are laid out [columns] per row with 2 dp gaps; the group's outer corners are r 20 and the inner ones r 6.
 * Labels may wrap to two lines (Danish "Modermælks­erstatning" at its soft hyphen); every button in a row then takes
 * the row's height. Exposed as a radio group.
 *
 * @param selected the selected option, or null when none is (e.g. a side not picked yet).
 * @param spokenLabel a TalkBack name when the visible label reads badly ("Left → Right" → "left then right").
 * @param weight each option's share of its row (equal by default); a long label can get more room.
 */
@Composable
internal fun <T> ConnectedChoiceGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    spokenLabel: (@Composable (T) -> String)? = null,
    weight: (T) -> Float = { 1f },
    columns: Int = options.size,
    enabled: Boolean = true,
) {
    val rows = options.chunked(columns)
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ConnectedGroupDefaults.Gap)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(ConnectedGroupDefaults.Gap),
            ) {
                row.forEachIndexed { columnIndex, option ->
                    val position = CellPosition(
                        top = rowIndex == 0,
                        bottom = rowIndex == rows.lastIndex,
                        start = columnIndex == 0,
                        end = columnIndex == row.lastIndex,
                    )
                    ChoiceButton(
                        label = label(option),
                        spoken = spokenLabel?.invoke(option),
                        selected = option == selected,
                        enabled = enabled,
                        position = position,
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .weight(weight(option))
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** Where a button sits in the group: its outer corners follow the group's. */
private data class CellPosition(val top: Boolean, val bottom: Boolean, val start: Boolean, val end: Boolean)

@Composable
private fun ChoiceButton(
    label: String,
    spoken: String?,
    selected: Boolean,
    enabled: Boolean,
    position: CellPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = LocalNightLightMotion.current
    fun corner(outer: Boolean) = if (outer) ConnectedGroupDefaults.OuterCorner else ConnectedGroupDefaults.InnerCorner
    val selectedCorner = ConnectedGroupDefaults.SelectedCorner
    val topStart by animateDpAsState(if (selected) selectedCorner else corner(position.top && position.start), motion.fastSpatialSpec())
    val topEnd by animateDpAsState(if (selected) selectedCorner else corner(position.top && position.end), motion.fastSpatialSpec())
    val bottomStart by animateDpAsState(if (selected) selectedCorner else corner(position.bottom && position.start), motion.fastSpatialSpec())
    val bottomEnd by animateDpAsState(if (selected) selectedCorner else corner(position.bottom && position.end), motion.fastSpatialSpec())
    val shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    Box(
        modifier
            .heightIn(min = ConnectedGroupDefaults.MinHeight)
            .alpha(if (enabled) 1f else ChipDefaults.DISABLED_ALPHA)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .border(1.dp, colors.outline, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (selected) {
                XIcon(XIcons.check, contentDescription = null, tint = content, size = 16.dp)
                Box(Modifier.width(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(hyphens = Hyphens.Auto, textAlign = TextAlign.Center),
                color = content,
                maxLines = 2,
            )
        }
    }
}
