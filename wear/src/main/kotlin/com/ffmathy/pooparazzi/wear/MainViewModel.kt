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
import com.ffmathy.pooparazzi.core.data.EntryRules
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.LogResult
import com.ffmathy.pooparazzi.core.data.wakeFor
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.lastBottleAmountMl
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.AlreadyRunningException
import com.ffmathy.pooparazzi.core.model.BottleContents
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.model.isInterval
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.wear.state.BottleDraft
import com.ffmathy.pooparazzi.wear.state.CLEAR_GUARD_MILLIS
import com.ffmathy.pooparazzi.wear.state.DiaperDraft
import com.ffmathy.pooparazzi.wear.state.FeedDraft
import com.ffmathy.pooparazzi.wear.state.Guard
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.GuardTarget
import com.ffmathy.pooparazzi.wear.state.MAX_SHIFT_MINUTES
import com.ffmathy.pooparazzi.wear.state.MainUiState
import com.ffmathy.pooparazzi.wear.state.QUICK_DAYS
import com.ffmathy.pooparazzi.wear.state.Receipt
import com.ffmathy.pooparazzi.wear.state.ReceiptKind
import com.ffmathy.pooparazzi.wear.state.SideDraft
import com.ffmathy.pooparazzi.wear.state.TimeDraft
import com.ffmathy.pooparazzi.wear.state.TimeTitle
import com.ffmathy.pooparazzi.wear.state.Undo
import com.ffmathy.pooparazzi.wear.state.WearEvent
import com.ffmathy.pooparazzi.wear.state.Woke
import com.ffmathy.pooparazzi.wear.state.XHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
import java.time.ZoneId

/**
 * The watch app's state holder, shared by every destination (it lives as long as the activity).
 *
 * It derives Main and Today from the repository, the sync status and a minute ticker; keeps the drafts of the
 * pickers (Feed type, Side, Bottle, Diaper) and of the Time screen, and the current Logged receipt; and performs
 * every write.
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
    private val settings: SettingsRepository,
    private val sync: EntrySync,
    private val clock: Clock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    ticks: Flow<Unit> = minuteTicks(clock),
) : ViewModel() {

    /**
     * The babies and the current one (all the screens need of the settings) with that baby's entries, as one value:
     * a switch never pairs the new baby with the previous one's log, not even for a frame.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val log: StateFlow<BabyLog?> = settings.settings
        .map { Settings(babies = it.babies, selectedBabyId = it.selectedBaby.id) }
        .distinctUntilChanged()
        .flatMapLatest { babies -> repository.observeEntries().map { BabyLog(babies, it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val entries: Flow<List<Entry>> = log.filterNotNull().map { it.entries }

    /** Bumped by the ambient minute tick, which the regular ticker cannot rely on while the watch sleeps. */
    private val refreshes = MutableStateFlow(0)
    private val guard = MutableStateFlow<Guard?>(null)

    /** "Now", refreshed on every minute boundary, ambient tick and data change. */
    private val nowTicks: Flow<Unit> = combine(ticks, refreshes) { _, _ -> }

    val main: StateFlow<MainUiState> =
        combine(log.filterNotNull(), nowTicks, guard, sync.status) { log, _, guard, status ->
            MainUiState.from(summarize(log.entries, clock.now(), zone()), guard, status, log.babies.babies, log.babies.selectedBabyId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.loading(clock.now(), zone()))

    private val _feedDraft = MutableStateFlow<FeedDraft?>(null)
    val feedDraft: StateFlow<FeedDraft?> = _feedDraft.asStateFlow()

    private val _sideDraft = MutableStateFlow<SideDraft?>(null)
    val sideDraft: StateFlow<SideDraft?> = _sideDraft.asStateFlow()

    private val _bottleDraft = MutableStateFlow<BottleDraft?>(null)
    val bottleDraft: StateFlow<BottleDraft?> = _bottleDraft.asStateFlow()

    private val _diaperDraft = MutableStateFlow<DiaperDraft?>(null)
    val diaperDraft: StateFlow<DiaperDraft?> = _diaperDraft.asStateFlow()

    private val _timeDraft = MutableStateFlow<TimeDraft?>(null)
    val timeDraft: StateFlow<TimeDraft?> = _timeDraft.asStateFlow()

    private val _receipt = MutableStateFlow<Receipt?>(null)
    val receipt: StateFlow<Receipt?> = _receipt.asStateFlow()

    private val _events = Channel<WearEvent>(Channel.BUFFERED)

    /** Haptics and navigation, each delivered once. */
    val events: Flow<WearEvent> = _events.receiveAsFlow()

    private val writes = Mutex()
    private var lastSleepToggleAt = NEVER
    private var receiptSerial = 0L
    private var guardJob: Job? = null

    /** When each option was last chosen, for the clear guard ([CLEAR_GUARD_MILLIS]). Reset by a new Feed or Diaper. */
    private val choiceAt = mutableMapOf<Choice, Long>()

    // ---- Feed --------------------------------------------------------------------------------

    /** Feed tapped on Main: stamps the time for the Feed type screen. */
    fun beginFeed() {
        _feedDraft.value = FeedDraft(stamp = clock.now(), zone = zone())
        choiceAt.clear()
    }

    /**
     * Breast opens the Side screen for the optional side order, Bottle the Bottle screen for the optional contents and
     * amount; both log there, at this screen's time. Food and "Just the
     * time" ([type] null) log an instant and show Logged ("Already logged" for a repeat within 5 s).
     */
    fun chooseFeedType(type: FeedType?): Job = action {
        // The Feed type draft stays, so swiping back from Side or Bottle keeps its time; Save there uses it up.
        if (type == FeedType.BREAST) {
            val draft = _feedDraft.value ?: return@action
            _sideDraft.value = SideDraft(stamp = draft.stamp, zone = draft.zone)
            send(WearEvent.ShowSide)
            return@action
        }
        if (type == FeedType.BOTTLE) {
            val draft = _feedDraft.value ?: return@action
            // Taken once: the amount chips never change under a finger.
            val last = lastBottleAmountMl(log.value?.entries.orEmpty(), clock.now(), QUICK_DAYS)
            _bottleDraft.value = BottleDraft(stamp = draft.stamp, zone = draft.zone, lastAmountMl = last)
            send(WearEvent.ShowBottle)
            return@action
        }
        // A second tap that reached the leaving screen finds the draft used and does nothing.
        val draft = _feedDraft.consume() ?: return@action
        restoreOnFailure(_feedDraft, draft) {
            val result = repository.logFeedChecked(type, draft.stamp)
            showLogReceipt(ReceiptKind.FEED_LOGGED, result)
        }
    }

    // ---- Breast side -------------------------------------------------------------------------

    /** Ticks or unticks [breast] on the Side screen; the tick order is the order recorded (re-ticking appends). */
    fun tickSide(breast: Breast) {
        _sideDraft.update { it?.copy(ticks = BreastSide.toggle(it.ticks, breast)) }
    }

    /**
     * Save on the Side screen: logs the breast feed at the time of the Feed tap with the ticked order (no side when
     * nothing is ticked) and shows Logged ("Already logged" for a repeat within 5 s).
     */
    fun saveBreastfeed(): Job = action {
        // Consumed: a second tap on the leaving Side screen must not log the feed again.
        val draft = _sideDraft.consume() ?: return@action
        restoreOnFailure(_sideDraft, draft) {
            val result = repository.logFeedChecked(FeedType.BREAST, draft.stamp, draft.side)
            _feedDraft.value = null
            showLogReceipt(ReceiptKind.FEED_LOGGED, result)
        }
    }

    // ---- Bottle ------------------------------------------------------------------------------

    /** Milk or Formula tapped on Bottle, or null to clear the one chosen (ignored within [CLEAR_GUARD_MILLIS] of choosing it). */
    fun chooseBottleContents(contents: BottleContents?) {
        _bottleDraft.choose(Choice.CONTENTS, contents, { it.contents }) { draft, value -> draft.copy(contents = value) }
    }

    /** An amount chip on Bottle, or null to clear it (guarded as [chooseBottleContents]); the stepper then shows it. */
    fun chooseBottleAmount(amountMl: Int?) {
        _bottleDraft.choose(Choice.AMOUNT, amountMl?.takeIf { it > 0 }, { it.amountMl }) { draft, value -> draft.copy(amountMl = value) }
    }

    /**
     * The amount stepper's − / + on Bottle: the amount in millilitres, or null for none. A tick per change, nothing
     * when it stays the same (at either end of the scale). Not guarded: a step never clears a choice by accident.
     */
    fun setBottleAmount(amountMl: Int?) {
        val before = _bottleDraft.value ?: return
        val after = amountMl?.takeIf { it > 0 }
        if (before.amountMl == after) return
        _bottleDraft.value = before.copy(amountMl = after)
        haptic(XHaptic.SEGMENT_TICK)
    }

    /**
     * Save on the Bottle screen: logs the bottle feed at its time with the contents and amount picked (each optional)
     * and shows Logged ("Already logged" for a repeat within 5 s).
     */
    fun saveBottle(): Job = action {
        // Consumed: a second tap on the leaving Bottle screen must not log the feed again.
        val draft = _bottleDraft.consume() ?: return@action
        restoreOnFailure(_bottleDraft, draft) {
            val result = repository.logFeedChecked(FeedType.BOTTLE, draft.stamp, amountMl = draft.amountMl, contents = draft.contents)
            _feedDraft.value = null
            showLogReceipt(ReceiptKind.FEED_LOGGED, result)
        }
    }

    // ---- Sleep -------------------------------------------------------------------------------

    /**
     * The sleep toggle (the pinned EdgeButton). Start sleep stays on Main;
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
        choiceAt.clear()
    }

    fun setPee(on: Boolean) = _diaperDraft.update { it?.copy(pee = on) }

    /**
     * Poop on or off. Turning it off keeps its colour and texture (hidden), so ticking it again shows them again;
     * Save only writes them with Poop on.
     */
    fun setPoop(on: Boolean) = _diaperDraft.update { it?.copy(poop = on) }

    /** A colour chip, or null to clear the one chosen (guarded as [chooseBottleContents]). */
    fun choosePoopColor(color: PoopColor?) {
        _diaperDraft.choose(Choice.COLOUR, color, { it.color }) { draft, value -> draft.copy(color = value) }
    }

    /** A texture chip, or null to clear the one chosen (guarded as [chooseBottleContents]). */
    fun choosePoopTexture(texture: PoopTexture?) {
        _diaperDraft.choose(Choice.TEXTURE, texture, { it.texture }) { draft, value -> draft.copy(texture = value) }
    }

    /**
     * Save: logs the diaper at the time of the Diaper tap and shows Logged. With both checkboxes off it is a plain change; colour and
     * texture only go with Poop on.
     */
    fun saveDiaper(): Job = action {
        // A second tap that reached the leaving screen finds the draft used and does nothing.
        val draft = _diaperDraft.consume() ?: return@action
        restoreOnFailure(_diaperDraft, draft) {
            // Details kept while Poop was off are not saved.
            val result = repository.logDiaperChecked(draft.stamp, draft.pee, draft.poop, draft.color.takeIf { draft.poop }, draft.texture.takeIf { draft.poop })
            showLogReceipt(ReceiptKind.DIAPER_LOGGED, result)
        }
    }

    // ---- Time --------------------------------------------------------------------------------

    /**
     * The time on Logged was tapped, or the crown turned: opens the Time screen at the logged time (a feed's or
     * diaper's time, a sleep's wake time). It can move up to now, and a wake time no earlier than a minute after the
     * sleep began. Returns false when the receipt has no time to change ("Sleep cancelled").
     */
    fun beginReceiptTime(): Boolean {
        val receipt = _receipt.value ?: return false
        val entry = receipt.entry
        val (title, base) = when (receipt.kind) {
            ReceiptKind.FEED_LOGGED -> TimeTitle.FEED to entry.startMillis
            ReceiptKind.DIAPER_LOGGED -> TimeTitle.DIAPER to entry.startMillis
            ReceiptKind.WOKE_UP -> TimeTitle.WOKE_UP to (entry.endMillis ?: return false)
            ReceiptKind.SLEEP_CANCELLED -> return false
        }
        val now = clock.now()
        val earliest = if (receipt.kind == ReceiptKind.WOKE_UP) entry.startMillis + MINUTE else base - MAX_SHIFT_MILLIS
        _timeDraft.value = TimeDraft(
            title = title,
            base = base,
            zone = receipt.zone,
            minOffset = ceilMinutes(earliest - base).coerceIn(-MAX_SHIFT_MINUTES, 0),
            maxOffset = Math.floorDiv(now - base, MINUTE).toInt().coerceIn(0, MAX_SHIFT_MINUTES),
        )
        return true
    }

    /** The crown ([minutes] ±1 a step) or a ±5m button on the Time screen; a tick per change, nothing past a limit. */
    fun shiftTime(minutes: Int) {
        val before = _timeDraft.value ?: return
        val after = before.shiftedBy(minutes)
        if (after == before) return
        _timeDraft.value = after
        haptic(XHaptic.SEGMENT_TICK)
    }

    /**
     * Done on the Time screen: the entry on Logged is moved to the new time (Undo still removes the feed or diaper, or
     * resumes the sleep) and Logged shows it with a fresh window. Then back to Logged.
     */
    fun saveTime(): Job = action {
        // Consumed: a second tap on the leaving screen does nothing.
        val draft = _timeDraft.consume() ?: return@action
        restoreOnFailure(_timeDraft, draft) {
            val receipt = _receipt.value ?: return@restoreOnFailure
            if (draft.offsetMinutes != 0) {
                val entry = receipt.entry
                val moved = if (draft.title == TimeTitle.WOKE_UP) entry.copy(endMillis = draft.at) else entry.copy(startMillis = draft.at)
                val updated = repository.update(moved)
                moveWake(receipt.woke, draft.at)
                _receipt.value = receipt.copy(entry = updated, duplicate = false, serial = ++receiptSerial)
            }
        }
        haptic(XHaptic.CONFIRM)
        send(WearEvent.CloseTime)
    }

    // ---- Logged / Undo -----------------------------------------------------------------------

    /**
     * Opens Logged for an existing entry, e.g. from the Tile's undo path ([fromTile]: Undo is active at once).
     * Instants (feeds, breastfeeds included, and diapers) undo by deletion; an ended sleep by restoring it as running,
     * and so does a sleep discarded as a mis-tap ("Sleep cancelled" on the tile, DESIGN_SPEC §4.4: with Undo on phone
     * and watch).
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
            !entry.isInterval -> ReceiptKind.FEED_LOGGED to Undo.Delete(entry.id)
            else -> return@action
        }
        _receipt.value = Receipt(kind, entry, undo, zone(), fromTile = fromTile, serial = ++receiptSerial)
        send(WearEvent.ShowLogged)
    }

    /** A sleep ended under a minute (soft-deleted): "Sleep cancelled", Undo resumes it. */
    private fun openCancelledReceipt(entry: Entry, fromTile: Boolean) {
        if (!entry.isSleep || entry.endMillis == null) return
        val running = entry.copy(deleted = false, endMillis = null)
        _receipt.value = Receipt(ReceiptKind.SLEEP_CANCELLED, entry, Undo.Restore(running), zone(), fromTile = fromTile, serial = ++receiptSerial)
        send(WearEvent.ShowLogged)
    }

    /** Undo on Logged: reverts the receipt, then "Undone". */
    fun undo(): Job = action {
        val receipt = _receipt.value ?: return@action
        try {
            // The sleep a feed or diaper ended runs again first: if another sleep started meanwhile, nothing changes.
            receipt.woke?.let { repository.restore(it.previous) }
            when (val undo = receipt.undo) {
                is Undo.Delete -> repository.delete(undo.id)
                is Undo.Restore -> repository.restore(undo.previous)
            }
        } catch (e: AlreadyRunningException) {
            // Another sleep started meanwhile (e.g. on the phone): the old one cannot run again.
            Log.i(TAG, "Undo blocked by a running entry", e)
            _receipt.value = null
            haptic(XHaptic.REJECT)
            send(WearEvent.BackToMain)
            return@action
        }
        _receipt.value = null
        // Undo is deliberate: the reverted toggle may be toggled again at once.
        lastSleepToggleAt = NEVER
        haptic(XHaptic.REJECT)
        send(WearEvent.ShowUndone)
    }

    // ---- Babies ------------------------------------------------------------------------------

    /**
     * The baby picker's choice: makes [id] the watch's current baby (on this watch only: never synced) and returns to
     * Main with a confirmation (BABIES_AND_BACKUP §2.5). What was open belongs to the previous baby, so a switch drops
     * the drafts and the Logged receipt and its Undo, and resets the 2 s double-toggle guard: the
     * other baby's sleep toggle must work at once.
     */
    fun selectBaby(id: String): Job = action {
        val before = settings.current().selectedBaby.id
        val after = settings.selectBaby(id).selectedBaby.id
        if (after != before) {
            _feedDraft.value = null
            _sideDraft.value = null
            _bottleDraft.value = null
            _diaperDraft.value = null
            _timeDraft.value = null
            _receipt.value = null
            lastSleepToggleAt = NEVER
            clearGuard()
        }
        haptic(XHaptic.CONFIRM)
        send(WearEvent.BackToMain)
    }

    // ---- Ticks -------------------------------------------------------------------------------

    /** Re-reads "now" and the time zone (the ambient minute tick, returning to the foreground, a zone change). */
    fun refresh() = refreshes.update { it + 1 }

    // ---- Internals ---------------------------------------------------------------------------

    private suspend fun currentSummary(): Summary = summarize(repository.observeEntries().first(), clock.now(), zone())

    /**
     * Sets a draft's optional [choice] to [value]. A null (the selected option tapped again) clears it, unless it was
     * chosen less than [CLEAR_GUARD_MILLIS] ago: the second tap of a double tap leaves it selected, with no haptic.
     * A tick on a change only.
     */
    private inline fun <T : Any, V : Any> MutableStateFlow<T?>.choose(choice: Choice, value: V?, current: (T) -> V?, with: (T, V?) -> T) {
        val before = this.value ?: return
        val now = clock.now()
        if (value == null && now - (choiceAt[choice] ?: NEVER) < CLEAR_GUARD_MILLIS) return
        if (value != null) choiceAt[choice] = now
        if (current(before) == value) return
        this.value = with(before, value)
        haptic(XHaptic.SEGMENT_TICK)
    }

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

    /**
     * Logged for a feed or diaper. A new log during a sleep first ends it at the log's time (DESIGN_SPEC §4.4), and
     * Logged says so ("Woke up too"); a repeat ("Already logged") changes nothing.
     */
    private suspend fun showLogReceipt(kind: ReceiptKind, result: LogResult) {
        val woke = if (result is LogResult.Logged) wake(result.entry) else null
        showReceipt(kind, result.entry, Undo.Delete(result.entry.id), duplicate = result is LogResult.Duplicate, woke = woke)
    }

    /** Ends the running sleep [logged] wakes the baby from; a failure there leaves the log as it is. */
    private suspend fun wake(logged: Entry): Woke? {
        val result = try {
            repository.wakeFor(logged)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Ending the sleep for a log failed", e)
            null
        }
        return when (result) {
            is EndResult.Ended -> Woke(result.previous)
            is EndResult.Discarded -> Woke(result.previous, cancelled = true)
            is EndResult.NotRunning, null -> null
        }
    }

    /** A feed or diaper that ended a sleep moved to [at] on the Time screen: the sleep now ends there too, where it can. */
    private suspend fun moveWake(woke: Woke?, at: Long) {
        if (woke == null || woke.cancelled) return
        val sleep = repository.get(woke.previous.id)?.takeUnless { it.deleted || it.endMillis == null } ?: return
        if (at - sleep.startMillis < EntryRules.MIS_TAP_MILLIS) return
        try {
            repository.update(sleep.copy(endMillis = at))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Moving the wake-up with the log failed", e)
        }
    }

    private fun showReceipt(
        kind: ReceiptKind,
        entry: Entry,
        undo: Undo,
        duplicate: Boolean = false,
        haptic: XHaptic? = null,
        woke: Woke? = null,
    ) {
        _receipt.value = Receipt(kind, entry, undo, zone(), duplicate = duplicate, serial = ++receiptSerial, woke = woke)
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
                MainViewModel(Core.repository, Core.settings, Core.sync, Core.clock, ticks = ticks)
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
private const val MAX_SHIFT_MILLIS = MAX_SHIFT_MINUTES * MINUTE

/** [millis] in whole minutes, rounded up (towards later). */
private fun ceilMinutes(millis: Long): Int = (-Math.floorDiv(-millis, MINUTE)).toInt()

/** The optional choices the clear guard covers. */
private enum class Choice { CONTENTS, AMOUNT, COLOUR, TEXTURE }

/** The babies with the current one ([Settings.selectedBabyId]) and that baby's entries. */
private class BabyLog(val babies: Settings, val entries: List<Entry>)
