package com.ffmathy.pooparazzi.phone.ui.home

import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.domain.forecast
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.lampState
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import java.time.ZoneId

/**
 * Derives [HomeUiState] from the entries and the view model's card and sheet records. Pure, so the screenshot tests
 * and previews build their states through the same rules as the app.
 */
internal object HomeStateBuilder {
    /** Receipt and Adjust start chips (DESIGN_SPEC §3.3.1): −15m · −10m · −5m · +5m. */
    val ADJUST_CHIPS = listOf(-15, -10, -5, 5)

    /** Sheet chips: Now · −5m · −10m · −15m. */
    val SHEET_CHIPS = listOf(0, -5, -10, -15)

    fun build(
        entries: List<Entry>,
        babyName: String,
        sync: SyncStatus,
        now: Long,
        zone: ZoneId,
        card: CardModel? = null,
        sheet: SheetModel? = null,
        babies: List<Baby> = listOf(Baby(Baby.FIRST_ID, babyName)),
        babyId: String = babies.first().id,
        switchingBaby: Boolean = false,
        cloud: CloudStatus = CloudStatus.Off,
    ): HomeUiState {
        val summary = summarize(entries, now, zone)
        val lamp = lampState(summary)
        return HomeUiState(
            now = now,
            zone = zone,
            babyName = babyName,
            sync = sync,
            cloud = cloud,
            lamp = lamp,
            row1 = row1(summary, lamp),
            row2 = row2(summary),
            today = summary.today,
            runningSleep = summary.runningSleep,
            forecast = forecast(summary),
            card = card?.let { cardUi(it) },
            sheet = sheet?.let { sheetUi(it, entries, summary) },
            babies = babies,
            babyId = babyId,
            switchingBaby = switchingBaby && babies.size > 1,
        )
    }

    private fun row1(summary: Summary, lamp: LampState): SinceRow? {
        val now = summary.now
        val awake = summary.lastSleep?.let { sleep ->
            val end = sleep.endMillis ?: return@let null
            SinceRow.Awake(sleep, end, clockDurationMillis(end, now))
        }
        return when (lamp) {
            is LampState.SleepRunning -> summary.lastFeed?.let { SinceRow.LastFed(it, clockDurationMillis(it.startMillis, now)) }
            is LampState.FeedIdle -> awake
        }
    }

    private fun row2(summary: Summary): SinceRow? = summary.lastDiaper?.let {
        SinceRow.LastDiaper(it, it.diaperKind ?: DiaperKind.PLAIN, clockDurationMillis(it.startMillis, summary.now))
    }

    private fun cardUi(model: CardModel): NoticeCardUi = NoticeCardUi(
        key = model.key,
        variant = when (val spec = model.spec) {
            is CardSpec.Guard -> NoticeVariant.Guard(spec.kind, spec.sinceMillis)
            CardSpec.Undone -> NoticeVariant.Undone
        },
    )

    private fun receiptUi(model: SheetModel.Receipt, entries: List<Entry>, summary: Summary): HomeSheetUi.Receipt {
        val now = summary.now
        val subject = model.spec.subject
        val entry = subject?.let { (id, _) -> entries.firstOrNull { it.id == id && !it.deleted } }
        val variant = when (val spec = model.spec) {
            is ReceiptSpec.FeedLogged -> ReceiptVariant.FeedLogged(spec.type, spec.side, spec.amountMl)
            is ReceiptSpec.DiaperLogged -> ReceiptVariant.DiaperLogged(spec.kind)
            is ReceiptSpec.SleepStarted -> ReceiptVariant.SleepStarted
            is ReceiptSpec.WokeUp -> ReceiptVariant.WokeUp(entry?.clockDurationMillis(now) ?: 0L)
            ReceiptSpec.SleepCancelled -> ReceiptVariant.SleepCancelled
            is ReceiptSpec.Deleted -> ReceiptVariant.Deleted(spec.previous)
        }
        val chips = if (subject == null || entry == null) {
            emptyList()
        } else {
            ADJUST_CHIPS.map { m ->
                val selected = model.offsetMinutes == m
                OffsetChipUi(m, selected, enabled = selected || offsetAllowed(entry, subject.second, model.stamp, m, now))
            }
        }
        val runningSleep = summary.runningSleep
        val endSleep = if (entry != null && showsEndSleep(model, runningSleep)) {
            runningSleep != null && entry.startMillis >= runningSleep.startMillis && entry.startMillis <= now
        } else {
            null
        }
        return HomeSheetUi.Receipt(
            stamp = model.stamp,
            offsetMinutes = model.offsetMinutes,
            chips = chips,
            key = model.key,
            variant = variant,
            undoable = model.undo != null,
            editEntryId = entry?.id,
            endSleep = endSleep,
            dedupe = model.dedupe,
        )
    }

    /** A receipt offers "End sleep" for a feed or diaper logged during a sleep that still runs. */
    fun showsEndSleep(model: SheetModel.Receipt, runningSleep: Entry?): Boolean =
        model.duringSleep && runningSleep != null && model.spec.canEndSleep

    /**
     * Whether moving [field] of [entry] to `base + minutes` is allowed: never into the future, never an end before
     * its start or a start after its end (DESIGN_SPEC §3.3.1 receipt rules).
     */
    fun offsetAllowed(entry: Entry, field: MoveField, base: Long, minutes: Int, now: Long): Boolean {
        val target = base + minutes * MINUTE_MILLIS
        if (target > now) return false
        return when (field) {
            MoveField.START -> entry.endMillis?.let { target <= it } ?: true
            MoveField.END -> target >= entry.startMillis
        }
    }

    private fun sheetUi(sheet: SheetModel, entries: List<Entry>, summary: Summary): HomeSheetUi {
        val now = summary.now
        fun chips(minutes: List<Int>, enabled: (Int) -> Boolean = { true }) =
            minutes.map { OffsetChipUi(it, selected = it == sheet.offsetMinutes, enabled = enabled(it)) }
        return when (sheet) {
            is SheetModel.Feed -> HomeSheetUi.Feed(
                sheet.stamp, sheet.offsetMinutes, chips(SHEET_CHIPS), sheet.breast, sheet.ticks, sheet.formula, sheet.amountMl,
            )
            is SheetModel.Diaper -> HomeSheetUi.Diaper(
                stamp = sheet.stamp,
                offsetMinutes = sheet.offsetMinutes,
                chips = chips(SHEET_CHIPS),
                pee = sheet.pee,
                poop = sheet.poop,
                color = sheet.color,
                texture = sheet.texture,
            )
            is SheetModel.Adjust -> {
                val entry = entries.firstOrNull { it.id == sheet.entryId && !it.deleted }
                HomeSheetUi.Adjust(
                    stamp = sheet.stamp,
                    offsetMinutes = sheet.offsetMinutes,
                    chips = chips(ADJUST_CHIPS) { m ->
                        m == sheet.offsetMinutes || (entry != null && offsetAllowed(entry, MoveField.START, sheet.stamp, m, now))
                    },
                    entryId = sheet.entryId,
                )
            }
            is SheetModel.Receipt -> receiptUi(sheet, entries, summary)
        }
    }
}
