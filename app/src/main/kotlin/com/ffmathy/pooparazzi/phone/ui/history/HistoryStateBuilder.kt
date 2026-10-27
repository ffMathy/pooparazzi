package com.ffmathy.pooparazzi.phone.ui.history

import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * Derives [HistoryUiState] from the entries. Pure, so the app, the previews and the screenshot tests share the rules.
 */
internal object HistoryStateBuilder {
    fun build(
        entries: List<Entry>,
        sync: SyncStatus,
        now: Long,
        zone: ZoneId,
        day: LocalDate?,
        receipt: DeletedReceipt? = null,
    ): HistoryUiState {
        val summary = summarize(entries, now, zone)
        val today = summary.todayDate
        val shown = clampDay(day ?: today, today)
        return HistoryUiState(
            now = now,
            zone = zone,
            day = shown,
            today = today,
            sync = sync,
            canGoBack = shown > earliestDay(entries, now, zone, today),
            canGoForward = shown < today,
            ribbon = summary.ribbon(shown),
            counts = summary.dayCounts(shown),
            entries = summary.dayEntries(shown),
            receipt = receipt,
        )
    }

    /** Never after today. */
    fun clampDay(day: LocalDate, today: LocalDate): LocalDate = if (day > today) today else day

    /** The first day with something logged (today when nothing is). History does not page into empty time. */
    fun earliestDay(entries: List<Entry>, now: Long, zone: ZoneId, today: LocalDate): LocalDate =
        entries.asSequence()
            .filter { !it.deleted && it.startMillis <= now }
            .minOfOrNull { it.startMillis }
            ?.let { localDate(it, zone) }
            ?.takeIf { it < today }
            ?: today
}
