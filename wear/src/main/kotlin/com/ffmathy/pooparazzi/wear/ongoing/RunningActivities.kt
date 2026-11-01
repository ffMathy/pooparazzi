package com.ffmathy.pooparazzi.wear.ongoing

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.ffmathy.pooparazzi.core.domain.Expected
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.forecast
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.DurationFormatter
import com.ffmathy.pooparazzi.core.format.EstimateText
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.wear.MainActivity
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.ui.inSentence
import com.ffmathy.pooparazzi.core.R as CoreR

/** One running interval shown as an Ongoing Activity: only a sleep runs (feeds are instants). */
enum class RunningKind(val notificationId: Int, @DrawableRes val icon: Int, @StringRes val status: Int) {
    SLEEP(NOTIFICATION_SLEEP, XDrawables.sleepRunning, CoreR.string.status_asleep),
}

private const val NOTIFICATION_SLEEP = 1001

/** The running-breastfeed activity of older versions (when breastfeeds were timed); removed if it is still showing. */
internal const val LEGACY_NOTIFICATION_FEED = 1002

/**
 * The watch Ongoing Activity (DESIGN_SPEC §3.8, §10.6) for a running sleep: a static white icon, the status, and a tap
 * that opens Main. The status says how the baby is and what comes next, relative to now: "Asleep · feed in 2h 05m"
 * (the earliest of the week's estimates: the next feed, the next poop or the expected wake-up, worded by core's
 * [EstimateText] rules), or how long the sleep has run when the week predicts nothing ("Asleep · 1h 07m"). It is
 * redrawn every minute by a non-waking alarm ([OngoingTickReceiver]), so it is current whenever the watch is awake.
 *
 * It is removed as soon as the sleep ends, wherever it ended (the app, the tile, or the phone through sync), because
 * [sync] runs on every change of the entries. It shows the watch's current baby's sleep; with two or more babies it
 * names that baby ("Noa · Asleep · feed in 2h 05m") and follows a switch to another baby (BABIES_AND_BACKUP §2.5).
 *
 * Posting needs `POST_NOTIFICATIONS` on API 33+; without it nothing is shown and logging works as usual.
 */
class RunningActivities(
    private val context: Context,
    /** The wall clock, for the next minute's redraw. */
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * Shows, updates or removes the activity to match [summary], the current baby's. [baby] is that baby's label when
     * there are two or more (null with one baby: the activity reads as it always did).
     */
    fun sync(summary: Summary, baby: String? = null) {
        show(RunningKind.SLEEP, summary, baby)
        manager.cancel(LEGACY_NOTIFICATION_FEED)
    }

    /**
     * "feed in 2h 05m": the earliest of the week's estimates for [summary] (an overdue one first: "feed due 5m ago"),
     * with its spoken form ("next feed expected around 15:30"); null when the week predicts nothing.
     */
    private fun next(summary: Summary): Pair<String, String>? {
        val week = forecast(summary)
        val estimates = EstimateText(context)
        val clock = ClockFormatter.from(context)
        fun time(e: Expected) = clock.format(e.roundedAtMillis, summary.zone)
        val candidates = listOfNotNull(
            week.feed?.let { e -> e to { estimates.format(R.string.ongoing_feed_in, R.string.ongoing_feed_soon, R.string.ongoing_feed_ago, e, summary.now) to context.getString(CoreR.string.spoken_forecast_feed_at, time(e)) } },
            week.poop?.let { e -> e to { estimates.format(R.string.ongoing_poop_in, R.string.ongoing_poop_soon, R.string.ongoing_poop_ago, e, summary.now) to context.getString(CoreR.string.spoken_forecast_poop_at, time(e)) } },
            week.sleep?.takeIf { summary.runningSleep != null }?.let { e ->
                e to { estimates.format(R.string.ongoing_wake_in, R.string.ongoing_wake_soon, R.string.ongoing_wake_ago, e, summary.now) to context.getString(CoreR.string.spoken_forecast_wake_at, time(e)) }
            },
        )
        return candidates.minByOrNull { it.first.atMillis }?.second?.invoke()
    }

    private fun show(kind: RunningKind, summary: Summary, baby: String?) {
        val running = summary.runningSleep
        if (running == null) {
            manager.cancel(kind.notificationId)
            shown.remove(kind)
            scheduleTick(false)
            return
        }
        scheduleTick(true)
        val clock = ClockFormatter.from(context)
        val start = clock.format(running.startMillis, summary.zone)
        val status = context.getString(kind.status)
        // What comes next ("feed in 2h 05m"), or how long the sleep has run ("1h 07m").
        val next = next(summary)
        val detail = next?.first ?: DurationFormatter.from(context).format(clockDurationMillis(running.startMillis, summary.now))
        // "Noa · Asleep · feed in 2h 05m": the baby leads, as in the app's time text. A "#" would read as a template part.
        val text = listOfNotNull(baby?.replace("#", ""), status, detail).joinToString(DOT_SEPARATOR)
        val key = Shown(running.id, text, running.babyId, baby)
        if (shown[kind] == key) return
        if (!canPost()) return
        ensureChannel()

        // TalkBack: "Asleep since 02:05, next feed expected around 15:30" (a clock time: it does not go stale).
        val since = context.getString(R.string.ongoing_asleep_since).replace(START_TOKEN, start)
        val locale = context.resources.configuration.locales[0]
        val said = next?.let { context.getString(R.string.spoken_pair, since, it.second) } ?: since
        val spoken = if (baby != null) context.getString(R.string.spoken_pair, baby, said.inSentence(locale)) else said
        val touch = PendingIntent.getActivity(
            context,
            kind.notificationId,
            MainActivity.intent(context, MainActivity.OPEN_MAIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(kind.icon)
            .setContentTitle(text)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(touch)
        val ongoingStatus = Status.Builder().addTemplate(text).build()
        OngoingActivity.Builder(context, kind.notificationId, notification)
            .setOngoingActivityId(kind.notificationId)
            .setStaticIcon(kind.icon)
            .setTouchIntent(touch)
            .setStatus(ongoingStatus)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentDescription(spoken)
            .build()
            .apply(context)
        try {
            manager.notify(kind.notificationId, notification.build())
            shown[kind] = key
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
        }
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.ongoing_channel_name), NotificationManager.IMPORTANCE_LOW)
            .apply {
                description = context.getString(R.string.ongoing_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * What is on screen for a kind, so an unchanged state is not re-posted. The baby is part of it: after a switch the
     * activity is the other baby's (or gone), and a new label re-posts it.
     */
    private data class Shown(val entryId: String, val text: String, val babyId: String, val baby: String?)

    /** Redraws at the next minute while a sleep runs ([OngoingTickReceiver]); the alarm does not wake the watch. */
    private fun scheduleTick(on: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = OngoingTickReceiver.intent(context)
        if (on) {
            val now = wallClock()
            alarms.set(AlarmManager.RTC, now - now.mod(MINUTE_MILLIS) + MINUTE_MILLIS, intent)
        } else {
            alarms.cancel(intent)
        }
    }

    companion object {
        private const val TAG = "RunningActivities"
        private const val CHANNEL_ID = "running"
        private const val START_TOKEN = "#start#"
        private const val MINUTE_MILLIS = 60_000L

        /** Shared by every instance in the process: the notifications are process-wide too. */
        private val shown = java.util.concurrent.ConcurrentHashMap<RunningKind, Shown>()

        /** Forgets what was posted (language change: the text must be rebuilt). */
        fun invalidate() = shown.clear()
    }
}
