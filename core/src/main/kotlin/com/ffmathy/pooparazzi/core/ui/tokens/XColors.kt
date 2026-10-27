package com.ffmathy.pooparazzi.core.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ffmathy.pooparazzi.core.model.PoopColor

/**
 * Neutral colour roles (DESIGN_SPEC §5.2–5.4). Fixed tokens: no dynamic colour anywhere. The apps map these
 * onto their Material colour schemes.
 */
@Immutable
data class XColors(
    val background: Color,
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val primaryDim: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val error: Color,
    val errorDim: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** Scrim including its alpha (Night 60 %, Day 32 %). */
    val scrim: Color,
    /** History ribbon night bands. */
    val ribbonNightBand: Color,
    /** The paper/background ring cut out under the Day ember. */
    val lampCut: Color,
    val isDark: Boolean,
)

/**
 * The colours of one event (DESIGN_SPEC §5.5, V1_ADDITIONS §2.1).
 *
 * @property strong icons, rings, outlines, marks; never text.
 * @property text event-coloured label text.
 * @property containerLit the lit fill of a running toggle (Day: equals [container]).
 * @property track the orbit track of a running Lamp.
 * @property ember the ember and its tail on a running Lamp.
 */
@Immutable
data class EventColors(
    val container: Color,
    val onContainer: Color,
    val strong: Color,
    val containerLit: Color,
    val text: Color,
    val track: Color,
    val ember: Color,
)

/**
 * Event colours of one theme. BOTH is not a hue: it is a 50/50 split of [pee] and [poop] containers, a
 * label in [poop] onContainer and a two-tone icon in [pee] and [poop] strong.
 */
@Immutable
data class EventPalette(
    val sleep: EventColors,
    val feed: EventColors,
    val diaper: EventColors,
    val pee: EventColors,
    val poop: EventColors,
) {
    val bothStartContainer: Color get() = pee.container
    val bothEndContainer: Color get() = poop.container
    val bothLabel: Color get() = poop.onContainer
    val bothIconPee: Color get() = pee.strong
    val bothIconPoop: Color get() = poop.strong
}

/** A neutral colour set plus its event colours. Provided through [LocalXPalette]. */
@Immutable
data class XPalette(val colors: XColors, val events: EventPalette) {
    companion object {
        val Night = XPalette(XTokens.Night, XTokens.NightEvents)
        val Day = XPalette(XTokens.Day, XTokens.DayEvents)
        val Watch = XPalette(XTokens.Watch, XTokens.WatchEvents)
    }
}

/** The active palette. Apps provide it next to their Material theme. */
val LocalXPalette = staticCompositionLocalOf { XPalette.Night }

/** Every fixed colour token of the design. */
object XTokens {
    /** Phone Night (default), DESIGN_SPEC §5.2. */
    val Night = XColors(
        background = Color(0xFF0C0A08),
        surface = Color(0xFF0C0A08),
        surfaceContainerLowest = Color(0xFF080706),
        surfaceContainerLow = Color(0xFF14110E),
        surfaceContainer = Color(0xFF1C1713),
        surfaceContainerHigh = Color(0xFF26201A),
        surfaceContainerHighest = Color(0xFF312922),
        onSurface = Color(0xFFE8D8C3),
        onSurfaceVariant = Color(0xFFB8A48E),
        outline = Color(0xFF8A7864),
        outlineVariant = Color(0xFF3F352C),
        primary = Color(0xFFE8D8C3),
        primaryDim = Color(0xFFE8D8C3),
        onPrimary = Color(0xFF1F1914),
        primaryContainer = Color(0xFF3F3A35),
        onPrimaryContainer = Color(0xFFF2E6D6),
        error = Color(0xFFF2A497),
        errorDim = Color(0xFFF2A497),
        onError = Color(0xFF4C110A),
        errorContainer = Color(0xFF5E1E15),
        onErrorContainer = Color(0xFFFFDAD4),
        scrim = Color(0x99000000),
        ribbonNightBand = Color(0xFF080706),
        lampCut = Color(0xFF0C0A08),
        isDark = true,
    )

    /** Phone Day, DESIGN_SPEC §5.3. */
    val Day = XColors(
        background = Color(0xFFFBF6F0),
        surface = Color(0xFFFBF6F0),
        surfaceContainerLowest = Color(0xFFFFFCF8),
        surfaceContainerLow = Color(0xFFF6EEE5),
        surfaceContainer = Color(0xFFF0E6DA),
        surfaceContainerHigh = Color(0xFFEADDCF),
        surfaceContainerHighest = Color(0xFFE3D5C5),
        onSurface = Color(0xFF221A12),
        onSurfaceVariant = Color(0xFF54473A),
        outline = Color(0xFF7A6B5E),
        outlineVariant = Color(0xFFD5C6B6),
        primary = Color(0xFF3A332C),
        primaryDim = Color(0xFF3A332C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE2DDD6),
        onPrimaryContainer = Color(0xFF221A12),
        error = Color(0xFF9C3328),
        errorDim = Color(0xFF9C3328),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD4),
        onErrorContainer = Color(0xFF410100),
        scrim = Color(0x52000000),
        ribbonNightBand = Color(0xFFEADDCF),
        lampCut = Color(0xFFFBF6F0),
        isDark = false,
    )

    /** Watch (dark only), DESIGN_SPEC §5.4. Lowest/highest are not specified there and reuse the nearest roles. */
    val Watch = XColors(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF15110E),
        surfaceContainer = Color(0xFF1E1914),
        surfaceContainerHigh = Color(0xFF2A231C),
        surfaceContainerHighest = Color(0xFF2A231C),
        onSurface = Color(0xFFE8D8C3),
        onSurfaceVariant = Color(0xFFB8A48E),
        outline = Color(0xFF8A7864),
        outlineVariant = Color(0xFF3F352C),
        primary = Color(0xFFE8D8C3),
        primaryDim = Color(0xFFCDBCA6),
        onPrimary = Color(0xFF1F1914),
        primaryContainer = Color(0xFF3F3A35),
        onPrimaryContainer = Color(0xFFF2E6D6),
        error = Color(0xFFF2A497),
        errorDim = Color(0xFFD98B7E),
        onError = Color(0xFF4C110A),
        errorContainer = Color(0xFF5E1E15),
        onErrorContainer = Color(0xFFFFDAD4),
        scrim = Color(0x99000000),
        ribbonNightBand = Color(0xFF000000),
        lampCut = Color(0xFF000000),
        isDark = true,
    )

    /**
     * Night event colours (phone Night and watch), DESIGN_SPEC §5.5.
     * FEED containerLit `#4F351C` is from V1_ADDITIONS; FEED track reuses it, as SLEEP's track equals its containerLit
     * (ember on track 6.27:1). PEE/POOP use the DIAPER lit and text colours.
     */
    val NightEvents = EventPalette(
        sleep = EventColors(
            container = Color(0xFF222844), onContainer = Color(0xFFD6DBF6), strong = Color(0xFF7C88C4),
            containerLit = Color(0xFF2E3660), text = Color(0xFFAEB6E6), track = Color(0xFF2E3660), ember = Color(0xFF7C88C4),
        ),
        feed = EventColors(
            container = Color(0xFF3B2715), onContainer = Color(0xFFFFDCBB), strong = Color(0xFFF2B577),
            containerLit = Color(0xFF4F351C), text = Color(0xFFF2B577), track = Color(0xFF4F351C), ember = Color(0xFFF2B577),
        ),
        diaper = EventColors(
            container = Color(0xFF13312C), onContainer = Color(0xFFBFE9E0), strong = Color(0xFF62B3A5),
            containerLit = Color(0xFF1A423B), text = Color(0xFF8FD0C3), track = Color(0xFF1A423B), ember = Color(0xFF62B3A5),
        ),
        pee = EventColors(
            container = Color(0xFF13312C), onContainer = Color(0xFFBFE9E0), strong = Color(0xFF8FD0C3),
            containerLit = Color(0xFF1A423B), text = Color(0xFF8FD0C3), track = Color(0xFF1A423B), ember = Color(0xFF8FD0C3),
        ),
        poop = EventColors(
            container = Color(0xFF0E2622), onContainer = Color(0xFFA8DCD1), strong = Color(0xFF358276),
            containerLit = Color(0xFF1A423B), text = Color(0xFF8FD0C3), track = Color(0xFF1A423B), ember = Color(0xFF358276),
        ),
    )

    /**
     * Day event colours, DESIGN_SPEC §5.5. Day has no containerLit (it resolves to the container).
     * FEED track `#AE6E30` passes 3:1 on paper (3.85) and on the FEED disc (3.32). Because FEED strong is almost the
     * same colour as any such track, the Day FEED ember and tail use FEED text `#84490E` (6.65:1 on its paper
     * cut-out), mirroring SLEEP where Day strong equals text.
     */
    val DayEvents = EventPalette(
        sleep = EventColors(
            container = Color(0xFFE1E4F8), onContainer = Color(0xFF1B2152), strong = Color(0xFF37418A),
            containerLit = Color(0xFFE1E4F8), text = Color(0xFF37418A), track = Color(0xFF6A73B3), ember = Color(0xFF37418A),
        ),
        feed = EventColors(
            container = Color(0xFFFFE1C4), onContainer = Color(0xFF452300), strong = Color(0xFFA35F1A),
            containerLit = Color(0xFFFFE1C4), text = Color(0xFF84490E), track = Color(0xFFAE6E30), ember = Color(0xFF84490E),
        ),
        diaper = EventColors(
            container = Color(0xFFCFEDE6), onContainer = Color(0xFF0F3D36), strong = Color(0xFF24665C),
            containerLit = Color(0xFFCFEDE6), text = Color(0xFF24665C), track = Color(0xFF24665C), ember = Color(0xFF24665C),
        ),
        pee = EventColors(
            container = Color(0xFFCFEDE6), onContainer = Color(0xFF0F3D36), strong = Color(0xFF2C7468),
            containerLit = Color(0xFFCFEDE6), text = Color(0xFF24665C), track = Color(0xFF24665C), ember = Color(0xFF2C7468),
        ),
        poop = EventColors(
            container = Color(0xFFB9E0D7), onContainer = Color(0xFF0B332D), strong = Color(0xFF143F38),
            containerLit = Color(0xFFCFEDE6), text = Color(0xFF24665C), track = Color(0xFF24665C), ember = Color(0xFF143F38),
        ),
    )

    /** Watch event colours equal the Night ones. */
    val WatchEvents = NightEvents

    /** Watch ambient: all text and icons. */
    val WatchAmbientContent = Color(0xFFB8A48E)

    /** Watch ambient: the Lamp ring, dot and EdgeButton outline for a sleep. */
    val WatchAmbientSleepRing = Color(0xFF7C88C4)

    /** Splash and app-icon glyph colours (DESIGN_SPEC §5.6). */
    val SplashNightBackground = Color(0xFF0C0A08)
    val SplashNightGlyph = Color(0xFFE8D8C3)
    val SplashDayBackground = Color(0xFFFBF6F0)
    val SplashDayGlyph = Color(0xFF3A332C)
}

/**
 * Poop colour swatches (V1_ADDITIONS §3.1). Content colours: identical in Night and Day. Draw each with a
 * 1 dp outline ring so Black and Pale stay visible; every chip also has a text label.
 */
object PoopSwatches {
    fun color(color: PoopColor): Color = when (color) {
        PoopColor.YELLOW -> Color(0xFFE2B33C)
        PoopColor.GREEN -> Color(0xFF6F8F3A)
        PoopColor.BROWN -> Color(0xFF8A5A2B)
        PoopColor.BLACK -> Color(0xFF2B2622)
        PoopColor.RED -> Color(0xFFB23A2E)
        PoopColor.PALE -> Color(0xFFECE6D6)
    }

    /** Colours for which the health hint is shown. */
    val needsHint: Set<PoopColor> = setOf(PoopColor.PALE, PoopColor.RED, PoopColor.BLACK)
}
