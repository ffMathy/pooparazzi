package com.ffmathy.pooparazzi.wear.diaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.PoopSwatches
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.CHIP_PADDING
import com.ffmathy.pooparazzi.wear.ui.ChoiceChip
import com.ffmathy.pooparazzi.wear.ui.SectionHeader
import com.ffmathy.pooparazzi.wear.ui.Swatch
import com.ffmathy.pooparazzi.wear.ui.choiceGridItems
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.gridCellWidth
import com.ffmathy.pooparazzi.wear.ui.listContentWidth
import com.ffmathy.pooparazzi.wear.ui.rememberGridColumns
import com.ffmathy.pooparazzi.wear.ui.swatchCheckInk
import com.ffmathy.pooparazzi.core.R as CoreR

/*
 * Poop's colour and texture on the Diaper screen, shown while Poop is ticked (V1_ADDITIONS §3.2): a "Colour" header
 * over six swatch chips (Yellow, Green, Brown, Black, Red, Pale), then "Texture" over five chips in firmness order.
 * Each is optional; tapping the chosen chip again clears it.
 */

/** The colour and texture grids' columns and cell widths, measured once per configuration. */
@Immutable
internal data class PoopDetailLayout(val colourColumns: Int, val colourCell: Dp, val textureColumns: Int, val textureCell: Dp)

@Composable
internal fun rememberPoopDetailLayout(): PoopDetailLayout {
    val width = listContentWidth()
    val colours = rememberGridColumns(PoopColor.entries.map { stringResource(Labels.poopColor(it)) }, chipLabelStyle(), chrome = CHIP_PADDING * 2)
    val textures = rememberGridColumns(PoopTexture.entries.map { stringResource(Labels.poopTexture(it)) }, chipLabelStyle(), chrome = CHIP_PADDING * 2)
    return PoopDetailLayout(colours, gridCellWidth(width, colours), textures, gridCellWidth(width, textures))
}

/**
 * The colour and texture items (headers and grid rows), for the list builder while Poop is on. The chips are
 * [enabled] (else faded to [alpha]) after the screen's cooldowns.
 */
internal fun TransformingLazyColumnScope.poopDetailItems(
    color: PoopColor?,
    texture: PoopTexture?,
    layout: PoopDetailLayout,
    enabled: Boolean,
    alpha: Float,
    onColour: (PoopColor?) -> Unit,
    onTexture: (PoopTexture?) -> Unit,
    spec: TransformationSpec,
) {
    item(key = "colourHeader") { SectionHeader(stringResource(CoreR.string.poop_colour), Modifier.transformedHeight(this, spec)) }
    choiceGridItems("colour", PoopColor.entries.size, layout.colourColumns, layout.colourCell, spec) { index, modifier ->
        val c = PoopColor.entries[index]
        val selected = c == color
        ColourChip(c, selected, stacked = layout.colourColumns > 1, onClick = { onColour(if (selected) null else c) }, enabled, modifier.alpha(alpha))
    }
    item(key = "textureHeader") { SectionHeader(stringResource(CoreR.string.poop_texture), Modifier.transformedHeight(this, spec)) }
    choiceGridItems("texture", PoopTexture.entries.size, layout.textureColumns, layout.textureCell, spec) { index, modifier ->
        val t = PoopTexture.entries[index]
        val selected = t == texture
        TextureChip(t, selected, onClick = { onTexture(if (selected) null else t) }, enabled, modifier.alpha(alpha))
    }
}

/**
 * A colour chip: its swatch (the true colour, on the neutral fill) over its name, or beside it when the grid has one
 * column. Chosen: DIAPER lit with a check on the swatch, in an ink that shows on that swatch.
 */
@Composable
private fun ColourChip(color: PoopColor, selected: Boolean, stacked: Boolean, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val ambient = isAmbient()
    val name = stringResource(Labels.poopColor(color))
    ChoiceChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        event = events().diaper,
        modifier = modifier,
        minHeight = if (stacked) 56.dp else 52.dp,
    ) {
        val swatch = @Composable {
            Box(contentAlignment = Alignment.Center) {
                Swatch(PoopSwatches.color(color), size = SWATCH)
                if (selected) XIcon(XIcons.check, null, tint = if (ambient) WatchAmbient.content else swatchCheckInk(color), size = 12.dp)
            }
        }
        if (stacked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                swatch()
                Spacer(Modifier.height(4.dp))
                Text(name, style = chipLabelStyle(), maxLines = 1)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                swatch()
                Spacer(Modifier.width(8.dp))
                Text(name, style = chipLabelStyle(), maxLines = 1)
            }
        }
    }
}

/** A texture chip: an empty ring over the name, the ring becoming a DIAPER strong check when chosen (same size). */
@Composable
private fun TextureChip(texture: PoopTexture, selected: Boolean, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val ambient = isAmbient()
    val ring = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant
    ChoiceChip(selected = selected, onClick = onClick, enabled = enabled, event = events().diaper, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(TEXTURE_GLYPH), contentAlignment = Alignment.Center) {
                if (selected) {
                    XIcon(XIcons.check, null, tint = if (ambient) WatchAmbient.content else events().diaper.strong, size = TEXTURE_GLYPH)
                } else {
                    Canvas(Modifier.size(TEXTURE_GLYPH)) {
                        val stroke = 1.5.dp.toPx()
                        drawCircle(ring, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Labels.poopTexture(texture)), style = chipLabelStyle(), maxLines = 1)
        }
    }
}

/** A chip's label: labelSmall, weight 600. */
@Composable
private fun chipLabelStyle(): TextStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(600))

private val SWATCH = 20.dp
private val TEXTURE_GLYPH = 16.dp
