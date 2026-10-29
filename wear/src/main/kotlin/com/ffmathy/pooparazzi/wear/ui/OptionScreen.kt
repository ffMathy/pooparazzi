package com.ffmathy.pooparazzi.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.isAmbient

/**
 * One choice of an option list: its [value] (null for "None"), label, an optional swatch colour, and whether it is the
 * current value.
 */
data class Option<T>(val value: T?, val label: String, val swatch: Color?, val selected: Boolean)

/**
 * A single-select option list under a [title]: radio buttons, the current value selected. One tap chooses; the caller
 * returns to the screen that opened it. 500 ms tap cooldown. The poop colour and texture lists use it, and so does
 * the baby picker (no swatches there).
 */
@Composable
fun <T> OptionScreen(title: String, options: List<Option<T>>, onSelect: (T?) -> Unit, modifier: Modifier = Modifier) {
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
                        label = { Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}
