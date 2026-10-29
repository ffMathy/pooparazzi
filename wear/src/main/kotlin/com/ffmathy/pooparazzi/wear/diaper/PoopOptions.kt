package com.ffmathy.pooparazzi.wear.diaper

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.ui.tokens.PoopSwatches
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.ui.Option
import com.ffmathy.pooparazzi.wear.ui.OptionScreen
import com.ffmathy.pooparazzi.core.R as CoreR

/** The six colours (each with its swatch dot), then "None" (V1_ADDITIONS §3.2). */
@Composable
fun colourOptions(selected: PoopColor?): List<Option<PoopColor>> =
    PoopColor.entries.map { Option(it, stringResource(Labels.poopColor(it)), PoopSwatches.color(it), it == selected) } +
        Option(null, stringResource(CoreR.string.none), null, selected == null)

/** The five textures, then "None". */
@Composable
fun textureOptions(selected: PoopTexture?): List<Option<PoopTexture>> =
    PoopTexture.entries.map { Option(it, stringResource(Labels.poopTexture(it)), null, it == selected) } +
        Option(null, stringResource(CoreR.string.none), null, selected == null)

@WearPreviewDevices
@Composable
private fun ColourListPreview() = XWearTheme {
    OptionScreen(stringResource(CoreR.string.poop_colour), colourOptions(PoopColor.YELLOW), onSelect = {})
}

@WearPreviewDevices
@Composable
private fun TextureListPreview() = XWearTheme {
    OptionScreen(stringResource(CoreR.string.poop_texture), textureOptions(PoopTexture.SEEDY), onSelect = {})
}
