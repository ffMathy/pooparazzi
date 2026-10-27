package com.ffmathy.xaternity.phone.system

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Small device-local flags of the system surfaces. Not settings: nothing here syncs to the watch.
 *
 * - whether the notification permission was asked for (asked once, on the first timer start; DESIGN_SPEC §10.5);
 * - the running entries whose ongoing notification the parent swiped away (not reposted, §10.5);
 * - the app version whose generated widget previews were published (Glance 1.2 `setWidgetPreviews` is rate-limited).
 */
class SurfacePrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSION_ASKED, value) }

    /** Entry ids of running timers whose notification was dismissed. */
    var dismissedTimers: Set<String>
        get() = prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEY_DISMISSED, value) }

    var widgetPreviewsVersion: Long
        get() = prefs.getLong(KEY_PREVIEWS_VERSION, -1L)
        set(value) = prefs.edit { putLong(KEY_PREVIEWS_VERSION, value) }

    private companion object {
        const val FILE = "system_surfaces"
        const val KEY_PERMISSION_ASKED = "notification_permission_asked"
        const val KEY_DISMISSED = "dismissed_timers"
        const val KEY_PREVIEWS_VERSION = "widget_previews_version"
    }
}
