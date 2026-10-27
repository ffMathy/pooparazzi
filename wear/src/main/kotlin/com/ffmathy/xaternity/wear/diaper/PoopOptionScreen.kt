package com.ffmathy.xaternity.wear.diaper

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.RadioButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import com.ffmathy.xaternity.core.ui.tokens.PoopSwatches
import com.ffmathy.xaternity.wear.theme.WatchAmbient
import com.ffmathy.xaternity.wear.theme.XWearTheme
import com.ffmathy.xaternity.wear.theme.isAmbient
import com.ffmathy.xaternity.wear.ui.ScreenTitle
import com.ffmathy.xaternity.wear.ui.Swatch
import com.ffmathy.xaternity.wear.ui.WatchShapes
import com.ffmathy.xaternity.wear.ui.rememberCooldown
import com.ffmathy.xaternity.core.R as CoreR

/** One choice of an option list: its label, an optional swatch colour, and whether it is the current value. */
data class PoopOption<T>(val value: T?, val label: String, val swatch: Color?, val selected: Boolean)

/** The six colours (each with its swatch dot), then "None" (V1_ADDITIONS §3.2). */
@Composable
fun colourOptions(selected: PoopColor?): List<PoopOption<PoopColor>> =
    PoopColor.entries.map { PoopOption(it, stringResource(Labels.poopColor(it)), PoopSwatches.color(it), it == selected) } +
        PoopOption(null, stringResource(CoreR.string.none), null, selected == null)

/** The five textures, then "None". */
@Composable
fun textureOptions(selected: PoopTexture?): List<PoopOption<PoopTexture>> =
    PoopTexture.entries.map { PoopOption(it, stringResource(Labels.poopTexture(it)), null, it == selected) } +
        PoopOption(null, stringResource(CoreR.string.none), null, selected == null)

/**
 * A single-select option list for the poop colour or texture: radio buttons, the current value selected. One tap
 * sets the value and returns to the Diaper screen. 500 ms tap cooldown.
 */
@Composable
fun <T> PoopOptionScreen(title: String, options: List<PoopOption<T>>, onSelect: (T?) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    val ambient = isAmbient()
    val scheme = MaterialTheme.colorScheme
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "title") { ScreenTitle(title, Modifier.transformedHeight(this, spec)) }
            options.forEach { option ->
                item(key = option.value?.toString() ?: "none") {
                    RadioButton(
                        selected = option.selected,
                        onSelect = { onSelect(option.value) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        enabled = cooldown.ready,
                        shape = WatchShapes.Button,
                        colors = RadioButtonDefaults.radioButtonColors(
                            selectedContainerColor = if (ambient) Color.Transparent else scheme.primaryContainer,
                            selectedContentColor = if (ambient) WatchAmbient.content else scheme.onPrimaryContainer,
                            selectedControlColor = if (ambient) WatchAmbient.content else scheme.onPrimaryContainer,
                            selectedIconColor = Color.Unspecified,
                            unselectedContainerColor = if (ambient) Color.Transparent else scheme.surfaceContainer,
                            unselectedContentColor = if (ambient) WatchAmbient.content else scheme.onSurface,
                            unselectedIconColor = Color.Unspecified,
                        ),
                        transformation = SurfaceTransformation(spec),
                        icon = option.swatch?.let { swatch -> { Swatch(swatch) } },
                        label = { Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 2) },
                    )
                }
            }
        }
    }
}

@WearPreviewDevices
@Composable
private fun ColourListPreview() = XWearTheme {
    PoopOptionScreen(stringResource(CoreR.string.poop_colour), colourOptions(PoopColor.YELLOW), onSelect = {})
}

@WearPreviewDevices
@Composable
private fun TextureListPreview() = XWearTheme {
    PoopOptionScreen(stringResource(CoreR.string.poop_texture), textureOptions(PoopTexture.SEEDY), onSelect = {})
}
