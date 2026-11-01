package com.ffmathy.pooparazzi.core.ui.icons

import androidx.annotation.DrawableRes
import com.ffmathy.pooparazzi.core.R
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.FeedType

/**
 * The icons as vector drawable resources (`R.drawable.x_<name>`), generated from [XIconSpecs] (DESIGN_SPEC §6.1),
 * for surfaces that take resource ids: Wear tiles, complication images, the Ongoing Activity and notification small
 * icon ([sleepRunning]) and Glance widgets.
 *
 * They are drawn in **white** (strokes, and fills where the spec fills), so they work as-is as monochrome
 * complication images and notification icons; tint them everywhere else (tile `ColorFilter`, Glance
 * `ColorFilter.tint`, `android:tint`). `both` is also available in fixed two-tone colours: [bothNight]
 * (Night and watch PEE + POOP strong) and [bothDay].
 */
object XDrawables {
    @DrawableRes val sleep: Int = R.drawable.x_sleep
    @DrawableRes val wake: Int = R.drawable.x_wake
    @DrawableRes val feed: Int = R.drawable.x_feed
    @DrawableRes val diaper: Int = R.drawable.x_diaper
    @DrawableRes val pee: Int = R.drawable.x_pee
    @DrawableRes val poop: Int = R.drawable.x_poop
    @DrawableRes val both: Int = R.drawable.x_both
    @DrawableRes val history: Int = R.drawable.x_history
    @DrawableRes val upcoming: Int = R.drawable.x_upcoming
    @DrawableRes val edit: Int = R.drawable.x_edit
    @DrawableRes val undo: Int = R.drawable.x_undo
    @DrawableRes val delete: Int = R.drawable.x_delete
    @DrawableRes val profile: Int = R.drawable.x_profile
    @DrawableRes val settings: Int = R.drawable.x_settings
    @DrawableRes val phone: Int = R.drawable.x_phone
    @DrawableRes val synced: Int = R.drawable.x_synced
    @DrawableRes val pending: Int = R.drawable.x_pending
    @DrawableRes val offline: Int = R.drawable.x_offline
    @DrawableRes val check: Int = R.drawable.x_check
    @DrawableRes val plus: Int = R.drawable.x_plus
    @DrawableRes val back: Int = R.drawable.x_back
    @DrawableRes val chevronLeft: Int = R.drawable.x_chevron_left
    @DrawableRes val chevronRight: Int = R.drawable.x_chevron_right
    @DrawableRes val chevronDown: Int = R.drawable.x_chevron_down
    @DrawableRes val close: Int = R.drawable.x_close
    @DrawableRes val clock: Int = R.drawable.x_clock
    @DrawableRes val awake: Int = R.drawable.x_awake
    @DrawableRes val sleepRunning: Int = R.drawable.x_sleep_running
    @DrawableRes val breast: Int = R.drawable.x_breast
    @DrawableRes val food: Int = R.drawable.x_food
    @DrawableRes val sideLeft: Int = R.drawable.x_side_left
    @DrawableRes val sideRight: Int = R.drawable.x_side_right
    @DrawableRes val sideLeftRight: Int = R.drawable.x_side_left_right
    @DrawableRes val sideRightLeft: Int = R.drawable.x_side_right_left
    @DrawableRes val checkbox: Int = R.drawable.x_checkbox
    @DrawableRes val checkboxChecked: Int = R.drawable.x_checkbox_checked
    @DrawableRes val info: Int = R.drawable.x_info
    @DrawableRes val chart: Int = R.drawable.x_chart
    @DrawableRes val watchSynced: Int = R.drawable.x_watch_synced
    @DrawableRes val watchPending: Int = R.drawable.x_watch_pending
    @DrawableRes val watchOffline: Int = R.drawable.x_watch_offline
    @DrawableRes val watchError: Int = R.drawable.x_watch_error
    @DrawableRes val phoneSynced: Int = R.drawable.x_phone_synced
    @DrawableRes val phonePending: Int = R.drawable.x_phone_pending
    @DrawableRes val phoneOffline: Int = R.drawable.x_phone_offline
    @DrawableRes val cloudError: Int = R.drawable.x_cloud_error

    /** Two-tone `both` in Night/watch colours (droplet `#8FD0C3`, mound `#358276`). Do not tint. */
    @DrawableRes val bothNight: Int = R.drawable.x_both_night

    /** Two-tone `both` in Day colours (droplet `#2C7468`, mound `#143F38`). Do not tint. */
    @DrawableRes val bothDay: Int = R.drawable.x_both_day

    @DrawableRes
    fun forFeedType(type: FeedType?): Int = when (type) {
        null -> feed
        FeedType.BREAST -> breast
        FeedType.FORMULA -> feed
        FeedType.FOOD -> food
    }

    /** The kind glyph; [twoTone] picks the fixed-colour `both` ([bothNight] / [bothDay]) instead of the tintable one. */
    @DrawableRes
    fun forDiaperKind(kind: DiaperKind, twoTone: BothTone? = null): Int = when (kind) {
        DiaperKind.PEE -> pee
        DiaperKind.POOP -> poop
        DiaperKind.PLAIN -> diaper
        DiaperKind.BOTH -> when (twoTone) {
            null -> both
            BothTone.NIGHT -> bothNight
            BothTone.DAY -> bothDay
        }
    }

    @DrawableRes
    fun forSide(side: BreastSide): Int = when (side) {
        BreastSide.LEFT -> sideLeft
        BreastSide.RIGHT -> sideRight
        BreastSide.LEFT_THEN_RIGHT -> sideLeftRight
        BreastSide.RIGHT_THEN_LEFT -> sideRightLeft
    }

    /** Every tintable icon drawable by its spec name. */
    val all: Map<String, Int>
        get() = linkedMapOf(
            "sleep" to sleep,
            "wake" to wake,
            "feed" to feed,
            "diaper" to diaper,
            "pee" to pee,
            "poop" to poop,
            "both" to both,
            "history" to history,
            "upcoming" to upcoming,
            "edit" to edit,
            "undo" to undo,
            "delete" to delete,
            "profile" to profile,
            "settings" to settings,
            "phone" to phone,
            "synced" to synced,
            "pending" to pending,
            "offline" to offline,
            "check" to check,
            "plus" to plus,
            "back" to back,
            "chevronLeft" to chevronLeft,
            "chevronRight" to chevronRight,
            "chevronDown" to chevronDown,
            "close" to close,
            "clock" to clock,
            "awake" to awake,
            "sleepRunning" to sleepRunning,
            "breast" to breast,
            "food" to food,
            "sideLeft" to sideLeft,
            "sideRight" to sideRight,
            "sideLeftRight" to sideLeftRight,
            "sideRightLeft" to sideRightLeft,
            "checkbox" to checkbox,
            "checkboxChecked" to checkboxChecked,
            "info" to info,
            "chart" to chart,
            "watchSynced" to watchSynced,
            "watchPending" to watchPending,
            "watchOffline" to watchOffline,
            "watchError" to watchError,
            "phoneSynced" to phoneSynced,
            "phonePending" to phonePending,
            "phoneOffline" to phoneOffline,
            "cloudError" to cloudError,
        )

    /** Which fixed colours the two-tone `both` drawable uses. */
    enum class BothTone { NIGHT, DAY }
}
