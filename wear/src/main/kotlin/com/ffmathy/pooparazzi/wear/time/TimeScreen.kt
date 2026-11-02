package com.ffmathy.pooparazzi.wear.time

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.DurationFormatter
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.type.WatchType
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.TIME_BUTTON_STEP_MINUTES
import com.ffmathy.pooparazzi.wear.state.TimeDraft
import com.ffmathy.pooparazzi.wear.state.TimeTitle
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.AutoSizeLine
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
import com.ffmathy.pooparazzi.wear.ui.ambientBorder
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.rememberCooldown
import com.ffmathy.pooparazzi.wear.ui.rotarySteps
import com.ffmathy.pooparazzi.wear.ui.spokenText
import com.ffmathy.pooparazzi.wear.ui.tonalButtonColors
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * Time (W4): sets the time of a feed or diaper before it is logged (Earlier on Feed type and Diaper), or of the entry
 * Logged shows (tap its time, or turn the crown there). "Feed · −5m" over the time in the event colour; the crown
 * moves it a minute per step (clockwise later), "−5m" / "+5m" by 5 minutes, never into the future. Done applies it and
 * goes back; swiping back leaves the time as it was. 500 ms tap cooldown.
 */
@Composable
fun TimeScreen(draft: TimeDraft, onShift: (Int) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val f = rememberFormatters(draft.zone)
    val cooldown = rememberCooldown()
    val ambient = isAmbient()
    val events = events()
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(
        when (draft.title) {
            TimeTitle.FEED -> CoreR.string.kind_feed
            TimeTitle.DIAPER -> CoreR.string.kind_diaper
            TimeTitle.WOKE_UP -> CoreR.string.action_woke_up
        },
    )
    val timeColor = when {
        ambient -> WatchAmbient.content
        draft.title == TimeTitle.FEED -> events.feed.strong
        draft.title == TimeTitle.DIAPER -> events.diaper.text
        else -> events.sleep.text
    }
    val time = f.clock.format(draft.at, f.zone)
    val shifted = draft.offsetMinutes != 0
    val heading = if (shifted) title + DOT_SEPARATOR + offsetLabel(f, draft.offsetMinutes) else title
    // "Feed, 14:27, 5 minutes earlier".
    val words = spokenText()
    val spoken = words.pair(title, time).let { if (shifted) words.pair(it, f.relative.offsetSpoken(draft.offsetMinutes)) else it }

    ScreenScaffold(modifier = modifier) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize().rotarySteps(onShift)) {
            Column(
                Modifier.fillMaxWidth().padding(top = maxHeight * 0.12f, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
                ) {
                    Text(
                        heading,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ambient) WatchAmbient.content else scheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.displaySmall,
                        color = timeColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StepButton(
                        label = f.duration.offset(-TIME_BUTTON_STEP_MINUTES),
                        spoken = f.relative.offsetSpoken(-TIME_BUTTON_STEP_MINUTES),
                        enabled = cooldown.ready && draft.offsetMinutes > draft.minOffset,
                        onClick = { onShift(-TIME_BUTTON_STEP_MINUTES) },
                        modifier = Modifier.alpha(cooldown.alpha),
                    )
                    StepButton(
                        label = f.duration.offset(TIME_BUTTON_STEP_MINUTES),
                        spoken = f.relative.offsetSpoken(TIME_BUTTON_STEP_MINUTES),
                        enabled = cooldown.ready && draft.offsetMinutes < draft.maxOffset,
                        onClick = { onShift(TIME_BUTTON_STEP_MINUTES) },
                        modifier = Modifier.alpha(cooldown.alpha),
                    )
                }
            }
            DoneEdgeButton(
                enabled = cooldown.ready,
                onClick = onDone,
                ambient = ambient,
                modifier = Modifier.align(Alignment.BottomCenter).alpha(cooldown.alpha),
            )
        }
    }
}

/** "−5m", "+45m"; from an hour on "−1h 15m" (the spoken form is [com.ffmathy.pooparazzi.core.format.RelativeFormatter.offsetSpoken]). */
internal fun offsetLabel(f: Formatters, minutes: Int): String {
    val size = kotlin.math.abs(minutes)
    if (size < MINUTES_PER_HOUR) return f.duration.offset(minutes)
    val sign = if (minutes < 0) DurationFormatter.MINUS else "+"
    return sign + f.duration.format(size * MINUTE_MILLIS)
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTE_MILLIS = 60_000L

/** "−5m" / "+5m": a tonal CompactButton, read as "5 minutes earlier" / "5 minutes later". */
@Composable
private fun StepButton(label: String, spoken: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ambient = isAmbient()
    val content = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface
    CompactButton(
        onClick = onClick,
        modifier = modifier.width(64.dp).semantics { contentDescription = spoken },
        enabled = enabled,
        shape = WatchShapes.Compact,
        colors = tonalButtonColors(),
        border = ambientBorder(MaterialTheme.colorScheme.outline),
    ) {
        Text(
            label,
            style = WatchType.labelSmall.copy(fontWeight = FontWeight(600)),
            color = content,
            maxLines = 1,
            textAlign = TextAlign.Center,
            // Read as "5 minutes earlier" (the button's description), never "−5m".
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        )
    }
}

/** Done: Small EdgeButton (56) in `primaryContainer`, `check` 20 + "Done". */
@Composable
private fun DoneEdgeButton(enabled: Boolean, onClick: () -> Unit, ambient: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val content = if (ambient) WatchAmbient.content else scheme.onPrimaryContainer
    EdgeButton(
        onClick = onClick,
        modifier = modifier,
        buttonSize = EdgeButtonSize.Small,
        enabled = enabled,
        colors = if (ambient) {
            ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = content)
        } else {
            ButtonDefaults.buttonColors(
                containerColor = scheme.primaryContainer,
                contentColor = content,
                disabledContainerColor = scheme.primaryContainer,
                disabledContentColor = content,
            )
        },
        border = if (ambient) BorderStroke(2.dp, scheme.outline) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            XIcon(XIcons.check, null, tint = content, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            // Steps down rather than cutting "Færdig" at large font sizes (as Undo on Logged).
            AutoSizeLine(
                stringResource(CoreR.string.action_done),
                MaterialTheme.typography.labelMedium,
                content,
                minSize = 11,
                modifier = Modifier.widthIn(max = 64.dp),
            )
        }
    }
}

@WearPreviewDevices
@Composable
private fun TimePreview() = XWearTheme { TimeScreen(WearSamples.timeDraft(), onShift = {}, onDone = {}) }
