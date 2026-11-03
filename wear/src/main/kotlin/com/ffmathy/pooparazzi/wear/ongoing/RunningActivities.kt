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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.ffmathy.pooparazzi.core.domain.Expected
import com.ffmathy.pooparazzi.core.domain.ForecastKind
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
import java.time.LocalDate
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * What the Ongoing Activity is about: a running sleep (feeds are instants), or, while the baby is awake, what comes next.
 * There is one activity at a time, so both use the same notification.
 */
enum class RunningKind(val notificationId: Int, @DrawableRes val icon: Int) {
    SLEEP(NOTIFICATION_SLEEP, XDrawables.sleepRunning),
    NEXT(NOTIFICATION_SLEEP, XDrawables.upcoming),
}

private const val NOTIFICATION_SLEEP = 1001

/** The running-breastfeed activity of older versions (when breastfeeds were timed); removed if it is still showing. */
internal const val LEGACY_NOTIFICATION_FEED = 1002

/**
 * The watch Ongoing Activity (DESIGN_SPEC §3.8, §10.6): a static white icon, the status, and a tap that opens Main. The
 * status is short enough for a watch face: what comes next, relative to now (the earliest of the week's estimates of
 * the kinds chosen in the phone's Settings › Watch, `Settings.watchForecasts`, worded by core's [EstimateText] rules).
 *
 * - While a sleep runs: the sleep icon and "Wakes 55m", "Feed 2h 05m" or "Poop 40m", or how long the sleep has run
 *   when nothing chosen is predicted ("Asleep 1h 07m").
 * - While the baby is awake: the "upcoming" icon and "Feed 55m", "Sleep 20m" or "Poop 40m", and no activity at all
 *   when nothing chosen is predicted.
 *
 * It is redrawn every minute by a non-waking alarm ([OngoingTickReceiver]), so it is current whenever the watch is
 * awake. Because [sync] runs on every change of the entries and settings, it follows a sleep started or ended anywhere
 * (the app, the tile, or the phone through sync) and a change of the chosen kinds at once. It shows the watch's
 * current baby; with two or more babies it names that baby ("Noa · Wakes 55m") and follows a switch to another baby
 * (BABIES_AND_BACKUP §2.5).
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
     * there are two or more (null with one baby: the activity reads as it always did). [kinds] are the estimates the
     * parent chose to see on the watch, and [birthDate] is the baby's, when given (it tunes the forecast).
     */
    fun sync(
        summary: Summary,
        baby: String? = null,
        kinds: Set<ForecastKind> = ForecastKind.entries.toSet(),
        birthDate: LocalDate? = null,
    ) {
        show(summary, baby, kinds, birthDate)
        manager.cancel(LEGACY_NOTIFICATION_FEED)
    }

    /**
     * "Feed 2h 05m": the earliest of the week's estimates of [kinds] for [summary] (a due one first: "Feed now"), with
     * its spoken form ("next feed expected around 15:30"); null when the week predicts none of them. [ForecastKind.SLEEP]
     * is the wake-up while a sleep runs ("Wakes 55m") and the next sleep while awake ("Sleep 20m").
     */
    private fun next(summary: Summary, kinds: Set<ForecastKind>, birthDate: LocalDate?): Pair<String, String>? {
        val week = forecast(summary, birthDate).only(kinds)
        val estimates = EstimateText(context)
        val clock = ClockFormatter.from(context)
        fun time(e: Expected) = clock.format(e.roundedAtMillis, summary.zone)
        val asleep = summary.runningSleep != null
        val candidates = listOfNotNull(
            week.feed?.let { e -> e to { estimates.format(R.string.ongoing_feed_in, R.string.ongoing_feed_soon, R.string.ongoing_feed_now, e, summary.now) to context.getString(CoreR.string.spoken_forecast_feed_at, time(e)) } },
            week.poop?.let { e -> e to { estimates.format(R.string.ongoing_poop_in, R.string.ongoing_poop_soon, R.string.ongoing_poop_now, e, summary.now) to context.getString(CoreR.string.spoken_forecast_poop_at, time(e)) } },
            week.sleep?.takeIf { asleep }?.let { e ->
                e to { estimates.format(R.string.ongoing_wake_in, R.string.ongoing_wake_soon, R.string.ongoing_wake_now, e, summary.now) to context.getString(CoreR.string.spoken_forecast_wake_at, time(e)) }
            },
            week.sleep?.takeIf { !asleep }?.let { e ->
                e to { estimates.format(R.string.ongoing_sleep_in, R.string.ongoing_sleep_soon, R.string.ongoing_sleep_now, e, summary.now) to context.getString(CoreR.string.spoken_forecast_sleep_at, time(e)) }
            },
        )
        return candidates.minByOrNull { it.first.atMillis }?.second?.invoke()
    }

    private fun show(summary: Summary, baby: String?, kinds: Set<ForecastKind>, birthDate: LocalDate?) {
        val running = summary.runningSleep
        // What comes next ("Wakes 55m"), or how long the sleep has run ("Asleep 1h 07m"): one short phrase, so it is
        // not cut off or wrapped on the watch face.
        val next = next(summary, kinds, birthDate)
        if (running == null && next == null) {
            manager.cancel(NOTIFICATION_SLEEP)
            shown.clear()
            scheduleTick(false)
            return
        }
        val kind = if (running != null) RunningKind.SLEEP else RunningKind.NEXT
        scheduleTick(true)
        val clock = ClockFormatter.from(context)
        val detail = next?.first
            ?: context.getString(R.string.ongoing_asleep_for, DurationFormatter.from(context).format(clockDurationMillis(running!!.startMillis, summary.now)))
        // "Noa · Wakes 55m": the baby leads, as in the app's time text. A "#" would read as a template part.
        val text = listOfNotNull(baby?.replace("#", ""), detail).joinToString(DOT_SEPARATOR)
        val key = Shown(running?.id, text, baby)
        if (shown[kind] == key) return
        if (!canPost()) return
        ensureChannel()

        // TalkBack: "Asleep since 02:05, next feed expected around 15:30" (a clock time: it does not go stale); awake,
        // only what comes next ("next feed expected around 15:30").
        val since = running?.let { context.getString(R.string.ongoing_asleep_since).replace(START_TOKEN, clock.format(it.startMillis, summary.zone)) }
        val locale = context.resources.configuration.locales[0]
        val said = when {
            since == null -> next!!.second.replaceFirstChar { it.titlecase(locale) }
            next != null -> context.getString(R.string.spoken_pair, since, next.second)
            else -> since
        }
        val spoken = if (baby != null) context.getString(R.string.spoken_pair, baby, said.inSentence(locale)) else said
        val touch = PendingIntent.getActivity(
            context,
            kind.notificationId,
            MainActivity.intent(context, MainActivity.OPEN_MAIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val category = if (kind == RunningKind.SLEEP) NotificationCompat.CATEGORY_STOPWATCH else NotificationCompat.CATEGORY_STATUS
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(kind.icon)
            .setContentTitle(text)
            .setCategory(category)
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
            .setCategory(category)
            .setContentDescription(spoken)
            .build()
            .apply(context)
        try {
            manager.notify(kind.notificationId, notification.build())
            // One activity at a time: the other kind's is replaced by this one.
            shown.clear()
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
     * activity is the other baby's (or gone), and a new label re-posts it. [entryId] is the running sleep's (null awake).
     */
    private data class Shown(val entryId: String?, val text: String, val baby: String?)

    /** Redraws at the next minute while the activity shows ([OngoingTickReceiver]); the alarm does not wake the watch. */
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
