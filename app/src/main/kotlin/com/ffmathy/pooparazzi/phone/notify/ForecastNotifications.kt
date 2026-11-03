package com.ffmathy.pooparazzi.phone.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import com.ffmathy.pooparazzi.core.domain.Expected
import com.ffmathy.pooparazzi.core.domain.ForecastKind
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.format.EstimateText
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.launch.DeepLinks
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
import com.ffmathy.pooparazzi.phone.system.inAppLanguage
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * The phone's prediction notifications (DESIGN_SPEC §10.5): what comes next, as Home's buttons show it, while the app is
 * closed. One ongoing, silent notification per kind and baby: "Next feed in 1h 05m" / "Around 15:30", "Next poop soon",
 * "Expected to wake in 40m" (asleep) or "Next sleep in 20m" (awake). It shows only while the week predicts that kind
 * (Home shows nothing either then), and goes once it is no longer predicted (the next one was logged, or logging stopped).
 *
 * Each kind has its own channel ([channelId]), so the parent chooses which ones show in the system's notification
 * settings, one switch each; Settings › Notifications lists them with their state and opens each channel's page. The
 * channels are on (IMPORTANCE_LOW: silent, no peeking) until the parent turns one off.
 *
 * Swiped away, an estimate is not posted again ([SurfacePrefs.dismissedForecasts]); the next estimate of that kind (after
 * the next log) shows again. Posting needs the notification permission (Android 13+); without it nothing is posted.
 */
class ForecastNotifications(context: Context, private val prefs: SurfacePrefs = SurfacePrefs(context)) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * Posts, updates or cancels the notifications so they match [state]'s forecasts. Returns whether any shows (they
     * need the once-a-minute refresh then).
     */
    fun render(state: TimerState): Boolean {
        val wanted = state.forecasts.flatMap { (babyId, forecast) ->
            ForecastKind.entries.mapNotNull { kind -> forecast[kind]?.let { Shown(babyId, kind, it) } }
        }
        // Forget dismissals of estimates that have gone, so the next one of that kind shows again.
        val keys = wanted.mapTo(mutableSetOf()) { it.key }
        val dismissed = prefs.dismissedForecasts
        if (!keys.containsAll(dismissed)) prefs.dismissedForecasts = dismissed intersect keys

        val allowed = canPost()
        if (allowed) ensureChannels(context, state.language)
        val posting = wanted.filter { allowed && it.key !in dismissed && channelOn(it.kind) }
        // Everything not posted now goes: an estimate that has gone, a baby removed, a channel turned off.
        val keep = posting.mapTo(mutableSetOf()) { it.tag to notificationId(it.kind) }
        runCatching { manager.activeNotifications.toList() }.getOrDefault(emptyList())
            .filter { it.id in IDS && (it.tag to it.id) !in keep }
            .forEach { manager.cancel(it.tag, it.id) }
        return posting.map { shown ->
            try {
                manager.notify(shown.tag, notificationId(shown.kind), build(state, shown.babyId, shown.kind, shown.expected))
                true
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not post the forecast notification", e)
                false
            }
        }.any { it }
    }

    /** Whether the parent left [kind]'s channel on (a channel not created yet is on: it is created on). */
    private fun channelOn(kind: ForecastKind): Boolean =
        manager.getNotificationChannel(channelId(kind))?.importance != NotificationManager.IMPORTANCE_NONE

    private fun canPost(): Boolean =
        context.hasNotificationPermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    @VisibleForTesting
    internal fun build(state: TimerState, babyId: String, kind: ForecastKind, expected: Expected): Notification {
        val strings = context.inAppLanguage(state.language)
        val estimates = EstimateText(strings)
        val asleep = state.sleeping.any { it.baby.id == babyId }
        val words = words(kind, asleep)
        val title = estimates.format(words.inId, words.soonId, words.nowId, expected, state.now)
        val clock = ClockFormatter.from(strings)
        val around = strings.getString(R.string.notification_forecast_around, clock.format(expected.roundedAtMillis, state.zone))
        val baby = state.babies.firstOrNull { it.id == babyId } ?: Baby(babyId)
        val builder = Notification.Builder(context, channelId(kind))
            .setSmallIcon(words.icon)
            .setContentTitle(title)
            .setContentText(around)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setSortKey("$babyId:${kind.ordinal}")
            .setDeleteIntent(TimerActionReceiver.forecastDismissedIntent(context, Shown(babyId, kind, expected).key))
            .setContentIntent(DeepLinks.pendingIntent(context, HomeLaunch.Home))
        // With several babies (or a named one), the baby the estimate is for, as the notification's header.
        if (state.babies.size > 1 || baby.name.isNotBlank()) {
            builder.setSubText(Formatters.from(strings, state.zone).labels.baby(baby, state.babies))
        }
        return builder.build()
    }

    /** What a forecast notification is: one kind's estimate for one baby. */
    private data class Shown(val babyId: String, val kind: ForecastKind, val expected: Expected) {
        val tag: String get() = tagFor(babyId)

        /** The estimate's identity: swiped away, this one is not posted again; the next estimate is a new key. */
        val key: String get() = "$babyId|${kind.name}|${expected.atMillis}"
    }

    /** A kind's words ("Next feed in 1h 05m", "Next feed soon", "Feed due now") and icon. */
    private class Words(@StringRes val inId: Int, @StringRes val soonId: Int, @StringRes val nowId: Int, val icon: Int)

    private fun words(kind: ForecastKind, asleep: Boolean): Words = when (kind) {
        ForecastKind.FEED -> Words(R.string.notification_forecast_feed_in, R.string.notification_forecast_feed_soon, R.string.notification_forecast_feed_now, XDrawables.feed)
        ForecastKind.POOP -> Words(CoreR.string.estimate_poop_in, CoreR.string.estimate_poop_soon, CoreR.string.estimate_poop_now, XDrawables.poop)
        ForecastKind.SLEEP -> if (asleep) {
            Words(R.string.notification_forecast_wake_in, R.string.notification_forecast_wake_soon, R.string.notification_forecast_wake_now, XDrawables.wake)
        } else {
            Words(R.string.notification_forecast_sleep_in, R.string.notification_forecast_sleep_soon, R.string.notification_forecast_sleep_now, XDrawables.sleep)
        }
    }

    companion object {
        private const val TAG = "ForecastNotifications"

        /** The channels' group in the system settings ("What comes next"). */
        const val GROUP_ID = "forecasts"

        const val FEED_ID = 2001
        const val POOP_ID = 2002
        const val SLEEP_ID = 2003
        private val IDS = setOf(FEED_ID, POOP_ID, SLEEP_ID)

        fun notificationId(kind: ForecastKind): Int = when (kind) {
            ForecastKind.FEED -> FEED_ID
            ForecastKind.POOP -> POOP_ID
            ForecastKind.SLEEP -> SLEEP_ID
        }

        /** [kind]'s channel: the parent turns each kind on or off in the system's notification settings. */
        fun channelId(kind: ForecastKind): String = when (kind) {
            ForecastKind.FEED -> "forecast_feed"
            ForecastKind.POOP -> "forecast_poop"
            ForecastKind.SLEEP -> "forecast_sleep"
        }

        /** The tag of baby [babyId]'s forecast notifications (one of each kind per baby). */
        fun tagFor(babyId: String): String = "forecast:$babyId"

        /**
         * Creates (or renames, in the app [language]) the group and the three channels: silent, on until the parent
         * turns them off. Re-creating never changes what the parent chose.
         */
        fun ensureChannels(context: Context, language: AppLanguage) {
            val strings = context.inAppLanguage(language)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, strings.getString(R.string.notification_group_forecasts)))
            val channels = ForecastKind.entries.map { kind ->
                val (name, description) = when (kind) {
                    ForecastKind.FEED -> R.string.notification_channel_forecast_feed to R.string.notification_channel_forecast_feed_description
                    ForecastKind.POOP -> R.string.notification_channel_forecast_poop to R.string.notification_channel_forecast_poop_description
                    ForecastKind.SLEEP -> R.string.notification_channel_forecast_sleep to R.string.notification_channel_forecast_sleep_description
                }
                // An existing channel is only renamed: what the parent chose (on or off) stays as it is.
                manager.getNotificationChannel(channelId(kind))?.apply {
                    this.name = strings.getString(name)
                    this.description = strings.getString(description)
                } ?: NotificationChannel(channelId(kind), strings.getString(name), NotificationManager.IMPORTANCE_LOW).apply {
                    this.description = strings.getString(description)
                    group = GROUP_ID
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            }
            manager.createNotificationChannels(channels)
        }

        /**
         * Whether each kind's notifications can show: notifications are allowed for the app and the parent left the
         * kind's channel on. For Settings › Notifications.
         */
        fun channelStates(context: Context): Map<ForecastKind, Boolean> {
            val manager = context.getSystemService(NotificationManager::class.java)
            val allowed = context.hasNotificationPermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()
            return ForecastKind.entries.associateWith { kind ->
                allowed && manager?.getNotificationChannel(channelId(kind))?.importance != NotificationManager.IMPORTANCE_NONE
            }
        }
    }
}

/**
 * Opens [kind]'s channel page in the system's notification settings (creating the channels first, so the page exists).
 * With notifications off for the app, the app's notification settings instead: the channel switches are there too.
 */
fun Context.openForecastChannelSettings(kind: ForecastKind, language: AppLanguage) {
    ForecastNotifications.ensureChannels(this, language)
    val appOn = hasNotificationPermission() && NotificationManagerCompat.from(this).areNotificationsEnabled()
    val intent = if (appOn) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ForecastNotifications.channelId(kind))
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("ForecastNotifications", "No settings screen for ${intent.action}", e)
    }
}
