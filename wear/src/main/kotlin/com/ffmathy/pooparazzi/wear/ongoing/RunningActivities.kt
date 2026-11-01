package com.ffmathy.pooparazzi.wear.ongoing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.wear.MainActivity
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.ui.inSentence
import java.time.ZoneId

/** One running interval shown as an Ongoing Activity: only a sleep runs (feeds are instants). */
enum class RunningKind(val notificationId: Int, @DrawableRes val icon: Int, @StringRes val status: Int) {
    SLEEP(NOTIFICATION_SLEEP, XDrawables.sleepRunning, R.string.ongoing_asleep_for),
}

private const val NOTIFICATION_SLEEP = 1001

/** The running-breastfeed activity of older versions (when breastfeeds were timed); removed if it is still showing. */
internal const val LEGACY_NOTIFICATION_FEED = 1002

/**
 * The watch Ongoing Activity (DESIGN_SPEC §3.8, §10.6) for a running sleep: a static white icon, the status "Asleep
 * 1:07:23" (how long the sleep has run: a stopwatch the system keeps current on the watch face, with no help from the
 * app; the notification behind it shows the same as a chronometer), and a tap that opens Main. It is removed as soon as the
 * sleep ends, wherever it ended (the app, the tile, or the phone through sync), because [sync] runs on every change of
 * the entries. It shows the watch's current baby's sleep; with two or more babies it names that baby ("Noa · Asleep
 * 1:07:23") and follows a switch to another baby (BABIES_AND_BACKUP §2.5).
 *
 * Posting needs `POST_NOTIFICATIONS` on API 33+; without it nothing is shown and logging works as usual.
 */
class RunningActivities(
    private val context: Context,
    /** The wall clock, to place the sleep's start on the stopwatch's elapsed-realtime clock. */
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * Shows, updates or removes the activity to match [summary], the current baby's. [baby] is that baby's label when
     * there are two or more (null with one baby: the activity reads as it always did).
     */
    fun sync(summary: Summary, baby: String? = null) {
        show(RunningKind.SLEEP, summary.runningSleep, summary.zone, baby)
        manager.cancel(LEGACY_NOTIFICATION_FEED)
    }

    private fun show(kind: RunningKind, running: Entry?, zone: ZoneId, baby: String?) {
        if (running == null) {
            manager.cancel(kind.notificationId)
            shown.remove(kind)
            return
        }
        val clock = ClockFormatter.from(context)
        val start = clock.format(running.startMillis, zone)
        val key = Shown(running.id, start, running.babyId, baby)
        if (shown[kind] == key) return
        if (!canPost()) return
        ensureChannel()

        // "Noa · Asleep #elapsed#": the baby leads, as in the app's time text. A "#" would read as a template part.
        val statusTemplate = context.getString(kind.status)
        val template = if (baby != null) baby.replace("#", "") + DOT_SEPARATOR + statusTemplate else statusTemplate
        // The notification's own title: the chronometer next to it shows the time.
        val title = template.replace(ELAPSED_TOKEN, "").trim()
        // TalkBack cannot follow a ticking value: it reads when the sleep started ("Asleep since 02:05").
        val since = context.getString(R.string.ongoing_asleep_since).replace(START_TOKEN, start)
        val locale = context.resources.configuration.locales[0]
        val spoken = if (baby != null) {
            context.getString(R.string.spoken_pair, baby, since.inSentence(locale))
        } else {
            since
        }
        val touch = PendingIntent.getActivity(
            context,
            kind.notificationId,
            MainActivity.intent(context, MainActivity.OPEN_MAIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(kind.icon)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setWhen(running.startMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(touch)
        val status = Status.Builder()
            .addTemplate(template)
            // The stopwatch counts in the elapsed-realtime clock, which does not jump with the wall clock.
            .addPart(ELAPSED_PART, Status.StopwatchPart(SystemClock.elapsedRealtime() - (wallClock() - running.startMillis)))
            .build()
        OngoingActivity.Builder(context, kind.notificationId, notification)
            .setOngoingActivityId(kind.notificationId)
            .setStaticIcon(kind.icon)
            .setTouchIntent(touch)
            .setStatus(status)
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
    private data class Shown(val entryId: String, val start: String, val babyId: String, val baby: String?)

    companion object {
        private const val TAG = "RunningActivities"
        private const val CHANNEL_ID = "running"
        private const val START_TOKEN = "#start#"
        private const val ELAPSED_TOKEN = "#elapsed#"
        private const val ELAPSED_PART = "elapsed"

        /** Shared by every instance in the process: the notifications are process-wide too. */
        private val shown = java.util.concurrent.ConcurrentHashMap<RunningKind, Shown>()

        /** Forgets what was posted (language change: the text must be rebuilt). */
        fun invalidate() = shown.clear()
    }
}
