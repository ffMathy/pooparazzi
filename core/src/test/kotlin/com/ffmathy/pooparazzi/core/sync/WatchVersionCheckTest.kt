package com.ffmathy.pooparazzi.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A paired watch from before several babies: the tracker capability without the babies one (BABIES_AND_BACKUP §2.4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchVersionCheckTest {
    private fun check(vararg advertised: Pair<String, Set<String>>) =
        WatchVersionCheck { capability -> advertised.toMap()[capability].orEmpty() }

    @Test
    fun aWatchWithoutTheBabiesCapabilityNeedsAnUpdate() = runBlocking {
        assertTrue(check(SyncPaths.CAPABILITY to setOf("watch")).needsUpdate())
        assertTrue(
            "one of two watches is old",
            check(SyncPaths.CAPABILITY to setOf("a", "b"), SyncPaths.BABIES_CAPABILITY to setOf("a")).needsUpdate(),
        )
    }

    @Test
    fun anUpToDateWatchOrNoneIsFine() = runBlocking {
        assertFalse(check(SyncPaths.CAPABILITY to setOf("watch"), SyncPaths.BABIES_CAPABILITY to setOf("watch")).needsUpdate())
        assertFalse("no watch", check().needsUpdate())
        assertFalse("no answer counts as fine", WatchVersionCheck { error("no Play Services") }.needsUpdate())
    }
}

/** Both apps advertise both capabilities: they come from core's `res/values/wear.xml`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdvertisedCapabilitiesTest {
    @Test
    fun coreDeclaresTheTrackerAndTheBabiesCapability() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val declared = context.resources.getStringArray(R.array.android_wear_capabilities).toSet()
        assertTrue(declared.containsAll(setOf(SyncPaths.CAPABILITY, SyncPaths.BABIES_CAPABILITY)))
    }
}
