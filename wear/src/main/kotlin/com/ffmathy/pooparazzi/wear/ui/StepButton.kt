package com.ffmathy.pooparazzi.wear.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ffmathy.pooparazzi.core.ui.type.WatchType
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.isAmbient

/**
 * A step: "−5m" / "+5m", "−" / "+". A tonal CompactButton (32 dp visual inside a 48 dp target, r 16) [width] wide
 * (null: as wide as its [modifier] makes it, e.g. a grid cell), read as [spoken] ("5 minutes earlier", "10 ml more"),
 * never as its glyphs. The Time screen's ±5m, the Bottle amount stepper and the pickers' time nudges.
 */
@Composable
fun StepButton(
    label: String,
    spoken: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 64.dp,
    style: TextStyle = StepLabelStyle,
) {
    val ambient = isAmbient()
    val content = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurface
    CompactButton(
        onClick = onClick,
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier).semantics { contentDescription = spoken },
        enabled = enabled,
        shape = WatchShapes.Compact,
        colors = tonalButtonColors(),
        border = ambientBorder(MaterialTheme.colorScheme.outline),
        contentPadding = if (width == null) PaddingValues(horizontal = STEP_PADDING) else ButtonDefaults.CompactButtonContentPadding,
    ) {
        Text(
            label,
            style = style,
            color = content,
            maxLines = 1,
            textAlign = TextAlign.Center,
            // Read as "5 minutes earlier" (the button's description), never "−5m".
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        )
    }
}

/** A step's label: labelSmall 13 sp, weight 600. */
val StepLabelStyle: TextStyle = WatchType.labelSmall.copy(fontWeight = FontWeight(600))

/** A grid step's horizontal padding: its label needs its width plus twice this. */
val STEP_PADDING = 4.dp
