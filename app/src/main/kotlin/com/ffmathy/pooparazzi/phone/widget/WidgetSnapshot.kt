package com.ffmathy.pooparazzi.phone.widget

import com.ffmathy.pooparazzi.core.domain.localTime
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import com.ffmathy.pooparazzi.phone.ui.theme.NightRule
import java.time.ZoneId

/**
 * What the widgets show at one moment (DESIGN_SPEC §3.3.6, §8.5; V1_ADDITIONS §2.1). Pure data: the widgets format it
 * in the app language, and tests and previews build it from the reference scenario.
 *
 * @property night the app's resolved appearance (never the launcher's): the widgets use the fixed Night or Day tokens.
 * @property receipt the last widget log while it may still be undone; null once expired, replaced or undone elsewhere.
 * @property notice a 2 s line (guard, dedupe, "Undone"); shown instead of the receipt and the status facts.
 */
data class WidgetSnapshot(
    val night: Boolean,
    val zone: ZoneId,
    val language: AppLanguage,
    val lastFeed: Entry?,
    val lastDiaper: Entry?,
    val runningSleepStart: Long?,
    val awakeSinceMillis: Long?,
    val receipt: WidgetReceipt?,
    val notice: WidgetNotice?,
) {
    val asleep: Boolean get() = runningSleepStart != null

    /** What a tap on the widget background opens: Home with the undo card for the receipt, or Home. */
    val backgroundLaunch: HomeLaunch
        get() = receipt?.let { HomeLaunch.Receipt(it.kind, it.entryId) } ?: HomeLaunch.Home

    companion object {
        /**
         * The snapshot at [now]. Receipts and notices are dropped once expired, and a receipt also when its log no
         * longer stands (undone or edited away in the app or on the watch).
         */
        fun of(
            entries: List<Entry>,
            settings: Settings,
            memo: WidgetMemo,
            now: Long,
            zone: ZoneId,
            systemDark: Boolean,
        ): WidgetSnapshot {
            val summary = summarize(entries, now, zone)
            val sleep = summary.runningSleep
            return WidgetSnapshot(
                night = NightRule.isNight(settings.appearance, systemDark, localTime(now, zone), sleepRunning = sleep != null),
                zone = zone,
                language = settings.language,
                lastFeed = summary.lastFeed,
                lastDiaper = summary.lastDiaper,
                runningSleepStart = sleep?.startMillis,
                awakeSinceMillis = if (sleep == null) summary.lastSleepEnd else null,
                receipt = memo.receipt?.takeIf { it.expiresAtMillis > now && it.stillStands(entries) },
                notice = memo.notice?.takeIf { it.expiresAtMillis > now },
            )
        }

        private fun WidgetReceipt.stillStands(entries: List<Entry>): Boolean {
            val entry = entries.firstOrNull { it.id == entryId }
            return when (kind) {
                ReceiptKind.LOGGED -> entry != null
                ReceiptKind.SLEEP_STARTED -> entry != null && entry.kind == EntryKind.SLEEP && entry.isRunning
                ReceiptKind.WOKE_UP -> entry != null && entry.kind == EntryKind.SLEEP && !entry.isRunning
                // A discarded sleep is soft-deleted: it is gone from the visible entries.
                ReceiptKind.SLEEP_CANCELLED -> entry == null
            }
        }
    }
}

/** When the widgets next change without any new data: the receipt or notice expiry, if any. */
fun WidgetMemo.nextExpiry(now: Long): Long? =
    listOfNotNull(receipt?.expiresAtMillis, notice?.expiresAtMillis).filter { it > now }.minOrNull()
