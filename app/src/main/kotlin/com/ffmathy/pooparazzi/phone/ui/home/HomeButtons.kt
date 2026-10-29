package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.components.BigButton
import com.ffmathy.pooparazzi.phone.ui.components.BigButtonDefaults
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * Feed · Diaper and the Sleep toggle (DESIGN_SPEC §3.3.1, V1_ADDITIONS §2.1). Fills, labels and positions never change
 * in lights-down. Feed always opens the Feed sheet (every feed is one time); while a sleep runs, the toggle reads
 * "Woke up". The toggle exposes no separate on/off state: the name and a state description carry it (§9.4).
 *
 * Each button's second line is what the last 7 days say comes next for it (V1_ADDITIONS §9): Feed the next feed,
 * Diaper the next poop, Start sleep the next sleep and Woke up the expected wake-up. A button the week cannot predict
 * has no second line.
 */
@Composable
internal fun HomeButtons(
    state: HomeUiState,
    formatters: Formatters,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val events = LocalXPalette.current.events
    val minHeight = if (compact) BigButtonDefaults.CompactHeight else BigButtonDefaults.Height
    val feedForecast = forecastText(state.forecast.feed, ForecastKind.FEED, state.now, state.zone, formatters)
    val poopForecast = forecastText(state.forecast.poop, ForecastKind.POOP, state.now, state.zone, formatters)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(
                onClick = actions::onFeed,
                icon = XIcons.feed,
                iconTint = events.feed.strong,
                label = stringResource(CoreR.string.kind_feed),
                container = events.feed.container,
                labelColor = events.feed.onContainer,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                minHeight = minHeight,
                sublabel = feedForecast?.shown,
                sublabelSpoken = feedForecast?.spoken,
            )
            BigButton(
                onClick = actions::onDiaper,
                icon = XIcons.diaper,
                iconTint = events.diaper.strong,
                label = stringResource(CoreR.string.kind_diaper),
                container = events.diaper.container,
                labelColor = events.diaper.onContainer,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                minHeight = minHeight,
                sublabel = poopForecast?.shown,
                sublabelSpoken = poopForecast?.spoken,
            )
        }
        SleepToggle(state, formatters, actions, minHeight)
    }
}

@Composable
private fun SleepToggle(state: HomeUiState, formatters: Formatters, actions: HomeActions, minHeight: Dp) {
    val sleep = LocalXPalette.current.events.sleep
    val zone = state.zone
    val running = state.runningSleep
    val kind = if (running == null) ForecastKind.SLEEP else ForecastKind.WAKE
    val forecast = forecastText(state.forecast.sleep, kind, state.now, zone, formatters)
    if (running == null) {
        val awakeSince = (state.row1 as? SinceRow.Awake)?.sinceMillis
        val description = awakeSince?.let {
            stringResource(R.string.join_space, stringResource(CoreR.string.status_awake), formatters.relative.since(it, zone))
        }
        val startLabel = stringResource(CoreR.string.action_start_sleep)
        BigButton(
            onClick = { actions.onSleepToggle(shownRunning = false) },
            icon = XIcons.sleep,
            iconTint = sleep.strong,
            label = startLabel,
            container = sleep.container,
            labelColor = sleep.onContainer,
            modifier = Modifier.fillMaxWidth(),
            minHeight = minHeight,
            onClickLabel = startLabel,
            sublabel = forecast?.shown,
            sublabelSpoken = forecast?.spoken,
            semantics = { description?.let { stateDescription = it } },
        )
    } else {
        val description = stringResource(
            R.string.join_space,
            stringResource(CoreR.string.status_asleep),
            formatters.relative.since(running.startMillis, zone),
        )
        val name = stringResource(R.string.cd_woke_up_end_sleep)
        BigButton(
            onClick = { actions.onSleepToggle(shownRunning = true) },
            icon = XIcons.wake,
            iconTint = sleep.strong,
            label = stringResource(CoreR.string.action_woke_up),
            container = sleep.containerLit,
            labelColor = sleep.onContainer,
            border = BorderStroke(2.dp, sleep.strong),
            modifier = Modifier.fillMaxWidth(),
            minHeight = minHeight,
            onClickLabel = stringResource(CoreR.string.action_end_sleep),
            sublabel = forecast?.shown,
            sublabelSpoken = forecast?.spoken,
            semantics = {
                contentDescription = name
                stateDescription = description
            },
        )
    }
}
