package com.ffmathy.pooparazzi.wear.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
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
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.FeedDraft
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.ScreenTitle
import com.ffmathy.pooparazzi.wear.ui.XCompactButton
import com.ffmathy.pooparazzi.wear.ui.XListButton
import com.ffmathy.pooparazzi.wear.ui.ambientBorder
import com.ffmathy.pooparazzi.wear.ui.eventButtonColors
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.rememberCooldown
import com.ffmathy.pooparazzi.wear.ui.spokenOffset
import com.ffmathy.pooparazzi.core.R as CoreR

@Immutable
data class FeedTypeActions(
    /** Breast / Formula / Food, or null for "Just the time" (a feed with no type). */
    val onType: (FeedType?) -> Unit = {},
    val onEarlier: () -> Unit = {},
)

/**
 * Feed type (V1_ADDITIONS §2.2): "Feed · 14:32" (stamped on the Feed tap), three full-width FEED buttons
 * (Breast / Formula / Food), an outlined "Just the time" button (the type is optional: logs a feed with no type) and
 * an Earlier CompactButton that cycles the time through −5m / −10m / −15m.
 * Taps are ignored for 500 ms after the screen appears (DESIGN_SPEC §5.9).
 */
@Composable
fun FeedTypeScreen(draft: FeedDraft, actions: FeedTypeActions, modifier: Modifier = Modifier) {
    val f = rememberFormatters(draft.zone)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    val feed = events().feed
    val ambient = isAmbient()
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "title") {
                ScreenTitle(
                    stringResource(CoreR.string.kind_feed) + DOT_SEPARATOR + f.clock.format(draft.at, f.zone),
                    Modifier.transformedHeight(this, spec),
                )
            }
            FEED_TYPES.forEach { type ->
                item(key = type.name) {
                    XListButton(
                        label = stringResource(Labels.feedType(type, compact = true)),
                        onClick = { actions.onType(type) },
                        enabled = cooldown.ready,
                        icon = {
                            XIcon(XIcons.forFeedType(type), null, tint = if (ambient) WatchAmbient.content else feed.strong, size = 24.dp)
                        },
                        colors = eventButtonColors(feed),
                        border = ambientBorder(feed.strong),
                        transformation = SurfaceTransformation(spec),
                        modifier = Modifier.transformedHeight(this, spec).alpha(cooldown.alpha),
                    )
                }
            }
            item(key = "untyped") {
                XListButton(
                    label = stringResource(CoreR.string.feed_type_none),
                    onClick = { actions.onType(null) },
                    enabled = cooldown.ready,
                    icon = {
                        XIcon(XIcons.clock, null, tint = if (ambient) WatchAmbient.content else feed.strong, size = 24.dp)
                    },
                    border = BorderStroke(1.dp, if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.outline),
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
    }
}

/** The order of §2.2. */
private val FEED_TYPES = listOf(FeedType.BREAST, FeedType.FORMULA, FeedType.FOOD)

/**
 * The Earlier CompactButton shared by Feed type and Diaper: "Earlier" at no offset, then the offset ("−5m");
 * read as "5 minutes earlier, 14:27".
 */
@Composable
fun EarlierButton(offsetMinutes: Int, at: Long, f: Formatters, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val earlier = stringResource(CoreR.string.action_earlier)
    XCompactButton(
        label = if (offsetMinutes == 0) earlier else f.duration.offset(offsetMinutes),
        icon = XIcons.clock,
        onClick = onClick,
        enabled = enabled,
        contentDescription = if (offsetMinutes == 0) earlier else f.spokenOffset(offsetMinutes, at),
        modifier = modifier,
    )
}

@WearPreviewDevices
@Composable
private fun FeedTypePreview() = XWearTheme { FeedTypeScreen(WearSamples.feedDraft(), FeedTypeActions()) }
