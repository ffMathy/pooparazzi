package com.ffmathy.pooparazzi.core.ui.icons

import androidx.compose.runtime.Immutable

/**
 * One path of an icon in the 24 × 24 viewport (DESIGN_SPEC §6.1): stroked (width 2, round caps and joins),
 * filled, or both. [evenOdd] applies to the fill.
 */
@Immutable
data class XIconPath(
    val pathData: String,
    val stroke: Boolean = true,
    val fill: Boolean = false,
    val evenOdd: Boolean = false,
)

/**
 * An icon's geometry: the single source for both the Compose [XIcons] vectors and the generated vector drawables
 * `R.drawable.x_<name>` ([XDrawables], for tiles, complications, notifications, widgets and the splash).
 * [drawableName] is the resource name.
 */
@Immutable
data class XIconSpec(val name: String, val paths: List<XIconPath>) {
    val drawableName: String get() = "x_" + name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}

/**
 * Path data of every icon. The spec icons are copied verbatim from DESIGN_SPEC §6.2; the v1 additions
 * ([breast], [food], the side glyphs, [checkbox], [checkboxChecked], [info], [chart]) follow the same style.
 */
object XIconSpecs {
    val sleep = icon("sleep", "M20 14.6A8.5 8.5 0 1 1 9.4 4a7 7 0 0 0 10.6 10.6z")
    val wake = icon("wake", "M3 19.5h18", "M7 19.5a5 5 0 0 1 10 0", "M12 5v3", "M5.3 10.3l2.1 2.1", "M18.7 10.3l-2.1 2.1")
    val feed = icon(
        "feed",
        "M10 5v-.5a2 2 0 0 1 4 0V5",
        "M8.5 5h7v3h-7z",
        "M9 8 8 10.5v8.5a2.5 2.5 0 0 0 2.5 2.5h3a2.5 2.5 0 0 0 2.5-2.5v-8.5L15 8",
        "M10.5 13.5h3",
        "M10.5 17h3",
    )
    val diaper = icon(
        "diaper",
        "M3 6.5h18v4a3.5 3.5 0 0 0-3.5 3.5v1.5a3 3 0 0 1-3 3h-5a3 3 0 0 1-3-3V14A3.5 3.5 0 0 0 3 10.5z",
        "M3 9h18",
    )
    val pee = icon("pee", "M12 3.5C9 7.5 6.5 10.5 6.5 14a5.5 5.5 0 0 0 11 0c0-3.5-2.5-6.5-5.5-10.5z")
    val poop = icon(
        "poop",
        "M6.5 20.5h11a2.5 2.5 0 0 0 0-5h-2.25a2.25 2.25 0 0 0 0-4.5H13a2 2 0 0 0 0-4c0-1.2-.6-2.3-1.8-3L11 7a2 2 0 0 0 0 4H8.75a2.25 2.25 0 0 0 0 4.5H6.5a2.5 2.5 0 0 0 0 5z",
    )

    /** Two paths: [BOTH_PEE] (PEE strong) and [BOTH_POOP] (POOP strong) in the two-tone form. */
    val both = icon("both", BOTH_PEE, BOTH_POOP)
    val history = icon("history", "M3.5 12a8.5 8.5 0 1 0 2.5-6", "M3.5 3.5V8H8", "M12 7.5V12l3 2")
    val edit = icon("edit", "M5 19l.9-3.9L16.1 4.9a2.05 2.05 0 0 1 2.9 2.9L8.8 18.1z", "M14.5 6.5l3 3")
    val undo = icon("undo", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    val delete = icon(
        "delete",
        "M4 6.5h16", "M9.5 6.5v-2h5v2", "M6.5 6.5l.9 12.6a2 2 0 0 0 2 1.9h5.2a2 2 0 0 0 2-1.9l.9-12.6", "M10 10.5v6", "M14 10.5v6",
    )
    val profile = icon("profile", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M4.5 20.5a7.5 7.5 0 0 1 15 0")
    val settings = icon(
        "settings",
        "M4 7h8.5", "M16.5 7H20", "M4 17h3.5", "M11.5 17H20", "M14.5 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", "M9.5 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
    )
    val phone = icon(
        "phone",
        "M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5z",
        "M10.5 18.5h3",
    )
    val synced = icon("synced", CLOUD, "M9.5 14.5l2 2 3.5-3.5")
    val pending = icon("pending", CLOUD, "M12 17v-5", "M9.75 14.25 12 12l2.25 2.25")
    val offline = icon("offline", "M8.6 6.5A6 6 0 0 1 17.6 11a4 4 0 0 1 2.1 6.9", "M16 19H7a4.5 4.5 0 0 1-1-8.9", "M3 3l18 18")
    val check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val plus = icon("plus", "M12 5v14", "M5 12h14")
    val back = icon("back", "M19 12H5", "M11 18l-6-6 6-6")
    val chevronLeft = icon("chevronLeft", "M14.5 6l-6 6 6 6")
    val chevronRight = icon("chevronRight", "M9.5 6l6 6-6 6")
    val chevronDown = icon("chevronDown", "M6 9.5l6 6 6-6")
    val close = icon("close", "M6 6l12 12", "M18 6 6 18")
    val clock = icon("clock", "M12 20.5a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17z", "M12 7.5V12l3 2")
    val lamp = icon("lamp", "M8.5 3.5h7l3.5 8h-14z", "M12 11.5v6.5", "M8.5 20.5h7")
    val awake = icon(
        "awake",
        "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M12 2.5v2", "M12 19.5v2", "M2.5 12h2", "M19.5 12h2",
        "M5.3 5.3l1.4 1.4", "M17.3 17.3l1.4 1.4", "M5.3 18.7l1.4-1.4", "M17.3 6.7l1.4-1.4",
    )

    /** Ring (stroked) + filled crescent: "a sleep is running" on glance surfaces. */
    val sleepRunning = XIconSpec(
        "sleepRunning",
        listOf(
            XIconPath("M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 0 0 0-19z"),
            XIconPath("M16 13.3A4.25 4.25 0 1 1 10.7 8a3.5 3.5 0 0 0 5.3 5.3z", stroke = false, fill = true),
        ),
    )

    // ---- v1 additions (V1_ADDITIONS §7) -------------------------------------------------------

    /**
     * Breastfeeding: a seated parent (head, back down to the lap, the front of the shoulder) whose arm cradles a
     * swaddled baby, the baby's small head at the chest. Drawn to read at 16 dp: one figure, not loose glyphs.
     */
    val breast = icon(
        "breast",
        "M7.5 6.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
        "M7.5 9C5 9 3.5 11 3.5 14v7",
        "M7.5 9c1 0 1.8.5 2.3 1.3",
        "M12.75 12a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5z",
        "M7.5 12.5c0 3 2.2 5 5.5 5h5a2.5 2.5 0 0 0 0-5h-1.5",
    )

    /** Food (solids): a bowl with a spoon. */
    val food = icon(
        "food",
        "M3.5 12h17",
        "M4.5 12a7.5 7.5 0 0 0 15 0",
        "M13.5 12l3.5-5",
        "M19.9 5.6c.9-1.3.8-2.8-.2-3.4s-2.4 0-3.3 1.3-.8 2.8.2 3.4 2.4 0 3.3-1.3z",
    )

    /** Two breasts as circles; the filled one is the side. Letters come from text, never the glyph. */
    val sideLeft = XIconSpec("sideLeft", listOf(filled(SIDE_LEFT_CENTRE), XIconPath(SIDE_RIGHT_CENTRE)))
    val sideRight = XIconSpec("sideRight", listOf(XIconPath(SIDE_LEFT_CENTRE), filled(SIDE_RIGHT_CENTRE)))

    /** Left first (filled), then right, with an arrow arcing from left to right. */
    val sideLeftRight = XIconSpec(
        "sideLeftRight",
        listOf(filled(SIDE_LEFT_LOW), XIconPath(SIDE_RIGHT_LOW), XIconPath("M6.5 8.5C8 4.5 16 4.5 17.5 8.5"), XIconPath("M14.5 8.5h3V5.5")),
    )

    /** Right first (filled), then left, with an arrow arcing from right to left. */
    val sideRightLeft = XIconSpec(
        "sideRightLeft",
        listOf(XIconPath(SIDE_LEFT_LOW), filled(SIDE_RIGHT_LOW), XIconPath("M17.5 8.5C16 4.5 8 4.5 6.5 8.5"), XIconPath("M9.5 8.5h-3V5.5")),
    )

    /** Empty checkbox (rounded square). */
    val checkbox = icon("checkbox", "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z")

    /** Checked checkbox: a filled rounded square with the tick knocked out (even-odd), same outer bounds as [checkbox]. */
    val checkboxChecked = XIconSpec(
        "checkboxChecked",
        listOf(
            XIconPath(
                "M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4z" +
                    "M16.7 8.8a1 1 0 0 0-1.4 0L10.5 13.6 8.7 11.8a1 1 0 0 0-1.4 1.4l2.5 2.5a1 1 0 0 0 1.4 0l5.5-5.5a1 1 0 0 0 0-1.4z",
                stroke = false,
                fill = true,
                evenOdd = true,
            ),
        ),
    )

    /** Info: a circled "i" (health hint). */
    val info = icon("info", "M12 20.5a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17z", "M12 11v5", "M12 7.75h.01")

    /** Chart: three uneven bars on a baseline (History's button to Statistics). */
    val chart = icon("chart", "M3.5 20.5h17", "M7 17v-5", "M12 17V6", "M17 17v-8")

    // ---- Sync status (DESIGN_SPEC §4.5): the other device or the backup, with its state drawn inside ---------------

    /** Watch (the phone's top bars): the face holds a tick (synced), "…" (syncing), a slash (offline) or "!" (error). */
    val watchSynced = icon("watchSynced", WATCH_BODY, WATCH_STRAP_TOP, WATCH_STRAP_BOTTOM, "M9.25 12.25 11 14l3.75-3.75")
    val watchPending = icon("watchPending", WATCH_BODY, WATCH_STRAP_TOP, WATCH_STRAP_BOTTOM, "M9 12h.01", "M12 12h.01", "M15 12h.01")
    val watchOffline = icon(
        "watchOffline",
        "M10 5.5h5.5a2 2 0 0 1 2 2v6",
        "M14 18.5H8.5a2 2 0 0 1-2-2v-6",
        WATCH_STRAP_TOP,
        WATCH_STRAP_BOTTOM,
        "M4 4l16 16",
    )
    val watchError = icon("watchError", WATCH_BODY, WATCH_STRAP_TOP, WATCH_STRAP_BOTTOM, "M12 9v3", "M12 15.5h.01")

    /** Phone (the watch's footer): [phone] with a tick, "…" or a slash on its screen. The watch shows no error. */
    val phoneSynced = icon("phoneSynced", PHONE_BODY, PHONE_HOME, "M9.25 10.75 11 12.5l3.75-3.75")
    val phonePending = icon("phonePending", PHONE_BODY, PHONE_HOME, "M9 10.5h.01", "M12 10.5h.01", "M15 10.5h.01")
    val phoneOffline = icon(
        "phoneOffline",
        "M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v9.5",
        "M16 21.5H8A1.5 1.5 0 0 1 6.5 20v-9.5",
        PHONE_HOME,
        "M3 3l18 18",
    )

    /** The backup failed: the cloud of [synced] with "!" (its other states are [synced], [pending] and [offline]). */
    val cloudError = icon("cloudError", CLOUD, "M12 10v3", "M12 16h.01")

    /** Every icon, in the order of DESIGN_SPEC §6.2 followed by the v1 additions. */
    val all: List<XIconSpec> = listOf(
        sleep, wake, feed, diaper, pee, poop, both, history, edit, undo, delete, profile, settings, phone, synced, pending,
        offline, check, plus, back, chevronLeft, chevronRight, chevronDown, close, clock, lamp, awake, sleepRunning,
        breast, food, sideLeft, sideRight, sideLeftRight, sideRightLeft, checkbox, checkboxChecked, info, chart,
        watchSynced, watchPending, watchOffline, watchError, phoneSynced, phonePending, phoneOffline, cloudError,
    )

    internal const val BOTH_PEE = "M7.5 3C5.6 5.6 4 7.6 4 9.9a3.5 3.5 0 0 0 7 0C11 7.6 9.4 5.6 7.5 3z"
    internal const val BOTH_POOP =
        "M13 20.5h6a2 2 0 0 0 0-4h-.2A2.8 2.8 0 0 0 16.6 13c0-.9-.4-1.7-1.2-2.3l-.4 2.3a2.8 2.8 0 0 0-1.8 3.5H13a2 2 0 0 0 0 4z"
    private const val CLOUD = "M7 19h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6 10.1 4.5 4.5 0 0 0 7 19z"
    private const val WATCH_BODY = "M8.5 5.5h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z"
    private const val WATCH_STRAP_TOP = "M9.5 5.5 10 3h4l.5 2.5"
    private const val WATCH_STRAP_BOTTOM = "M9.5 18.5l.5 2.5h4l.5-2.5"
    private const val PHONE_BODY = "M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5z"
    private const val PHONE_HOME = "M10.5 18.5h3"
    private const val SIDE_LEFT_CENTRE = "M6.5 16a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5z"
    private const val SIDE_RIGHT_CENTRE = "M17.5 16a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5z"
    private const val SIDE_LEFT_LOW = "M6.5 19.5a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5z"
    private const val SIDE_RIGHT_LOW = "M17.5 19.5a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5z"

    private fun icon(name: String, vararg strokes: String) = XIconSpec(name, strokes.map { XIconPath(it) })

    private fun filled(path: String) = XIconPath(path, stroke = true, fill = true)
}
