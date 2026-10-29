package com.ffmathy.pooparazzi.core.ui.type

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ffmathy.pooparazzi.core.R

/**
 * Phone font families (DESIGN_SPEC §5.7, §10.12) built from the bundled variable fonts.
 *
 * A Compose [FontFamily] picks a font by weight, so ROND and opsz are fixed per family: each family below is one
 * ROND/opsz setting with every weight the type scale uses. Weight is set through `fontWeight` in the styles
 * (the family maps it onto the `wght` axis). Fallback when a variable font cannot load is the system sans.
 *
 * `res/font/google_sans_flex.ttf` is built by `core/fonts/build_fonts.py`: upstream Google Sans Flex with the
 * unused `wdth` and `slnt` axes pinned (761 KB instead of 3.9 MB) and U+2192 "→" added for the side labels.
 * Only the phone uses these families (and Atkinson); the watch uses [WatchFonts], so a wear release build with
 * resource shrinking drops both phone fonts.
 */
object XFonts {
    /** Weights the type scales use. */
    private val WEIGHTS = listOf(400, 450, 500, 600, 700)

    /**
     * Google Sans Flex at a fixed `ROND` ([rond], 0–100) and optional `opsz` ([opticalSize], 1–144; the font's
     * default 18 when null).
     */
    fun googleSansFlex(rond: Float, opticalSize: Float? = null): FontFamily = FontFamily(
        WEIGHTS.map { weight ->
            val settings = buildList {
                add(FontVariation.Setting("ROND", rond))
                if (opticalSize != null) add(FontVariation.Setting("opsz", opticalSize))
            }
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight(weight),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontWeight(weight), FontStyle.Normal, *settings.toTypedArray()),
            )
        },
    )

    /** Phone text: titles, labels, data lines (ROND 40). */
    val PhoneText: FontFamily by lazy { googleSansFlex(rond = 40f) }

    /** Phone headlineSmall: big button labels (ROND 60). */
    val PhoneHeadline: FontFamily by lazy { googleSansFlex(rond = 60f) }

    /** Phone hero numerals and units (ROND 100, opsz 144). */
    val PhoneNumeral: FontFamily by lazy { googleSansFlex(rond = 100f, opticalSize = 144f) }

    /** Phone display/heroWord: "just now", edit-sheet time fields (ROND 100). */
    val PhoneDisplay: FontFamily by lazy { googleSansFlex(rond = 100f) }

    /** Watch text uses the Wear system font (§10.12); kept for source compatibility. */
    @Deprecated("Watch text uses the system font", ReplaceWith("FontFamily.Default"))
    val WatchText: FontFamily get() = FontFamily.Default

    @Deprecated("Moved to WatchFonts so the watch does not load the phone fonts", ReplaceWith("WatchFonts.Numeral"))
    val WatchNumeral: FontFamily get() = WatchFonts.Numeral

    /** Atkinson Hyperlegible Next: phone prose without digits. */
    val Atkinson: FontFamily by lazy {
        FontFamily(
            WEIGHTS.map { weight ->
                Font(
                    resId = R.font.atkinson_hyperlegible_next,
                    weight = FontWeight(weight),
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(FontWeight(weight), FontStyle.Normal),
                )
            },
        )
    }
}

/**
 * Watch fonts (DESIGN_SPEC §5.7, §10.12). Watch text uses the Wear system font; only the Lamp and status numerals
 * (and their "just now") use Google Sans Flex, from a small subset: `res/font/google_sans_flex_watch.ttf`
 * (96 KB; ROND 100 and opsz 18 pinned, `wght` variable; Latin letters, digits, units and the separators).
 */
object WatchFonts {
    private val WEIGHTS = listOf(500, 600)

    /** Lamp / status numerals, units and "just now" (GSF ROND 100). */
    val Numeral: FontFamily by lazy {
        FontFamily(
            WEIGHTS.map { weight ->
                Font(
                    resId = R.font.google_sans_flex_watch,
                    weight = FontWeight(weight),
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(FontWeight(weight), FontStyle.Normal),
                )
            },
        )
    }
}

/** Tabular figures: every line with a digit uses a GSF style with `tnum` (the digit rule). */
private const val TNUM = "tnum"

/** The phone type scale (DESIGN_SPEC §5.7). Map onto Material Typography in the app. */
@Immutable
object PhoneType {
    val heroNumeral = TextStyle(
        fontFamily = XFonts.PhoneNumeral, fontSize = 76.sp, lineHeight = 80.sp, fontWeight = FontWeight(600),
        letterSpacing = (-0.005).em, fontFeatureSettings = TNUM,
    )
    val heroUnit = TextStyle(fontFamily = XFonts.PhoneNumeral, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight(500))
    val heroWord = TextStyle(fontFamily = XFonts.PhoneDisplay, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight(500))
    val displaySmall = TextStyle(
        fontFamily = XFonts.PhoneDisplay, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight(500), fontFeatureSettings = TNUM,
    )
    val headlineSmall = TextStyle(fontFamily = XFonts.PhoneHeadline, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight(600))
    val titleLarge = TextStyle(
        fontFamily = XFonts.PhoneText, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM,
    )
    val titleMedium = TextStyle(
        fontFamily = XFonts.PhoneText, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM,
    )
    val dataMedium = TextStyle(
        fontFamily = XFonts.PhoneText, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight(450), fontFeatureSettings = TNUM,
    )
    val dataSmall = TextStyle(
        fontFamily = XFonts.PhoneText, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight(500), fontFeatureSettings = TNUM,
    )
    val labelLarge = TextStyle(
        fontFamily = XFonts.PhoneText, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM,
    )
    val labelMedium = TextStyle(
        fontFamily = XFonts.PhoneText, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM,
    )
    val labelSmall = TextStyle(
        fontFamily = XFonts.PhoneText, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM,
    )
    val bodyLarge = TextStyle(fontFamily = XFonts.Atkinson, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight(400))
    val bodyMedium = TextStyle(fontFamily = XFonts.Atkinson, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight(400))
    val bodySmall = TextStyle(fontFamily = XFonts.Atkinson, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(400))
}

/**
 * The watch type scale (DESIGN_SPEC §5.7). Text styles have no font family, so they use the Wear system font
 * (merge them into the Wear Material `Typography`); numerals, units and "just now" use [WatchFonts.Numeral].
 * Units of numerals are separate styles ([lampUnit], [statusUnit]).
 */
@Immutable
object WatchType {
    val lampNumeral = TextStyle(
        fontFamily = WatchFonts.Numeral, fontSize = 34.sp, lineHeight = 34.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM,
    )
    val lampUnit = TextStyle(fontFamily = WatchFonts.Numeral, fontSize = 17.sp, lineHeight = 34.sp, fontWeight = FontWeight(500))
    val statusNumeral = TextStyle(
        fontFamily = WatchFonts.Numeral, fontSize = 36.sp, lineHeight = 36.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM,
    )
    val statusUnit = TextStyle(fontFamily = WatchFonts.Numeral, fontSize = 18.sp, lineHeight = 36.sp, fontWeight = FontWeight(500))
    /** "just now" in the watch Lamp (GSF 500, ROND 100, 20 sp). */
    val lampWord = TextStyle(fontFamily = WatchFonts.Numeral, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight(500))
    /** "just now" in the idle status block (GSF 500, 28 sp). */
    val statusWord = TextStyle(fontFamily = WatchFonts.Numeral, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight(500))
    val titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM)
    val titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM)
    val titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM)
    val labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight(600))
    val labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight(500), fontFeatureSettings = TNUM)
    val bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight(450), fontFeatureSettings = TNUM)
    val bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight(500), fontFeatureSettings = TNUM)
    val timeText = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(600), fontFeatureSettings = TNUM)
}
