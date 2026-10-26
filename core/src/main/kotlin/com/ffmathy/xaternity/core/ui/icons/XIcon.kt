package com.ffmathy.xaternity.core.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws an [XIcons] vector at [size] in [tint] without a Material dependency. A null [contentDescription]
 * makes it decorative. Pass `Color.Unspecified` to keep the vector's own colours (e.g. [XIcons.bothTwoTone]).
 */
@Composable
fun XIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null,
    )
}

/** The two-tone `both` icon for the given PEE / POOP strong colours, remembered per colour pair. */
@Composable
fun rememberBothIcon(pee: Color, poop: Color): ImageVector = remember(pee, poop) { XIcons.bothTwoTone(pee, poop) }
