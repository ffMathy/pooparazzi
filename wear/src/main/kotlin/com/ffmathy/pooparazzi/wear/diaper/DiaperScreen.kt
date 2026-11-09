package com.ffmathy.pooparazzi.wear.diaper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.DiaperDraft
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.LIST_SIDE_PADDING
import com.ffmathy.pooparazzi.wear.ui.SaveEdgeButton
import com.ffmathy.pooparazzi.wear.ui.ScreenTitle
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.pickerListTopPadding
import com.ffmathy.pooparazzi.wear.ui.rememberCooldown
import com.ffmathy.pooparazzi.wear.ui.saveButtonSize
import com.ffmathy.pooparazzi.wear.ui.saveListBottomPadding
import com.ffmathy.pooparazzi.core.R as CoreR

@Immutable
data class DiaperActions(
    val onPee: (Boolean) -> Unit = {},
    val onPoop: (Boolean) -> Unit = {},
    /** A colour chip tapped, or null when the chosen one is tapped again (clears it). */
    val onColour: (PoopColor?) -> Unit = {},
    /** A texture chip tapped, or null when the chosen one is tapped again. */
    val onTexture: (PoopTexture?) -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * Diaper (V1_ADDITIONS §3.2); everything is picked on this one list. "Diaper · 14:32" (stamped on the Diaper tap),
 * Pee | Poop as two tiles (both off at first; two full-width checkbox rows when the labels do not fit), while Poop
 * is on its colour and texture chips under "Colour" and "Texture", and the pinned Save (Medium; Small below 225 dp),
 * always enabled (nothing ticked logs a plain diaper change). The time is moved afterwards, from Logged (the Time
 * screen). The crown always scrolls the list; nothing scrolls by itself.
 *
 * 500 ms tap cooldown when the screen appears, and again for everything below the tiles when Poop is turned on or
 * off (those items appear, or move, under the finger).
 */
@Composable
fun DiaperScreen(draft: DiaperDraft, actions: DiaperActions, modifier: Modifier = Modifier) {
    val f = rememberFormatters(draft.zone)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    // Hoisted to the screen: one made inside a lazy item would restart whenever the item scrolls into view.
    val poopReflow = rememberCooldown(key = draft.poop)
    val belowReady = cooldown.ready && poopReflow.ready
    val belowAlpha = minOf(cooldown.alpha, poopReflow.alpha)
    val events = events()
    val ambient = isAmbient()
    val saveSize = saveButtonSize()

    // Measured before the list's builder, which is not composable.
    val kinds = rememberKindLayout()
    val details = rememberPoopDetailLayout()

    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            TransformingLazyColumn(
                state = listState,
                // Comes to rest on an item, never half under the time.
                flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(
                    top = pickerListTopPadding(contentPadding.calculateTopPadding()),
                    bottom = saveListBottomPadding(saveSize),
                    start = LIST_SIDE_PADDING,
                    end = LIST_SIDE_PADDING,
                ),
            ) {
                item(key = "title") {
                    ScreenTitle(
                        stringResource(CoreR.string.kind_diaper) + DOT_SEPARATOR + f.clock.format(draft.stamp, f.zone),
                        Modifier.transformedHeight(this, spec),
                    )
                }
                if (kinds.tiles) {
                    item(key = "kinds") {
                        KindTiles(
                            pee = draft.pee,
                            poop = draft.poop,
                            onPee = actions.onPee,
                            onPoop = actions.onPoop,
                            enabled = cooldown.ready,
                            alpha = cooldown.alpha,
                            height = kinds.height,
                            transformation = SurfaceTransformation(spec),
                            modifier = Modifier.transformedHeight(this, spec),
                        )
                    }
                } else {
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
                }
                if (draft.poop) {
                    poopDetailItems(
                        color = draft.color,
                        texture = draft.texture,
                        layout = details,
                        enabled = belowReady,
                        alpha = belowAlpha,
                        onColour = actions.onColour,
                        onTexture = actions.onTexture,
                        spec = spec,
                    )
                }
            }
            SaveEdgeButton(
                enabled = cooldown.ready,
                onClick = actions.onSave,
                ambient = ambient,
                size = saveSize,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@WearPreviewDevices
@Composable
private fun DiaperPreview() = XWearTheme { DiaperScreen(WearSamples.diaperDraft(), DiaperActions()) }

@WearPreviewDevices
@Composable
private fun DiaperEmptyPreview() = XWearTheme { DiaperScreen(WearSamples.emptyDiaperDraft(), DiaperActions()) }
