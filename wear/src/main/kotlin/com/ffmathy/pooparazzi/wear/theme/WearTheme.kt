package com.ffmathy.pooparazzi.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.Typography
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.tokens.XPalette
import com.ffmathy.pooparazzi.core.ui.tokens.XTokens
import com.ffmathy.pooparazzi.core.ui.type.WatchType

/**
 * The watch colour scheme (DESIGN_SPEC §5.4): fixed warm neutrals, dark only, no dynamic colour. No Material role
 * carries an event hue: `secondary*` and `tertiary*` repeat the primary family, and event colours come from
 * [XPalette.Watch] through [LocalXPalette].
 */
internal val WatchColorScheme: ColorScheme = with(XTokens.Watch) {
    ColorScheme(
        primary = primary,
        primaryDim = primaryDim,
        primaryContainer = primaryContainer,
        onPrimary = onPrimary,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary,
        secondaryDim = primaryDim,
        secondaryContainer = primaryContainer,
        onSecondary = onPrimary,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = primary,
        tertiaryDim = primaryDim,
        tertiaryContainer = primaryContainer,
        onTertiary = onPrimary,
        onTertiaryContainer = onPrimaryContainer,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        background = background,
        onBackground = onSurface,
        error = error,
        errorDim = errorDim,
        errorContainer = errorContainer,
        onError = onError,
        onErrorContainer = onErrorContainer,
    )
}

/**
 * The watch type scale (DESIGN_SPEC §5.7) merged over the Wear defaults, so text keeps the Wear system font and
 * only sizes, line heights, weights and tabular figures change. Numerals use [WatchType] styles directly.
 */
internal fun watchTypography(): Typography {
    val base = Typography()
    fun TextStyle.with(style: TextStyle) = merge(style)
    return Typography(
        displayLarge = base.displayLarge,
        displayMedium = base.displayMedium,
        displaySmall = base.displaySmall,
        titleLarge = base.titleLarge.with(WatchType.titleLarge),
        titleMedium = base.titleMedium.with(WatchType.titleMedium),
        titleSmall = base.titleSmall.with(WatchType.titleSmall),
        labelLarge = base.labelLarge,
        labelMedium = base.labelMedium.with(WatchType.labelMedium),
        labelSmall = base.labelSmall.with(WatchType.labelSmall),
        bodyLarge = base.bodyLarge,
        bodyMedium = base.bodyMedium.with(WatchType.bodyMedium),
        bodySmall = base.bodySmall.with(WatchType.bodySmall),
        bodyExtraSmall = base.bodyExtraSmall,
        numeralExtraLarge = base.numeralExtraLarge,
        numeralLarge = base.numeralLarge,
        numeralMedium = base.numeralMedium,
        numeralSmall = base.numeralSmall,
        numeralExtraSmall = base.numeralExtraSmall,
    )
}

/**
 * The app theme: Wear Material 3 with [WatchColorScheme], the watch type scale, the standard (no overshoot) motion
 * scheme the Night appearance uses (DESIGN_SPEC §5.10), and the watch event palette.
 */
@Composable
fun XWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WatchColorScheme,
        typography = watchTypography(),
        motionScheme = MotionScheme.standard(),
    ) {
        CompositionLocalProvider(LocalXPalette provides XPalette.Watch, content = content)
    }
}

/** Watch ambient colours (DESIGN_SPEC §5.6): every text and icon, and the sleep ring / outline. */
object WatchAmbient {
    val content: Color = XTokens.WatchAmbientContent
    val sleepRing: Color = XTokens.WatchAmbientSleepRing
}
