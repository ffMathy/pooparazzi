package com.ffmathy.pooparazzi.phone.widget

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind

/**
 * Widget states from the reference scenario (V1_ADDITIONS §6), built through [WidgetSnapshot.of] like the real ones.
 * Used by the generated picker previews, Glance previews and the screenshot tests.
 */
object WidgetSamples {
    private val zone = SampleData.ZONE

    /** The sleep started on the widget at 14:32 in [justLogged]. */
    val sleep1432: Entry = Entry.sleep(SampleData.NOW_1432, id = "sample-widget-sleep-1432")

    /** 14:32: "Fed at 12:22", "Pee at 13:44", "Awake since 13:27". */
    fun idle(night: Boolean): WidgetSnapshot = build(SampleData.scenario(zone), SampleData.NOW_1432, night)

    /** 14:32, sleep just started on the widget: "Sleep started at 14:32" with Undo. */
    fun justLogged(night: Boolean): WidgetSnapshot = build(
        SampleData.scenario(zone) + sleep1432,
        SampleData.NOW_1432,
        night,
        WidgetMemo(
            receipt = WidgetReceipt(
                kind = ReceiptKind.SLEEP_STARTED,
                entryId = sleep1432.id,
                atMillis = SampleData.NOW_1432,
                expiresAtMillis = SampleData.NOW_1432 + WidgetCommands.RECEIPT_MILLIS,
            ),
        ),
    )

    /** 03:12, asleep since 02:05: always Night under Auto. */
    fun asleep(): WidgetSnapshot = build(SampleData.scenarioAt0312(zone), SampleData.NOW_0312, night = null)

    /** 14:50, after the 14:32 breast feed: "Fed at 14:32" with the breast icon. */
    fun afterBreastfeed(night: Boolean): WidgetSnapshot = build(SampleData.scenarioWithRecentFeed(zone), SampleData.NOW_1450, night)

    /** [night] fixes the Appearance (Night or Day); null leaves it on Auto. */
    private fun build(entries: List<Entry>, now: Long, night: Boolean?, memo: WidgetMemo = WidgetMemo()): WidgetSnapshot {
        val appearance = when (night) {
            null -> Appearance.AUTO
            true -> Appearance.NIGHT
            false -> Appearance.DAY
        }
        val settings: Settings = SampleData.settings.copy(appearance = appearance)
        return WidgetSnapshot.of(entries, settings, memo, now, zone, systemDark = false)
    }
}
