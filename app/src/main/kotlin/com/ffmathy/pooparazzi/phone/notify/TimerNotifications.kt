package com.ffmathy.pooparazzi.phone.notify

import android.app.AlarmManager
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
import com.ffmathy.pooparazzi.core.domain.DayCounts
import com.ffmathy.pooparazzi.core.domain.localTime
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.format.DurationFormatter
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import com.ffmathy.pooparazzi.core.format.RelativeFormatter
import com.ffmathy.pooparazzi.core.format.RelativePhrases
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.model.isBreastfeed
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.launch.DeepLinks
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
import com.ffmathy.pooparazzi.phone.system.inAppLanguage
import java.time.Instant
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** A running sleep as its notification shows it: which entry, and since when. */
data class RunningTimer(val entryId: String, val startMillis: Long)

/** A baby's day as the notifications show it under the first line: the last feed and diaper, and today's counts. */
data class BabyDay(val lastFeed: Entry? = null, val lastDiaper: Entry? = null, val today: DayCounts? = null)

/** A baby who is asleep: [sleep] is their running sleep. */
data class SleepingBaby(val baby: Baby, val sleep: RunningTimer)

/**
 * What the ongoing notifications show (DESIGN_SPEC §3.3.7, §10.5; BABIES_AND_BACKUP §2.3): one per baby who is asleep,
 * whichever baby is the current one, how long each sleep has run at [now] (whole minutes), each baby's [days] (the last
 * feed and diaper, today's counts), and the babies (for their labels) and language the text uses. Equal states post
 * nothing new; [now] moves once a minute, so the "1h 25m" in the text follows the clock. Feeds are instants
 * (breastfeeds too), so nothing else ever runs.
 */
data class TimerState(
    val sleeping: List<SleepingBaby>,
    val babies: List<Baby>,
    val language: AppLanguage,
    val zone: ZoneId,
    val now: Long = 0L,
    val days: Map<String, BabyDay> = emptyMap(),
) {
    /** Baby [babyId]'s day; empty for a baby with nothing logged. */
    fun dayOf(babyId: String): BabyDay = days[babyId] ?: BabyDay()

    companion object {
        /**
         * The state for [entries] of every baby (`observeAllEntries`), in the order of [settings]' babies; a baby whose
         * entries arrived before the phone adopted it is listed after them, unnamed.
         */
        fun of(entries: List<Entry>, settings: Settings, now: Long, zone: ZoneId): TimerState {
            val byBaby = entries.groupBy { it.babyId }
            val babies = settings.babies + byBaby.keys.filter { id -> settings.babies.none { it.id == id } }.map { Baby(it) }
            // Summarized at the real time (a sleep started seconds ago is not in the future), shown in whole minutes.
            val summaries = babies.associate { it.id to summarize(byBaby[it.id].orEmpty(), now, zone) }
            val sleeping = babies.mapNotNull { baby ->
                val sleep = summaries.getValue(baby.id).runningSleep ?: return@mapNotNull null
                SleepingBaby(baby, RunningTimer(sleep.id, sleep.startMillis))
            }
            return TimerState(
                sleeping = sleeping,
                babies = babies,
                language = settings.language,
                zone = zone,
                now = now - now.mod(MINUTE_MILLIS),
                days = summaries
                    .filterKeys { id -> byBaby.containsKey(id) }
                    .mapValues { (_, summary) -> BabyDay(summary.lastFeed, summary.lastDiaper, summary.today) },
            )
        }
    }
}

/**
 * The ongoing notification of a running sleep (DESIGN_SPEC §3.3.7, §10.5): a silent IMPORTANCE_LOW channel, "Noa is
 * asleep" / "1h 07m · since 02:05" with a "Woke up" action that works from the lock screen. Expanded, it adds the last
 * feed (with its breast order), the last diaper and today's counts. It asks to be promoted to a Live Update where the
 * system allows it, and is a normal ongoing notification otherwise.
 *
 * Every baby who is asleep has their own notification ("Noa is asleep", "Ida is asleep", BABIES_AND_BACKUP §2.3), told
 * apart by a tag per baby ([tagFor]) on [SLEEP_ID]; each "Woke up" ends that baby's sleep, and that baby's woke-up
 * notification (tagged the same on [WOKE_UP_ID]) follows.
 *
 * "Woke up" tapped on the notification leaves a "Noa woke up at 03:12" notification with the Earlier offsets
 * (−5m / −10m / −15m) that move the wake-up time, as the undo card in the app does.
 *
 * Durations are whole minutes, re-rendered once a minute while the sleep runs (a non-waking alarm, so a phone that
 * wakes up shows the current value); never a ticking seconds chronometer. A notification the parent swipes away is not
 * posted again for that entry. Posting needs the notification permission (Android 13+); without it nothing is posted
 * and the timers run as usual.
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
        (postedTags(SLEEP_ID) + null).filter { it !in wanted }.forEach { manager.cancel(it, SLEEP_ID) }
        val shown = state.sleeping.map { sleeping ->
            val tag = tagFor(sleeping.baby.id)
            // A new sleep (or the woke-up undone in the app) makes that baby's woke-up offsets meaningless.
            manager.cancel(tag, WOKE_UP_ID)
            sleeping.sleep.entryId !in dismissed && post(tag, SLEEP_ID, state) { buildSleep(state, sleeping) }
        }
        scheduleMinuteRefresh(shown.any { it })
        // A breastfeed notification posted by an older version (when breastfeeds were timed) is never wanted now.
        manager.cancel(LEGACY_FEED_ID)
    }

    /** The tags of the notifications with [id] showing now. */
    private fun postedTags(id: Int): Set<String?> =
        runCatching { manager.activeNotifications.filter { it.id == id }.mapTo(mutableSetOf()) { it.tag } }.getOrDefault(emptySet())

    /**
     * The woke-up notification of [sleep], ended from the notification: its wake-up time moved [offsetMinutes] from
     * [baseMillis] (the time "Woke up" was tapped), with the offsets that are still possible.
     */
    fun renderWokeUp(state: TimerState, sleep: Entry, baseMillis: Long, offsetMinutes: Int) {
        if (sleep.endMillis == null || sleep.deleted) {
            manager.cancel(tagFor(sleep.babyId), WOKE_UP_ID)
            return
        }
        post(tagFor(sleep.babyId), WOKE_UP_ID, state) { buildWokeUp(state, sleep, baseMillis, offsetMinutes) }
    }

    private fun post(tag: String?, id: Int, state: TimerState, build: () -> Notification): Boolean {
        if (!canPost()) return false
        return try {
            ensureChannel(context.inAppLanguage(state.language))
            manager.notify(tag, id, build())
            true
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the post.
            Log.w(TAG, "Could not post the timer notification", e)
            false
        }
    }

    /**
     * Cancels baby [babyId]'s sleep notification at once (its "Woke up" action), before the entries flow catches up.
     * Other babies' notifications stay.
     */
    fun cancelSleep(babyId: String) = manager.cancel(tagFor(babyId), SLEEP_ID)

    /** Cancels baby [babyId]'s woke-up notification; other babies' stay. */
    fun cancelWokeUp(babyId: String) = manager.cancel(tagFor(babyId), WOKE_UP_ID)

    /**
     * The name in a notification's title: none for one unnamed baby (it reads "Asleep", as before several babies),
     * otherwise the baby's label ("Noa", "Baby 2").
     */
    private fun titleName(strings: Context, state: TimerState, baby: Baby): String? =
        if (state.babies.size < 2 && baby.name.isBlank()) null else Formatters.from(strings, state.zone).labels.baby(baby, state.babies)

    /** The baby with [babyId], unnamed when the state does not know it. */
    private fun babyOf(state: TimerState, babyId: String): Baby = state.babies.firstOrNull { it.id == babyId } ?: Baby(babyId)

    private fun canPost(): Boolean =
        context.hasNotificationPermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Re-renders at the next minute while the sleep notification shows, so its "1h 07m" follows the clock. The alarm
     * does not wake the phone: one that sleeps gets the update as soon as it wakes up.
     */
    private fun scheduleMinuteRefresh(on: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = TimerActionReceiver.refreshIntent(context)
        if (on) {
            val now = System.currentTimeMillis()
            alarms.set(AlarmManager.RTC, now - now.mod(MINUTE_MILLIS) + MINUTE_MILLIS, intent)
        } else {
            alarms.cancel(intent)
        }
    }

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
        val day = state.dayOf(sleeping.baby.id)
        val strings = context.inAppLanguage(state.language)
        val text = NotificationText(strings, state.zone)
        val status = strings.getString(CoreR.string.status_asleep)
        val title = titleName(strings, state, sleeping.baby)?.let { strings.getString(R.string.notification_asleep_title, it) } ?: status
        val asleepFor = text.asleepFor(sleep.startMillis, state.now)
        val wokeUp = action(
            icon = XDrawables.wake,
            title = strings.getString(CoreR.string.action_woke_up),
            intent = TimerActionReceiver.wokeUpIntent(context, sleep.entryId, sleeping.baby.id),
        )
        val builder = base(title, asleepFor, smallIcon = XDrawables.sleepRunning)
            .setOngoing(true)
            .setCategory(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Notification.CATEGORY_STOPWATCH else Notification.CATEGORY_STATUS)
            .addAction(wokeUp)
            .setDeleteIntent(TimerActionReceiver.dismissedIntent(context, sleep.entryId))
            .setStyle(Notification.BigTextStyle().bigText(text.lines(asleepFor, day, state.now)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // The status chip: a word, never a ticking value (DESIGN_SPEC §3.3.7).
            builder.setShortCriticalText(status)
            // Promotion arrived in Android 16 QPR2 (36.1); on 36.0 the notification stays a normal ongoing one.
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 && canPostPromoted()) {
                builder.setRequestPromotedOngoing(true)
            }
        }
        return builder.withMetrics(strings, text, state.zone, day, sleep.startMillis).build()
    }

    @VisibleForTesting
    internal fun buildWokeUp(state: TimerState, sleep: Entry, baseMillis: Long, offsetMinutes: Int): Notification {
        val strings = context.inAppLanguage(state.language)
        val text = NotificationText(strings, state.zone)
        val end = requireNotNull(sleep.endMillis)
        val title = titleName(strings, state, babyOf(state, sleep.babyId))
            ?.let { strings.getString(R.string.notification_woke_up_title, it, text.time(end)) }
            ?: strings.getString(CoreR.string.receipt_at_time, strings.getString(CoreR.string.action_woke_up), text.time(end))
        val slept = text.slept(sleep.startMillis, end)
        val builder = base(title, slept, smallIcon = XDrawables.wake)
            .setCategory(Notification.CATEGORY_STATUS)
            .setTimeoutAfter(WOKE_UP_TIMEOUT_MILLIS)
            .setStyle(Notification.BigTextStyle().bigText(text.lines(slept, state.dayOf(sleep.babyId), state.now)))
        // The Earlier offsets of the undo card (DESIGN_SPEC §3.3.2): relative to the tap, never cumulative; the
        // selected one reads "✓ −10m" and a second tap returns to the tapped time. Impossible ones are left out.
        val now = state.now + MINUTE_MILLIS - 1
        for (minutes in EARLIER_OFFSETS) {
            val selected = minutes == offsetMinutes
            val target = baseMillis + minutes * MINUTE_MILLIS
            if (!selected && (target > now || target < sleep.startMillis)) continue
            val label = text.offset(minutes).let { if (selected) strings.getString(R.string.notification_offset_selected, it) else it }
            builder.addAction(
                action(
                    icon = if (selected) XDrawables.check else XDrawables.clock,
                    title = label,
                    intent = TimerActionReceiver.earlierIntent(context, sleep.id, baseMillis, minutes, offsetMinutes),
                ),
            )
        }
        return builder.build()
    }

    private fun base(title: String, text: String, smallIcon: Int): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(DeepLinks.pendingIntent(context, HomeLaunch.Home))

    private fun action(icon: Int, title: String, intent: PendingIntent): Notification.Action {
        val builder = Notification.Action.Builder(Icon.createWithResource(context, icon), title, intent)
        // Usable from the lock screen without unlocking (DESIGN_SPEC §10.5).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAuthenticationRequired(false)
        return builder.build()
    }

    /**
     * Android 17 metrics: "Asleep for" 1h 07m and "Since" 02:05, then the time since the last feed, labelled with its
     * breast order ("Last fed · L → R"). The two durations are the system's own minute-precision TimeDifference, so
     * they stay current on the lock screen without a re-render.
     */
    private fun Notification.Builder.withMetrics(strings: Context, text: NotificationText, zone: ZoneId, day: BabyDay, since: Long): Notification.Builder {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return this
        val adaptive = Notification.Metric.TimeDifference.FORMAT_ADAPTIVE
        val style = Notification.MetricStyle()
            .addMetric(
                Notification.Metric(
                    Notification.Metric.TimeDifference.forStopwatch(Instant.ofEpochMilli(since), adaptive),
                    strings.getString(R.string.notification_metric_asleep_for),
                ),
            )
            .addMetric(Notification.Metric(Notification.Metric.FixedTime(localTime(since, zone)), strings.getString(R.string.notification_metric_since)))
        day.lastFeed?.let { feed ->
            style.addMetric(
                Notification.Metric(
                    Notification.Metric.TimeDifference.forStopwatch(Instant.ofEpochMilli(feed.startMillis), adaptive),
                    text.lastFedLabel(feed),
                ),
            )
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
         * The tag of baby [babyId]'s sleep and woke-up notifications: none for the first baby (as before several
         * babies, so an update keeps managing a notification that is already showing), its id for the others.
         */
        fun tagFor(babyId: String): String? = if (babyId == Baby.FIRST_ID) null else "sleep:$babyId"

        /** The running-breastfeed notification of older versions; cancelled if it is still showing. */
        const val LEGACY_FEED_ID = 1002
        /** "Noa woke up at 03:12", with the Earlier offsets. */
        const val WOKE_UP_ID = 1003
        /** The Earlier offsets, as on the undo card. */
        val EARLIER_OFFSETS = listOf(-5, -10, -15)
        /** The woke-up notification goes away by itself; the wake-up time stays editable in History. */
        const val WOKE_UP_TIMEOUT_MILLIS = 30 * MINUTE_MILLIS
    }
}

/**
 * The notification texts in the app language, in the time grammar of DESIGN_SPEC §7 (relative and absolute together,
 * "48m ago · 13:44").
 */
private class NotificationText(private val strings: Context, private val zone: ZoneId) {
    private val clock = ClockFormatter.from(strings)
    private val duration = DurationFormatter.from(strings)
    private val relative = RelativeFormatter(duration, clock, RelativePhrases.from(strings))
    private val labels = LabelFormatter(strings)

    fun time(millis: Long): String = clock.format(millis, zone)

    fun offset(minutes: Int): String = duration.offset(minutes)

    /** "1h 07m · since 02:05". */
    fun asleepFor(start: Long, now: Long): String = dot(duration.between(start, now), relative.since(start, zone))

    /** "Slept 1h 07m · 02:05 – 03:12". */
    fun slept(start: Long, end: Long): String =
        dot(strings.getString(CoreR.string.receipt_slept, duration.between(start, end)), clock.range(start, end, zone))

    /** "Last fed · L → R", for the Android 17 metric. */
    fun lastFedLabel(feed: Entry): String =
        listOfNotNull(strings.getString(CoreR.string.status_last_fed), feedDetail(feed)).joinToString(DOT)

    /** [first], then the last feed, the last diaper and today's counts, one per line. */
    fun lines(first: String, day: BabyDay, now: Long): String = listOfNotNull(
        first,
        day.lastFeed?.let { fed(it, now) },
        day.lastDiaper?.let { diaper(it, now) },
        day.today?.let(::today),
    ).joinToString("\n")

    /** "Fed 1h 32m ago · 01:40 · L → R"; "Formula" in place of the order for other feeds. */
    private fun fed(feed: Entry, now: Long): String =
        listOfNotNull(ago(strings.getString(CoreR.string.status_fed), feed.startMillis, now), time(feed.startMillis), feedDetail(feed))
            .joinToString(DOT)

    /** "Poop 48m ago · 13:44". */
    private fun diaper(diaper: Entry, now: Long): String =
        dot(ago(labels.diaperKindStatus(diaper.diaperKind ?: DiaperKind.PLAIN), diaper.startMillis, now), time(diaper.startMillis))

    /** "Today · 5 feeds · 4 pee · 2 poop · 7h 57m sleep". */
    private fun today(counts: DayCounts): String = listOf(
        strings.getString(CoreR.string.nav_today),
        "${counts.feeds} ${labels.feedsCaption(counts.feeds)}",
        "${counts.pee} ${labels.peeCaption()}",
        "${counts.poop} ${labels.poopCaption()}",
        "${duration.format(counts.sleepMinutes * MINUTE_MILLIS)} ${labels.sleepCaption()}",
    ).joinToString(DOT)

    private fun ago(label: String, at: Long, now: Long): String = "$label ${relative.ago(clockDurationMillis(at, now))}"

    private fun feedDetail(feed: Entry): String? =
        feed.breastSide?.takeIf { feed.isBreastfeed }?.let(labels::sideShort) ?: feed.feedType?.let { labels.feedType(it) }

    private fun dot(first: String, second: String): String = first + DOT + second

    private companion object {
        const val DOT = " · "
    }
}

private const val MINUTE_MILLIS = 60_000L
