package com.ffmathy.pooparazzi.wear.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.SideDraft
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.AutoSizeLine
import com.ffmathy.pooparazzi.wear.ui.ScreenTitle
import com.ffmathy.pooparazzi.wear.ui.SpokenLabels
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.isSmallWatch
import com.ffmathy.pooparazzi.wear.ui.rememberCooldown
import com.ffmathy.pooparazzi.wear.ui.spokenText
import com.ffmathy.pooparazzi.core.R as CoreR

@Immutable
data class SideActions(
    val onTick: (Breast) -> Unit = {},
    val onEnd: () -> Unit = {},
)

/**
 * Side (V1_ADDITIONS §2.2): "Which side?" (with the feed's range and length under it), two CheckboxButtons Left and
 * Right whose tick order is the order recorded (a "1" / "2" badge on each ticked box and a "Left → Right" line under
 * them), and the pinned Medium EdgeButton "End feed". The side is optional: End feed with nothing ticked records
 * only the times. The feed ends at the time stamped on "End feed"; swipe right keeps it running. 500 ms tap cooldown.
 */
@Composable
fun SideScreen(draft: SideDraft, actions: SideActions, modifier: Modifier = Modifier) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    val ambient = isAmbient()
    val small = isSmallWatch()
    val f = rememberFormatters(draft.zone)
    // "14:32–14:50 · 18m": the feed being ended, so the parent can confirm it before ending it.
    val summary = f.clock.range(draft.feed.startMillis, draft.endAt, f.zone, spaced = false) +
        DOT_SEPARATOR + f.duration.between(draft.feed.startMillis, draft.endAt)
    // Read "14:32 to 14:50, 18 minutes", not the dash and "18m".
    val spoken = spokenText().let {
        it.pair(it.range(f, draft.feed.startMillis, draft.endAt), f.duration.spokenBetween(draft.feed.startMillis, draft.endAt))
    }
    val variant = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            TransformingLazyColumn(
                state = listState,
                flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
                // Tighter than the default list so the title, summary and BOTH checkboxes sit above the pinned Medium
                // "End feed" EdgeButton at rest on a 227 dp watch (its top edge is ~154 dp down). A small watch can't
                // fit both anyway, so it keeps the full top padding (the title stays clear of the time) and the bottom
                // padding lets Right scroll clear of the button.
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(
                    top = if (small) contentPadding.calculateTopPadding()
                    else (contentPadding.calculateTopPadding() - 8.dp).coerceAtLeast(0.dp),
                    bottom = 84.dp,
                    start = 10.dp,
                    end = 10.dp,
                ),
            ) {
                item(key = "title") {
                    // Not morphed: a shrinking title item let the summary line slide under the Left checkbox.
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ScreenTitle(stringResource(CoreR.string.side_question))
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = variant,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                        )
                    }
                }
                Breast.entries.forEach { breast ->
                    item(key = breast.name) {
                        val place = draft.ticks.indexOf(breast).takeIf { it >= 0 }?.plus(1)
                        SideCheckbox(
                            breast = breast,
                            place = place,
                            onToggle = { actions.onTick(breast) },
                            enabled = cooldown.ready,
                            transformation = SurfaceTransformation(spec),
                            modifier = Modifier.transformedHeight(this, spec).alpha(cooldown.alpha),
                        )
                    }
                }
                item(key = "order") {
                    val side = draft.side
                    val text = side?.let { stringResource(Labels.side(it)) } ?: stringResource(CoreR.string.side_none)
                    val sideSpoken = side?.let { stringResource(SpokenLabels.side(it)) } ?: text
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (side == null || ambient) variant else events().feed.text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec)
                            .padding(top = 4.dp)
                            .semantics {
                                contentDescription = sideSpoken
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                }
            }
            EndFeedEdgeButton(
                enabled = cooldown.ready,
                onClick = actions.onEnd,
                ambient = ambient,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * A Left / Right checkbox in FEED colours (as the Diaper screen's checkboxes): container when off; FEED lit fill with
 * the checked box in FEED strong when on, and the icon slot then shows the tick's place ("1" / "2") in a FEED strong
 * badge instead of the side glyph. TalkBack: "Left, first, checkbox, checked".
 */
@Composable
private fun SideCheckbox(
    breast: Breast,
    place: Int?,
    onToggle: () -> Unit,
    enabled: Boolean,
    transformation: SurfaceTransformation,
    modifier: Modifier,
) {
    val ambient = isAmbient()
    val feed = events().feed
    val side = if (breast == Breast.LEFT) BreastSide.LEFT else BreastSide.RIGHT
    val label = stringResource(Labels.side(side))
    val placeSpoken = place?.let { stringResource(if (it == 1) R.string.spoken_side_first else R.string.spoken_side_second) }
    val content = if (ambient) WatchAmbient.content else feed.onContainer
    CheckboxButton(
        checked = place != null,
        onCheckedChange = { onToggle() },
        modifier = modifier
            .fillMaxWidth()
            // 48 dp (the minimum touch target) instead of the default 52 dp, so both rows clear the EdgeButton.
            .height(48.dp)
            .semantics {
                role = Role.Checkbox
                contentDescription = if (placeSpoken != null) "$label, $placeSpoken" else label
            }
            .then(if (ambient) Modifier.border(BorderStroke(2.dp, feed.strong), WatchShapes.Button) else Modifier),
        enabled = enabled,
        transformation = transformation,
        shape = WatchShapes.Button,
        colors = CheckboxButtonDefaults.checkboxButtonColors(
            checkedContainerColor = if (ambient) Color.Transparent else feed.containerLit,
            checkedContentColor = content,
            checkedIconColor = if (ambient) WatchAmbient.content else feed.strong,
            checkedBoxColor = if (ambient) WatchAmbient.content else feed.strong,
            checkedCheckmarkColor = if (ambient) WatchAmbient.content else feed.onContainer,
            uncheckedContainerColor = if (ambient) Color.Transparent else feed.container,
            uncheckedContentColor = content,
            uncheckedIconColor = if (ambient) WatchAmbient.content else feed.strong,
            uncheckedBoxColor = if (ambient) WatchAmbient.content else feed.onContainer.copy(alpha = 0.7f),
        ),
        icon = {
            if (place != null) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (ambient) WatchAmbient.content else feed.strong)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        place.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ambient) Color.Black else feed.containerLit,
                        maxLines = 1,
                    )
                }
            } else {
                XIcon(XIcons.forSide(side), null, tint = if (ambient) WatchAmbient.content else feed.strong, size = 24.dp)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
    )
}

/** "End feed": pinned Medium EdgeButton in the running feed's treatment (FEED lit, FEED strong border). Always enabled. */
@Composable
private fun EndFeedEdgeButton(enabled: Boolean, onClick: () -> Unit, ambient: Boolean, modifier: Modifier) {
    val feed = events().feed
    val content = if (ambient) WatchAmbient.content else feed.onContainer
    val label = stringResource(CoreR.string.action_end_feed)
    EdgeButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        buttonSize = EdgeButtonSize.Medium,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (ambient) Color.Transparent else feed.containerLit,
            contentColor = content,
            disabledContainerColor = if (ambient) Color.Transparent else feed.containerLit,
            disabledContentColor = content,
        ),
        border = BorderStroke(if (ambient) 2.dp else 1.5.dp, feed.strong),
    ) {
        if (isSmallWatch()) {
            // A 192 dp watch's EdgeButton is too narrow for "Afslut amning" on one line: wrap it in place of the icon.
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.clearAndSetSemantics { },
            )
        } else {
            Column(Modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
                XIcon(XIcons.check, null, tint = if (ambient) WatchAmbient.content else feed.strong, size = 24.dp)
                AutoSizeLine(label, MaterialTheme.typography.labelMedium, content, minSize = 12, modifier = Modifier.width(96.dp))
            }
        }
    }
}

@WearPreviewDevices
@Composable
private fun SidePreview() = XWearTheme { SideScreen(WearSamples.sideDraft(), SideActions()) }

@WearPreviewDevices
@Composable
private fun SideTickedPreview() = XWearTheme {
    SideScreen(WearSamples.sideDraft().copy(ticks = listOf(Breast.RIGHT, Breast.LEFT)), SideActions())
}
