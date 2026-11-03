package com.ffmathy.pooparazzi.wear.diaper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.CheckboxButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextToggleButton
import androidx.wear.compose.material3.TextToggleButtonDefaults
import androidx.wear.compose.material3.TextToggleButtonShapes
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.GRID_GAP
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.listContentWidth
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * How the Diaper screen shows Pee and Poop: side by side as two [height]-tall tiles when both labels fit ([tiles]),
 * else as two full-width [DiaperCheckbox] rows (English at 200 % on a small watch). Measured once per configuration.
 */
@Immutable
internal data class KindLayout(val tiles: Boolean, val height: Dp)

/**
 * Tiles while each label fits its tile with 8 dp either side, and the tile is at least 64 dp wide (48 + 16); a tile is
 * at least 60 dp tall, taller when the font scale makes the label taller (glyphs 24 + 14 + the label's line).
 */
@Composable
internal fun rememberKindLayout(): KindLayout {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val style = MaterialTheme.typography.labelMedium
    val labels = listOf(stringResource(CoreR.string.diaper_pee), stringResource(CoreR.string.diaper_poop))
    val tile = (listContentWidth() - GRID_GAP) / 2
    return remember(labels, style, density, configuration.fontScale, configuration.screenWidthDp) {
        val widest = labels.maxOf { with(density) { measurer.measure(it, style, maxLines = 1, softWrap = false, density = density).size.width.toDp() } }
        val height = max(TILE_MIN_HEIGHT, 14.dp + 24.dp + with(density) { style.lineHeight.toDp() })
        KindLayout(tiles = widest + 16.dp <= tile && 48.dp + 16.dp <= tile, height = height)
    }
}

/**
 * Pee | Poop as two tiles in a ButtonGroup (6 dp apart, nothing widens on press, no group padding, so the tiles are as
 * wide as the rows below them), the size of Main's Feed and Diaper buttons: the checkbox glyph and the kind's icon over
 * the label. Off: the kind's container; on: the DIAPER lit fill with a 2 dp DIAPER strong border and a ticked box
 * (V1_ADDITIONS §3.1), so the state is never colour alone. Shapes never change (§5.8): r 26 in all four states.
 * Announced as checkboxes ("Pee, checkbox, not checked"). Ambient: outlines only.
 */
@Composable
internal fun KindTiles(
    pee: Boolean,
    poop: Boolean,
    onPee: (Boolean) -> Unit,
    onPoop: (Boolean) -> Unit,
    enabled: Boolean,
    alpha: Float,
    height: Dp,
    transformation: SurfaceTransformation,
    modifier: Modifier,
) {
    val events = events()
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
        spacing = GRID_GAP,
        expansionWidth = 0.dp,
        // The list already applies LIST_SIDE_PADDING; the default fullWidthPaddings() would inset the tiles further.
        contentPadding = PaddingValues(0.dp),
        transformation = transformation,
    ) {
        KindTile(stringResource(CoreR.string.diaper_pee), XIcons.pee, pee, onPee, events.pee, enabled, Modifier.weight(1f).height(height).alpha(alpha))
        KindTile(stringResource(CoreR.string.diaper_poop), XIcons.poop, poop, onPoop, events.poop, enabled, Modifier.weight(1f).height(height).alpha(alpha))
    }
}

@Composable
private fun KindTile(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EventColors,
    enabled: Boolean,
    modifier: Modifier,
) {
    val ambient = isAmbient()
    val diaper = events().diaper
    val container = if (ambient) Color.Transparent else colors.container
    val lit = if (ambient) Color.Transparent else diaper.containerLit
    val content = if (ambient) WatchAmbient.content else colors.onContainer
    val box = when {
        ambient -> WatchAmbient.content
        checked -> diaper.strong
        else -> colors.onContainer.copy(alpha = 0.7f)
    }
    val border = when {
        ambient -> BorderStroke(2.dp, colors.strong)
        checked -> BorderStroke(2.dp, diaper.strong)
        else -> null
    }
    TextToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // "Pee, checkbox, not checked" (V1_ADDITIONS §3.1), set outermost so it wins.
        modifier = Modifier.semantics { role = Role.Checkbox }.then(modifier),
        enabled = enabled,
        colors = TextToggleButtonDefaults.colors(
            checkedContainerColor = lit,
            checkedContentColor = content,
            uncheckedContainerColor = container,
            uncheckedContentColor = content,
            // The cooldown fades the tiles in; their colours stay.
            disabledCheckedContainerColor = lit,
            disabledCheckedContentColor = content,
            disabledUncheckedContainerColor = container,
            disabledUncheckedContentColor = content,
        ),
        // All four states (unchecked, checked and both pressed) r 26: shapes(Shape) sets only the unchecked one.
        shapes = TextToggleButtonShapes(WatchShapes.Button),
        border = border,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { }) {
                XIcon(if (checked) XIcons.checkboxChecked else XIcons.checkbox, null, tint = box, size = 20.dp)
                Spacer(Modifier.width(4.dp))
                XIcon(icon, null, tint = if (ambient) WatchAmbient.content else colors.strong, size = 24.dp)
            }
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
        }
    }
}

/**
 * A Pee / Poop checkbox row, where the tiles do not fit: the kind's container when off; the DIAPER lit fill with the
 * checked box in DIAPER strong when on (V1_ADDITIONS §3.2: `CheckboxButton`s). The state is in the button's own
 * colours, so it morphs and fades at the screen edge like every other list item; the checkbox glyph keeps it from
 * being colour alone. Announced as a checkbox with its state. The label wraps onto a second line.
 */
@Composable
internal fun DiaperCheckbox(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EventColors,
    lit: Color,
    outline: Color,
    enabled: Boolean,
    transformation: SurfaceTransformation,
    modifier: Modifier,
) {
    val ambient = isAmbient()
    val content = if (ambient) WatchAmbient.content else colors.onContainer
    CheckboxButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .fillMaxWidth()
            // "Pee, checkbox, not checked" (V1_ADDITIONS §3.1); the Wear CheckboxButton sets no role of its own.
            .semantics { role = Role.Checkbox }
            // Ambient has no fills: the outline carries the shape (the list does not scroll in ambient).
            .then(if (ambient) Modifier.border(BorderStroke(2.dp, colors.strong), WatchShapes.Button) else Modifier),
        enabled = enabled,
        transformation = transformation,
        shape = WatchShapes.Button,
        colors = CheckboxButtonDefaults.checkboxButtonColors(
            checkedContainerColor = if (ambient) Color.Transparent else lit,
            checkedContentColor = content,
            checkedIconColor = if (ambient) WatchAmbient.content else colors.strong,
            checkedBoxColor = if (ambient) WatchAmbient.content else outline,
            checkedCheckmarkColor = if (ambient) WatchAmbient.content else colors.onContainer,
            uncheckedContainerColor = if (ambient) Color.Transparent else colors.container,
            uncheckedContentColor = content,
            uncheckedIconColor = if (ambient) WatchAmbient.content else colors.strong,
            uncheckedBoxColor = if (ambient) WatchAmbient.content else colors.onContainer.copy(alpha = 0.7f),
        ),
        icon = { XIcon(icon, null, tint = if (ambient) WatchAmbient.content else colors.strong, size = 24.dp) },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                maxLines = 2,
            )
        },
    )
}

private val TILE_MIN_HEIGHT = 60.dp
