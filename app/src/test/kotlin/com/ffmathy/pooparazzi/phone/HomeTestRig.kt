package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.CoreGraph
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.data.PooparazziDatabase
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
import com.ffmathy.pooparazzi.phone.ui.home.HomeHaptic
import com.ffmathy.pooparazzi.phone.ui.home.HomeTimings
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.HomeUiState
import com.ffmathy.pooparazzi.phone.ui.home.HomeViewModel
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** Settings held in memory. */
class InMemorySettings(initial: Settings = SampleData.settings) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state
    override suspend fun setBabyName(name: String): Settings = update { it.withBabyName(it.selectedBaby.id, name) }
    override suspend fun renameBaby(id: String, name: String): Settings = update { it.withBabyName(id, name) }
    override suspend fun addBaby(): Baby {
        val baby = Baby(Baby.newId())
        val next = update { if (it.babies.size >= Settings.MAX_BABIES) it else it.withBabies(listOf(baby.id)) }
        return next.babies.firstOrNull { it.id == baby.id } ?: next.babies.last()
    }
    override suspend fun selectBaby(id: String): Settings = update { it.withSelectedBaby(id) }
    override suspend fun ensureBabies(ids: Collection<String>): Settings = update { it.withBabies(ids) }
    override suspend fun importBabies(babies: Collection<Baby>): Settings = update { it.withMergedBabies(babies) }
    override suspend fun setAppearance(appearance: Appearance): Settings = update { it.copy(appearance = appearance) }
    override suspend fun setLanguage(language: AppLanguage): Settings = update { it.copy(language = language) }
    override suspend fun applyRemote(remote: Settings): Boolean {
        state.value = remote.normalized().copy(selectedBabyId = state.value.selectedBabyId)
        return true
    }
    override suspend fun applyLegacyRemote(remote: Settings): Boolean {
        val next = state.value.withLegacyRemote(remote.normalized()) ?: return false
        state.value = next
        return true
    }

    private fun update(transform: (Settings) -> Settings): Settings = transform(state.value).also { state.value = it }
}

/**
 * Home's view model on a real core graph: an in-memory Room database driven by the test dispatcher (so writes, the
 * entry flow and the notice timers all run in virtual time), a [FixedClock] and [NoopEntrySync], installed with
 * [Core.install]. Monotonic time for the guards and cooldowns is the scheduler's virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeTestRig(val scope: TestScope, now: Long = SampleData.NOW_1432) {
    val clock = FixedClock(now)
    private val dispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
    private val database: PooparazziDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), PooparazziDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryCoroutineContext(dispatcher)
        .build()
    val settings = InMemorySettings()

    /** Writes and reads follow the rig's current baby, as in the app. */
    val repository = RoomEntryRepository(database, clock, selectedBaby = settings.settings.map { it.selectedBaby.id })

    init {
        Core.install(CoreGraph(clock, repository, settings, NoopEntrySync, CoroutineScope(SupervisorJob() + dispatcher)))
    }

    val viewModel = HomeViewModel(
        repository = Core.repository,
        settings = Core.settings,
        sync = Core.sync,
        clock = Core.clock,
        zone = SampleData.ZONE,
        timings = HomeTimings(),
        elapsedRealtime = { scope.testScheduler.currentTime },
    )

    val haptics = mutableListOf<HomeHaptic>()

    /** Keeps the state flow subscribed and records haptics. Call first in each test. */
    fun start() {
        scope.backgroundScope.launch { viewModel.state.collect { } }
        scope.backgroundScope.launch { viewModel.effects.collect { haptics += it } }
        scope.runCurrent()
    }

    /** Runs the queued work (without moving virtual time) and returns the state. */
    fun settle(): HomeUiState {
        scope.runCurrent()
        return checkNotNull(viewModel.state.value) { "Home has not loaded" }
    }

    /** Taps the sleep toggle in the form Home shows now ("Start sleep" or "Woke up"). */
    fun tapSleepToggle() = viewModel.onSleepToggle(shownRunning = settle().sleepRunning)

    /** Taps Feed. */
    fun tapFeed() = viewModel.onFeed()

    /** Taps Done on the open receipt and lets it finish closing (and Home's cooldown pass). */
    fun closeReceipt() {
        viewModel.onReceiptDone()
        settle()
        viewModel.onSheetClosed()
        scope.testScheduler.advanceTimeBy(700)
        settle()
    }

    suspend fun entries(): List<Entry> = repository.observeEntries().first()

    suspend fun seed(entries: List<Entry>) {
        entries.forEach { repository.add(it) }
        scope.runCurrent()
    }

    fun close() {
        Core.reset()
        database.close()
    }
}

/** The open receipt sheet. */
val HomeUiState.receipt: HomeSheetUi.Receipt get() = sheet as HomeSheetUi.Receipt
