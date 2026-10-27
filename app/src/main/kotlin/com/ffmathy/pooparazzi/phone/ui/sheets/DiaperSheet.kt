package com.ffmathy.pooparazzi.phone.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.time.rememberReducedMotion
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.tokens.PoopSwatches
import com.ffmathy.pooparazzi.phone.ui.components.XChip
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.diaperIcon
import com.ffmathy.pooparazzi.phone.ui.home.titleWithTime
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLight
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** What the Diaper sheet can ask for. */
internal class DiaperSheetCallbacks(
    val onOffset: (Int) -> Unit,
    val onPee: (Boolean) -> Unit,
    val onPoop: (Boolean) -> Unit,
    val onColor: (PoopColor?) -> Unit,
    val onTexture: (PoopTexture?) -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The Diaper sheet (V1_ADDITIONS §3.1): "Diaper · 14:32", offset chips, Pee and Poop checkbox tiles (both off when
 * the sheet opens, both optional), Colour and Texture (optional, single-select; tap again clears) once Poop is on, the
 * health hint for Pale, Red or Black, and Save at the bottom end, in the thumb zone. Save is always enabled: nothing
 * ticked logs a plain diaper change.
 */
@Composable
internal fun DiaperSheetContent(sheet: HomeSheetUi.Diaper, zone: ZoneId, formatters: Formatters, callbacks: DiaperSheetCallbacks) {
    val readiness = rememberPickerReadiness()
    val title = titleWithTime(CoreR.string.kind_diaper, formatters.clock.format(sheet.time, zone))
    SheetColumn(title) {
        SheetHeader(title, callbacks.onCancel)
        Spacer(Modifier.height(12.dp))
        SheetChipRow(sheet.chips, formatters, readiness, callbacks.onOffset)
        Spacer(Modifier.height(12.dp))
        DiaperToggleTiles(
            pee = sheet.pee,
            poop = sheet.poop,
            onPee = callbacks.onPee,
            onPoop = callbacks.onPoop,
            enabled = readiness.ready,
            modifier = Modifier.alpha(readiness.alpha),
        )
        PoopDetails(
            visible = sheet.poop,
            color = sheet.color,
            texture = sheet.texture,
            onColor = callbacks.onColor,
            onTexture = callbacks.onTexture,
            acceptsTaps = readiness.ready,
            modifier = Modifier.alpha(readiness.alpha),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SaveButton(enabled = readiness.ready, onClick = callbacks.onSave)
        }
    }
}

/** Pee and Poop checkbox tiles side by side (V1_ADDITIONS §3.1), shared by the Diaper sheet and the edit sheet. */
@Composable
internal fun DiaperToggleTiles(
    pee: Boolean,
    poop: Boolean,
    onPee: (Boolean) -> Unit,
    onPoop: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val events = LocalXPalette.current.events
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DiaperTile(
            checked = pee,
            onCheckedChange = onPee,
            kind = DiaperKind.PEE,
            eventColors = events.pee,
            litColor = events.diaper.containerLit,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        DiaperTile(
            checked = poop,
            onCheckedChange = onPoop,
            kind = DiaperKind.POOP,
            eventColors = events.poop,
            litColor = events.diaper.containerLit,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Colour and Texture (optional, single-select; tapping the selected chip clears it) and the health hint for Pale, Red
 * or Black. Expands below the tiles while [visible] (Poop is on); reduced motion makes it instant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PoopDetails(
    visible: Boolean,
    color: PoopColor?,
    texture: PoopTexture?,
    onColor: (PoopColor?) -> Unit,
    onTexture: (PoopTexture?) -> Unit,
    acceptsTaps: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
        exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
    ) {
        Column(modifier) {
            SectionHeading(stringResource(CoreR.string.poop_colour))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoopColor.entries.forEach { option ->
                    val selected = color == option
                    XChip(
                        selected = selected,
                        onClick = { onColor(if (selected) null else option) },
                        label = stringResource(Labels.poopColor(option)),
                        showCheck = false,
                        acceptsTaps = acceptsTaps,
                        leading = { Swatch(PoopSwatches.color(option), selected) },
                    )
                }
            }
            SectionHeading(stringResource(CoreR.string.poop_texture))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoopTexture.entries.forEach { option ->
                    val selected = texture == option
                    XChip(
                        selected = selected,
                        onClick = { onTexture(if (selected) null else option) },
                        label = stringResource(Labels.poopTexture(option)),
                        reserveCheck = true,
                        acceptsTaps = acceptsTaps,
                    )
                }
            }
            AnimatedVisibility(
                visible = color in PoopSwatches.needsHint,
                enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
            ) {
                HealthHint()
            }
        }
    }
}

/**
 * A checkbox tile, 165 × 112, r 28. Off: container fill, 1 dp outline border, empty checkbox. On: the diaper lit fill,
 * a 2 dp border and a checked checkbox in DIAPER strong for both kinds (POOP strong is only 2.4:1 on the lit fill at
 * Night; the watch does the same). The icon keeps the kind's colour. `Role.Checkbox`: "Pee, checkbox, not checked".
 */
@Composable
private fun DiaperTile(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    kind: DiaperKind,
    eventColors: EventColors,
    litColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val events = LocalXPalette.current.events
    val shape = RoundedCornerShape(28.dp)
    val checkedColor = events.diaper.strong
    val border = if (checked) BorderStroke(2.dp, checkedColor) else BorderStroke(1.dp, colors.outline)
    val icon = diaperIcon(kind, events)
    Box(
        modifier
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(if (checked) litColor else eventColors.container)
            .border(border, shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        XIcon(
            if (checked) XIcons.checkboxChecked else XIcons.checkbox,
            contentDescription = null,
            tint = if (checked) checkedColor else colors.outline,
            size = 24.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp),
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            XIcon(icon.icon, contentDescription = null, tint = icon.tint, size = 40.dp)
            Text(
                stringResource(Labels.diaperKind(kind)),
                style = MaterialTheme.typography.headlineSmall,
                color = eventColors.onContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/**
 * A 20 dp content-colour dot with a 1 dp outline ring, so Black and Pale show on any surface. A selected swatch carries
 * the chip's check, so selecting never changes the chip's width.
 */
@Composable
private fun Swatch(color: Color, selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            XIcon(XIcons.check, contentDescription = null, tint = if (color.luminance() > 0.4f) SwatchInkDark else SwatchInkLight, size = 14.dp)
        }
    }
}

private val SwatchInkDark = Color(0xFF221A12)
private val SwatchInkLight = Color(0xFFFFFFFF)

/** "Pale, red or black poop can be a reason to call your doctor or health visitor." The app never diagnoses. */
@Composable
private fun HealthHint() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        XIcon(XIcons.info, contentDescription = null, tint = colors.onSurfaceVariant, size = 20.dp)
        Text(stringResource(CoreR.string.poop_health_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/**
 * Save: 160 × 56, r 28, `check` 20 + 8 + labelLarge; Night primaryContainer, Day primary (DESIGN_SPEC §3.3.4).
 * [label] defaults to "Save" (the Side sheet passes "End feed").
 */
@Composable
internal fun SaveButton(enabled: Boolean, onClick: () -> Unit, label: String = stringResource(CoreR.string.action_save)) {
    val colors = MaterialTheme.colorScheme
    val night = LocalNightLight.current.isNight
    val container = if (night) colors.primaryContainer else colors.primary
    val content = if (night) colors.onPrimaryContainer else colors.onPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .widthIn(min = 160.dp)
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.onSurface.copy(alpha = 0.12f),
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        val tint = if (enabled) content else colors.onSurface.copy(alpha = 0.38f)
        XIcon(XIcons.check, contentDescription = null, tint = tint, size = 20.dp)
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
