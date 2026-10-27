package com.ffmathy.xaternity.phone.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.phone.ui.theme.LocalNightLightMotion

/** Home's big buttons (DESIGN_SPEC §3.3.1, §5.8): 96 dp tall, icon 32 + 12 + headlineSmall. */
object BigButtonDefaults {
    val Height: Dp = 96.dp
    val PillRadius: Dp = 48.dp
    val PressedRadius: Dp = 16.dp
    val CheckedRadius: Dp = 28.dp
    val IconSize: Dp = 32.dp
}

/**
 * A big Home button. The shape morphs from [radius] to [pressedRadius] while pressed (expressive by day, standard
 * motion at night). Labels wrap to two lines at large font scales; the button grows instead of clipping.
 *
 * @param icon drawn in [iconTint]; pass `Color.Unspecified` for a two-tone vector.
 * @param onClickLabel what a tap does, for accessibility services ("End sleep").
 * @param semantics extra semantics on the clickable node (a name or state description that differs from the label).
 */
@Composable
fun BigButton(
    onClick: () -> Unit,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    container: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    radius: Dp = BigButtonDefaults.PillRadius,
    pressedRadius: Dp = BigButtonDefaults.PressedRadius,
    border: BorderStroke? = null,
    onClickLabel: String? = null,
    semantics: (SemanticsPropertyReceiver.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) pressedRadius else radius,
        animationSpec = LocalNightLightMotion.current.fastSpatialSpec(),
        label = "corner",
    )
    val shape = RoundedCornerShape(corner)
    Surface(
        modifier = modifier
            .heightIn(min = BigButtonDefaults.Height),
        shape = shape,
        color = container,
        border = border,
    ) {
        Row(
            Modifier
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClickLabel = onClickLabel,
                    role = Role.Button,
                    onClick = onClick,
                )
                .then(if (semantics != null) Modifier.semantics(properties = semantics) else Modifier)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XIcon(icon, contentDescription = null, tint = iconTint, size = BigButtonDefaults.IconSize)
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall.copy(hyphens = Hyphens.Auto),
                color = labelColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
