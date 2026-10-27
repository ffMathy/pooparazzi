package com.ffmathy.xaternity.phone.ui.sheets

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
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.time.rememberReducedMotion
import com.ffmathy.xaternity.core.ui.tokens.EventColors
import com.ffmathy.xaternity.core.ui.tokens.LocalXPalette
import com.ffmathy.xaternity.core.ui.tokens.PoopSwatches
import com.ffmathy.xaternity.phone.ui.components.XChip
import com.ffmathy.xaternity.phone.ui.home.HomeSheetUi
import com.ffmathy.xaternity.phone.ui.home.diaperIcon
import com.ffmathy.xaternity.phone.ui.home.titleWithTime
import com.ffmathy.xaternity.phone.ui.theme.LocalNightLight
import java.time.ZoneId
import com.ffmathy.xaternity.core.R as CoreR

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
 * the sheet opens), Colour and Texture (optional, single-select; tap again clears) once Poop is on, the health hint
 * for Pale, Red or Black, and Save (disabled until a checkbox is on) at the bottom end, in the thumb zone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiaperSheetContent(sheet: HomeSheetUi.Diaper, zone: ZoneId, formatters: Formatters, callbacks: DiaperSheetCallbacks) {
    val readiness = rememberPickerReadiness()
    val events = LocalXPalette.current.events
    val reducedMotion = rememberReducedMotion()
    val title = titleWithTime(CoreR.string.kind_diaper, formatters.clock.format(sheet.time, zone))
    SheetColumn(title) {
        SheetHeader(title, callbacks.onCancel)
        Spacer(Modifier.height(12.dp))
        SheetChipRow(sheet.chips, formatters, readiness, callbacks.onOffset)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.alpha(readiness.alpha), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DiaperTile(
                checked = sheet.pee,
                onCheckedChange = callbacks.onPee,
                kind = DiaperKind.PEE,
                eventColors = events.pee,
                litColor = events.diaper.containerLit,
                enabled = readiness.ready,
                modifier = Modifier.weight(1f),
            )
            DiaperTile(
                checked = sheet.poop,
                onCheckedChange = callbacks.onPoop,
                kind = DiaperKind.POOP,
                eventColors = events.poop,
                litColor = events.diaper.containerLit,
                enabled = readiness.ready,
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(
            visible = sheet.poop,
            enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.alpha(readiness.alpha)) {
                SectionHeading(stringResource(CoreR.string.poop_colour))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PoopColor.entries.forEach { color ->
                        val selected = sheet.color == color
                        XChip(
                            selected = selected,
                            onClick = { callbacks.onColor(if (selected) null else color) },
                            label = stringResource(Labels.poopColor(color)),
                            showCheck = false,
                            acceptsTaps = readiness.ready,
                            leading = { Swatch(PoopSwatches.color(color), selected) },
                        )
                    }
                }
                SectionHeading(stringResource(CoreR.string.poop_texture))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PoopTexture.entries.forEach { texture ->
                        val selected = sheet.texture == texture
                        XChip(
                            selected = selected,
                            onClick = { callbacks.onTexture(if (selected) null else texture) },
                            label = stringResource(Labels.poopTexture(texture)),
                            reserveCheck = true,
                            acceptsTaps = readiness.ready,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = sheet.color in PoopSwatches.needsHint,
                    enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
                    exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
                ) {
                    HealthHint()
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SaveButton(enabled = sheet.canSave && readiness.ready, onClick = callbacks.onSave)
        }
    }
}

/**
 * A checkbox tile, 165 × 112, r 28. Off: container fill, 1 dp outline border, empty checkbox. On: the diaper lit fill,
 * a 2 dp border and a checked checkbox in the kind's strong colour. `Role.Checkbox`: "Pee, checkbox, not checked".
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
    val border = if (checked) BorderStroke(2.dp, eventColors.strong) else BorderStroke(1.dp, colors.outline)
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
            tint = if (checked) eventColors.strong else colors.outline,
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
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
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

/** Save: 160 × 56, r 28, `check` 20 + 8 + labelLarge; Night primaryContainer, Day primary (DESIGN_SPEC §3.3.4). */
@Composable
internal fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
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
        Text(stringResource(CoreR.string.action_save), style = MaterialTheme.typography.labelLarge)
    }
}
