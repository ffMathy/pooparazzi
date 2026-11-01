package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.DurationFormatter
import com.ffmathy.pooparazzi.core.format.DurationPart
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.lamp.Lamp
import com.ffmathy.pooparazzi.core.ui.lamp.LampVariant
import com.ffmathy.pooparazzi.core.ui.time.rememberReducedMotion
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLight
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** Lamp geometry on Home (DESIGN_SPEC §2.4, §10.3). */
internal object HomeLampDefaults {
    val Reference: Dp = 264.dp
    val Min: Dp = 220.dp

    /**
     * The smallest Lamp when the screen is short or the font large (below it, the area above the buttons scrolls):
     * 184 dp at 100 % font, rising to 208 dp at 130 % so the Lamp's larger text still fits inside the ring.
     */
    fun floor(fontScale: Float): Dp = (184.dp + 24.dp * ((fontScale - 1f) / 0.3f)).coerceIn(184.dp, 208.dp)

    /** The hero band: 80 dp in a Lamp of 220 dp or more, shrinking with a smaller Lamp down to 64 dp. */
    fun heroBand(size: Dp): Dp = (80.dp - (Min - size) / 2).coerceIn(64.dp, 80.dp)

    /** The hero's usable chord: 223 dp in the Ø264 Lamp. */
    const val HERO_CHORD = 223f / 264f

    /** The sub-line's chord (the "· Formula" type word is dropped beyond it). */
    const val SUB_CHORD = 0.82f

    /** Hero autosize range (sp) and the unit/number ratio (34/76). */
    const val HERO_MAX_SP = 76f
    const val HERO_MIN_SP = 56f
    const val UNIT_RATIO = 34f / 76f

    /** Continuous Lamp size: clamp(264 − (796 − H), 220, 264) for the usable window height H. */
    fun size(usableHeight: Dp): Dp = (Reference - (796.dp - usableHeight)).coerceIn(Min, Reference)
}

/**
 * The Lamp with its text (DESIGN_SPEC §2.3–2.4, V1_ADDITIONS §2.1): FEED glow/disc when idle with the last feed's
 * type icon and "Last fed"; a running sleep with its orbit and "since …".
 * The text is one TalkBack stop with a spoken form, never a live region. Like a since-row it is the one-tap correction
 * path: it opens the edit sheet of the entry it shows (the last feed, or the running sleep), and a chevron after the
 * label says so. Nothing is written until Save.
 */
@Composable
internal fun HomeLamp(
    lamp: LampState,
    zone: ZoneId,
    size: Dp,
    formatters: Formatters,
    onEditEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val night = LocalNightLight.current.isNight
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Lamp(
            state = lamp,
            variant = if (night) LampVariant.NIGHT else LampVariant.DAY,
            size = size,
            reducedMotion = rememberReducedMotion(),
        )
        LampText(lamp, zone, size, formatters, onEditEntry)
    }
}

@Composable
private fun LampText(
    lamp: LampState,
    zone: ZoneId,
    size: Dp,
    formatters: Formatters,
    onEditEntry: (String) -> Unit,
) {
    val events = LocalXPalette.current.events
    val colors = MaterialTheme.colorScheme
    val text = lampTexts(lamp, zone, size, formatters)
    val open: (() -> Unit)? = when (lamp) {
        is LampState.FeedIdle -> lamp.lastFeed?.let { feed -> { onEditEntry(feed.id) } }
        is LampState.SleepRunning -> lamp.sleep.id.let { id -> { onEditEntry(id) } }
    }
    val openLabel = stringResource(CoreR.string.action_edit)
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .then(if (open != null) Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = open) else Modifier)
            .clearAndSetSemantics {
                contentDescription = text.spoken
                if (open != null) {
                    role = Role.Button
                    onClick(label = openLabel) { open(); true }
                }
            }
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.heightIn(min = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val sleep = lamp is LampState.SleepRunning
            XIcon(text.labelIcon, contentDescription = null, tint = if (sleep) events.sleep.strong else events.feed.strong, size = 20.dp)
            Text(
                text.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (sleep) events.sleep.text else events.feed.text,
                maxLines = 1,
            )
            if (open != null) XIcon(XIcons.chevronRight, contentDescription = null, tint = colors.outline, size = 16.dp)
        }
        Spacer(Modifier.height(8.dp))
        // An 80 dp hero band (DESIGN_SPEC §2.4); it may grow only at large font scales.
        val large = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
        val band = HomeLampDefaults.heroBand(size)
        Box(if (large) Modifier.heightIn(min = band) else Modifier.height(band), contentAlignment = Alignment.Center) {
            when {
                text.heroParts != null -> HeroDuration(text.heroParts, maxWidth = size * HomeLampDefaults.HERO_CHORD, color = colors.onSurface)
                // "just now" / "lige nu" shrinks to the chord rather than wrap or clip on a small Lamp.
                else -> BasicText(
                    text.heroWord.orEmpty(),
                    style = PhoneType.heroWord.copy(color = colors.onSurface, textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = PhoneType.heroWord.fontSize, stepSize = 1.sp),
                    modifier = Modifier.widthIn(max = size * HomeLampDefaults.HERO_CHORD),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // `history` says the hero is time since, so the line needs no "ago" / "since": "⟲ 12:22 · Formula".
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SUB_ICON_GAP)) {
            XIcon(XIcons.history, contentDescription = null, tint = colors.onSurfaceVariant, size = SUB_ICON)
            Text(
                text.sub,
                style = PhoneType.dataMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The sub-line's leading `history` icon and its gap. */
private val SUB_ICON = 18.dp
private val SUB_ICON_GAP = 4.dp

/** The Lamp's words for [lamp]. Exactly one of [heroParts] and [heroWord] is set. */
private class LampTexts(
    val labelIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val heroParts: List<DurationPart>?,
    val heroWord: String?,
    val sub: String,
    val spoken: String,
)

@Composable
private fun lampTexts(lamp: LampState, zone: ZoneId, size: Dp, formatters: Formatters): LampTexts {
    val duration = formatters.duration
    val relative = formatters.relative
    val clock = formatters.clock
    val justNow = relative.justNow
    return when (lamp) {
        is LampState.FeedIdle -> {
            val feed = lamp.lastFeed
            val since = lamp.sinceMillis
            val label = stringResource(CoreR.string.status_last_fed)
            if (feed == null || since == null) {
                val none = stringResource(R.string.lamp_no_feed)
                LampTexts(XIcons.feed, label, null, stringResource(R.string.lamp_no_feed_hero), none, stringResource(R.string.spoken_pair, label, none))
            } else {
                val type = feed.feedType
                val detail = feedDetail(feed)
                val shortDetail = feedDetail(feed, short = true)
                val spokenDetail = spokenFeedDetail(feed, clock.locale)
                val time = clock.format(feed.startMillis, zone)
                val under = DurationFormatter.isUnderAMinute(since)
                // After the `history` icon: "12:22 · Formula", or the breast order "12:22 · Left → Right". When that
                // would exceed the Lamp chord the order shortens to "L → R" (it is what the next feed starts from); a
                // type word is simply dropped. A feed logged with just its time has neither.
                val sub = if (detail == null) {
                    time
                } else {
                    fitFirst(
                        listOfNotNull(time + DOT_SEPARATOR + detail, shortDetail?.let { time + DOT_SEPARATOR + it }, time),
                        PhoneType.dataMedium,
                        size * HomeLampDefaults.SUB_CHORD - SUB_ICON - SUB_ICON_GAP,
                    )
                }
                LampTexts(
                    labelIcon = XIcons.forFeedType(type),
                    label = label,
                    heroParts = if (under) null else duration.parts(since),
                    heroWord = if (under) justNow else null,
                    sub = sub,
                    spoken = if (spokenDetail == null) {
                        stringResource(
                            R.string.spoken_running,
                            stringResource(CoreR.string.spoken_last_feed),
                            relative.agoSpoken(since),
                            relative.at(feed.startMillis, zone),
                        )
                    } else {
                        stringResource(
                            R.string.spoken_last_entry,
                            stringResource(CoreR.string.spoken_last_feed),
                            spokenDetail,
                            relative.agoSpoken(since),
                            relative.at(feed.startMillis, zone),
                        )
                    },
                )
            }
        }
        is LampState.SleepRunning -> runningTexts(
            icon = XIcons.sleep,
            label = stringResource(CoreR.string.status_asleep),
            startMillis = lamp.sleep.startMillis,
            elapsedMillis = lamp.elapsedMillis,
            zone = zone,
            formatters = formatters,
        )
    }
}

@Composable
private fun runningTexts(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    startMillis: Long,
    elapsedMillis: Long,
    zone: ZoneId,
    formatters: Formatters,
): LampTexts {
    val under = DurationFormatter.isUnderAMinute(elapsedMillis)
    val since = formatters.relative.since(startMillis, zone)
    return LampTexts(
        labelIcon = icon,
        label = label,
        heroParts = if (under) null else formatters.duration.parts(elapsedMillis),
        heroWord = if (under) formatters.relative.justNow else null,
        // After the `history` icon, the start alone: "02:05".
        sub = formatters.clock.format(startMillis, zone),
        spoken = stringResource(R.string.spoken_running, label, formatters.duration.spoken(elapsedMillis), since),
    )
}

/** The first of [candidates] that fits on one line in [maxWidth] at [style], else the last. */
@Composable
private fun fitFirst(candidates: List<String>, style: TextStyle, maxWidth: Dp): String {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(candidates, style, maxWidth, density) {
        val max = with(density) { maxWidth.toPx() }
        candidates.firstOrNull { measurer.measure(it, style, maxLines = 1).size.width <= max } ?: candidates.last()
    }
}

/**
 * The hero numeral: number and unit spans baseline-aligned, 4 dp after each number and 12 dp between groups, no
 * space characters (DESIGN_SPEC §2.4). Autosizes 76 → 56 sp to fit [maxWidth] ("10h 45m" → 60/27); at very large
 * font scales it keeps shrinking rather than clip.
 */
@Composable
private fun HeroDuration(parts: List<DurationPart>, maxWidth: Dp, color: Color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numberSp = remember(parts, maxWidth, density) {
        fun widthAt(sp: Float): Float = with(density) {
            parts.withIndex().sumOf { (index, part) ->
                val number = measurer.measure(part.number, numberStyle(sp), maxLines = 1).size.width
                val unit = measurer.measure(part.unit, unitStyle(sp), maxLines = 1).size.width
                (number + unit + 4.dp.toPx() + if (index > 0) 12.dp.toPx() else 0f).toDouble()
            }.toFloat()
        }
        val limit = with(density) { maxWidth.toPx() }
        var sp = HomeLampDefaults.HERO_MAX_SP
        while (sp > HomeLampDefaults.HERO_MIN_SP && widthAt(sp) > limit) sp -= 2f
        val width = widthAt(sp)
        if (width > limit) sp * limit / width else sp
    }
    Row(verticalAlignment = Alignment.Bottom) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Spacer(Modifier.size(width = 12.dp, height = 0.dp))
            Text(part.number, style = numberStyle(numberSp), color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.size(width = 4.dp, height = 0.dp))
            Text(part.unit, style = unitStyle(numberSp), color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

private fun numberStyle(sp: Float): TextStyle =
    PhoneType.heroNumeral.copy(fontSize = sp.sp, lineHeight = (sp * 80f / 76f).sp)

private fun unitStyle(numberSp: Float): TextStyle {
    val sp = numberSp * HomeLampDefaults.UNIT_RATIO
    return PhoneType.heroUnit.copy(fontSize = sp.sp, lineHeight = (sp * 40f / 34f).sp)
}
