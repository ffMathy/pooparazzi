package com.ffmathy.xaternity.phone.widget

import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.settings.Appearance
import com.ffmathy.xaternity.core.settings.Settings
import com.ffmathy.xaternity.phone.launch.ReceiptKind

/**
 * Widget states from the reference scenario (V1_ADDITIONS §6), built through [WidgetSnapshot.of] like the real ones.
 * Used by the generated picker previews, Glance previews and the screenshot tests.
 */
object WidgetSamples {
    private val zone = SampleData.ZONE

    /** The poop logged on the widget at 14:32 in [justLogged]. */
    val poop1432: Entry = Entry.diaper(SampleData.NOW_1432, pee = false, poop = true, id = "sample-widget-poop-1432")

    /** 14:32: "Fed at 12:22", "Pee at 13:44", "Awake since 13:27". */
    fun idle(night: Boolean): WidgetSnapshot = build(SampleData.scenario(zone), SampleData.NOW_1432, night)

    /** 14:32, Poop just logged on the widget: "Poop logged at 14:32" with Undo. */
    fun justLogged(night: Boolean): WidgetSnapshot = build(
        SampleData.scenario(zone) + poop1432,
        SampleData.NOW_1432,
        night,
        WidgetMemo(
            receipt = WidgetReceipt(
                kind = ReceiptKind.LOGGED,
                entryId = poop1432.id,
                atMillis = SampleData.NOW_1432,
                expiresAtMillis = SampleData.NOW_1432 + WidgetCommands.RECEIPT_MILLIS,
                diaperKind = DiaperKind.POOP,
            ),
        ),
    )

    /** 03:12, asleep since 02:05: always Night under Auto. */
    fun asleep(): WidgetSnapshot = build(SampleData.scenarioAt0312(zone), SampleData.NOW_0312, night = null)

    /** 14:50, breastfeeding since 14:32: Feed is "End feed". */
    fun breastfeeding(night: Boolean): WidgetSnapshot = build(SampleData.scenarioWithRunningFeed(zone), SampleData.NOW_1450, night)

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
