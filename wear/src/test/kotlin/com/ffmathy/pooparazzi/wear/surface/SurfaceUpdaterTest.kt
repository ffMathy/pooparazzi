package com.ffmathy.pooparazzi.wear.surface

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.data.PooparazziDatabase
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.wear.SurfacesRestoreReceiver
import com.ffmathy.pooparazzi.wear.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private lateinit var db: PooparazziDatabase
    private lateinit var repo: RoomEntryRepository
    private val clock = FixedClock(SampleData.NOW_1432)
    private val settings = FakeSettingsRepository()
    private val updates: MutableList<Summary> = Collections.synchronizedList(mutableListOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        db = PooparazziDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = RoomEntryRepository(db, clock, selectedBaby = settings.state.map { it.selectedBaby.id })
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
        settings.state.update { it.withBabyName(it.selectedBaby.id, "Noa").copy(updatedAtMillis = 1) }
        awaitUpdates(2)
        settings.state.update { it.copy(language = AppLanguage.DA, updatedAtMillis = 2) }
        awaitUpdates(3)
        // The appearance does not matter on the watch.
        settings.state.update { it.copy(appearance = Appearance.DAY, updatedAtMillis = 3) }
        runBlocking { delay(200) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, updates.size)
    }

    @Test
    fun updateNowBringsTheSurfacesUpToDateBeforeReturning() {
        awaitUpdates(1)
        val sleep = runBlocking { repo.startSleep(clock.now()).getOrThrow() }
        awaitUpdates(2)
        val before = updates.size
        val updater = SurfaceUpdater(ApplicationProvider.getApplicationContext(), repo, settings, clock, surfaces = { updates += it })
        val job = scope.launch { updater.updateNow() }
        runBlocking {
            withTimeout(5_000) {
                while (!job.isCompleted) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(10)
                }
            }
        }
        assertEquals(before + 1, updates.size)
        assertEquals(sleep.id, updates.last().runningSleep?.id)
    }

    @Test
    fun aRebootOrAnUpdateRestartsTheAppToPutTheOngoingActivityBack() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packages = context.packageManager
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            val receivers = packages.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            assertTrue(action, receivers.any { it.activityInfo.name == SurfacesRestoreReceiver::class.java.name && !it.activityInfo.exported })
        }
        val requested = packages.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(Manifest.permission.RECEIVE_BOOT_COMPLETED in requested)
    }
}
