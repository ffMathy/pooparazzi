package com.ffmathy.pooparazzi.phone.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import kotlin.math.ceil
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLightMotion

/** Home's big buttons (DESIGN_SPEC §3.3.1, §5.8): 96 dp tall, icon 32 + 12 + headlineSmall. */
object BigButtonDefaults {
    val Height: Dp = 96.dp

    /** On a short screen (the Lamp already at its minimum size) the buttons give up 16 dp each before Home scrolls. */
    val CompactHeight: Dp = 80.dp
    val PillRadius: Dp = 48.dp
    val PressedRadius: Dp = 16.dp
    val IconSize: Dp = 32.dp
}

/**
 * A big Home button. The shape morphs from [radius] to [pressedRadius] while pressed (expressive by day, standard
 * motion at night). Labels wrap to two lines at large font scales; the button grows instead of clipping. The label and
 * sublabel are start-aligned beside the icon, and icon and text are centred in the button together.
 *
 * @param icon drawn in [iconTint]; pass `Color.Unspecified` for a two-tone vector.
 * @param onClickLabel what a tap does, for accessibility services ("End sleep").
 * @param semantics extra semantics on the clickable node (a name or state description that differs from the label).
 * @param sublabel a small second line under the label (the forecast, "in 1h 05m"), said as [sublabelSpoken].
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
    minHeight: Dp = BigButtonDefaults.Height,
    sublabel: String? = null,
    sublabelSpoken: String? = null,
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
            .heightIn(min = minHeight),
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
            val style = MaterialTheme.typography.headlineSmall.copy(hyphens = Hyphens.Auto, textAlign = TextAlign.Start)
            val measurer = rememberTextMeasurer()
            // Label and forecast start at the same edge, next to the icon; the Row centres icon and text as one group.
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    label,
                    style = style,
                    color = labelColor,
                    // A wrapped label ("Afslut" / "amning") is as wide as its widest line, so icon and label stay
                    // centred together instead of the text taking all the remaining width.
                    modifier = Modifier.layout { measurable, constraints ->
                        val lines = measurer.measure(label, style, constraints = Constraints(maxWidth = constraints.maxWidth))
                        val widest = (0 until lines.lineCount).maxOf { ceil(lines.getLineRight(it) - lines.getLineLeft(it)).toInt() }
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = widest.coerceIn(0, constraints.maxWidth)))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                )
                if (sublabel != null) {
                    // One line: a narrow button at a large font shrinks it ("om 2t 45m") rather than cut it.
                    val sublabelStyle = MaterialTheme.typography.labelLarge
                    BasicText(
                        sublabel,
                        style = sublabelStyle.copy(color = labelColor.copy(alpha = 0.78f)),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = sublabelStyle.fontSize, stepSize = 0.5.sp),
                        modifier = if (sublabelSpoken != null) Modifier.semantics { contentDescription = sublabelSpoken } else Modifier,
                    )
                }
            }
        }
    }
}
