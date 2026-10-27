package com.ffmathy.xaternity.phone.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import com.ffmathy.xaternity.core.domain.localTime
import com.ffmathy.xaternity.core.domain.summarize
import com.ffmathy.xaternity.core.format.ClockFormatter
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.settings.AppLanguage
import com.ffmathy.xaternity.core.settings.Settings
import com.ffmathy.xaternity.core.ui.icons.XDrawables
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.launch.DeepLinks
import com.ffmathy.xaternity.phone.launch.HomeLaunch
import com.ffmathy.xaternity.phone.system.SurfacePrefs
import com.ffmathy.xaternity.phone.system.inAppLanguage
import java.time.ZoneId
import com.ffmathy.xaternity.core.R as CoreR

/** A running sleep or breastfeed as its notification shows it: which entry, and since when. */
data class RunningTimer(val entryId: String, val startMillis: Long)

/**
 * What the ongoing notifications show (DESIGN_SPEC §3.3.7, §10.5): the running sleep and breastfeed, the last feed (the
 * sleep's "Last fed" metric), and the name and language the text uses. Equal states post nothing new.
 */
data class TimerState(
    val sleep: RunningTimer?,
    val feed: RunningTimer?,
    val lastFeedStartMillis: Long?,
    val babyName: String,
    val language: AppLanguage,
    val zone: ZoneId,
) {
    companion object {
        fun of(entries: List<Entry>, settings: Settings, now: Long, zone: ZoneId): TimerState {
            val summary = summarize(entries, now, zone)
            return TimerState(
                sleep = summary.runningSleep?.let { RunningTimer(it.id, it.startMillis) },
                feed = summary.runningFeed?.let { RunningTimer(it.id, it.startMillis) },
                lastFeedStartMillis = summary.lastFeed?.startMillis,
                babyName = settings.babyName,
                language = settings.language,
                zone = zone,
            )
        }
    }
}

/**
 * The ongoing notifications of a running sleep and a running breastfeed (DESIGN_SPEC §3.3.7, §10.5): a silent
 * IMPORTANCE_LOW channel, "Noa is asleep" / "Since 02:05" with a "Woke up" action that works from the lock screen, and
 * "Noa is breastfeeding" with "End feed", which opens the app on the Side sheet. Each asks to be promoted to a Live
 * Update where the system allows it, and is a normal ongoing notification otherwise.
 *
 * Absolute times only, never a ticking duration. A notification the parent swipes away is not posted again for that
 * entry. Posting needs the notification permission (Android 13+); without it nothing is posted and the timers run as
 * usual.
 */
class TimerNotifications(context: Context, private val prefs: SurfacePrefs = SurfacePrefs(context)) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Posts, updates or cancels both notifications so they match [state]. */
    fun render(state: TimerState) {
        // Forget dismissals of timers that have ended, so the next sleep or feed gets its notification again.
        val running = setOfNotNull(state.sleep?.entryId, state.feed?.entryId)
        val dismissed = prefs.dismissedTimers
        if (!running.containsAll(dismissed)) prefs.dismissedTimers = dismissed intersect running

        show(SLEEP_ID, state, state.sleep, dismissed) { buildSleep(state, it) }
        show(FEED_ID, state, state.feed, dismissed) { buildFeed(state, it) }
    }

    private fun show(id: Int, state: TimerState, timer: RunningTimer?, dismissed: Set<String>, build: (RunningTimer) -> Notification) {
        when {
            timer == null -> manager.cancel(id)
            timer.entryId in dismissed -> Unit
            !canPost() -> Unit
            else -> try {
                ensureChannel(context.inAppLanguage(state.language))
                manager.notify(id, build(timer))
            } catch (e: SecurityException) {
                // The permission was revoked between the check and the post.
                Log.w(TAG, "Could not post the timer notification", e)
            }
        }
    }

    /** Cancels the sleep notification at once (its "Woke up" action), before the entries flow catches up. */
    fun cancelSleep() = manager.cancel(SLEEP_ID)

    private fun canPost(): Boolean =
        context.hasNotificationPermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The silent channel; its name and description follow the app language (re-creating updates them). */
    private fun ensureChannel(strings: Context) {
        val channel = NotificationChannel(CHANNEL_ID, strings.getString(R.string.notification_channel_timers), NotificationManager.IMPORTANCE_LOW)
            .apply {
                description = strings.getString(R.string.notification_channel_timers_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        manager.createNotificationChannel(channel)
    }

    @VisibleForTesting
    internal fun buildSleep(state: TimerState, sleep: RunningTimer): Notification {
        val strings = context.inAppLanguage(state.language)
        val clock = ClockFormatter.from(strings)
        val status = strings.getString(CoreR.string.status_asleep)
        val title = state.babyName.takeIf { it.isNotBlank() }?.let { strings.getString(R.string.notification_asleep_title, it) } ?: status
        val wokeUp = action(
            icon = XDrawables.wake,
            title = strings.getString(CoreR.string.action_woke_up),
            intent = TimerActionReceiver.wokeUpIntent(context, sleep.entryId),
        )
        return base(strings, title, sleep, clock, state.zone, smallIcon = XDrawables.sleepRunning, status = status)
            .addAction(wokeUp)
            .setDeleteIntent(TimerActionReceiver.dismissedIntent(context, sleep.entryId))
            .withMetrics(
                strings = strings,
                zone = state.zone,
                since = sleep.startMillis,
                lastFed = state.lastFeedStartMillis,
            )
            .build()
    }

    @VisibleForTesting
    internal fun buildFeed(state: TimerState, feed: RunningTimer): Notification {
        val strings = context.inAppLanguage(state.language)
        val clock = ClockFormatter.from(strings)
        val status = strings.getString(CoreR.string.status_breastfeeding)
        val title = state.babyName.takeIf { it.isNotBlank() }?.let { strings.getString(R.string.notification_breastfeeding_title, it) } ?: status
        val endFeed = action(
            icon = XDrawables.breast,
            title = strings.getString(CoreR.string.action_end_feed),
            intent = DeepLinks.pendingIntent(context, HomeLaunch.Side),
        )
        return base(strings, title, feed, clock, state.zone, smallIcon = XDrawables.breast, status = status)
            .addAction(endFeed)
            .setDeleteIntent(TimerActionReceiver.dismissedIntent(context, feed.entryId))
            .withMetrics(strings = strings, zone = state.zone, since = feed.startMillis, lastFed = null)
            .build()
    }

    private fun base(
        strings: Context,
        title: String,
        timer: RunningTimer,
        clock: ClockFormatter,
        zone: ZoneId,
        smallIcon: Int,
        status: String,
    ): Notification.Builder {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(strings.getString(R.string.notification_since, clock.format(timer.startMillis, zone)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(DeepLinks.pendingIntent(context, HomeLaunch.Home))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // The status chip: a word, never a ticking value (DESIGN_SPEC §3.3.7).
            builder.setShortCriticalText(status)
            if (canPostPromoted()) builder.setRequestPromotedOngoing(true)
        }
        return builder
    }

    private fun action(icon: Int, title: String, intent: PendingIntent): Notification.Action {
        val builder = Notification.Action.Builder(Icon.createWithResource(context, icon), title, intent)
        // Usable from the lock screen without unlocking (DESIGN_SPEC §10.5).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAuthenticationRequired(false)
        return builder.build()
    }

    /** Android 17 metrics: "Since 02:05" and "Last fed 01:40", both fixed times (no TimeDifference, §10.5). */
    private fun Notification.Builder.withMetrics(strings: Context, zone: ZoneId, since: Long, lastFed: Long?): Notification.Builder {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return this
        val style = Notification.MetricStyle()
            .addMetric(Notification.Metric(Notification.Metric.FixedTime(localTime(since, zone)), strings.getString(R.string.notification_metric_since)))
        if (lastFed != null) {
            style.addMetric(Notification.Metric(Notification.Metric.FixedTime(localTime(lastFed, zone)), strings.getString(CoreR.string.status_last_fed)))
        }
        return setStyle(style)
    }

    private fun canPostPromoted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false)

    companion object {
        private const val TAG = "TimerNotifications"
        const val CHANNEL_ID = "running_timers"
        const val SLEEP_ID = 1001
        const val FEED_ID = 1002
    }
}
