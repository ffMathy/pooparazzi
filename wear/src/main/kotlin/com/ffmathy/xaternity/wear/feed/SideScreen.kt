package com.ffmathy.xaternity.wear.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.xaternity.core.format.DOT_SEPARATOR
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.format.rememberFormatters
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.wear.sample.WearSamples
import com.ffmathy.xaternity.wear.state.SideDraft
import com.ffmathy.xaternity.wear.theme.WatchAmbient
import com.ffmathy.xaternity.wear.theme.XWearTheme
import com.ffmathy.xaternity.wear.theme.isAmbient
import com.ffmathy.xaternity.wear.ui.ScreenTitle
import com.ffmathy.xaternity.wear.ui.SpokenLabels
import com.ffmathy.xaternity.wear.ui.XListButton
import com.ffmathy.xaternity.wear.ui.ambientBorder
import com.ffmathy.xaternity.wear.ui.eventButtonColors
import com.ffmathy.xaternity.wear.ui.events
import com.ffmathy.xaternity.wear.ui.rememberCooldown
import com.ffmathy.xaternity.wear.ui.spokenText
import com.ffmathy.xaternity.core.R as CoreR

/**
 * Side (V1_ADDITIONS §2.2): "Which side?" (with the feed's range and length under it) and four full-width FEED
 * buttons. One tap ends the breastfeed at the time
 * stamped on "End feed" and shows Logged. Swipe right keeps the feed running. 500 ms tap cooldown.
 */
@Composable
fun SideScreen(draft: SideDraft, onSide: (BreastSide) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    val feed = events().feed
    val ambient = isAmbient()
    val f = rememberFormatters(draft.zone)
    // "14:32–14:50 · 18m": the feed being ended, so the parent can confirm it before picking a side.
    val summary = f.clock.range(draft.feed.startMillis, draft.endAt, f.zone, spaced = false) +
        DOT_SEPARATOR + f.duration.between(draft.feed.startMillis, draft.endAt)
    // Read "14:32 to 14:50, 18 minutes", not the dash and "18m".
    val spoken = spokenText().let {
        it.pair(it.range(f, draft.feed.startMillis, draft.endAt), f.duration.spokenBetween(draft.feed.startMillis, draft.endAt))
    }
    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "title") {
                Column(Modifier.fillMaxWidth().transformedHeight(this, spec), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScreenTitle(stringResource(CoreR.string.side_question))
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                    )
                }
            }
            BreastSide.entries.forEach { side ->
                item(key = side.name) {
                    XListButton(
                        label = stringResource(Labels.side(side)),
                        // "Left then right": the order matters, and TalkBack would read the arrow.
                        contentDescription = stringResource(SpokenLabels.side(side)),
                        onClick = { onSide(side) },
                        enabled = cooldown.ready,
                        icon = {
                            XIcon(XIcons.forSide(side), null, tint = if (ambient) WatchAmbient.content else feed.strong, size = 24.dp)
                        },
                        colors = eventButtonColors(feed),
                        border = ambientBorder(feed.strong),
                        transformation = SurfaceTransformation(spec),
                        modifier = Modifier.transformedHeight(this, spec).alpha(cooldown.alpha),
                    )
                }
            }
        }
    }
}

@WearPreviewDevices
@Composable
private fun SidePreview() = XWearTheme { SideScreen(WearSamples.sideDraft(), onSide = {}) }
