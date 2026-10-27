package com.ffmathy.pooparazzi.wear

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.EndResult
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.LogResult
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.AlreadyRunningException
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.model.isBreastfeed
import com.ffmathy.pooparazzi.core.model.isInterval
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.wear.state.AdjustRequest
import com.ffmathy.pooparazzi.wear.state.AdjustUiState
import com.ffmathy.pooparazzi.wear.state.DiaperDraft
import com.ffmathy.pooparazzi.wear.state.EarlierOffsets
import com.ffmathy.pooparazzi.wear.state.FeedDraft
import com.ffmathy.pooparazzi.wear.state.Guard
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.GuardTarget
import com.ffmathy.pooparazzi.wear.state.MainUiState
import com.ffmathy.pooparazzi.wear.state.Receipt
import com.ffmathy.pooparazzi.wear.state.ReceiptKind
import com.ffmathy.pooparazzi.wear.state.SetTimeUiState
import com.ffmathy.pooparazzi.wear.state.SideDraft
import com.ffmathy.pooparazzi.wear.state.TimeField
import com.ffmathy.pooparazzi.wear.state.TodayUiState
import com.ffmathy.pooparazzi.wear.state.Undo
import com.ffmathy.pooparazzi.wear.state.WearEvent
import com.ffmathy.pooparazzi.wear.state.XHaptic
import com.ffmathy.pooparazzi.wear.state.timeOf
import com.ffmathy.pooparazzi.wear.state.withTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.time.ZoneId

/**
 * The watch app's state holder, shared by every destination (it lives as long as the activity).
 *
 * It derives Main and Today from the repository, the sync status and a minute ticker; keeps the drafts of the
 * pickers (Feed type, Side, Diaper, Adjust, Set time) and the current Logged receipt; and performs every write.
 * Haptics and navigation that depend on a write's outcome are sent as one-shot [events]
 * (DESIGN_SPEC §3.5, §5.11, §5.12; V1_ADDITIONS §2.2, §3.2).
 *
 * Writes run one at a time, so a double tap is handled after the first tap's write and sees its result
 * (the 2 s toggle guard, the repository's 5 s dedupe). A picker's write consumes its draft, so a second tap that
 * reached the leaving picker does nothing instead of writing again.
 *
 * @param zone the device time zone, read again on every tick and refresh (the watch can change zone while the app
 *   lives: travel, or a time-zone broadcast, see [WearApp]).
 * @param ticks emits once at start and then on every minute boundary (see [minuteTicks]).
 */
class MainViewModel(
    private val repository: EntryRepository,
    private val sync: EntrySync,
    private val clock: Clock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    ticks: Flow<Unit> = minuteTicks(clock),
) : ViewModel() {

    private val entries: StateFlow<List<Entry>?> =
        repository.observeEntries().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Bumped by the ambient minute tick, which the regular ticker cannot rely on while the watch sleeps. */
    private val refreshes = MutableStateFlow(0)
    private val guard = MutableStateFlow<Guard?>(null)

    /** "Now", refreshed on every minute boundary, ambient tick and data change. */
    private val nowTicks: Flow<Unit> = combine(ticks, refreshes) { _, _ -> }

    val main: StateFlow<MainUiState> =
        combine(entries.filterNotNull(), nowTicks, guard, sync.status) { list, _, guard, status ->
            MainUiState.from(summarize(list, clock.now(), zone()), guard, status)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.loading(clock.now(), zone()))

    val today: StateFlow<TodayUiState> =
        combine(entries.filterNotNull(), nowTicks) { list, _ ->
            val zone = zone()
            val summary = summarize(list, clock.now(), zone)
            TodayUiState(summary.now, zone, summary.todayDate, summary.dayEntries(summary.todayDate))
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            zone().let { TodayUiState(clock.now(), it, localDate(clock.now(), it), emptyList(), loaded = false) },
        )

    private val _feedDraft = MutableStateFlow<FeedDraft?>(null)
    val feedDraft: StateFlow<FeedDraft?> = _feedDraft.asStateFlow()

    private val _sideDraft = MutableStateFlow<SideDraft?>(null)
    val sideDraft: StateFlow<SideDraft?> = _sideDraft.asStateFlow()

    private val _diaperDraft = MutableStateFlow<DiaperDraft?>(null)
    val diaperDraft: StateFlow<DiaperDraft?> = _diaperDraft.asStateFlow()

    private val _receipt = MutableStateFlow<Receipt?>(null)
    val receipt: StateFlow<Receipt?> = _receipt.asStateFlow()

    /** What Adjust is open for; cleared once a choice is applied, so a second tap on the leaving screen does nothing. */
    private val adjustRequest = MutableStateFlow<AdjustRequest?>(null)
    val adjust: StateFlow<AdjustUiState?> =
        combine(entries.filterNotNull(), adjustRequest, nowTicks) { list, request, _ ->
            request ?: return@combine null
            val entry = list.firstOrNull { it.id == request.entryId } ?: return@combine null
            AdjustUiState.build(entry, request.fields, request.allowSetTime, clock.now(), zone())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val setTimeField = MutableStateFlow<TimeField?>(null)
    val setTime: StateFlow<SetTimeUiState?> =
        combine(entries.filterNotNull(), adjustRequest, setTimeField) { list, request, field ->
            if (request == null || field == null) return@combine null
            val entry = list.firstOrNull { it.id == request.entryId } ?: return@combine null
            val base = entry.timeOf(field) ?: return@combine null
            SetTimeUiState(entry, field, base, zone())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _events = Channel<WearEvent>(Channel.BUFFERED)

    /** Haptics and navigation, each delivered once. */
    val events: Flow<WearEvent> = _events.receiveAsFlow()

    private val writes = Mutex()
    private var lastSleepToggleAt = NEVER
    private var lastFeedToggleAt = NEVER
    private var receiptSerial = 0L
    private var guardJob: Job? = null

    // ---- Feed --------------------------------------------------------------------------------

    /** Feed tapped on Main: stamps the time for the Feed type screen. */
    fun beginFeed() {
        _feedDraft.value = FeedDraft(stamp = clock.now(), zone = zone())
    }

    /** Earlier on the Feed type screen: 0 → −5 → −10 → −15 → 0 minutes. */
    fun cycleFeedOffset() {
        _feedDraft.update { it?.copy(offsetMinutes = EarlierOffsets.next(it.offsetMinutes)) }
        haptic(XHaptic.SEGMENT_TICK)
    }

    /**
     * Breast starts the timer and returns to Main; a breastfeed already running shows the guard on "End feed".
     * Formula, Food and "Just the time" ([type] null) log an instant and show Logged ("Already logged" for a repeat
     * within 5 s).
     */
    fun chooseFeedType(type: FeedType?): Job = action {
        // A second tap that reached the leaving screen finds the draft used and does nothing.
        val draft = _feedDraft.consume() ?: return@action
        restoreOnFailure(_feedDraft, draft) { logFeed(type, draft.at) }
    }

    private suspend fun logFeed(type: FeedType?, at: Long) {
        if (type == FeedType.BREAST) {
            val started = repository.startBreastfeed(at)
            val error = started.exceptionOrNull()
            when {
                error == null -> {
                    lastFeedToggleAt = clock.now()
                    haptic(XHaptic.TOGGLE_ON)
                    send(WearEvent.BackToMain)
                }
                error is AlreadyRunningException -> {
                    showGuard(GuardKind.ALREADY_FEEDING, GuardTarget.END_FEED)
                    send(WearEvent.BackToMain)
                }
                else -> throw error
            }
        } else {
            val result = repository.logFeedChecked(type, at)
            showReceipt(ReceiptKind.FEED_LOGGED, result.entry, Undo.Delete(result.entry.id), duplicate = result is LogResult.Duplicate)
        }
    }

    // ---- Breastfeed end ----------------------------------------------------------------------

    /** "End feed": stamps the end and opens the Side screen, unless within 2 s of the start (guard). */
    fun requestEndFeed(): Job = action {
        val now = clock.now()
        val running = currentSummary().runningFeed
        when {
            running == null -> {
                haptic(XHaptic.REJECT)
                send(WearEvent.BackToMain)
            }
            now - lastFeedToggleAt < GUARD_MILLIS -> showGuard(GuardKind.ALREADY_FEEDING, GuardTarget.END_FEED)
            else -> {
                _sideDraft.value = SideDraft(running, endAt = now, zone = zone())
                send(WearEvent.ShowSide)
            }
        }
    }

    /** Ticks or unticks [breast] on the Side screen; the tick order is the order recorded (re-ticking appends). */
    fun tickSide(breast: Breast) {
        _sideDraft.update { it?.copy(ticks = BreastSide.toggle(it.ticks, breast)) }
    }

    /**
     * "End feed" on the Side screen ends the feed at the stamped time with the ticked order (no side when nothing is
     * ticked): "Feed ended" (or "Feed cancelled" under 60 s).
     */
    fun endFeed(): Job = action {
        // Consumed: a second tap on the leaving Side screen must not end the (ended) feed again and close its receipt.
        val draft = _sideDraft.consume() ?: return@action
        restoreOnFailure(_sideDraft, draft) { endBreastfeed(draft, draft.side) }
    }

    private suspend fun endBreastfeed(draft: SideDraft, side: BreastSide?) {
        when (val result = repository.endBreastfeed(draft.feed.id, draft.endAt, side)) {
            is EndResult.Ended -> {
                lastFeedToggleAt = clock.now()
                showReceipt(ReceiptKind.FEED_ENDED, result.entry, Undo.Restore(result.previous), haptic = XHaptic.TOGGLE_OFF)
            }
            is EndResult.Discarded -> {
                lastFeedToggleAt = clock.now()
                showReceipt(ReceiptKind.FEED_CANCELLED, result.entry, Undo.Restore(result.previous), haptic = XHaptic.TOGGLE_OFF)
            }
            is EndResult.NotRunning -> {
                haptic(XHaptic.REJECT)
                send(WearEvent.BackToMain)
            }
        }
    }

    // ---- Sleep -------------------------------------------------------------------------------

    /**
     * The sleep toggle (pinned EdgeButton, or the list button while a breastfeed runs). Start sleep stays on Main;
     * Woke up shows Logged (or "Sleep cancelled" under 60 s).
     *
     * The tap carries what the button showed: [shownSleepId] is the running sleep behind "Woke up", or null for
     * "Start sleep". When the other device changed the state in the meantime the tap does not flip it back: "Start
     * sleep" while a sleep runs shows "Already asleep", "Woke up" for a sleep that already ended shows "Already
     * awake" (DESIGN_SPEC §4.4, §3.5.11). Within 2 s of a toggle it shows the guard too.
     */
    fun toggleSleep(shownSleepId: String?): Job = action {
        val now = clock.now()
        val running = currentSummary().runningSleep
        if (now - lastSleepToggleAt < GUARD_MILLIS) {
            showGuard(if (running != null) GuardKind.ALREADY_ASLEEP else GuardKind.ALREADY_AWAKE, GuardTarget.SLEEP_TOGGLE)
            return@action
        }
        if (shownSleepId == null && running != null) {
            showGuard(GuardKind.ALREADY_ASLEEP, GuardTarget.SLEEP_TOGGLE)
            return@action
        }
        if (shownSleepId != null && running?.id != shownSleepId) {
            showGuard(GuardKind.ALREADY_AWAKE, GuardTarget.SLEEP_TOGGLE)
            return@action
        }
        if (running == null) {
            val started = repository.startSleep(now)
            val error = started.exceptionOrNull()
            when {
                error == null -> {
                    lastSleepToggleAt = now
                    clearGuard()
                    haptic(XHaptic.TOGGLE_ON)
                }
                error is AlreadyRunningException -> showGuard(GuardKind.ALREADY_ASLEEP, GuardTarget.SLEEP_TOGGLE)
                else -> throw error
            }
            return@action
        }
        when (val result = repository.endSleep(running.id, now)) {
            is EndResult.Ended -> {
                lastSleepToggleAt = now
                clearGuard()
                showReceipt(ReceiptKind.WOKE_UP, result.entry, Undo.Restore(result.previous), haptic = XHaptic.TOGGLE_OFF)
            }
            is EndResult.Discarded -> {
                lastSleepToggleAt = now
                clearGuard()
                showReceipt(ReceiptKind.SLEEP_CANCELLED, result.entry, Undo.Restore(result.previous), haptic = XHaptic.TOGGLE_OFF)
            }
            is EndResult.NotRunning -> showGuard(GuardKind.ALREADY_AWAKE, GuardTarget.SLEEP_TOGGLE)
        }
    }

    // ---- Diaper ------------------------------------------------------------------------------

    /** Diaper tapped on Main: stamps the time; both checkboxes start off. */
    fun beginDiaper() {
        _diaperDraft.value = DiaperDraft(stamp = clock.now(), zone = zone())
    }

    fun cycleDiaperOffset() {
        _diaperDraft.update { it?.copy(offsetMinutes = EarlierOffsets.next(it.offsetMinutes)) }
        haptic(XHaptic.SEGMENT_TICK)
    }

    fun setPee(on: Boolean) = _diaperDraft.update { it?.copy(pee = on) }

    /** Turning Poop off also clears its colour and texture (they only exist with poop). */
    fun setPoop(on: Boolean) = _diaperDraft.update {
        it?.copy(poop = on, color = it.color.takeIf { on }, texture = it.texture.takeIf { on })
    }

    fun setPoopColor(color: PoopColor?) {
        _diaperDraft.update { it?.copy(color = color) }
        haptic(XHaptic.SEGMENT_TICK)
    }

    fun setPoopTexture(texture: PoopTexture?) {
        _diaperDraft.update { it?.copy(texture = texture) }
        haptic(XHaptic.SEGMENT_TICK)
    }

    /** Save: logs the diaper at stamp + offset and shows Logged. With both checkboxes off it is a plain change. */
    fun saveDiaper(): Job = action {
        // A second tap that reached the leaving screen finds the draft used and does nothing.
        val draft = _diaperDraft.consume() ?: return@action
        restoreOnFailure(_diaperDraft, draft) {
            val result = repository.logDiaperChecked(draft.at, draft.pee, draft.poop, draft.color, draft.texture)
            showReceipt(ReceiptKind.DIAPER_LOGGED, result.entry, Undo.Delete(result.entry.id), duplicate = result is LogResult.Duplicate)
        }
    }

    // ---- Logged / Undo -----------------------------------------------------------------------

    /**
     * Opens Logged for an existing entry, e.g. from the Tile's undo path ([fromTile]: Undo is active at once).
     * Instants undo by deletion; ended intervals by restoring them as running, and so do intervals discarded as a
     * mis-tap ("Sleep cancelled" on the tile, DESIGN_SPEC §4.4: with Undo on phone and watch).
     */
    fun openReceipt(entryId: String, fromTile: Boolean): Job = action {
        val stored = repository.get(entryId) ?: return@action
        if (stored.deleted) {
            openCancelledReceipt(stored, fromTile)
            return@action
        }
        val entry = stored
        val (kind, undo) = when {
            entry.kind == EntryKind.DIAPER -> ReceiptKind.DIAPER_LOGGED to Undo.Delete(entry.id)
            entry.isSleep && entry.endMillis != null -> ReceiptKind.WOKE_UP to Undo.Restore(entry.copy(endMillis = null))
            entry.isBreastfeed && entry.endMillis != null ->
                ReceiptKind.FEED_ENDED to Undo.Restore(entry.copy(endMillis = null, breastSide = null))
            !entry.isInterval -> ReceiptKind.FEED_LOGGED to Undo.Delete(entry.id)
            else -> return@action
        }
        _receipt.value = Receipt(kind, entry, undo, zone(), fromTile = fromTile, serial = ++receiptSerial)
        send(WearEvent.ShowLogged)
    }

    /** A sleep or breastfeed ended under a minute (soft-deleted): "Sleep cancelled" / "Feed cancelled", Undo resumes it. */
    private fun openCancelledReceipt(entry: Entry, fromTile: Boolean) {
        if (!entry.isInterval || entry.endMillis == null) return
        val kind = if (entry.isSleep) ReceiptKind.SLEEP_CANCELLED else ReceiptKind.FEED_CANCELLED
        val running = entry.copy(deleted = false, endMillis = null, breastSide = null)
        _receipt.value = Receipt(kind, entry, Undo.Restore(running), zone(), fromTile = fromTile, serial = ++receiptSerial)
        send(WearEvent.ShowLogged)
    }

    /** Undo on Logged: reverts the receipt, then "Undone". */
    fun undo(): Job = action {
        val receipt = _receipt.value ?: return@action
        try {
            when (val undo = receipt.undo) {
                is Undo.Delete -> repository.delete(undo.id)
                is Undo.Restore -> repository.restore(undo.previous)
            }
        } catch (e: AlreadyRunningException) {
            // Another sleep or feed started meanwhile (e.g. on the phone): the old one cannot run again.
            Log.i(TAG, "Undo blocked by a running entry", e)
            _receipt.value = null
            haptic(XHaptic.REJECT)
            send(WearEvent.BackToMain)
            return@action
        }
        _receipt.value = null
        // Undo is deliberate: the reverted toggle may be toggled again at once.
        lastSleepToggleAt = NEVER
        lastFeedToggleAt = NEVER
        haptic(XHaptic.REJECT)
        send(WearEvent.ShowUndone)
    }

    // ---- Adjust / Set time -------------------------------------------------------------------

    /** Opens Adjust for [entryId]'s [fields]; [allowSetTime] adds "Set time" (from Today rows). */
    fun beginAdjust(entryId: String, fields: List<TimeField>, allowSetTime: Boolean = false) {
        setTimeField.value = null
        adjustRequest.value = AdjustRequest(entryId, fields, allowSetTime)
    }

    /**
     * An Adjust choice: moves [field] to [at], the time the tapped button showed ("−5m · 02:00"), plays SegmentTick
     * and returns. The target is absolute, so offsets are never cumulative (DESIGN_SPEC §4.4), and the request is used
     * up: a second tap queued behind the first does nothing.
     */
    fun adjust(field: TimeField, at: Long): Job = action {
        val request = adjustRequest.value ?: return@action
        val entry = repository.get(request.entryId)?.takeUnless { it.deleted }
        val updated = entry?.withTime(field, at, clock.now())
        if (updated == null) {
            haptic(XHaptic.REJECT)
            return@action
        }
        repository.update(updated)
        closeAdjust()
    }

    /** "Set time" on Adjust: opens the time picker for [field]. */
    fun beginSetTime(field: TimeField) {
        setTimeField.value = field
    }

    /**
     * The picked clock time for the open Set time field: the latest such time on or before now, on the base's
     * day or the day before. Returns to Adjust's caller.
     */
    fun setTime(time: LocalTime): Job = action {
        // Used up by an earlier confirm (a double tap): nothing to do.
        val request = adjustRequest.value ?: return@action
        val field = setTimeField.value ?: return@action
        val entry = repository.get(request.entryId)?.takeUnless { it.deleted }
        val base = entry?.timeOf(field)
        if (entry == null || base == null) {
            haptic(XHaptic.REJECT)
            return@action
        }
        val now = clock.now()
        val zone = zone()
        var at = localDate(base, zone).atTime(time).atZone(zone).toInstant().toEpochMilli()
        if (at > now) at = localDate(base, zone).minusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
        val updated = entry.withTime(field, at, now)
        if (updated == null) {
            haptic(XHaptic.REJECT)
            return@action
        }
        repository.update(updated)
        closeAdjust()
    }

    /** After an applied choice: SegmentTick, then back to Adjust's caller; the request is used up. */
    private fun closeAdjust() {
        setTimeField.value = null
        adjustRequest.value = null
        haptic(XHaptic.SEGMENT_TICK)
        send(WearEvent.CloseAdjust)
    }

    // ---- Ticks -------------------------------------------------------------------------------

    /** Re-reads "now" and the time zone (the ambient minute tick, returning to the foreground, a zone change). */
    fun refresh() = refreshes.update { it + 1 }

    // ---- Internals ---------------------------------------------------------------------------

    private suspend fun currentSummary(): Summary = summarize(repository.observeEntries().first(), clock.now(), zone())

    /** Takes the draft for one write; null when another tap already took it. */
    private fun <T : Any> MutableStateFlow<T?>.consume(): T? = getAndUpdate { null }

    /** Runs [write] for a consumed [draft]; a failed write (nothing changed) puts the draft back for another try. */
    private suspend fun <T : Any> restoreOnFailure(flow: MutableStateFlow<T?>, draft: T, write: suspend () -> Unit) {
        try {
            write()
        } catch (e: Exception) {
            flow.compareAndSet(null, draft)
            throw e
        }
    }

    private fun action(block: suspend () -> Unit): Job = viewModelScope.launch {
        writes.withLock {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Invalid times (e.g. in the future) or storage errors: nothing changed, so say no.
                Log.w(TAG, "Action failed", e)
                haptic(XHaptic.REJECT)
            }
        }
    }

    private fun showReceipt(kind: ReceiptKind, entry: Entry, undo: Undo, duplicate: Boolean = false, haptic: XHaptic? = null) {
        _receipt.value = Receipt(kind, entry, undo, zone(), duplicate = duplicate, serial = ++receiptSerial)
        haptic(if (duplicate) XHaptic.REJECT else haptic ?: XHaptic.CONFIRM)
        send(WearEvent.ShowLogged)
    }

    private fun showGuard(kind: GuardKind, target: GuardTarget) {
        val shown = Guard(kind, target, untilMillis = clock.now() + GUARD_MILLIS)
        guard.value = shown
        haptic(XHaptic.REJECT)
        guardJob?.cancel()
        guardJob = viewModelScope.launch {
            delay(GUARD_MILLIS)
            guard.compareAndSet(shown, null)
        }
    }

    private fun clearGuard() {
        guardJob?.cancel()
        guard.value = null
    }

    private fun haptic(type: XHaptic) = send(WearEvent.Haptic(type))

    private fun send(event: WearEvent) {
        _events.trySend(event)
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val NEVER = Long.MIN_VALUE / 2

        /** Double-toggle guard and guard message (DESIGN_SPEC §5.12). */
        const val GUARD_MILLIS = 2_000L

        /**
         * The production ViewModel on [Core]'s graph. Besides the minute ticks it re-reads the time zone and the
         * 12/24 h setting whenever the system says they changed ([WearApp.timeSettingsChanges]).
         */
        val Factory = viewModelFactory {
            initializer {
                val timeSettings = (this[APPLICATION_KEY] as? WearApp)?.timeSettingsChanges?.map { }
                val ticks = listOfNotNull(minuteTicks(Core.clock), timeSettings).merge()
                MainViewModel(Core.repository, Core.sync, Core.clock, ticks = ticks)
            }
        }
    }
}

/**
 * Emits at once and then just after every minute boundary of [clock] (nothing on the watch ticks faster,
 * DESIGN_SPEC §2.1). A [FixedClock] emits once (previews, tests, screenshots).
 */
fun minuteTicks(clock: Clock): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        if (clock is FixedClock) awaitCancellation()
        delay(MINUTE - Math.floorMod(clock.now(), MINUTE) + 50L)
    }
}

private const val MINUTE = 60_000L
