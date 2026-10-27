package com.ffmathy.xaternity.phone

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.CoreGraph
import com.ffmathy.xaternity.core.data.FixedClock
import com.ffmathy.xaternity.core.data.RoomEntryRepository
import com.ffmathy.xaternity.core.data.XaternityDatabase
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.sync.NoopEntrySync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.coroutines.ContinuationInterceptor

/**
 * A real core graph for view-model tests: an in-memory Room database driven by the test dispatcher (writes and flows
 * run in virtual time), a [FixedClock], [InMemorySettings] and [NoopEntrySync], installed with [Core.install].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoreTestRig(val scope: TestScope, now: Long = SampleData.NOW_1432) {
    val clock = FixedClock(now)
    private val dispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
    private val database: XaternityDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), XaternityDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryCoroutineContext(dispatcher)
        .build()
    val repository = RoomEntryRepository(database, clock)
    val settings = InMemorySettings()

    init {
        Core.install(CoreGraph(clock, repository, settings, NoopEntrySync, CoroutineScope(SupervisorJob() + dispatcher)))
    }

    suspend fun seed(entries: List<Entry>) {
        entries.forEach { repository.add(it) }
        scope.runCurrent()
    }

    suspend fun entries(): List<Entry> = repository.observeEntries().first()

    suspend fun entry(id: String): Entry? = repository.get(id)

    /** Keeps [flow] collected (a `WhileSubscribed` state) and records what it emits. */
    fun <T> collect(flow: Flow<T>): MutableList<T> {
        val seen = mutableListOf<T>()
        scope.backgroundScope.launch { flow.collect { seen += it } }
        scope.runCurrent()
        return seen
    }

    fun close() {
        Core.reset()
        database.close()
    }
}

/** Runs each test on a fresh [CoreTestRig] with the test dispatcher as Main (the view models' scope). */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class CoreTestBase {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()

    protected fun coreTest(
        now: Long = SampleData.NOW_1432,
        seed: List<Entry> = SampleData.scenario(SampleData.ZONE),
        block: suspend TestScope.(CoreTestRig) -> Unit,
    ) = runTest(dispatcher) {
        val rig = CoreTestRig(this, now)
        try {
            rig.seed(seed)
            block(rig)
        } finally {
            rig.close()
        }
    }

    protected companion object {
        const val MINUTE = 60_000L
    }
}
