package com.ffmathy.pooparazzi.phone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.AlreadyRunningException
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/** Haptics Home asks the screen to play (DESIGN_SPEC §5.11). */
enum class HomeHaptic { CONFIRM, TOGGLE_ON, TOGGLE_OFF, REJECT, SEGMENT_TICK }

/**
 * Home's state machine (DESIGN_SPEC §3.3.1, §4.4; V1_ADDITIONS §2.1, §3.1): logging, the sheets, the receipt sheet
 * with its offsets and variants, Adjust start, the notices, the 2 s double-toggle guard, the 60 s discard, dedupe and the input cooldowns.
 *
 * Writes go through [repository] (which enforces the entry invariants); everything shown is derived from its
 * entries by [HomeStateBuilder]. Actions run one at a time, in tap order.
 *
 * @param elapsedRealtime monotonic milliseconds for the guard and cooldown windows (virtual time in tests).
 */
class HomeViewModel(
    private val repository: EntryRepository,
    private val settings: SettingsRepository,
    private val sync: EntrySync,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val timings: HomeTimings = HomeTimings(),
    private val elapsedRealtime: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : ViewModel(), HomeActions {

    private val now = MutableStateFlow(clock.now())
    private val card = MutableStateFlow<CardModel?>(null)
    private val sheet = MutableStateFlow<SheetModel?>(null)
    private val haptics = Channel<HomeHaptic>(Channel.BUFFERED)
    private val timerStarts = Channel<Unit>(Channel.CONFLATED)

    /** Haptics to play, one per parent-triggered action. */
    val effects: Flow<HomeHaptic> = haptics.receiveAsFlow()

    /**
     * A sleep was started here: the moment to ask for the notification permission in context, so the running sleep can
     * show its ongoing notification (DESIGN_SPEC §3.3.1, §10.5).
     */
    val timerStarted: Flow<Unit> = timerStarts.receiveAsFlow()

    /** Null until the entries and settings have loaded, so Home never flashes an empty "No feeds yet" state. */
    val state: StateFlow<HomeUiState?> = combine(
        repository.observeEntries(),
        settings.settings,
        sync.status,
        now,
        combine(card, sheet) { c, s -> c to s },
    ) { entries, s, syncStatus, _, (c, sh) ->
        // [now] only re-runs this each minute; the time itself is read fresh, so an entry written elsewhere within the
        // current minute (the watch, a widget, the lock-screen notification) shows at once instead of at the next tick.
        HomeStateBuilder.build(entries, s.babyName, syncStatus, clock.now(), zone, c, sh)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val actions = Mutex()
    private var nextKey = 0L
    private var cardJob: Job? = null
    private var messageJob: Job? = null
    private var lastToggleAt: Long? = null
    private var homeReadyAt = Long.MIN_VALUE
    private var cooldownPending = false

    init {
        // "Now" steps once per minute, just after the boundary (nothing ticks faster, DESIGN_SPEC §2.1).
        if (clock !is FixedClock) {
            viewModelScope.launch {
                while (true) {
                    val t = clock.now()
                    now.value = t
                    delay(MINUTE_MILLIS - Math.floorMod(t, MINUTE_MILLIS) + 50L)
                }
            }
        }
    }

    // ---- Top bar ------------------------------------------------------------------------------

    override fun onRetrySync() {
        viewModelScope.launch { sync.retry() }
    }

    // ---- Big buttons --------------------------------------------------------------------------

    override fun onFeed() = act {
        if (homeBlocked()) return@act
        sheet.value = SheetModel.Feed(clock.now())
    }

    override fun onDiaper() = act {
        if (homeBlocked()) return@act
        sheet.value = SheetModel.Diaper(stamp = clock.now())
    }

    override fun onSleepToggle(shownRunning: Boolean) = act {
        if (homeBlocked()) return@act
        val summary = summary()
        val running = summary.runningSleep
        val tapAt = elapsedRealtime()
        val last = lastToggleAt
        val guarded = last != null && tapAt - last < timings.toggleGuardMillis
        // The sleep was started or ended elsewhere (the watch, a widget, the notification) after the button was drawn:
        // the parent asked for what has already happened, so the tap is idempotent (DESIGN_SPEC §4.4) and never undoes
        // the other device's toggle.
        val stale = (running != null) != shownRunning
        if (guarded || stale) {
            if (running != null) {
                showGuard(GuardKind.ASLEEP, running.startMillis)
            } else {
                showGuard(GuardKind.AWAKE, summary.lastSleepEnd ?: clock.now())
            }
            haptic(HomeHaptic.REJECT)
            return@act
        }
        lastToggleAt = tapAt
        if (running == null) startSleep() else endSleep(running, clock.now())
    }

    private suspend fun startSleep() {
        attempt { repository.startSleep(clock.now()).getOrThrow() }
            .onSuccess { sleep ->
                openReceipt(ReceiptSpec.SleepStarted(sleep.id), baseTime = sleep.startMillis, undo = Undo.Delete(sleep.id))
                haptic(HomeHaptic.TOGGLE_ON)
                timerStarts.trySend(Unit)
            }
            .onFailure { e ->
                if (e is AlreadyRunningException) showGuard(GuardKind.ASLEEP, e.running.startMillis)
                haptic(HomeHaptic.REJECT)
            }
    }

    private suspend fun endSleep(sleep: Entry, at: Long) {
        attempt { repository.endSleep(sleep.id, at) }
            .onSuccess { result ->
                when (result) {
                    is EndResult.Ended -> {
                        openReceipt(ReceiptSpec.WokeUp(sleep.id), baseTime = result.entry.endMillis ?: at, undo = Undo.Restore(result.previous))
                        haptic(HomeHaptic.TOGGLE_OFF)
                    }
                    is EndResult.Discarded -> {
                        openReceipt(ReceiptSpec.SleepCancelled, baseTime = at, undo = Undo.Restore(result.previous))
                        haptic(HomeHaptic.TOGGLE_OFF)
                    }
                    is EndResult.NotRunning -> {
                        showGuard(GuardKind.AWAKE, result.current?.endMillis ?: clock.now())
                        haptic(HomeHaptic.REJECT)
                    }
                }
            }
            .onFailure { haptic(HomeHaptic.REJECT) }
    }

    // ---- Lamp ---------------------------------------------------------------------------------

    override fun onAdjustStart() = act {
        if (homeBlocked()) return@act
        val target = summary().runningSleep ?: return@act
        sheet.value = SheetModel.Adjust(stamp = target.startMillis, entryId = target.id)
    }

    // ---- Receipt sheet ------------------------------------------------------------------------

    override fun onEndSleep() = act {
        val receipt = sheet.value as? SheetModel.Receipt ?: return@act
        if (!receipt.spec.canEndSleep) return@act
        val (id, _) = receipt.spec.subject ?: return@act
        val logged = repository.get(id)?.takeUnless { it.deleted } ?: return@act
        val sleep = summary().runningSleep ?: return@act
        if (logged.startMillis < sleep.startMillis) {
            haptic(HomeHaptic.REJECT)
            return@act
        }
        lastToggleAt = elapsedRealtime()
        // "End sleep" ends the running sleep at the logged event's time; the receipt becomes Woke up for that sleep.
        endSleep(sleep, logged.startMillis)
    }

    override fun onUndo() = act {
        val receipt = sheet.value as? SheetModel.Receipt ?: return@act
        val undo = receipt.undo ?: return@act
        attempt {
            when (undo) {
                is Undo.Delete -> repository.delete(undo.entryId)
                is Undo.Restore -> repository.restore(undo.previous)
            }
        }
            .onSuccess {
                lastToggleAt = null
                closeSheet(committed = false)
                showCard(CardSpec.Undone)
                haptic(HomeHaptic.REJECT)
            }
            .onFailure { e ->
                if (e is AlreadyRunningException) showGuard(GuardKind.ASLEEP, e.running.startMillis)
                haptic(HomeHaptic.REJECT)
            }
    }

    override fun onReceiptDone() = act {
        if (sheet.value !is SheetModel.Receipt) return@act
        closeSheet(committed = true)
    }

    /** A delete in the edit sheet over Home: the "… deleted" receipt, whose Undo writes [previous] back. */
    fun onEntryDeleted(previous: Entry) = act {
        openReceipt(ReceiptSpec.Deleted(previous), baseTime = clock.now(), undo = Undo.Restore(previous))
    }

    // ---- Sheets -------------------------------------------------------------------------------

    override fun onSheetOffset(minutes: Int) = act {
        when (val current = sheet.value) {
            null -> Unit
            is SheetModel.Receipt -> receiptOffset(current, minutes)
            is SheetModel.Adjust -> adjustOffset(current, minutes)
            is SheetModel.Feed, is SheetModel.Diaper -> {
                if (current.offsetMinutes == minutes) return@act
                sheet.value = when (current) {
                    is SheetModel.Feed -> current.copy(offsetMinutes = minutes)
                    else -> (current as SheetModel.Diaper).copy(offsetMinutes = minutes)
                }
                haptic(HomeHaptic.SEGMENT_TICK)
            }
        }
    }

    /**
     * A receipt's offset applies at once, relative to its base time and never cumulative; re-tapping the selected chip
     * returns it to 0.
     */
    private suspend fun receiptOffset(receipt: SheetModel.Receipt, minutes: Int) {
        val (id, field) = receipt.spec.subject ?: return
        val entry = repository.get(id)?.takeUnless { it.deleted } ?: return
        val next = if (receipt.offsetMinutes == minutes) 0 else minutes
        if (!HomeStateBuilder.offsetAllowed(entry, field, receipt.stamp, next, clock.now())) {
            haptic(HomeHaptic.REJECT)
            return
        }
        attempt { repository.update(entry.moved(field, receipt.stamp + next * MINUTE_MILLIS)) }
            .onSuccess {
                sheet.update { if (it is SheetModel.Receipt && it.key == receipt.key) it.copy(offsetMinutes = next, dedupe = null) else it }
                haptic(HomeHaptic.SEGMENT_TICK)
            }
            .onFailure { haptic(HomeHaptic.REJECT) }
    }

    /** Adjust start's offset waits for Save; re-tapping the selected chip returns it to 0. */
    private suspend fun adjustOffset(adjust: SheetModel.Adjust, minutes: Int) {
        val entry = repository.get(adjust.entryId)?.takeUnless { it.deleted } ?: return
        val next = if (adjust.offsetMinutes == minutes) 0 else minutes
        if (!HomeStateBuilder.offsetAllowed(entry, MoveField.START, adjust.stamp, next, clock.now())) {
            haptic(HomeHaptic.REJECT)
            return
        }
        sheet.value = adjust.copy(offsetMinutes = next)
        haptic(HomeHaptic.SEGMENT_TICK)
    }

    override fun onAdjustSave() = act {
        val adjust = sheet.value as? SheetModel.Adjust ?: return@act
        closeSheet(committed = true)
        if (saveAdjust(adjust)) haptic(HomeHaptic.CONFIRM) else haptic(HomeHaptic.REJECT)
    }

    /** Moves the running sleep's start to the Adjust start sheet's time; returns whether it now has it. */
    private suspend fun saveAdjust(adjust: SheetModel.Adjust): Boolean {
        val entry = repository.get(adjust.entryId)?.takeUnless { it.deleted } ?: return false
        if (!HomeStateBuilder.offsetAllowed(entry, MoveField.START, adjust.stamp, adjust.offsetMinutes, clock.now())) return false
        val moved = entry.moved(MoveField.START, adjust.stamp + adjust.offsetMinutes * MINUTE_MILLIS)
        return moved == entry || attempt { repository.update(moved) }.isSuccess
    }

    override fun onSheetEdit(entryId: String) = onSheetEdit(entryId) {}

    /**
     * "Edit" in a receipt or Adjust start (which saves its offset first). [openEditor] runs once the sheet has closed
     * and any offset is saved, so the editor shows it.
     */
    fun onSheetEdit(entryId: String, openEditor: () -> Unit) = act {
        when (val current = sheet.value) {
            is SheetModel.Adjust -> {
                if (current.entryId != entryId) return@act
                closeSheet(committed = false)
                if (!saveAdjust(current)) haptic(HomeHaptic.REJECT)
            }
            is SheetModel.Receipt -> {
                if (current.spec.subject?.first != entryId) return@act
                closeSheet(committed = false)
            }
            else -> return@act
        }
        openEditor()
    }

    override fun onFeedType(type: FeedType?) = act {
        val feedSheet = sheet.value as? SheetModel.Feed ?: return@act
        if (type == FeedType.BREAST) {
            // Breast is an instant too, but first offers the optional side order: the sheet stays open.
            if (!feedSheet.breast) {
                sheet.value = feedSheet.copy(breast = true)
                haptic(HomeHaptic.SEGMENT_TICK)
            }
            return@act
        }
        logFeed(feedSheet, type, side = null)
    }

    override fun onSideTick(breast: Breast) = act {
        val feedSheet = sheet.value as? SheetModel.Feed ?: return@act
        if (!feedSheet.breast) return@act
        sheet.value = feedSheet.copy(ticks = BreastSide.toggle(feedSheet.ticks, breast))
    }

    override fun onLogBreastfeed() = act {
        val feedSheet = sheet.value as? SheetModel.Feed ?: return@act
        if (!feedSheet.breast) return@act
        logFeed(feedSheet, FeedType.BREAST, BreastSide.fromOrder(feedSheet.ticks))
    }

    /** Logs a feed instant of [type] at the sheet's stamp + offset, closing the sheet. */
    private suspend fun logFeed(feedSheet: SheetModel.Feed, type: FeedType?, side: BreastSide?) {
        val at = feedSheet.stamp + feedSheet.offsetMinutes * MINUTE_MILLIS
        closeSheet(committed = true)
        val duringSleep = summary().runningSleep != null
        attempt { repository.logFeedChecked(type, at, side) }
            .onSuccess { result ->
                when (result) {
                    is LogResult.Logged -> {
                        openReceipt(
                            ReceiptSpec.FeedLogged(result.entry.id, type, result.entry.breastSide),
                            baseTime = feedSheet.stamp,
                            offsetMinutes = feedSheet.offsetMinutes,
                            duringSleep = duringSleep,
                            undo = Undo.Delete(result.entry.id),
                        )
                        haptic(HomeHaptic.CONFIRM)
                    }
                    is LogResult.Duplicate -> showDuplicate(result.entry, duringSleep)
                }
            }
            .onFailure { haptic(HomeHaptic.REJECT) }
    }

    override fun onDiaperPee(checked: Boolean) = updateDiaper { it.copy(pee = checked) }

    override fun onDiaperPoop(checked: Boolean) = updateDiaper {
        // Colour and texture belong to poop only (V1_ADDITIONS §1).
        if (checked) it.copy(poop = true) else it.copy(poop = false, color = null, texture = null)
    }

    override fun onPoopColor(color: PoopColor?) = updateDiaper { if (it.poop) it.copy(color = color) else it }

    override fun onPoopTexture(texture: PoopTexture?) = updateDiaper { if (it.poop) it.copy(texture = texture) else it }

    private fun updateDiaper(transform: (SheetModel.Diaper) -> SheetModel.Diaper) = act {
        val diaper = sheet.value as? SheetModel.Diaper ?: return@act
        sheet.value = transform(diaper)
    }

    override fun onDiaperSave() = act {
        val diaper = sheet.value as? SheetModel.Diaper ?: return@act
        val at = diaper.stamp + diaper.offsetMinutes * MINUTE_MILLIS
        closeSheet(committed = true)
        val duringSleep = summary().runningSleep != null
        attempt {
            repository.logDiaperChecked(
                at = at,
                pee = diaper.pee,
                poop = diaper.poop,
                color = diaper.color.takeIf { diaper.poop },
                texture = diaper.texture.takeIf { diaper.poop },
            )
        }
            .onSuccess { result ->
                when (result) {
                    is LogResult.Logged -> {
                        val kind = result.entry.diaperKind ?: DiaperKind.PLAIN
                        openReceipt(
                            ReceiptSpec.DiaperLogged(result.entry.id, kind),
                            baseTime = diaper.stamp,
                            offsetMinutes = diaper.offsetMinutes,
                            duringSleep = duringSleep,
                            undo = Undo.Delete(result.entry.id),
                        )
                        haptic(HomeHaptic.CONFIRM)
                    }
                    is LogResult.Duplicate -> showDuplicate(result.entry, duringSleep)
                }
            }
            .onFailure { haptic(HomeHaptic.REJECT) }
    }

    override fun onSheetDismiss() = act {
        sheet.value = null
        cooldownPending = false
    }

    override fun onSheetClosed() = act {
        if (cooldownPending) {
            cooldownPending = false
            homeReadyAt = elapsedRealtime() + timings.homeCooldownMillis
        }
    }

    // ---- Deep links (widgets, notifications) --------------------------------------------------

    /**
     * A widget or notification opened Home ([HomeLaunch]): the sheet it asks for, replacing any open one, or the
     * receipt for a widget log. Stale requests (the log already undone) show nothing.
     */
    fun onLaunch(launch: HomeLaunch) = act {
        when (launch) {
            // History is opened by the navigation (PooparazziNavHost); Home has nothing to show for it.
            HomeLaunch.Home, HomeLaunch.History -> Unit
            HomeLaunch.Feed -> sheet.value = SheetModel.Feed(clock.now())
            HomeLaunch.Diaper -> sheet.value = SheetModel.Diaper(stamp = clock.now())
            is HomeLaunch.Receipt -> showWidgetReceipt(launch)
        }
    }

    /** The receipt for a log made on the widget, as if it had been made here (DESIGN_SPEC §3.3.6). */
    private suspend fun showWidgetReceipt(receipt: HomeLaunch.Receipt) {
        val entry = repository.get(receipt.entryId) ?: return
        when (receipt.kind) {
            ReceiptKind.LOGGED -> {
                if (entry.deleted) return
                val spec = when (entry.kind) {
                    EntryKind.DIAPER -> ReceiptSpec.DiaperLogged(entry.id, entry.diaperKind ?: DiaperKind.PLAIN)
                    EntryKind.FEED -> ReceiptSpec.FeedLogged(entry.id, entry.feedType, entry.breastSide)
                    EntryKind.SLEEP -> ReceiptSpec.SleepStarted(entry.id)
                }
                val sleep = summary().runningSleep
                val duringSleep = sleep != null && entry.kind != EntryKind.SLEEP && entry.startMillis >= sleep.startMillis
                openReceipt(spec, baseTime = entry.startMillis, duringSleep = duringSleep, undo = Undo.Delete(entry.id))
            }
            ReceiptKind.SLEEP_STARTED -> if (entry.isSleep && entry.isRunning) {
                openReceipt(ReceiptSpec.SleepStarted(entry.id), baseTime = entry.startMillis, undo = Undo.Delete(entry.id))
            }
            ReceiptKind.WOKE_UP -> {
                val end = entry.endMillis
                if (entry.isSleep && !entry.deleted && end != null) {
                    openReceipt(ReceiptSpec.WokeUp(entry.id), baseTime = end, undo = Undo.Restore(entry.copy(endMillis = null)))
                }
            }
            ReceiptKind.SLEEP_CANCELLED -> if (entry.isSleep && entry.deleted) {
                openReceipt(
                    ReceiptSpec.SleepCancelled,
                    baseTime = entry.endMillis ?: clock.now(),
                    undo = Undo.Restore(entry.copy(endMillis = null, deleted = false)),
                )
            }
        }
    }

    // ---- Receipt and notice machinery ---------------------------------------------------------

    /** Opens the receipt for a log (replacing any sheet), with no timeout. */
    private fun openReceipt(
        spec: ReceiptSpec,
        baseTime: Long,
        offsetMinutes: Int = 0,
        duringSleep: Boolean = false,
        undo: Undo? = null,
        dedupe: Entry? = null,
    ) {
        messageJob?.cancel()
        val receipt = SheetModel.Receipt(
            stamp = baseTime,
            offsetMinutes = offsetMinutes,
            key = ++nextKey,
            spec = spec,
            duringSleep = duringSleep,
            undo = undo,
            dedupe = dedupe,
        )
        sheet.value = receipt
        if (dedupe != null) {
            messageJob = viewModelScope.launch {
                delay(timings.messageMillis)
                sheet.update { if (it is SheetModel.Receipt && it.key == receipt.key) it.copy(dedupe = null) else it }
            }
        }
    }

    /** A 2 s notice in the whisper slot ("Undone", a guard). */
    private fun showCard(spec: CardSpec) {
        cardJob?.cancel()
        val model = CardModel(key = ++nextKey, spec = spec)
        card.value = model
        cardJob = viewModelScope.launch {
            delay(timings.noticeMillis)
            card.update { if (it?.key == model.key) null else it }
        }
    }

    /** "Already asleep since 14:32" / "Already awake since 14:40" in the whisper slot (DESIGN_SPEC §4.4). */
    private fun showGuard(kind: GuardKind, sinceMillis: Long) = showCard(CardSpec.Guard(kind, sinceMillis))

    /** Dedupe: the receipt for the existing entry with "Already logged at …" for 2 s; no new entry. */
    private fun showDuplicate(existing: Entry, duringSleep: Boolean) {
        val spec = when (existing.kind) {
            EntryKind.DIAPER -> ReceiptSpec.DiaperLogged(existing.id, existing.diaperKind ?: DiaperKind.PLAIN)
            else -> ReceiptSpec.FeedLogged(existing.id, existing.feedType, existing.breastSide)
        }
        openReceipt(spec, baseTime = existing.startMillis, duringSleep = duringSleep, undo = Undo.Delete(existing.id), dedupe = existing)
        haptic(HomeHaptic.REJECT)
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private fun Entry.moved(field: MoveField, target: Long): Entry = when (field) {
        MoveField.START -> copy(startMillis = target)
        MoveField.END -> copy(endMillis = target)
    }

    private fun closeSheet(committed: Boolean) {
        sheet.value = null
        if (committed) {
            cooldownPending = true
            // Upper bound in case the closing animation never reports back (e.g. the activity went away).
            homeReadyAt = elapsedRealtime() + SHEET_CLOSE_FALLBACK_MILLIS
        }
    }

    /** Feed, Diaper and the toggle ignore taps while a sheet is open and briefly after a committed one closed. */
    private fun homeBlocked(): Boolean = sheet.value != null || elapsedRealtime() < homeReadyAt

    private suspend fun summary(): Summary = summarize(repository.observeEntries().first(), clock.now(), zone)

    private fun haptic(type: HomeHaptic) {
        haptics.trySend(type)
    }

    /** Runs [block] after the actions before it, then refreshes "now" so the state reflects the write. */
    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            actions.withLock {
                now.value = clock.now()
                block()
                now.value = clock.now()
            }
        }
    }

    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        private const val SHEET_CLOSE_FALLBACK_MILLIS = 2_000L

        /** The production view model on [Core]'s graph. */
        fun factory(timings: HomeTimings = HomeTimings()): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(Core.repository, Core.settings, Core.sync, Core.clock, timings = timings) }
        }
    }
}
