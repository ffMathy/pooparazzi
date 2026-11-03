package com.ffmathy.pooparazzi.wear.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.BottleDraft
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.time.DoneEdgeButton
import com.ffmathy.pooparazzi.wear.time.StepButton
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.rememberCooldown
import com.ffmathy.pooparazzi.wear.ui.rotarySteps
import com.ffmathy.pooparazzi.wear.ui.spokenText
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * Amount, from Amount on the Bottle screen (as the Time screen): "Amount" over the amount in the FEED colour ("120 ml",
 * or "No amount"). The crown moves it a step at a time (clockwise more), and so do "−" / "+": 10 ml up to 300 ml, or
 * ½ fl oz up to 10 fl oz where the region measures in ounces (the phone's slider steps). Each change applies at once;
 * Done (or swiping back) returns to Bottle. 500 ms tap cooldown.
 */
@Composable
fun AmountScreen(draft: BottleDraft, onAmount: (Int?) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val f = rememberFormatters(draft.zone)
    val volume = f.volume
    val cooldown = rememberCooldown()
    val ambient = isAmbient()
    val scheme = MaterialTheme.colorScheme
    val step = volume.stepOf(draft.amountMl)
    val title = stringResource(CoreR.string.amount)
    val value = draft.amountMl?.let(volume::format) ?: stringResource(CoreR.string.amount_none)
    val stepSize = volume.format(volume.amountAt(1) ?: 0)
    fun moveBy(steps: Int) = onAmount(volume.amountAt(step + steps))

    ScreenScaffold(modifier = modifier) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize().rotarySteps(::moveBy)) {
            Column(
                Modifier.fillMaxWidth().padding(top = maxHeight * 0.12f, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val spoken = spokenText().pair(title, value)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ambient) WatchAmbient.content else scheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Text(
                        value,
                        style = if (draft.amountMl == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                        color = if (ambient) WatchAmbient.content else events().feed.strong,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StepButton(
                        label = MINUS,
                        spoken = stringResource(R.string.spoken_amount_less, stepSize),
                        enabled = cooldown.ready && step > 0,
                        onClick = { moveBy(-1) },
                        modifier = Modifier.alpha(cooldown.alpha),
                    )
                    StepButton(
                        label = PLUS,
                        spoken = stringResource(R.string.spoken_amount_more, stepSize),
                        enabled = cooldown.ready && step < volume.steps,
                        onClick = { moveBy(1) },
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

private const val MINUS = "−"
private const val PLUS = "+"

@WearPreviewDevices
@Composable
private fun AmountPreview() = XWearTheme { AmountScreen(WearSamples.bottleDraft(), onAmount = {}, onDone = {}) }
