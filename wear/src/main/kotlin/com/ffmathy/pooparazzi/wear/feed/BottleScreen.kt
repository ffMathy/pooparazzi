package com.ffmathy.pooparazzi.wear.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.model.BottleContents
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.wear.diaper.SaveEdgeButton
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.BottleDraft
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.Option
import com.ffmathy.pooparazzi.wear.ui.OptionScreen
import com.ffmathy.pooparazzi.wear.ui.ScreenTitle
import com.ffmathy.pooparazzi.wear.ui.XListButton
import com.ffmathy.pooparazzi.wear.ui.ambientBorder
import com.ffmathy.pooparazzi.wear.ui.eventButtonColors
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.rememberCooldown
import com.ffmathy.pooparazzi.core.R as CoreR

@Immutable
data class BottleActions(
    /** Contents: opens the Milk / Formula / None list. */
    val onContents: () -> Unit = {},
    /** Amount: opens the Amount screen (the crown or −/+). */
    val onAmount: () -> Unit = {},
    /** Earlier: opens the Time screen for the bottle feed's time. */
    val onEarlier: () -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * Bottle, after Bottle on the Feed type screen (as the phone's Feed sheet offers after Bottle): "Bottle · 14:32" (the
 * time the feed is logged at), Contents (Milk or Formula, or "Optional") and Amount ("120 ml", "4 fl oz", or
 * "Optional"), each opening its own picker; Earlier (the Time screen, as on Feed type), and the pinned Medium
 * EdgeButton "Save". Both details are optional: Save with neither records just the time. Swipe right goes back to the
 * Feed type screen without logging. 500 ms tap cooldown.
 */
@Composable
fun BottleScreen(draft: BottleDraft, actions: BottleActions, modifier: Modifier = Modifier) {
    val f = rememberFormatters(draft.zone)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    val feed = events().feed
    val ambient = isAmbient()
    val optional = stringResource(CoreR.string.optional)

    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            TransformingLazyColumn(
                state = listState,
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
                        stringResource(CoreR.string.feed_type_bottle) + DOT_SEPARATOR + f.clock.format(draft.at, f.zone),
                        Modifier.transformedHeight(this, spec),
                    )
                }
                item(key = "contents") {
                    XListButton(
                        label = stringResource(CoreR.string.bottle_contents),
                        secondaryLabel = draft.contents?.let { stringResource(Labels.bottleContents(it, compact = true)) } ?: optional,
                        onClick = actions.onContents,
                        enabled = cooldown.ready,
                        icon = {
                            XIcon(XIcons.forFeedType(FeedType.BOTTLE), null, tint = if (ambient) WatchAmbient.content else feed.strong, size = 24.dp)
                        },
                        colors = eventButtonColors(feed),
                        border = ambientBorder(feed.strong),
                        transformation = SurfaceTransformation(spec),
                        modifier = Modifier.transformedHeight(this, spec).alpha(cooldown.alpha),
                    )
                }
                item(key = "amount") {
                    XListButton(
                        label = stringResource(CoreR.string.amount),
                        secondaryLabel = draft.amountMl?.let(f.volume::format) ?: optional,
                        onClick = actions.onAmount,
                        enabled = cooldown.ready,
                        border = ambientBorder(MaterialTheme.colorScheme.outline),
                        transformation = SurfaceTransformation(spec),
                        modifier = Modifier.transformedHeight(this, spec).alpha(cooldown.alpha),
                    )
                }
                item(key = "earlier") {
                    Box(Modifier.fillMaxWidth().transformedHeight(this, spec).padding(top = 2.dp), contentAlignment = Alignment.Center) {
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

/** Milk, Formula, then "None" (what the bottle held is optional). */
@Composable
fun contentsOptions(selected: BottleContents?): List<Option<BottleContents>> =
    BottleContents.entries.map { Option(it, stringResource(Labels.bottleContents(it, compact = true)), null, it == selected) } +
        Option(null, stringResource(CoreR.string.none), null, selected == null)

@WearPreviewDevices
@Composable
private fun BottlePreview() = XWearTheme { BottleScreen(WearSamples.bottleDraft(), BottleActions()) }

@WearPreviewDevices
@Composable
private fun ContentsListPreview() = XWearTheme {
    OptionScreen(stringResource(CoreR.string.bottle_contents), contentsOptions(BottleContents.FORMULA), onSelect = {})
}
