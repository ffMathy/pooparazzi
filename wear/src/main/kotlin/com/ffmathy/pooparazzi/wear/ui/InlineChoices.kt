package com.ffmathy.pooparazzi.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.isAmbient

/*
 * Choices made inside a list, without opening another screen (Bottle, Diaper and the inline time): grids of chips
 * whose column count is measured, not guessed (DESIGN_SPEC §9.3), section headers, and the chip itself.
 */

/** The gap between the cells of a row (§5.9). */
val GRID_GAP = 6.dp

/** The list's side padding on the pickers: items are [listContentWidth] wide. */
val LIST_SIDE_PADDING = 10.dp

/** The width of a picker list's items: the screen less the list's side padding. */
@Composable
@ReadOnlyComposable
fun listContentWidth(): Dp = LocalConfiguration.current.screenWidthDp.dp - LIST_SIDE_PADDING * 2

/** One cell's width in a grid of [columns] across [width] ([GRID_GAP] between cells). */
fun gridCellWidth(width: Dp, columns: Int): Dp = (width - GRID_GAP * (columns - 1)) / columns

/**
 * The most columns, at most [max], in which every one of [labels] fits on one line in [style] (the style it is drawn
 * in) with [chrome] (the cell's padding and anything beside the text) in a cell of [gridCellWidth]. Remembered per
 * configuration (font scale, density, screen width), so a layout never flips while the screen is open. Text in a grid
 * never autosizes: columns drop instead.
 *
 * Call it in the screen's composable, before the list's builder (which is not composable).
 */
@Composable
fun rememberGridColumns(labels: List<String>, style: TextStyle, chrome: Dp, max: Int = 3): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val width = listContentWidth()
    return remember(labels, style, chrome, max, density, configuration.fontScale, configuration.screenWidthDp) {
        val widest = labels.maxOfOrNull { label ->
            with(density) { measurer.measure(label, style, maxLines = 1, softWrap = false, density = density).size.width.toDp() }
        } ?: 0.dp
        (max downTo 1).firstOrNull { columns -> widest + chrome <= gridCellWidth(width, columns) } ?: 1
    }
}

/**
 * A grid of [count] cells in rows of [columns], one list item per row (keys "[keyPrefix]0", "[keyPrefix]1", …). Each
 * row is centred with [GRID_GAP] between its cells, so a short last row sits in the middle at the same cell width.
 * The list's transformation is applied once, to the whole row ([rowTransformation]); the cells get none.
 */
fun TransformingLazyColumnScope.choiceGridItems(
    keyPrefix: String,
    count: Int,
    columns: Int,
    cellWidth: Dp,
    spec: TransformationSpec,
    cell: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val rows = (count + columns - 1) / columns
    for (row in 0 until rows) {
        item(key = "$keyPrefix$row") {
            Row(
                Modifier.fillMaxWidth().transformedHeight(this, spec).rowTransformation(this, spec),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (index in row * columns until minOf(count, (row + 1) * columns)) cell(index, Modifier.width(cellWidth))
            }
        }
    }
}

/**
 * The list's morph and fade at the screen edge, for an item made of several controls side by side: applied once to
 * the whole row (the call ButtonGroup makes inside), never to each control.
 */
fun Modifier.rowTransformation(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier {
    val transformation = with(scope) { SurfaceTransformation(spec) }
    return graphicsLayer { with(transformation) { applyContainerTransformation() } }
}

/**
 * An inline choice (r 20, at least [minHeight] tall): `surfaceContainer` when not chosen; the [event]'s lit fill
 * with a 2 dp border in its strong colour when chosen (the chip's content adds a check, so it is never colour
 * alone). Its shape never changes (§5.8). Ambient: no fill, a 1 dp outline, or 2 dp in the ambient ink when chosen.
 *
 * Announced as a radio button with its state; tapping a chosen one clears it, so it then offers "Clear" as its
 * click label. [contentDescription] replaces what the content reads (the content is then not read).
 */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    event: EventColors,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    minHeight: Dp = 52.dp,
    content: @Composable () -> Unit,
) {
    val ambient = isAmbient()
    val scheme = MaterialTheme.colorScheme
    val clear = stringResource(R.string.action_clear)
    val container = when {
        ambient -> Color.Transparent
        selected -> event.containerLit
        else -> scheme.surfaceContainer
    }
    val ink = when {
        ambient -> WatchAmbient.content
        selected -> event.onContainer
        else -> scheme.onSurface
    }
    val border = when {
        ambient && selected -> BorderStroke(2.dp, WatchAmbient.content)
        ambient -> BorderStroke(1.dp, scheme.outline)
        selected -> BorderStroke(2.dp, event.strong)
        else -> null
    }
    Button(
        onClick = onClick,
        // Outermost, so these win over the Button's own role and click action.
        modifier = Modifier
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                if (contentDescription != null) this.contentDescription = contentDescription
                if (selected) onClick(label = clear) { onClick(); true }
            }
            .then(modifier)
            .heightIn(min = minHeight),
        enabled = enabled,
        shape = WatchShapes.Chip,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = ink,
            // The cooldown fades the chips in; they keep their colours meanwhile.
            disabledContainerColor = container,
            disabledContentColor = ink,
        ),
        border = border,
        contentPadding = PaddingValues(CHIP_PADDING),
    ) {
        Box(
            Modifier.fillMaxWidth().then(if (contentDescription != null) Modifier.clearAndSetSemantics { } else Modifier),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** A chip's padding on every side: its content has the cell's width less twice this. */
val CHIP_PADDING = 6.dp

/** A heading inside a list ("Colour", "Texture"): labelMedium, centred, at least 24 dp tall; heading navigation jumps to it. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 24.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isAmbient()) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** The check drawn on a chosen colour's swatch: dark on the light swatches, white on the dark ones. */
fun swatchCheckInk(color: PoopColor): Color = when (color) {
    PoopColor.YELLOW, PoopColor.GREEN, PoopColor.PALE -> Color(0xFF1B1611)
    PoopColor.BROWN, PoopColor.BLACK, PoopColor.RED -> Color.White
}
