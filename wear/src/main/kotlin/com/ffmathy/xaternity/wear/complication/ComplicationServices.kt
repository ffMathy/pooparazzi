package com.ffmathy.xaternity.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.domain.summarize
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.wear.MainActivity
import com.ffmathy.xaternity.wear.surface.SurfaceText
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * A complication data source for one [XComplication] (DESIGN_SPEC §3.7, §10.10). `UPDATE_PERIOD_SECONDS` is 0: the
 * text ticks on its own (dynamic or time-difference text), and the app asks for an update after every change
 * ([requestUpdateAll]).
 */
abstract class XComplicationService(private val complication: XComplication) : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != complication.type) return ComplicationContent.noData()
        val graph = Core.init(applicationContext)
        val zone = ZoneId.systemDefault()
        val summary = summarize(graph.repository.observeEntries().first(), graph.clock.now(), zone)
        return ComplicationContent(this, SurfaceText.from(this, zone)).build(complication, summary, tapAction(this, complication))
    }

    /** The face editor's preview: the reference scenario at 14:32 (V1_ADDITIONS §6), as plain text. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != complication.type) return null
        val summary = summarize(SampleData.scenario(), SampleData.NOW_1432, SampleData.ZONE)
        return ComplicationContent(this, SurfaceText.from(this, SampleData.ZONE), static = true).build(complication, summary, tapAction = null)
    }

    companion object {
        private const val TAG = "XComplication"

        /** Every complication service, for update requests. */
        val services: List<Class<out XComplicationService>> = listOf(
            SinceFeedComplicationService::class.java,
            LastDiaperComplicationService::class.java,
            SleepComplicationService::class.java,
            SleepLongComplicationService::class.java,
        )

        /** Asks every watch face showing one of these complications for fresh data. */
        fun requestUpdateAll(context: Context) {
            for (service in services) {
                try {
                    ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
                } catch (e: Exception) {
                    Log.d(TAG, "Complication update not requested", e)
                }
            }
        }

        /** A tap opens Main, where the fact lives; complications never log (§3.7). */
        fun tapAction(context: Context, complication: XComplication): PendingIntent = PendingIntent.getActivity(
            context,
            complication.ordinal,
            MainActivity.intent(context, MainActivity.OPEN_MAIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** "2h 10m" since the last feed, with the feed type's icon. */
class SinceFeedComplicationService : XComplicationService(XComplication.SINCE_FEED)

/** "48m" since the last diaper, with its kind glyph. */
class LastDiaperComplicationService : XComplicationService(XComplication.LAST_DIAPER)

/** Asleep or awake for "1h 07m". */
class SleepComplicationService : XComplicationService(XComplication.SLEEP)

/** "Awake 1h 05m" · "since 13:27" or "Asleep 1h 07m" · "Fed 1h 32m ago". */
class SleepLongComplicationService : XComplicationService(XComplication.SLEEP_LONG)
