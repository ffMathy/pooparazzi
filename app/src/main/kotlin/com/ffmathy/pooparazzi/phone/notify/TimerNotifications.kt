package com.ffmathy.pooparazzi.phone.notify

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
import com.ffmathy.pooparazzi.core.domain.localTime
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.launch.DeepLinks
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
import com.ffmathy.pooparazzi.phone.system.inAppLanguage
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** A running sleep as its notification shows it: which entry, and since when. */
data class RunningTimer(val entryId: String, val startMillis: Long)

/**
 * A baby who is asleep: [sleep] is their running sleep and [lastFeedStartMillis] their last feed (the sleep's "Last fed"
 * metric).
 */
data class SleepingBaby(val baby: Baby, val sleep: RunningTimer, val lastFeedStartMillis: Long?)

/**
 * What the ongoing notifications show (DESIGN_SPEC §3.3.7, §10.5; BABIES_AND_BACKUP §2.3): one per baby who is asleep,
 * whichever baby is the current one, and the babies (for their labels) and language the text uses. Equal states post
 * nothing new. Feeds are instants (breastfeeds too), so nothing else ever runs.
 */
data class TimerState(
    val sleeping: List<SleepingBaby>,
    val babies: List<Baby>,
    val language: AppLanguage,
    val zone: ZoneId,
) {
    companion object {
        /**
         * The state for [entries] of every baby (`observeAllEntries`), in the order of [settings]' babies; a baby whose
         * entries arrived before the phone adopted it is listed after them, unnamed.
         */
        fun of(entries: List<Entry>, settings: Settings, now: Long, zone: ZoneId): TimerState {
            val byBaby = entries.groupBy { it.babyId }
            val babies = settings.babies + byBaby.keys.filter { id -> settings.babies.none { it.id == id } }.map { Baby(it) }
            val sleeping = babies.mapNotNull { baby ->
                val summary = summarize(byBaby[baby.id].orEmpty(), now, zone)
                val sleep = summary.runningSleep ?: return@mapNotNull null
                SleepingBaby(baby, RunningTimer(sleep.id, sleep.startMillis), summary.lastFeed?.startMillis)
            }
            return TimerState(sleeping = sleeping, babies = babies, language = settings.language, zone = zone)
        }
    }
}

/**
 * The ongoing notification of a running sleep (DESIGN_SPEC §3.3.7, §10.5): a silent IMPORTANCE_LOW channel, "Noa is
 * asleep" / "Since 02:05" with a "Woke up" action that works from the lock screen. It asks to be promoted to a Live
 * Update where the system allows it, and is a normal ongoing notification otherwise.
 *
 * Every baby who is asleep has their own notification ("Noa is asleep", "Ida is asleep", BABIES_AND_BACKUP §2.3), told
 * apart by a tag per baby ([tagFor]) on [SLEEP_ID]; each "Woke up" ends that baby's sleep.
 *
 * Absolute times only, never a ticking duration. A notification the parent swipes away is not posted again for that
 * entry. Posting needs the notification permission (Android 13+); without it nothing is posted and the timers run as
 * usual.
 */
class TimerNotifications(context: Context, private val prefs: SurfacePrefs = SurfacePrefs(context)) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Posts, updates or cancels the notifications so they match [state]. */
    fun render(state: TimerState) {
        // Forget dismissals of sleeps that have ended, so the next sleep gets its notification again.
        val running = state.sleeping.mapTo(mutableSetOf()) { it.sleep.entryId }
        val dismissed = prefs.dismissedTimers
        if (!running.containsAll(dismissed)) prefs.dismissedTimers = dismissed intersect running

        // Babies who are awake now: their notifications go (the first baby's untagged one is always checked).
        val wanted = state.sleeping.mapTo(mutableSetOf()) { tagFor(it.baby.id) }
        (postedSleepTags() + null).filter { it !in wanted }.forEach { manager.cancel(it, SLEEP_ID) }
        state.sleeping.forEach { sleeping -> show(tagFor(sleeping.baby.id), state, sleeping, dismissed) }
        // A breastfeed notification posted by an older version (when breastfeeds were timed) is never wanted now.
        manager.cancel(LEGACY_FEED_ID)
    }

    private fun show(tag: String?, state: TimerState, sleeping: SleepingBaby, dismissed: Set<String>) {
        when {
            sleeping.sleep.entryId in dismissed -> Unit
            !canPost() -> Unit
            else -> try {
                ensureChannel(context.inAppLanguage(state.language))
                manager.notify(tag, SLEEP_ID, buildSleep(state, sleeping))
            } catch (e: SecurityException) {
                // The permission was revoked between the check and the post.
                Log.w(TAG, "Could not post the timer notification", e)
            }
        }
    }

    /** The tags of the sleep notifications showing now. */
    private fun postedSleepTags(): Set<String?> =
        runCatching { manager.activeNotifications.filter { it.id == SLEEP_ID }.mapTo(mutableSetOf()) { it.tag } }.getOrDefault(emptySet())

    /**
     * Cancels baby [babyId]'s sleep notification at once (its "Woke up" action), before the entries flow catches up.
     * Other babies' notifications stay.
     */
    fun cancelSleep(babyId: String) = manager.cancel(tagFor(babyId), SLEEP_ID)

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
    internal fun buildSleep(state: TimerState, sleeping: SleepingBaby): Notification {
        val sleep = sleeping.sleep
        val strings = context.inAppLanguage(state.language)
        val clock = ClockFormatter.from(strings)
        val status = strings.getString(CoreR.string.status_asleep)
        // One unnamed baby reads "Asleep", as before several babies; otherwise the baby's label ("Baby 2 is asleep").
        val unnamedOnly = state.babies.size < 2 && sleeping.baby.name.isBlank()
        val label = Formatters.from(strings, state.zone).labels.baby(sleeping.baby, state.babies)
        val title = if (unnamedOnly) status else strings.getString(R.string.notification_asleep_title, label)
        val wokeUp = action(
            icon = XDrawables.wake,
            title = strings.getString(CoreR.string.action_woke_up),
            intent = TimerActionReceiver.wokeUpIntent(context, sleep.entryId, sleeping.baby.id),
        )
        return base(strings, title, sleep, clock, state.zone, smallIcon = XDrawables.sleepRunning, status = status)
            .addAction(wokeUp)
            .setDeleteIntent(TimerActionReceiver.dismissedIntent(context, sleep.entryId))
            .withMetrics(
                strings = strings,
                zone = state.zone,
                since = sleep.startMillis,
                lastFed = sleeping.lastFeedStartMillis,
            )
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
            .setCategory(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Notification.CATEGORY_STOPWATCH else Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(DeepLinks.pendingIntent(context, HomeLaunch.Home))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // The status chip: a word, never a ticking value (DESIGN_SPEC §3.3.7).
            builder.setShortCriticalText(status)
            // Promotion arrived in Android 16 QPR2 (36.1); on 36.0 the notification stays a normal ongoing one.
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 && canPostPromoted()) {
                builder.setRequestPromotedOngoing(true)
            }
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

        /**
         * The tag of baby [babyId]'s sleep notification: none for the first baby (as before several babies, so an
         * update keeps managing a notification that is already showing), its id for the others.
         */
        fun tagFor(babyId: String): String? = if (babyId == Baby.FIRST_ID) null else "sleep:$babyId"

        /** The running-breastfeed notification of older versions; cancelled if it is still showing. */
        const val LEGACY_FEED_ID = 1002
    }
}
