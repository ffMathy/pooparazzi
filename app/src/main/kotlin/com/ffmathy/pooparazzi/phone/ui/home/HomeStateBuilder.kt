package com.ffmathy.pooparazzi.phone.ui.home

import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.data.sleepWokenBy
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * Derives [HomeUiState] from the entries and the view model's card and sheet records. Pure, so the screenshot tests
 * and previews build their states through the same rules as the app.
 */
internal object HomeStateBuilder {
    /** The nudge chips of every Home sheet (DESIGN_SPEC §3.3.1): −15m · −10m · −5m · +5m, cumulative. */
    val OFFSET_CHIPS = listOf(-15, -10, -5, 5)

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
        val born = (babies.firstOrNull { it.id == babyId } ?: babies.firstOrNull())?.born
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
            forecast = forecast(summary, born),
            card = card?.let { cardUi(it) },
            sheet = sheet?.let { sheetUi(it, entries, summary, expectedChip(it, entries, now, zone, born)) },
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
            is CardSpec.Duplicate -> NoticeVariant.Duplicate(spec.existing)
        },
    )

    /** Estimates closer than this before a sheet's base time are not offered: the nudge chips reach them. */
    const val EXPECTED_MIN_MILLIS = 5 * MINUTE_MILLIS

    /**
     * The time the past week expected the log on [sheet] (V1_ADDITIONS §9, to the nearest 5 minutes), for "Expected
     * 11:30" under its chips: a feed's from the last feed, a sleep's start from the last wake-up (as if this sleep had
     * not started), a wake-up's from the sleep's start, each seen from the sheet's base time and kept even once it is
     * long past ([forecast]'s keepStale: a forgotten log is exactly when it is needed). Null without an estimate, for a
     * diaper, and when it is under [EXPECTED_MIN_MILLIS] before the base time.
     */
    fun expectedAt(sheet: SheetModel, entries: List<Entry>, zone: ZoneId, born: LocalDate?): Long? {
        fun estimate(log: List<Entry>) = forecast(summarize(log, sheet.stamp, zone), born, keepStale = true)
        fun sleepStart(sleepId: String) = estimate(entries.filterNot { it.id == sleepId }).sleep
        val expected = when (sheet) {
            is SheetModel.Feed -> estimate(entries).feed
            is SheetModel.Diaper -> null
            is SheetModel.Adjust -> sleepStart(sheet.entryId)
            is SheetModel.Receipt -> when (val spec = sheet.spec) {
                is ReceiptSpec.SleepStarted -> sleepStart(spec.entryId)
                // The sleep as it was before Woke up: running, so the estimate is its wake-up.
                is ReceiptSpec.WokeUp -> estimate(entries.map { if (it.id == spec.entryId) it.copy(endMillis = null) else it }).sleep
                else -> null
            }
        } ?: return null
        return expected.roundedAtMillis.takeIf { it <= sheet.stamp - EXPECTED_MIN_MILLIS }
    }

    /** The offset from [sheet]'s base time that shows [at] (the base time's seconds kept, so the clock reads [at]). */
    fun offsetTo(sheet: SheetModel, at: Long): Int = -Math.floorDiv(sheet.stamp - at, MINUTE_MILLIS).toInt()

    /** "Expected 11:30" for [sheet], when its time can go there: never into the future, within the entry's bounds. */
    private fun expectedChip(sheet: SheetModel, entries: List<Entry>, now: Long, zone: ZoneId, born: LocalDate?): ExpectedChipUi? {
        val at = expectedAt(sheet, entries, zone, born) ?: return null
        val offset = offsetTo(sheet, at)
        if (sheet.stamp + offset * MINUTE_MILLIS > now) return null
        val subject = when (sheet) {
            is SheetModel.Adjust -> sheet.entryId to MoveField.START
            is SheetModel.Receipt -> sheet.spec.subject
            else -> null
        }
        if (subject != null) {
            val entry = entries.firstOrNull { it.id == subject.first && !it.deleted } ?: return null
            if (!offsetAllowed(entry, subject.second, sheet.stamp, offset, now)) return null
        }
        return ExpectedChipUi(at, offset, selected = sheet.offsetMinutes == offset)
    }

    private fun receiptUi(model: SheetModel.Receipt, entries: List<Entry>, summary: Summary, expected: ExpectedChipUi?): HomeSheetUi.Receipt {
        val now = summary.now
        val subject = model.spec.subject
        val entry = subject?.let { (id, _) -> entries.firstOrNull { it.id == id && !it.deleted } }
        val variant = when (val spec = model.spec) {
            is ReceiptSpec.FeedLogged -> ReceiptVariant.FeedLogged(spec.type, spec.side, spec.amountMl, spec.contents)
            is ReceiptSpec.DiaperLogged -> ReceiptVariant.DiaperLogged(spec.kind)
            is ReceiptSpec.SleepStarted -> ReceiptVariant.SleepStarted
            is ReceiptSpec.WokeUp -> ReceiptVariant.WokeUp(entry?.clockDurationMillis(now) ?: 0L)
            ReceiptSpec.SleepCancelled -> ReceiptVariant.SleepCancelled
            is ReceiptSpec.Deleted -> ReceiptVariant.Deleted(spec.previous)
        }
        val chips = if (subject == null || entry == null) {
            emptyList()
        } else {
            OFFSET_CHIPS.map { m ->
                OffsetChipUi(m, enabled = offsetAllowed(entry, subject.second, model.stamp, model.offsetMinutes + m, now))
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
            expected = expected,
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

    private fun sheetUi(sheet: SheetModel, entries: List<Entry>, summary: Summary, expected: ExpectedChipUi?): HomeSheetUi {
        val now = summary.now
        // A chip is enabled when one more tap is allowed: never into the future, and within the entry's own bounds.
        fun chips(allowed: (Int) -> Boolean = { m -> sheet.stamp + m * MINUTE_MILLIS <= now }) =
            OFFSET_CHIPS.map { OffsetChipUi(it, enabled = allowed(sheet.offsetMinutes + it)) }
        // A feed or diaper logged during a sleep also logs Woke up (DESIGN_SPEC §4.4): the sheet says so.
        val wakes = entries.sleepWokenBy(sheet.stamp + sheet.offsetMinutes * MINUTE_MILLIS) != null
        return when (sheet) {
            is SheetModel.Feed -> HomeSheetUi.Feed(
                sheet.stamp, sheet.offsetMinutes, chips(), sheet.breast, sheet.ticks, sheet.bottle, sheet.amountMl, sheet.contents,
                wakes = wakes,
                expected = expected,
            )
            is SheetModel.Diaper -> HomeSheetUi.Diaper(
                stamp = sheet.stamp,
                offsetMinutes = sheet.offsetMinutes,
                chips = chips(),
                pee = sheet.pee,
                poop = sheet.poop,
                color = sheet.color,
                texture = sheet.texture,
                wakes = wakes,
            )
            is SheetModel.Adjust -> {
                val entry = entries.firstOrNull { it.id == sheet.entryId && !it.deleted }
                HomeSheetUi.Adjust(
                    stamp = sheet.stamp,
                    offsetMinutes = sheet.offsetMinutes,
                    chips = chips { m -> entry != null && offsetAllowed(entry, MoveField.START, sheet.stamp, m, now) },
                    entryId = sheet.entryId,
                    expected = expected,
                )
            }
            is SheetModel.Receipt -> receiptUi(sheet, entries, summary, expected)
        }
    }
}
