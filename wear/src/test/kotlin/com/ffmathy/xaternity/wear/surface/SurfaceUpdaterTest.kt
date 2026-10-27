package com.ffmathy.xaternity.wear.surface

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.data.FixedClock
import com.ffmathy.xaternity.core.data.RoomEntryRepository
import com.ffmathy.xaternity.core.data.XaternityDatabase
import com.ffmathy.xaternity.core.domain.Summary
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.settings.AppLanguage
import com.ffmathy.xaternity.core.settings.Appearance
import com.ffmathy.xaternity.wear.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * The tile, complications and Ongoing Activities follow every change: local writes, merges from the phone, and a new
 * baby name or language from the phone's settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SurfaceUpdaterTest {
    private lateinit var db: XaternityDatabase
    private lateinit var repo: RoomEntryRepository
    private val clock = FixedClock(SampleData.NOW_1432)
    private val settings = FakeSettingsRepository()
    private val updates: MutableList<Summary> = Collections.synchronizedList(mutableListOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        db = XaternityDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = RoomEntryRepository(db, clock)
        SurfaceUpdater(ApplicationProvider.getApplicationContext(), repo, settings, clock, surfaces = { updates += it }).start(scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun awaitUpdates(count: Int) = runBlocking {
        withTimeout(5_000) {
            while (updates.size < count) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }
    }

    @Test
    fun everyChangeOfTheEntriesRefreshesTheSurfaces() {
        awaitUpdates(1)
        assertNull(updates.last().runningSleep)

        runBlocking { repo.startSleep(clock.now()).getOrThrow() }
        awaitUpdates(2)
        assertEquals(clock.now(), updates.last().runningSleep?.startMillis)
    }

    @Test
    fun aMergeFromThePhoneRefreshesTheSurfaces() {
        awaitUpdates(1)
        // The sync listener merges the phone's running sleep: the watch's Ongoing Activity must appear.
        val phoneSleep = Entry.sleep(SampleData.at(14, 0), null, id = "phone-sleep")
        runBlocking { repo.applyRemote(phoneSleep) }
        awaitUpdates(2)
        assertEquals("phone-sleep", updates.last().runningSleep?.id)

        // Ended on the phone: the activity must go.
        runBlocking { repo.applyRemote(phoneSleep.copy(endMillis = SampleData.at(14, 30), updatedAtMillis = SampleData.NOW_1432)) }
        awaitUpdates(3)
        assertNull(updates.last().runningSleep)
    }

    @Test
    fun aNewNameOrLanguageRefreshesTheSurfaces() {
        awaitUpdates(1)
        settings.state.update { it.copy(babyName = "Noa", updatedAtMillis = 1) }
        awaitUpdates(2)
        settings.state.update { it.copy(language = AppLanguage.DA, updatedAtMillis = 2) }
        awaitUpdates(3)
        // The appearance does not matter on the watch.
        settings.state.update { it.copy(appearance = Appearance.DAY, updatedAtMillis = 3) }
        runBlocking { delay(200) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, updates.size)
    }
}
