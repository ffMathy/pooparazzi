package com.ffmathy.pooparazzi.phone.ui.home

import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.domain.LampState
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
    /** Card chips (DESIGN_SPEC §3.3.1). */
    val EARLIER_CHIPS = listOf(-5, -10, -15)
    val RUNNING_SLEEP_CHIPS = listOf(-5, -10)
    val ADJUST_CHIPS = listOf(-15, -5, 5)

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
        timings: HomeTimings = HomeTimings(),
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
            card = card?.let { cardUi(it, entries, summary, timings) },
            sheet = sheet?.let { sheetUi(it) },
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

    private fun cardUi(model: CardModel, entries: List<Entry>, summary: Summary, timings: HomeTimings): UndoCardUi {
        val now = summary.now
        val subjectEntry = model.spec.subject?.let { (id, _) -> entries.firstOrNull { it.id == id && !it.deleted } }
        val variant = when (val spec = model.spec) {
            is CardSpec.FeedLogged -> CardVariant.FeedLogged(spec.type, spec.side, spec.amountMl)
            is CardSpec.DiaperLogged -> CardVariant.DiaperLogged(spec.kind)
            is CardSpec.SleepStarted -> CardVariant.SleepStarted
            is CardSpec.WokeUp -> CardVariant.WokeUp(subjectEntry?.clockDurationMillis(now) ?: 0L)
            is CardSpec.SleepCancelled -> CardVariant.SleepCancelled
            is CardSpec.AdjustStart -> CardVariant.AdjustStart
            is CardSpec.Notice -> CardVariant.Notice(spec.guard)
            is CardSpec.Deleted -> CardVariant.Deleted(spec.previous)
            is CardSpec.Undone -> CardVariant.Undone
        }
        val action = when {
            model.spec is CardSpec.AdjustStart -> CardAction.DONE
            model.undo != null -> CardAction.UNDO
            else -> CardAction.NONE
        }
        val runningSleep = summary.runningSleep
        val row = when (val message = model.message) {
            is CardMessage.Guard -> CardRow.Guard(message.kind, message.sinceMillis)
            is CardMessage.Dedupe -> CardRow.Dedupe(message.entry)
            null -> chipRow(model, subjectEntry, runningSleep, now)
        }
        return UndoCardUi(
            key = model.key,
            windowKey = model.windowKey,
            timeoutMillis = if (model.spec is CardSpec.Undone) timings.undoneMillis else timings.undoWindowMillis,
            variant = variant,
            action = action,
            row = row,
        )
    }

    private fun chipRow(model: CardModel, entry: Entry?, runningSleep: Entry?, now: Long): CardRow {
        val subject = model.spec.subject ?: return CardRow.None
        if (entry == null) return CardRow.None
        fun chips(minutes: List<Int>) = minutes.map { m ->
            val selected = model.offsetMinutes == m
            OffsetChipUi(m, selected, enabled = selected || offsetAllowed(entry, subject.second, model.baseTime, m, now))
        }
        return when {
            model.spec is CardSpec.AdjustStart -> CardRow.Adjust(chips(ADJUST_CHIPS), entry.id)
            showsRunningSleepRow(model, runningSleep) -> CardRow.RunningSleep(
                endSleepEnabled = runningSleep != null && entry.startMillis >= runningSleep.startMillis && entry.startMillis <= now,
                chips = chips(RUNNING_SLEEP_CHIPS),
            )
            else -> CardRow.Earlier(chips(EARLIER_CHIPS))
        }
    }

    /** The running-sleep row replaces "Earlier?" for a feed or diaper logged during a sleep that still runs. */
    fun showsRunningSleepRow(model: CardModel, runningSleep: Entry?): Boolean =
        model.duringSleep && runningSleep != null && model.spec.canEndSleep

    /**
     * Whether moving [field] of [entry] to `base + minutes` is allowed: never into the future, never an end before
     * its start or a start after its end (DESIGN_SPEC §3.3.1 card rules).
     */
    fun offsetAllowed(entry: Entry, field: MoveField, base: Long, minutes: Int, now: Long): Boolean {
        val target = base + minutes * MINUTE_MILLIS
        if (target > now) return false
        return when (field) {
            MoveField.START -> entry.endMillis?.let { target <= it } ?: true
            MoveField.END -> target >= entry.startMillis
        }
    }

    private fun sheetUi(sheet: SheetModel): HomeSheetUi {
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
        }
    }
}
