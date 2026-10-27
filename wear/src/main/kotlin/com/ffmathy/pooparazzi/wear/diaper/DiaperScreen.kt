package com.ffmathy.pooparazzi.wear.diaper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.CheckboxButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.core.ui.tokens.PoopSwatches
import com.ffmathy.pooparazzi.wear.feed.EarlierButton
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.DiaperDraft
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.ScreenTitle
import com.ffmathy.pooparazzi.wear.ui.Swatch
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
import com.ffmathy.pooparazzi.wear.ui.XListButton
import com.ffmathy.pooparazzi.wear.ui.ambientBorder
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.rememberCooldown
import com.ffmathy.pooparazzi.core.R as CoreR

@Immutable
data class DiaperActions(
    val onPee: (Boolean) -> Unit = {},
    val onPoop: (Boolean) -> Unit = {},
    val onColour: () -> Unit = {},
    val onTexture: () -> Unit = {},
    val onEarlier: () -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * Diaper (V1_ADDITIONS §3.2): "Diaper · 14:32" (stamped on the Diaper tap), CheckboxButtons Pee and Poop (both off
 * at first); with Poop on, Colour and Texture show their value or "Optional" and open option lists; Earlier cycles
 * the time; the pinned Medium EdgeButton "Save" is always enabled (nothing ticked logs a plain diaper change).
 * 500 ms tap cooldown.
 */
@Composable
fun DiaperScreen(draft: DiaperDraft, actions: DiaperActions, modifier: Modifier = Modifier) {
    val f = rememberFormatters(draft.zone)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    val events = events()
    val ambient = isAmbient()
    val optional = stringResource(CoreR.string.optional)

    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            TransformingLazyColumn(
                state = listState,
                // Comes to rest on an item, never half under the time.
                flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = 84.dp,
                    start = 10.dp,
                    end = 10.dp,
                ),
            ) {
                item(key = "title") {
                    ScreenTitle(
                        stringResource(CoreR.string.kind_diaper) + DOT_SEPARATOR + f.clock.format(draft.at, f.zone),
                        Modifier.transformedHeight(this, spec),
                    )
                }
                item(key = "pee") {
                    DiaperCheckbox(
                        label = stringResource(CoreR.string.diaper_pee),
                        icon = XIcons.pee,
                        checked = draft.pee,
                        onCheckedChange = actions.onPee,
                        colors = events.pee,
                        lit = events.diaper.containerLit,
                        outline = events.diaper.strong,
                        enabled = cooldown.ready,
                        transformation = SurfaceTransformation(spec),
                        modifier = Modifier.transformedHeight(this, spec).alpha(cooldown.alpha),
                    )
                }
                item(key = "poop") {
                    DiaperCheckbox(
                        label = stringResource(CoreR.string.diaper_poop),
                        icon = XIcons.poop,
                        checked = draft.poop,
                        onCheckedChange = actions.onPoop,
                        colors = events.poop,
                        lit = events.diaper.containerLit,
                        outline = events.diaper.strong,
                        enabled = cooldown.ready,
                        transformation = SurfaceTransformation(spec),
                        modifier = Modifier.transformedHeight(this, spec).alpha(cooldown.alpha),
                    )
                }
                if (draft.poop) {
                    item(key = "colour") {
                        val color = draft.color
                        XListButton(
                            label = stringResource(CoreR.string.poop_colour),
                            secondaryLabel = color?.let { stringResource(Labels.poopColor(it)) } ?: optional,
                            onClick = actions.onColour,
                            enabled = cooldown.ready,
                            icon = color?.let { { Swatch(PoopSwatches.color(it)) } },
                            border = ambientBorder(MaterialTheme.colorScheme.outline),
                            transformation = SurfaceTransformation(spec),
                            modifier = Modifier.transformedHeight(this, spec),
                        )
                    }
                    item(key = "texture") {
                        XListButton(
                            label = stringResource(CoreR.string.poop_texture),
                            secondaryLabel = draft.texture?.let { stringResource(Labels.poopTexture(it)) } ?: optional,
                            onClick = actions.onTexture,
                            enabled = cooldown.ready,
                            border = ambientBorder(MaterialTheme.colorScheme.outline),
                            transformation = SurfaceTransformation(spec),
                            modifier = Modifier.transformedHeight(this, spec),
                        )
                    }
                }
                item(key = "earlier") {
                    Box(Modifier.fillMaxWidth().transformedHeight(this, spec), contentAlignment = Alignment.Center) {
                        EarlierButton(draft.offsetMinutes, draft.at, f, actions.onEarlier, cooldown.ready, Modifier.alpha(cooldown.alpha))
                    }
                }
            }
            SaveEdgeButton(
                enabled = cooldown.ready,
                onClick = actions.onSave,
                ambient = ambient,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * A Pee / Poop checkbox: the kind's container when off; the DIAPER lit fill with the checked box in DIAPER strong
 * when on (V1_ADDITIONS §3.2: `CheckboxButton`s). The state is in the button's own colours, so it morphs and fades
 * at the screen edge like every other list item (an outer border could not follow that shape); the checkbox glyph
 * keeps it from being colour alone. Announced as a checkbox with its state.
 */
@Composable
private fun DiaperCheckbox(
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
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
    )
}

/** Save (V1_ADDITIONS §3.2): pinned Medium EdgeButton in the neutral chrome container. */
@Composable
private fun SaveEdgeButton(enabled: Boolean, onClick: () -> Unit, ambient: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    EdgeButton(
        onClick = onClick,
        modifier = modifier,
        buttonSize = EdgeButtonSize.Medium,
        enabled = enabled,
        colors = if (ambient) {
            ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = WatchAmbient.content)
        } else {
            ButtonDefaults.buttonColors(
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                disabledContainerColor = scheme.primaryContainer.copy(alpha = 0.38f),
                disabledContentColor = scheme.onPrimaryContainer.copy(alpha = 0.38f),
            )
        },
        border = if (ambient) BorderStroke(2.dp, scheme.outline) else null,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            XIcon(
                XIcons.check,
                null,
                tint = when {
                    ambient -> WatchAmbient.content
                    enabled -> scheme.onPrimaryContainer
                    else -> scheme.onPrimaryContainer.copy(alpha = 0.38f)
                },
                size = 24.dp,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(stringResource(CoreR.string.action_save), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@WearPreviewDevices
@Composable
private fun DiaperPreview() = XWearTheme { DiaperScreen(WearSamples.diaperDraft(), DiaperActions()) }

@WearPreviewDevices
@Composable
private fun DiaperEmptyPreview() = XWearTheme {
    DiaperScreen(DiaperDraft(stamp = WearSamples.feedDraft().stamp, zone = WearSamples.zone), DiaperActions())
}
