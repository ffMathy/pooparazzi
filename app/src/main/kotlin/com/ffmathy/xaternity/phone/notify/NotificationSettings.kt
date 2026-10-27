package com.ffmathy.xaternity.phone.notify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/** What Settings › Notifications offers (DESIGN_SPEC §10.5). */
enum class NotificationLink {
    /** Notifications are off for the app: its notification settings, to turn them on. */
    OFF,

    /** Android 16 QPR2+: the system's Live Update (promoted notification) setting for the app. */
    LIVE_UPDATES,

    /** Older Android: the app's notification settings. */
    NOTIFICATIONS,
    ;

    companion object {
        fun current(context: Context): NotificationLink {
            val enabled = context.hasNotificationPermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()
            return when {
                !enabled -> OFF
                liveUpdatesSupported() -> LIVE_UPDATES
                else -> NOTIFICATIONS
            }
        }

        /** Apps can ask for promotion from Android 16 QPR2 (API 36.1). */
        private fun liveUpdatesSupported(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
    }
}

/** Opens the system setting behind [link]. */
fun Context.openNotificationSettings(link: NotificationLink) {
    val action = if (link == NotificationLink.LIVE_UPDATES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS
    } else {
        Settings.ACTION_APP_NOTIFICATION_SETTINGS
    }
    val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("NotificationSettings", "No settings screen for $action", e)
    }
}
