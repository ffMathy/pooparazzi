package com.ffmathy.pooparazzi.wear.complication

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.DynamicComplicationText
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.surface.SurfaceText
import com.ffmathy.pooparazzi.wear.surface.TimeText
import com.ffmathy.pooparazzi.wear.ui.SpokenLabels
import java.time.Instant
import java.util.concurrent.TimeUnit
import com.ffmathy.pooparazzi.core.R as CoreR

/** The app's complications (DESIGN_SPEC §3.7, §10.10). */
enum class XComplication(val type: ComplicationType) {
    /** `feed`-type icon + "2h 10m" since the last feed started (any type). */
    SINCE_FEED(ComplicationType.SHORT_TEXT),

    /** Kind glyph + "48m" since the last diaper; title "Pee" for faces that draw no image. */
    LAST_DIAPER(ComplicationType.SHORT_TEXT),

    /** `awake` + time since waking, or `sleepRunning` + time asleep. */
    SLEEP(ComplicationType.SHORT_TEXT),

    /** "Awake 1h 05m" / "since 13:27", or "Asleep 1h 07m" / "Fed 1h 32m ago". */
    SLEEP_LONG(ComplicationType.LONG_TEXT),
}

/**
 * Builds the complication data for the current [Summary]. Text follows the app grammar through
 * [DynamicComplicationText] on Wear OS 4+ (the system ticks it); older watches get the system
 * [TimeDifferenceComplicationText] in `SHORT_DUAL_UNIT` ("1h 5m"), the one exemption §7 allows. Never `STOPWATCH`,
 * never `RANGED_VALUE`. Images are the white core drawables; the watch face colours them.
 *
 * Taps open the matching app screen and never log.
 *
 * @param static plain text only, as the values read at the summary's time: the face editor's preview data and the
 *   screenshot of the data (a dynamic or time-difference text would be evaluated against the real clock there).
 */
class ComplicationContent(
    private val context: Context,
    private val text: SurfaceText,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val static: Boolean = false,
) {
    private val res = context.resources

    fun build(complication: XComplication, summary: Summary, tapAction: PendingIntent?): ComplicationData =
        when (complication) {
            XComplication.SINCE_FEED -> sinceFeed(summary, tapAction)
            XComplication.LAST_DIAPER -> lastDiaper(summary, tapAction)
            XComplication.SLEEP -> sleep(summary, tapAction)
            XComplication.SLEEP_LONG -> sleepLong(summary, tapAction)
        }

    private fun sinceFeed(summary: Summary, tapAction: PendingIntent?): ComplicationData {
        val feed = summary.lastFeed
        val now = summary.now
        if (feed == null) {
            return empty(XDrawables.feed, R.string.complication_cd_no_feed, tapAction)
        }
        // "Last feed 2 hours 10 minutes ago" (DESIGN_SPEC §9.5).
        val spoken = text.template(SurfaceText.Template.of(res, R.string.spoken_last_feed_ago), text.spokenAgo(feed.startMillis, now))
        return shortText(
            value = text.duration(feed.startMillis, now),
            since = feed.startMillis,
            spoken = spoken,
            icon = XDrawables.forFeedType(feed.feedType),
            title = null,
            tapAction = tapAction,
        )
    }

    private fun lastDiaper(summary: Summary, tapAction: PendingIntent?): ComplicationData {
        val diaper = summary.lastDiaper
        val now = summary.now
        if (diaper == null) {
            return empty(XDrawables.diaper, R.string.complication_cd_no_diaper, tapAction)
        }
        val kind = diaper.diaperKind ?: DiaperKind.PLAIN
        val word = res.getString(Labels.diaperKindStatus(kind))
        // "Last diaper, pee and poop, 48 minutes ago": the spoken word, never the status word "Both".
        val spokenWord = res.getString(SpokenLabels.diaperKind(kind)).lowercase(text.formatters.clock.locale)
        val spokenTemplate = res.getString(R.string.tile_cd_last_diaper, spokenWord, SurfaceText.Template.PLACEHOLDER)
        val spoken = text.template(SurfaceText.Template(spokenTemplate), text.spokenAgo(diaper.startMillis, now))
        return shortText(
            value = text.duration(diaper.startMillis, now),
            since = diaper.startMillis,
            spoken = spoken,
            icon = XDrawables.forDiaperKind(kind),
            title = word,
            tapAction = tapAction,
        )
    }

    private fun sleep(summary: Summary, tapAction: PendingIntent?): ComplicationData {
        val now = summary.now
        val running = summary.runningSleep
        val awakeSince = summary.lastSleepEnd
        return when {
            running != null -> shortText(
                value = text.duration(running.startMillis, now),
                since = running.startMillis,
                spoken = spokenStatus(R.string.complication_cd_asleep, running.startMillis, now),
                icon = XDrawables.sleepRunning,
                title = res.getString(CoreR.string.status_asleep),
                tapAction = tapAction,
            )
            awakeSince != null -> shortText(
                value = text.duration(awakeSince, now),
                since = awakeSince,
                spoken = spokenStatus(R.string.complication_cd_awake, awakeSince, now),
                icon = XDrawables.awake,
                title = res.getString(CoreR.string.status_awake),
                tapAction = tapAction,
            )
            else -> empty(XDrawables.sleep, R.string.complication_cd_no_sleep, tapAction)
        }
    }

    private fun sleepLong(summary: Summary, tapAction: PendingIntent?): ComplicationData {
        val now = summary.now
        val running = summary.runningSleep
        val awakeSince = summary.lastSleepEnd
        val lastFeed = summary.lastFeed
        val (from, line, spoken, icon) = when {
            running != null -> Quad(
                running.startMillis,
                text.template(SurfaceText.Template.of(res, R.string.complication_asleep_for), text.duration(running.startMillis, now)),
                spokenStatus(R.string.complication_cd_asleep, running.startMillis, now),
                XDrawables.sleepRunning,
            )
            awakeSince != null -> Quad(
                awakeSince,
                text.template(SurfaceText.Template.of(res, R.string.complication_awake_for), text.duration(awakeSince, now)),
                spokenStatus(R.string.complication_cd_awake, awakeSince, now),
                XDrawables.awake,
            )
            else -> return empty(XDrawables.sleep, R.string.complication_cd_no_sleep, tapAction, ComplicationType.LONG_TEXT)
        }
        // Title: "since 13:27" while awake; while asleep the last feed, "Fed 1h 32m ago" (DESIGN_SPEC §3.7). The system
        // time difference fills only "1h 32m", so its template carries the "ago" phrase too ("Fed ^1 ago").
        val fedTemplate = res.getString(R.string.complication_fed_ago, res.getString(CoreR.string.rel_ago_format, TIME_TOKEN))
        val title: ComplicationText? = when {
            running == null -> plain(text.formatters.relative.since(from, text.zone))
            lastFeed != null -> complicationText(
                text.template(SurfaceText.Template.of(res, R.string.complication_fed_ago), text.ago(lastFeed.startMillis, now)),
                since = lastFeed.startMillis,
                fallbackTemplate = fedTemplate,
            )
            else -> null
        }
        val surrounding = res.getString(if (running != null) R.string.complication_asleep_for else R.string.complication_awake_for, TIME_TOKEN)
        val builder = LongTextComplicationData.Builder(
            complicationText(line, since = from, fallbackTemplate = surrounding),
            complicationText(spoken, since = from, fallbackTemplate = null),
        )
            .setTitle(title)
            .setMonochromaticImage(monochrome(icon))
            .setTapAction(tapAction)
        return builder.build()
    }

    // ---- Pieces ----------------------------------------------------------------------------------

    private fun shortText(
        value: TimeText,
        since: Long,
        spoken: TimeText,
        @DrawableRes icon: Int,
        title: String?,
        tapAction: PendingIntent?,
    ): ComplicationData {
        val builder = ShortTextComplicationData.Builder(
            complicationText(value, since, fallbackTemplate = TIME_TOKEN),
            complicationText(spoken, since, fallbackTemplate = null),
        )
            .setTitle(title?.let(::plain))
            .setMonochromaticImage(monochrome(icon))
            .setSmallImage(SmallImage.Builder(Icon.createWithResource(context, icon), SmallImageType.ICON).build())
            .setTapAction(tapAction)
        return builder.build()
    }

    /** "--" with the event's icon, read as e.g. "No feeds yet". */
    private fun empty(
        @DrawableRes icon: Int,
        @StringRes spoken: Int,
        tapAction: PendingIntent?,
        type: ComplicationType = ComplicationType.SHORT_TEXT,
    ): ComplicationData {
        val value = plain(res.getString(R.string.complication_no_value))
        val description = plain(res.getString(spoken))
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(description, description)
                .setMonochromaticImage(monochrome(icon))
                .setTapAction(tapAction)
                .build()
            else -> ShortTextComplicationData.Builder(value, description)
                .setMonochromaticImage(monochrome(icon))
                .setTapAction(tapAction)
                .build()
        }
    }

    /**
     * The app grammar as [DynamicComplicationText] (Wear OS 4+), otherwise the system time difference inside
     * [fallbackTemplate] (`^1` is the time), or the static text when there is no template.
     */
    @SuppressLint("NewApi") // dynamicSupported checks the API level (injectable for tests).
    private fun complicationText(value: TimeText, since: Long, fallbackTemplate: String?): ComplicationText = when {
        static -> plain(value.static)
        dynamicSupported -> DynamicComplicationText(value.dynamic, value.static)
        fallbackTemplate != null -> timeDifference(since, fallbackTemplate)
        else -> plain(value.static)
    }

    private fun timeDifference(since: Long, surrounding: String): ComplicationText =
        TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_DUAL_UNIT, CountUpTimeReference(Instant.ofEpochMilli(since)))
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .setText(surrounding)
            .build()

    private fun spokenStatus(@StringRes template: Int, since: Long, now: Long): TimeText =
        text.template(SurfaceText.Template(res.getString(template, SurfaceText.Template.PLACEHOLDER, text.clock(since))), text.spokenDuration(since, now))

    private fun plain(value: String): ComplicationText = PlainComplicationText.Builder(value).build()

    private fun monochrome(@DrawableRes icon: Int) = MonochromaticImage.Builder(Icon.createWithResource(context, icon)).build()

    private val dynamicSupported: Boolean get() = !static && sdkInt >= Build.VERSION_CODES.TIRAMISU

    private data class Quad(val from: Long, val line: TimeText, val spoken: TimeText, @DrawableRes val icon: Int)

    companion object {
        /** Where [TimeDifferenceComplicationText] puts the time inside its surrounding text. */
        private const val TIME_TOKEN = "^1"

        /** The data for a complication type this source does not provide. */
        fun noData(): ComplicationData = NoDataComplicationData()
    }
}
