package com.ffmathy.xaternity.phone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.data.Clock
import com.ffmathy.xaternity.core.data.EndResult
import com.ffmathy.xaternity.core.data.EntryRepository
import com.ffmathy.xaternity.core.data.FixedClock
import com.ffmathy.xaternity.core.data.LogResult
import com.ffmathy.xaternity.core.domain.Summary
import com.ffmathy.xaternity.core.domain.summarize
import com.ffmathy.xaternity.core.model.AlreadyRunningException
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import com.ffmathy.xaternity.core.model.diaperKind
import com.ffmathy.xaternity.core.model.isSleep
import com.ffmathy.xaternity.core.settings.SettingsRepository
import com.ffmathy.xaternity.core.sync.EntrySync
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
 * Home's state machine (DESIGN_SPEC §3.3.1, §4.4; V1_ADDITIONS §2.1, §3.1): logging, the sheets, the undo card with
 * its offsets and variants, the 2 s double-toggle guard, the 60 s discard, dedupe and the input cooldowns.
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

    /** Haptics to play, one per parent-triggered action. */
    val effects: Flow<HomeHaptic> = haptics.receiveAsFlow()

    /** Null until the entries and settings have loaded, so Home never flashes an empty "No feeds yet" state. */
    val state: StateFlow<HomeUiState?> = combine(
        repository.observeEntries(),
        settings.settings,
        sync.status,
        now,
        combine(card, sheet) { c, s -> c to s },
    ) { entries, s, syncStatus, now, (c, sh) ->
        HomeStateBuilder.build(entries, s.babyName, syncStatus, now, zone, c, sh, timings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val actions = Mutex()
    private var nextCardKey = 0L
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
        val stamp = clock.now()
        val running = summary().runningFeed
        sheet.value = if (running != null) SheetModel.Side(running.id, running.startMillis, stamp) else SheetModel.Feed(stamp)
    }

    override fun onDiaper() = act {
        if (homeBlocked()) return@act
        sheet.value = SheetModel.Diaper(stamp = clock.now())
    }

    override fun onSleepToggle() = act {
        if (homeBlocked()) return@act
        val summary = summary()
        val running = summary.runningSleep
        val tapAt = elapsedRealtime()
        val last = lastToggleAt
        if (last != null && tapAt - last < timings.toggleGuardMillis) {
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
                showCard(CardSpec.SleepStarted(sleep.id), baseTime = sleep.startMillis, undo = Undo.Delete(sleep.id))
                haptic(HomeHaptic.TOGGLE_ON)
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
                        showCard(CardSpec.WokeUp(sleep.id), baseTime = result.entry.endMillis ?: at, undo = Undo.Restore(result.previous))
                        haptic(HomeHaptic.TOGGLE_OFF)
                    }
                    is EndResult.Discarded -> {
                        showCard(CardSpec.SleepCancelled, baseTime = at, undo = Undo.Restore(result.previous))
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
        val summary = summary()
        val target = summary.runningFeed ?: summary.runningSleep ?: return@act
        showCard(CardSpec.AdjustStart(target.id, sleep = target.isSleep), baseTime = target.startMillis)
    }

    // ---- Undo card ----------------------------------------------------------------------------

    override fun onCardOffset(minutes: Int) = act {
        val model = card.value ?: return@act
        val (id, field) = model.spec.subject ?: return@act
        val entry = repository.get(id)?.takeUnless { it.deleted } ?: return@act
        val next = if (model.offsetMinutes == minutes) 0 else minutes
        if (!HomeStateBuilder.offsetAllowed(entry, field, model.baseTime, next, clock.now())) {
            haptic(HomeHaptic.REJECT)
            return@act
        }
        val target = model.baseTime + next * MINUTE_MILLIS
        val moved = when (field) {
            MoveField.START -> entry.copy(startMillis = target)
            MoveField.END -> entry.copy(endMillis = target)
        }
        attempt { repository.update(moved) }
            .onSuccess {
                restartWindow(model.key) { it.copy(offsetMinutes = next, message = null) }
                haptic(HomeHaptic.SEGMENT_TICK)
            }
            .onFailure { haptic(HomeHaptic.REJECT) }
    }

    override fun onCardEndSleep() = act {
        val model = card.value ?: return@act
        if (!model.spec.canEndSleep) return@act
        val (id, _) = model.spec.subject ?: return@act
        val logged = repository.get(id)?.takeUnless { it.deleted } ?: return@act
        val sleep = summary().runningSleep ?: return@act
        if (logged.startMillis < sleep.startMillis) {
            haptic(HomeHaptic.REJECT)
            return@act
        }
        lastToggleAt = elapsedRealtime()
        // "End sleep" ends the running sleep at the logged event's time; the card becomes Woke up for that sleep.
        endSleep(sleep, logged.startMillis)
    }

    override fun onUndo() = act {
        val model = card.value ?: return@act
        val undo = model.undo ?: return@act
        attempt {
            when (undo) {
                is Undo.Delete -> repository.delete(undo.entryId)
                is Undo.Restore -> repository.restore(undo.previous)
            }
        }
            .onSuccess {
                lastToggleAt = null
                showCard(CardSpec.Undone, baseTime = clock.now())
                haptic(HomeHaptic.REJECT)
            }
            .onFailure { e ->
                if (e is AlreadyRunningException) {
                    showGuard(if (e.running.isSleep) GuardKind.ASLEEP else GuardKind.FEEDING, e.running.startMillis)
                }
                haptic(HomeHaptic.REJECT)
            }
    }

    override fun onCardDone() = act {
        clearCard()
    }

    /** A delete in the edit sheet over Home: the "… deleted" card, whose Undo writes [previous] back. */
    fun onEntryDeleted(previous: Entry) = act {
        showCard(CardSpec.Deleted(previous), baseTime = clock.now(), undo = Undo.Restore(previous))
    }

    // ---- Sheets -------------------------------------------------------------------------------

    override fun onSheetOffset(minutes: Int) = act {
        val current = sheet.value ?: return@act
        if (current.offsetMinutes == minutes) return@act
        val next = when (current) {
            is SheetModel.Feed -> current.copy(offsetMinutes = minutes)
            is SheetModel.Diaper -> current.copy(offsetMinutes = minutes)
            is SheetModel.Side -> {
                if (!HomeStateBuilder.sideEndAllowed(current.startMillis, current.stamp, minutes)) return@act
                current.copy(offsetMinutes = minutes)
            }
        }
        sheet.value = next
        haptic(HomeHaptic.SEGMENT_TICK)
    }

    override fun onFeedType(type: FeedType) = act {
        val feedSheet = sheet.value as? SheetModel.Feed ?: return@act
        val at = feedSheet.stamp + feedSheet.offsetMinutes * MINUTE_MILLIS
        closeSheet(committed = true)
        val duringSleep = summary().runningSleep != null
        if (type == FeedType.BREAST) {
            attempt { repository.startBreastfeed(at).getOrThrow() }
                .onSuccess { feed ->
                    showCard(
                        CardSpec.FeedStarted(feed.id),
                        baseTime = feedSheet.stamp,
                        offsetMinutes = feedSheet.offsetMinutes,
                        duringSleep = duringSleep,
                        undo = Undo.Delete(feed.id),
                    )
                    haptic(HomeHaptic.CONFIRM)
                }
                .onFailure { e ->
                    if (e is AlreadyRunningException) showGuard(GuardKind.FEEDING, e.running.startMillis)
                    haptic(HomeHaptic.REJECT)
                }
        } else {
            attempt { repository.logFeedChecked(type, at) }
                .onSuccess { result ->
                    when (result) {
                        is LogResult.Logged -> {
                            showCard(
                                CardSpec.FeedLogged(result.entry.id, type),
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
    }

    override fun onSide(side: BreastSide) = act {
        val sideSheet = sheet.value as? SheetModel.Side ?: return@act
        val at = sideSheet.stamp + sideSheet.offsetMinutes * MINUTE_MILLIS
        if (at < sideSheet.startMillis) return@act
        closeSheet(committed = true)
        attempt { repository.endBreastfeed(sideSheet.feedId, at, side) }
            .onSuccess { result ->
                when (result) {
                    is EndResult.Ended -> {
                        showCard(
                            CardSpec.FeedEnded(result.entry.id, side),
                            baseTime = sideSheet.stamp,
                            offsetMinutes = sideSheet.offsetMinutes,
                            undo = Undo.Restore(result.previous),
                        )
                        haptic(HomeHaptic.CONFIRM)
                    }
                    is EndResult.Discarded -> {
                        showCard(CardSpec.FeedCancelled(side), baseTime = at, undo = Undo.Restore(result.previous))
                        haptic(HomeHaptic.CONFIRM)
                    }
                    is EndResult.NotRunning -> haptic(HomeHaptic.REJECT)
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
        if (!diaper.pee && !diaper.poop) return@act
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
                        val kind = result.entry.diaperKind ?: DiaperKind.PEE
                        showCard(
                            CardSpec.DiaperLogged(result.entry.id, kind),
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

    // ---- Card machinery -----------------------------------------------------------------------

    private fun showCard(
        spec: CardSpec,
        baseTime: Long,
        offsetMinutes: Int = 0,
        duringSleep: Boolean = false,
        undo: Undo? = null,
        message: CardMessage? = null,
    ) {
        messageJob?.cancel()
        val model = CardModel(
            key = ++nextCardKey,
            spec = spec,
            baseTime = baseTime,
            offsetMinutes = offsetMinutes,
            duringSleep = duringSleep,
            undo = undo,
            message = message,
        )
        card.value = model
        scheduleExpiry(model)
        if (message != null) scheduleMessageEnd(model.key, message)
    }

    private fun clearCard() {
        cardJob?.cancel()
        messageJob?.cancel()
        card.value = null
    }

    /** Restarts the 10 s window of card [key] (chip taps, dedupe) after applying [transform]. */
    private fun restartWindow(key: Long, transform: (CardModel) -> CardModel) {
        val current = card.value?.takeIf { it.key == key } ?: return
        val next = transform(current).copy(windowKey = current.windowKey + 1)
        card.value = next
        scheduleExpiry(next)
    }

    private fun scheduleExpiry(model: CardModel) {
        cardJob?.cancel()
        val window = if (model.spec is CardSpec.Undone) timings.undoneMillis else timings.undoWindowMillis
        cardJob = viewModelScope.launch {
            delay(window)
            card.update { if (it?.key == model.key) null else it }
        }
    }

    private fun scheduleMessageEnd(key: Long, message: CardMessage) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(timings.messageMillis)
            card.update { if (it?.key == key && it.message == message) it.copy(message = null) else it }
        }
    }

    /** The guard line on the current card, or on a notice card when nothing is showing (DESIGN_SPEC §4.4). */
    private fun showGuard(kind: GuardKind, sinceMillis: Long) {
        val message = CardMessage.Guard(kind, sinceMillis)
        val current = card.value
        if (current == null || current.spec is CardSpec.Undone) {
            showCard(CardSpec.Notice(kind), baseTime = sinceMillis, message = message)
        } else {
            card.value = current.copy(message = message)
            scheduleMessageEnd(current.key, message)
        }
    }

    /** Dedupe: the card for the existing entry (re-shown if replaced) with "Already logged at …"; the drain restarts. */
    private fun showDuplicate(existing: Entry, duringSleep: Boolean) {
        val message = CardMessage.Dedupe(existing)
        val current = card.value
        if (current != null && current.spec.subject?.first == existing.id) {
            restartWindow(current.key) { it.copy(message = message) }
            scheduleMessageEnd(current.key, message)
        } else {
            val spec = when (existing.kind) {
                EntryKind.DIAPER -> CardSpec.DiaperLogged(existing.id, existing.diaperKind ?: DiaperKind.PEE)
                else -> CardSpec.FeedLogged(existing.id, existing.feedType ?: FeedType.FORMULA)
            }
            showCard(spec, baseTime = existing.startMillis, duringSleep = duringSleep, undo = Undo.Delete(existing.id), message = message)
        }
        haptic(HomeHaptic.REJECT)
    }

    // ---- Helpers ------------------------------------------------------------------------------

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
