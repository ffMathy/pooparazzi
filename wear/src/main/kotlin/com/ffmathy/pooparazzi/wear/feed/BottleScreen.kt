package com.ffmathy.pooparazzi.wear.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.RadioButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.format.VolumeFormatter
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.model.BottleContents
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.BottleDraft
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import com.ffmathy.pooparazzi.wear.ui.CHIP_PADDING
import com.ffmathy.pooparazzi.wear.ui.ChoiceChip
import com.ffmathy.pooparazzi.wear.ui.GRID_GAP
import com.ffmathy.pooparazzi.wear.ui.LIST_SIDE_PADDING
import com.ffmathy.pooparazzi.wear.ui.SaveEdgeButton
import com.ffmathy.pooparazzi.wear.ui.ScreenTitle
import com.ffmathy.pooparazzi.wear.ui.StepButton
import com.ffmathy.pooparazzi.wear.ui.WatchShapes
import com.ffmathy.pooparazzi.wear.ui.choiceGridItems
import com.ffmathy.pooparazzi.wear.ui.events
import com.ffmathy.pooparazzi.wear.ui.gridCellWidth
import com.ffmathy.pooparazzi.wear.ui.listContentWidth
import com.ffmathy.pooparazzi.wear.ui.pickerListTopPadding
import com.ffmathy.pooparazzi.wear.ui.rememberCooldown
import com.ffmathy.pooparazzi.wear.ui.rememberGridColumns
import com.ffmathy.pooparazzi.wear.ui.rowTransformation
import com.ffmathy.pooparazzi.wear.ui.saveButtonSize
import com.ffmathy.pooparazzi.wear.ui.saveListBottomPadding
import com.ffmathy.pooparazzi.wear.ui.spokenText
import com.ffmathy.pooparazzi.core.R as CoreR

@Immutable
data class BottleActions(
    /** Milk or Formula tapped, or null when the chosen one is tapped again (clears it). */
    val onContents: (BottleContents?) -> Unit = {},
    /** An amount chip tapped (millilitres), or null when the chosen one is tapped again. */
    val onAmountChip: (Int?) -> Unit = {},
    /** The amount stepper's − / +: the new amount in millilitres, or null at "No amount". */
    val onAmountStep: (Int?) -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * Bottle (V1_ADDITIONS §2.2), after Bottle on the Feed type screen; everything is picked on this one list.
 * "Bottle · 14:32" (the time the feed is logged at: the Feed tap), Milk and Formula as FEED radio buttons
 * (optional, tap the chosen one again to clear it), the amount stepper "[−] 120 ml [+]" (10 ml or ½ fl oz a step),
 * three amount chips in ascending order (centred on the last bottle's amount, 60 · 90 · 120 ml without one) and the
 * pinned Save (Medium; Small below 225 dp). Save with nothing picked
 * records just the time; the time is moved afterwards, from Logged. Swipe right goes back to Feed type. The crown always scrolls the list.
 * 500 ms tap cooldown.
 */
@Composable
fun BottleScreen(draft: BottleDraft, actions: BottleActions, modifier: Modifier = Modifier) {
    val f = rememberFormatters(draft.zone)
    val volume = f.volume
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val cooldown = rememberCooldown()
    val feed = events().feed
    val ambient = isAmbient()
    val saveSize = saveButtonSize()

    // Measured before the list's builder, which is not composable.
    val stepper = rememberStepperLayout(volume)
    val chips = remember(volume, draft.lastAmountMl) { volume.quickAmounts(draft.lastAmountMl) }
    val chipColumns = minOf(
        rememberGridColumns(chips.map(volume::numberOf), MaterialTheme.typography.titleMedium, chrome = CHIP_PADDING * 2),
        // The unit is centred and the glyph hangs off its left: room for the glyph and its gap on both sides.
        rememberGridColumns(listOf(volume.unitSymbol), ChipUnitStyle, chrome = (CHIP_GLYPH + CHIP_GLYPH_GAP) * 2),
    )
    val chipCell = gridCellWidth(listContentWidth(), chipColumns)

    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            TransformingLazyColumn(
                state = listState,
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
                        stringResource(CoreR.string.feed_type_bottle) + DOT_SEPARATOR + f.clock.format(draft.stamp, f.zone),
                        Modifier.transformedHeight(this, spec),
                    )
                }
                BottleContents.entries.forEach { contents ->
                    item(key = contents.name.lowercase()) {
                        ContentsRadio(
                            contents = contents,
                            selected = draft.contents == contents,
                            onContents = actions.onContents,
                            enabled = cooldown.ready,
                            transformation = SurfaceTransformation(spec),
                            modifier = Modifier.transformedHeight(this, spec).alpha(cooldown.alpha),
                        )
                    }
                }
                val step = volume.stepOf(draft.amountMl)
                fun moveBy(steps: Int) = actions.onAmountStep(volume.amountAt(step + steps))
                if (stepper.oneRow) {
                    item(key = "amount") {
                        Row(
                            Modifier.fillMaxWidth().transformedHeight(this, spec).rowTransformation(this, spec),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AmountStep(-1, step, volume, cooldown.ready, ::moveBy, Modifier.alpha(cooldown.alpha), width = STEPPER_BUTTON)
                            AmountValue(draft.amountMl, volume, stepper.valueHeight, Modifier.weight(1f))
                            AmountStep(1, step, volume, cooldown.ready, ::moveBy, Modifier.alpha(cooldown.alpha), width = STEPPER_BUTTON)
                        }
                    }
                } else {
                    // Too narrow for "[−] 300 ml [+]" (fluid ounces on a small watch, large fonts): the value on its own
                    // line, the steps under it.
                    item(key = "amount") {
                        AmountValue(draft.amountMl, volume, stepper.valueHeight, Modifier.fillMaxWidth().transformedHeight(this, spec))
                    }
                    item(key = "amountSteps") {
                        Row(
                            Modifier.fillMaxWidth().transformedHeight(this, spec).rowTransformation(this, spec),
                            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                        ) {
                            AmountStep(-1, step, volume, cooldown.ready, ::moveBy, Modifier.weight(1f).alpha(cooldown.alpha), width = null)
                            AmountStep(1, step, volume, cooldown.ready, ::moveBy, Modifier.weight(1f).alpha(cooldown.alpha), width = null)
                        }
                    }
                }
                choiceGridItems("amountChips", chips.size, chipColumns, chipCell, spec) { index, cellModifier ->
                    val amount = chips[index]
                    val selected = draft.amountMl != null && volume.stepOf(draft.amountMl) == volume.stepOf(amount)
                    AmountChip(
                        amountMl = amount,
                        selected = selected,
                        asLastTime = draft.lastAmountMl != null && volume.stepOf(draft.lastAmountMl) == volume.stepOf(amount),
                        volume = volume,
                        onClick = { actions.onAmountChip(if (selected) null else amount) },
                        enabled = cooldown.ready,
                        modifier = cellModifier.alpha(cooldown.alpha),
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

/**
 * Milk or Formula: a full-width FEED radio button (at least 52 dp, r 26; Danish "Modermælks­erstatning" hyphenates
 * onto a second line). Tapping the chosen one clears it ("Clear" for TalkBack).
 */
@Composable
private fun ContentsRadio(
    contents: BottleContents,
    selected: Boolean,
    onContents: (BottleContents?) -> Unit,
    enabled: Boolean,
    transformation: SurfaceTransformation,
    modifier: Modifier,
) {
    val ambient = isAmbient()
    val feed = events().feed
    val clear = stringResource(R.string.action_clear)
    val content = if (ambient) WatchAmbient.content else feed.onContainer
    val unselectedControl = if (ambient) WatchAmbient.content else feed.onContainer.copy(alpha = 0.7f)
    val selectedControl = if (ambient) WatchAmbient.content else feed.strong
    val lit = if (ambient) Color.Transparent else feed.containerLit
    val container = if (ambient) Color.Transparent else feed.container
    RadioButton(
        selected = selected,
        onSelect = { onContents(if (selected) null else contents) },
        // The click label first, so it wins over the radio button's own click action.
        modifier = Modifier
            .semantics { if (selected) onClick(label = clear) { onContents(null); true } }
            .then(modifier)
            .fillMaxWidth()
            .then(
                if (ambient) {
                    Modifier.border(
                        if (selected) BorderStroke(2.dp, WatchAmbient.content) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        WatchShapes.Button,
                    )
                } else {
                    Modifier
                },
            ),
        enabled = enabled,
        shape = WatchShapes.Button,
        colors = RadioButtonDefaults.radioButtonColors(
            selectedContainerColor = lit,
            selectedContentColor = content,
            selectedControlColor = selectedControl,
            unselectedContainerColor = container,
            unselectedContentColor = content,
            unselectedControlColor = unselectedControl,
            // The cooldown fades the list in; the colours stay.
            disabledSelectedContainerColor = lit,
            disabledSelectedContentColor = content,
            disabledSelectedControlColor = selectedControl,
            disabledUnselectedContainerColor = container,
            disabledUnselectedContentColor = content,
            disabledUnselectedControlColor = unselectedControl,
        ),
        transformation = transformation,
        label = {
            Text(
                stringResource(Labels.bottleContents(contents, compact = true)),
                style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                maxLines = 3,
            )
        },
    )
}

/**
 * The amount in the middle of the stepper: "120 ml" in titleMedium FEED strong, or "No amount" in labelSmall. As tall
 * as its tallest text from the start ([height]). One TalkBack node, "Amount, 120 ml", a polite live region.
 */
@Composable
private fun AmountValue(amountMl: Int?, volume: VolumeFormatter, height: Dp, modifier: Modifier) {
    val ambient = isAmbient()
    val title = stringResource(CoreR.string.amount)
    val value = amountMl?.let(volume::format) ?: stringResource(CoreR.string.amount_none)
    val spoken = spokenText().pair(title, value)
    Box(
        modifier
            .heightIn(min = height)
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        if (amountMl != null) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = if (ambient) WatchAmbient.content else events().feed.strong,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        } else {
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** "−" ([direction] −1) or "+": one step, read as "10 ml less" / "10 ml more"; disabled at the end of the scale. */
@Composable
private fun AmountStep(
    direction: Int,
    step: Int,
    volume: VolumeFormatter,
    enabled: Boolean,
    moveBy: (Int) -> Unit,
    modifier: Modifier,
    width: Dp?,
) {
    val stepSize = volume.format(volume.amountAt(1) ?: 0)
    StepButton(
        label = if (direction < 0) MINUS else PLUS,
        spoken = stringResource(if (direction < 0) R.string.spoken_amount_less else R.string.spoken_amount_more, stepSize),
        enabled = enabled && if (direction < 0) step > 0 else step < volume.steps,
        onClick = { moveBy(direction) },
        modifier = modifier,
        width = width,
        style = MaterialTheme.typography.titleLarge,
    )
}

/**
 * An amount chip: the number ("120", "4,5") over the unit, both centred; a 12 dp check when chosen, or `history` on the
 * chip equal to the last bottle (read "120 ml, as last time"), hangs 2 dp left of the unit and takes no width, so the
 * label never moves. FEED lit when chosen.
 */
@Composable
private fun AmountChip(
    amountMl: Int,
    selected: Boolean,
    asLastTime: Boolean,
    volume: VolumeFormatter,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val ambient = isAmbient()
    val feed = events().feed
    val shown = volume.format(amountMl)
    ChoiceChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        event = feed,
        modifier = modifier,
        contentDescription = if (asLastTime) stringResource(R.string.spoken_as_last_time, shown) else shown,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(volume.numberOf(amountMl), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            // The unit is centred under the number on its own; the glyph hangs off its left edge and takes no width
            // (it may sit in the chip's padding), so the label never shifts with selection.
            Box(contentAlignment = Alignment.Center) {
                Text(volume.unitSymbol, style = ChipUnitStyle, maxLines = 1)
                val glyph = Modifier.align(Alignment.CenterStart).offset(x = -(CHIP_GLYPH + CHIP_GLYPH_GAP))
                when {
                    selected -> XIcon(XIcons.check, null, tint = if (ambient) WatchAmbient.content else feed.strong, size = CHIP_GLYPH, modifier = glyph)
                    asLastTime -> XIcon(
                        XIcons.history,
                        null,
                        tint = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant,
                        size = CHIP_GLYPH,
                        modifier = glyph,
                    )
                }
            }
        }
    }
}

/**
 * Whether "[−] 300 ml [+]" fits on one row (the widest amount in titleMedium, and "No amount" on two lines in
 * labelSmall, between two 48 dp steps), and the height the value needs so it never changes as the amount does.
 * Measured once per configuration.
 */
@Immutable
private data class StepperLayout(val oneRow: Boolean, val valueHeight: Dp)

@Composable
private fun rememberStepperLayout(volume: VolumeFormatter): StepperLayout {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val width = listContentWidth()
    val widest = volume.format(checkNotNull(volume.amountAt(volume.steps)))
    val none = stringResource(CoreR.string.amount_none)
    val valueStyle = MaterialTheme.typography.titleMedium
    val noneStyle = MaterialTheme.typography.labelSmall
    return remember(widest, none, valueStyle, noneStyle, density, configuration.fontScale, configuration.screenWidthDp) {
        fun fits(available: Dp): Boolean {
            val px = with(density) { available.roundToPx() }
            val value = measurer.measure(widest, valueStyle, maxLines = 1, softWrap = false, density = density).size.width
            val words = none.split(' ').maxOf { measurer.measure(it, noneStyle, maxLines = 1, softWrap = false, density = density).size.width }
            val lines = measurer.measure(none, noneStyle, constraints = Constraints(maxWidth = px), density = density).lineCount
            return value <= px && words <= px && lines <= 2
        }
        val middle = width - STEPPER_BUTTON * 2 - 8.dp
        val oneRow = fits(middle)
        val available = if (oneRow) middle else width
        val px = with(density) { available.roundToPx() }
        val heights = listOf(
            measurer.measure(widest, valueStyle, constraints = Constraints(maxWidth = px), density = density).size.height,
            measurer.measure(none, noneStyle, constraints = Constraints(maxWidth = px), density = density).size.height,
        )
        StepperLayout(oneRow, with(density) { heights.max().toDp() }.coerceAtLeast(24.dp))
    }
}

private const val MINUS = "−"
private const val PLUS = "+"

/** The stepper's − and + on one row: 48 dp each (a CompactButton's touch target). */
private val STEPPER_BUTTON = 48.dp

/** The glyph left of an amount chip's unit, and the gap between them. */
private val CHIP_GLYPH = 12.dp
private val CHIP_GLYPH_GAP = 2.dp

private val ChipUnitStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(600))

@WearPreviewDevices
@Composable
private fun BottlePreview() = XWearTheme { BottleScreen(WearSamples.bottleDraft(), BottleActions()) }

@WearPreviewDevices
@Composable
private fun BottleEmptyPreview() = XWearTheme { BottleScreen(WearSamples.emptyBottleDraft(), BottleActions()) }
