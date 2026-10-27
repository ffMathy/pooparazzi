package com.ffmathy.pooparazzi.wear.ongoing

import android.Manifest
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
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.wear.MainActivity
import com.ffmathy.pooparazzi.wear.R
import java.time.ZoneId

/** One running interval shown as an Ongoing Activity: the sleep or the breastfeed. */
enum class RunningKind(val notificationId: Int, @DrawableRes val icon: Int, @StringRes val status: Int) {
    SLEEP(NOTIFICATION_SLEEP, XDrawables.sleepRunning, R.string.ongoing_asleep_since),
    BREASTFEED(NOTIFICATION_FEED, XDrawables.breast, R.string.ongoing_breastfeeding_since),
}

private const val NOTIFICATION_SLEEP = 1001
private const val NOTIFICATION_FEED = 1002

/**
 * The watch Ongoing Activities (DESIGN_SPEC §3.8, §10.6) for a running sleep and a running breastfeed: a static
 * white icon, the status "Asleep since 02:05" / "Breastfeeding since 14:32" as a text part (never a ticking
 * stopwatch), and a tap that opens Main. Each is removed as soon as its entry ends, wherever it ended (the app, the
 * tile, or the phone through sync), because [sync] runs on every change of the entries.
 *
 * Posting needs `POST_NOTIFICATIONS` on API 33+; without it nothing is shown and logging works as usual.
 */
class RunningActivities(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Shows, updates or removes both activities to match [summary]. */
    fun sync(summary: Summary) {
        show(RunningKind.SLEEP, summary.runningSleep, summary.zone)
        show(RunningKind.BREASTFEED, summary.runningFeed, summary.zone)
    }

    private fun show(kind: RunningKind, running: Entry?, zone: ZoneId) {
        if (running == null) {
            manager.cancel(kind.notificationId)
            shown.remove(kind)
            return
        }
        val clock = ClockFormatter.from(context)
        val start = clock.format(running.startMillis, zone)
        val key = Shown(running.id, start)
        if (shown[kind] == key) return
        if (!canPost()) return
        ensureChannel()

        val template = context.getString(kind.status)
        val statusText = template.replace(START_TOKEN, start)
        val touch = PendingIntent.getActivity(
            context,
            kind.notificationId,
            MainActivity.intent(context, MainActivity.OPEN_MAIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(kind.icon)
            .setContentTitle(statusText)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(touch)
        val status = Status.Builder()
            .addTemplate(template)
            .addPart(START_PART, Status.TextPart(start))
            .build()
        OngoingActivity.Builder(context, kind.notificationId, notification)
            .setOngoingActivityId(kind.notificationId)
            .setStaticIcon(kind.icon)
            .setTouchIntent(touch)
            .setStatus(status)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentDescription(statusText)
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

    /** What is on screen for a kind, so an unchanged state is not re-posted. */
    private data class Shown(val entryId: String, val start: String)

    companion object {
        private const val TAG = "RunningActivities"
        private const val CHANNEL_ID = "running"
        private const val START_TOKEN = "#start#"
        private const val START_PART = "start"

        /** Shared by every instance in the process: the notifications are process-wide too. */
        private val shown = java.util.concurrent.ConcurrentHashMap<RunningKind, Shown>()

        /** Forgets what was posted (language change: the text must be rebuilt). */
        fun invalidate() = shown.clear()
    }
}
