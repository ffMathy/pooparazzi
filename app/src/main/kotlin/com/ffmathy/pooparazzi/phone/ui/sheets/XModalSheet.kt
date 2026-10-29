package com.ffmathy.pooparazzi.phone.ui.sheets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.core.R as CoreR

/** Sheet geometry (DESIGN_SPEC §3.3.2, §5.8). */
internal object SheetDefaults {
    val Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Gutter = 24.dp

    /** Pickers ignore taps this long each time they appear, fading 0.6 → 1 (DESIGN_SPEC §5.9). */
    const val PICKER_COOLDOWN_MILLIS = 500
    const val PICKER_START_ALPHA = 0.6f
}

/**
 * A modal bottom sheet that opens fully (`skipPartiallyExpanded`) while [value] is non-null and animates closed when it
 * becomes null, then reports [onClosed]. Cancel, a scrim tap, back or a swipe call [onDismissRequest].
 * Dialog semantics come from the window; the content labels it with a pane title.
 *
 * @param sheetModifier applied to the sheet surface (the edit sheet uses it to stop 24 dp below the status bar).
 * @param handleTop the drag handle's distance from the sheet's top; the same space separates it from the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Any> XModalSheet(
    value: T?,
    onDismissRequest: () -> Unit,
    onClosed: () -> Unit,
    windowInsets: WindowInsets,
    sheetModifier: Modifier = Modifier,
    handleTop: Dp = 12.dp,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val last = remember { mutableStateOf<T?>(null) }
    if (value != null) SideEffect { last.value = value }
    val shown = value ?: last.value ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val closing = value == null
    val currentOnClosed by rememberUpdatedState(onClosed)
    LaunchedEffect(closing) {
        if (closing) {
            sheetState.hide()
            last.value = null
            currentOnClosed()
        }
    }
    val palette = LocalXPalette.current
    ModalBottomSheet(
        onDismissRequest = { if (!closing) onDismissRequest() },
        modifier = sheetModifier,
        sheetState = sheetState,
        shape = SheetDefaults.Shape,
        containerColor = palette.colors.surfaceContainerLow,
        contentColor = palette.colors.onSurface,
        tonalElevation = 0.dp,
        scrimColor = palette.colors.scrim,
        dragHandle = { DragHandle(handleTop) },
        contentWindowInsets = { windowInsets },
    ) {
        content(shown)
    }
}

/** Handle 32 × 4, r 2, onSurfaceVariant at 0.4, [top] from the top. Decorative: the sheet has no partial state. */
@Composable
private fun DragHandle(top: Dp) {
    Box(
        Modifier
            .padding(top = top, bottom = top)
            .size(width = 32.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
    )
}

/**
 * Header (48): the title at the 24 gutter and "Cancel" at the end; after a nudge, "was 14:32" ([was]) dataMedium
 * onSurfaceVariant between them, like the edit sheet's time field.
 */
@Composable
internal fun SheetHeader(title: String, onCancel: () -> Unit, modifier: Modifier = Modifier, was: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            // Hyphens.Auto honours the soft hyphen of a long Danish title ("Modermælks­erstatning · 14:32").
            style = MaterialTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto),
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (was != null) {
            Text(
                stringResource(R.string.edit_was, was),
                style = PhoneType.dataMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = onCancel)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(CoreR.string.action_cancel), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * The picker cooldown: 0.6 → 1 over 500 ms each time the sheet appears; [PickerReadiness.ready] stays false until
 * then, so chips and tiles are exposed as disabled and ignore a double tap carried over from Home.
 */
internal class PickerReadiness(private val alphaState: State<Float>) {
    val alpha: Float get() = alphaState.value
    val ready: Boolean get() = alphaState.value >= 1f
}

@Composable
internal fun rememberPickerReadiness(): PickerReadiness {
    val alpha = remember { Animatable(SheetDefaults.PICKER_START_ALPHA) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(SheetDefaults.PICKER_COOLDOWN_MILLIS, easing = LinearEasing)) }
    return remember { PickerReadiness(alpha.asState()) }
}

/** The horizontal sheet content padding. */
internal val SheetContentPadding = Modifier.padding(horizontal = SheetDefaults.Gutter)
