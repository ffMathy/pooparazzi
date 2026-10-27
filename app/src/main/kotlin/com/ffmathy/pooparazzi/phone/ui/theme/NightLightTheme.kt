package com.ffmathy.pooparazzi.phone.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.domain.localTime
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.ui.time.rememberMinuteNow
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.tokens.XColors
import com.ffmathy.pooparazzi.core.ui.tokens.XPalette
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import java.time.ZoneId

/**
 * Which appearance is active. Components read it for the things the palette alone does not decide:
 * the Lamp variant (glow vs disc), lights-down, and the Save button fill (DESIGN_SPEC §3.3.4, §8.2).
 */
@Immutable
data class NightLight(val isNight: Boolean) {
    val palette: XPalette get() = if (isNight) XPalette.Night else XPalette.Day
}

val LocalNightLight = staticCompositionLocalOf { NightLight(isNight = true) }

/**
 * The "Night Light" theme with an explicit appearance: Night or Day tokens (fixed palette, no dynamic colour),
 * the phone type scale, shapes and motion (Night: standard, Day: expressive; DESIGN_SPEC §5.10, §8.3).
 * Previews and screenshot tests use this overload.
 */
@Composable
fun NightLightTheme(night: Boolean, content: @Composable () -> Unit) {
    val light = NightLight(night)
    val palette = light.palette
    // Material 3 1.4.0 keeps MotionScheme internal, so Material components animate with their standard motion; the
    // app's own motion follows NightLightMotion (Night standard, Day expressive).
    MaterialTheme(colorScheme = colorSchemeOf(palette.colors), shapes = NightLightShapes, typography = NightLightTypography) {
        CompositionLocalProvider(
            LocalNightLight provides light,
            LocalNightLightMotion provides if (night) NightLightMotion.Standard else NightLightMotion.Expressive,
            LocalXPalette provides palette,
            LocalRippleConfiguration provides RippleConfiguration(
                color = palette.colors.onSurface,
                rippleAlpha = if (night) NightRipple else DayRipple,
            ),
            content = content,
        )
    }
}

/**
 * The theme resolved from the Appearance setting (DESIGN_SPEC §8.1): Auto follows the system dark theme, the
 * night window and a running sleep; Night and Day are fixed.
 */
@Composable
fun NightLightTheme(
    appearance: Appearance,
    sleepRunning: Boolean,
    clock: Clock,
    zone: ZoneId = ZoneId.systemDefault(),
    content: @Composable () -> Unit,
) {
    val now = rememberMinuteNow(clock)
    val night = NightRule.isNight(
        appearance = appearance,
        systemDark = isSystemInDarkTheme(),
        localTime = localTime(now, zone),
        sleepRunning = sleepRunning,
    )
    NightLightTheme(night = night, content = content)
}

/** Maps the fixed tokens onto Material roles. No role carries an event hue (DESIGN_SPEC §5). */
fun colorSchemeOf(c: XColors): ColorScheme {
    val scheme = if (c.isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        inversePrimary = c.onPrimary,
        secondary = c.primary,
        onSecondary = c.onPrimary,
        secondaryContainer = c.primaryContainer,
        onSecondaryContainer = c.onPrimaryContainer,
        tertiary = c.primary,
        onTertiary = c.onPrimary,
        tertiaryContainer = c.primaryContainer,
        onTertiaryContainer = c.onPrimaryContainer,
        background = c.background,
        onBackground = c.onSurface,
        surface = c.surface,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceContainerHigh,
        onSurfaceVariant = c.onSurfaceVariant,
        // Elevation is 0 everywhere and no surface is tinted (DESIGN_SPEC §5.9).
        surfaceTint = Color.Transparent,
        inverseSurface = c.onSurface,
        inverseOnSurface = c.surface,
        error = c.error,
        onError = c.onError,
        errorContainer = c.errorContainer,
        onErrorContainer = c.onErrorContainer,
        outline = c.outline,
        outlineVariant = c.outlineVariant,
        scrim = c.scrim.copy(alpha = 1f),
        surfaceBright = c.surfaceContainerHighest,
        surfaceDim = c.surfaceContainerLowest,
        surfaceContainerLowest = c.surfaceContainerLowest,
        surfaceContainerLow = c.surfaceContainerLow,
        surfaceContainer = c.surfaceContainer,
        surfaceContainerHigh = c.surfaceContainerHigh,
        surfaceContainerHighest = c.surfaceContainerHighest,
    )
}

/** Material slots filled from the phone scale (DESIGN_SPEC §5.7); the app's own tokens use [PhoneType] directly. */
val NightLightTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.merge(PhoneType.displaySmall.copy(fontSize = base.displayLarge.fontSize)),
        displayMedium = base.displayMedium.merge(PhoneType.displaySmall.copy(fontSize = base.displayMedium.fontSize)),
        displaySmall = PhoneType.displaySmall,
        headlineLarge = base.headlineLarge.merge(PhoneType.headlineSmall.copy(fontSize = base.headlineLarge.fontSize)),
        headlineMedium = base.headlineMedium.merge(PhoneType.headlineSmall.copy(fontSize = base.headlineMedium.fontSize)),
        headlineSmall = PhoneType.headlineSmall,
        titleLarge = PhoneType.titleLarge,
        titleMedium = PhoneType.titleMedium,
        titleSmall = PhoneType.labelLarge,
        bodyLarge = PhoneType.bodyLarge,
        bodyMedium = PhoneType.bodyMedium,
        bodySmall = PhoneType.bodySmall,
        labelLarge = PhoneType.labelLarge,
        labelMedium = PhoneType.labelMedium,
        labelSmall = PhoneType.labelSmall,
    )
}

/** Shape scale (DESIGN_SPEC §5.8): sheets and cards 28, chips and pills 20, FAB 16, since-rows 12, inner list corners 6. */
val NightLightShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Lower ripple alpha at night (DESIGN_SPEC §8.3). */
private val NightRipple = RippleAlpha(draggedAlpha = 0.08f, focusedAlpha = 0.06f, hoveredAlpha = 0.04f, pressedAlpha = 0.06f)
private val DayRipple = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.10f, hoveredAlpha = 0.08f, pressedAlpha = 0.10f)

/**
 * The app's own springs for its custom motion (the big buttons' shape morph), matching the Material motion tokens:
 * Night standard (no overshoot to speak of), Day expressive (DESIGN_SPEC §5.10).
 */
@Immutable
class NightLightMotion private constructor(
    private val spatialDamping: Float,
    private val spatialStiffness: Float,
    private val fastSpatialDamping: Float,
    private val fastSpatialStiffness: Float,
) {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(spatialDamping, spatialStiffness)
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(fastSpatialDamping, fastSpatialStiffness)
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 1600f)

    companion object {
        val Standard = NightLightMotion(0.9f, 700f, 0.9f, 1400f)
        val Expressive = NightLightMotion(0.8f, 380f, 0.6f, 800f)
    }
}

val LocalNightLightMotion = staticCompositionLocalOf { NightLightMotion.Standard }
