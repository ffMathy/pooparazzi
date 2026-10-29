package com.ffmathy.pooparazzi.phone.widget

import com.ffmathy.pooparazzi.core.domain.Expected
import com.ffmathy.pooparazzi.core.domain.forecast
import com.ffmathy.pooparazzi.core.domain.localTime
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
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
 * @property babies all babies, in order; with two or more the widgets name the current one ([babyId]).
 * @property sleepForecast the expected wake-up while asleep, else the expected next sleep (V1_ADDITIONS §9); null when
 *   the last week cannot predict it.
 * @property feedForecast the expected next feed; [poopForecast] the expected next poop, when poop is regular enough.
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
    val babies: List<Baby> = listOf(Baby(Baby.FIRST_ID)),
    val babyId: String = babies.first().id,
    val sleepForecast: Expected? = null,
    val feedForecast: Expected? = null,
    val poopForecast: Expected? = null,
) {
    val asleep: Boolean get() = runningSleepStart != null

    /** Two or more babies: the status row starts with the baby's label and TalkBack names it (BABIES_AND_BACKUP §2.4). */
    val hasSeveralBabies: Boolean get() = babies.size > 1

    /** The current baby, the one the widgets log for. */
    val baby: Baby get() = babies.firstOrNull { it.id == babyId } ?: babies.first()

    /** What a tap on the widget background opens: Home with the receipt sheet, or Home. */
    val backgroundLaunch: HomeLaunch
        get() = receipt?.let { HomeLaunch.Receipt(it.kind, it.entryId) } ?: HomeLaunch.Home

    companion object {
        /**
         * The snapshot at [now] of the current baby's [entries]. Receipts and notices are dropped once expired, and a
         * receipt also when its log no longer stands (undone or edited away in the app or on the watch) or was made for
         * another baby than the current one. Auto dims for naps while any baby is asleep ([allEntries], every baby's
         * log; BABIES_AND_BACKUP §2.3), as the app does.
         */
        fun of(
            entries: List<Entry>,
            settings: Settings,
            memo: WidgetMemo,
            now: Long,
            zone: ZoneId,
            systemDark: Boolean,
            allEntries: List<Entry> = entries,
        ): WidgetSnapshot {
            val summary = summarize(entries, now, zone)
            val week = forecast(summary)
            val sleep = summary.runningSleep
            val anyAsleep = sleep != null || allEntries.any { it.isSleep && it.isRunning }
            return WidgetSnapshot(
                night = NightRule.isNight(settings.appearance, systemDark, localTime(now, zone), sleepRunning = anyAsleep),
                zone = zone,
                language = settings.language,
                lastFeed = summary.lastFeed,
                lastDiaper = summary.lastDiaper,
                runningSleepStart = sleep?.startMillis,
                awakeSinceMillis = if (sleep == null) summary.lastSleepEnd else null,
                receipt = memo.receipt?.takeIf {
                    it.expiresAtMillis > now && it.babyId == settings.selectedBaby.id && it.stillStands(entries)
                },
                notice = memo.notice?.takeIf { it.expiresAtMillis > now },
                babies = settings.babies,
                babyId = settings.selectedBaby.id,
                sleepForecast = week.sleep,
                feedForecast = week.feed,
                poopForecast = week.poop,
            )
        }

        private fun WidgetReceipt.stillStands(entries: List<Entry>): Boolean {
            val entry = entries.firstOrNull { it.id == entryId }
            return when (kind) {
                ReceiptKind.LOGGED -> entry != null
                ReceiptKind.SLEEP_STARTED -> entry != null && entry.kind == EntryKind.SLEEP && entry.isRunning
                ReceiptKind.WOKE_UP -> entry != null && entry.kind == EntryKind.SLEEP && !entry.isRunning
                // A discarded sleep is soft-deleted: it is gone from the visible entries (of its own baby: see [of]).
                ReceiptKind.SLEEP_CANCELLED -> entry == null
            }
        }
    }
}

/**
 * When a forecast next changes without any new data: once its time has passed, the week's stretches that are still
 * longer give a later estimate (or "soon").
 */
fun WidgetSnapshot.forecastExpiry(now: Long): Long? = listOfNotNull(sleepForecast, feedForecast, poopForecast)
    .filter { !it.overdue && it.atMillis > now }
    .minOfOrNull { it.atMillis }

/** When the widgets next change without any new data: the receipt or notice expiry, if any. */
fun WidgetMemo.nextExpiry(now: Long): Long? =
    listOfNotNull(receipt?.expiresAtMillis, notice?.expiresAtMillis).filter { it > now }.minOrNull()
