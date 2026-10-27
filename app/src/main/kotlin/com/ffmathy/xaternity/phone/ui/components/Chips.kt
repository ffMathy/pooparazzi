package com.ffmathy.xaternity.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.phone.ui.home.OffsetChipUi
import com.ffmathy.xaternity.core.R as CoreR

/** Chip geometry (DESIGN_SPEC §3.3.1 "Chip", §5.8). */
object ChipDefaults {
    val TargetHeight: Dp = 48.dp
    val PillHeight: Dp = 40.dp
    val PillShape = RoundedCornerShape(20.dp)

    /** Offset chips are a fixed width so selecting one never moves its neighbours. */
    val OffsetWidth: Dp = 76.dp

    /** Disabled label and border (an inactive component, exempt from the no-alpha-on-text rule). */
    const val DISABLED_ALPHA = 0.38f
}

/**
 * The chip pattern: a 48 dp transparent target holding a 40 dp pill (r 20, 1 dp outline border, transparent fill).
 * Selected: primaryContainer fill, onPrimaryContainer label, the border kept, a leading `check` 16 + 4.
 * Disabled: label and border at 0.38. The selected state is exposed to accessibility services.
 *
 * @param width the pill's minimum width (offset chips: 76, so selecting one never moves its neighbours; it grows only at
 *   large font scales), or null to hug the content with 12 dp padding.
 * @param reserveCheck keep room for the check when unselected, so a hugging chip keeps its width when selected
 *   (a wrapping row never re-flows under the finger).
 * @param leading content before the label (e.g. a colour swatch).
 * @param showCheck false when [leading] shows the selection itself (a swatch with a check).
 * @param acceptsTaps false during a picker cooldown: the chip keeps its look but is exposed as disabled and ignores taps.
 */
@Composable
fun XChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp? = null,
    role: Role? = null,
    spokenLabel: String? = null,
    reserveCheck: Boolean = false,
    showCheck: Boolean = true,
    acceptsTaps: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val labelColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    Box(
        modifier = modifier
            .height(ChipDefaults.TargetHeight)
            .selectable(
                selected = selected,
                enabled = enabled && acceptsTaps,
                role = role,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(if (spokenLabel != null) Modifier.semantics { contentDescription = spokenLabel } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(ChipDefaults.PillHeight)
                .then(if (width != null) Modifier.widthIn(min = width) else Modifier)
                .alpha(if (enabled) 1f else ChipDefaults.DISABLED_ALPHA)
                .clip(ChipDefaults.PillShape)
                .background(if (selected) colors.primaryContainer else Color.Transparent)
                .border(1.dp, colors.outline, ChipDefaults.PillShape)
                .indication(interaction, ripple())
                .padding(horizontal = if (width != null) 4.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (reserveCheck && showCheck && !selected) {
                // Invisible twin with the check: holds the selected width. Hidden from accessibility services.
                ChipContent(true, label, labelColor, leading, Modifier.alpha(0f).clearAndSetSemantics { })
            }
            ChipContent(selected && showCheck, label, labelColor, leading)
        }
    }
}

@Composable
private fun ChipContent(
    showCheck: Boolean,
    label: String,
    color: Color,
    leading: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showCheck) XIcon(XIcons.check, contentDescription = null, tint = color, size = 16.dp)
        leading?.invoke(this)
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** An offset chip: "Now", "−5m", "+5m"; spoken "5 minutes earlier" (DESIGN_SPEC §9.5). */
@Composable
fun OffsetChip(
    chip: OffsetChipUi,
    formatters: Formatters,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role? = null,
    acceptsTaps: Boolean = true,
) {
    val now = stringResource(CoreR.string.action_now)
    XChip(
        selected = chip.selected,
        onClick = onClick,
        label = if (chip.minutes == 0) now else formatters.duration.offset(chip.minutes),
        modifier = modifier,
        enabled = chip.enabled,
        width = ChipDefaults.OffsetWidth,
        role = role,
        spokenLabel = if (chip.minutes == 0) null else formatters.relative.offsetSpoken(chip.minutes),
        reserveCheck = true,
        acceptsTaps = acceptsTaps,
    )
}
