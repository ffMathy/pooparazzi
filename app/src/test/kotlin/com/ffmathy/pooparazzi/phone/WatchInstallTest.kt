package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.phone.watch.WatchInstall
import com.ffmathy.pooparazzi.phone.watch.WatchNodes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Watch › "Install on your watch": which watches lack the app, and opening the Play Store on them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class WatchInstallTest {

    private class FakeNodes(
        val connected: Set<String>,
        val withApp: Set<String>,
        val failing: Set<String> = emptySet(),
        val broken: Boolean = false,
    ) : WatchNodes {
        val opened = mutableListOf<String>()
        override suspend fun connected(): Set<String> = if (broken) throw IllegalStateException("no Play Services") else connected
        override suspend fun withApp(): Set<String> = withApp
        override suspend fun openStore(nodeId: String) {
            if (nodeId in failing) throw IllegalStateException("unreachable")
            opened += nodeId
        }
    }

    @Test
    fun onlyConnectedWatchesWithoutTheAppAreOffered() {
        assertEquals(setOf("b"), WatchInstall.missing(connected = setOf("a", "b"), withApp = setOf("a", "c")))
        assertEquals(emptySet<String>(), WatchInstall.missing(connected = emptySet(), withApp = setOf("a")))
    }

    @Test
    fun opensTheStoreOnEveryWatchWithoutTheApp() = runBlocking {
        val nodes = FakeNodes(connected = setOf("a", "b", "c"), withApp = setOf("a"), failing = setOf("c"))
        assertTrue(WatchInstall(nodes).openStoreOnWatches())
        assertEquals(listOf("b"), nodes.opened)
    }

    @Test
    fun nothingToOpenOrNoPlayServicesIsFalse() = runBlocking {
        assertFalse(WatchInstall(FakeNodes(connected = setOf("a"), withApp = setOf("a"))).openStoreOnWatches())
        val broken = WatchInstall(FakeNodes(connected = setOf("a"), withApp = emptySet(), broken = true))
        assertEquals(emptySet<String>(), broken.watchesWithoutApp())
        assertFalse(broken.openStoreOnWatches())
    }

    @Test
    fun theStorePageIsTheSharedApplicationId() {
        assertEquals("market://details?id=com.ffmathy.pooparazzi", WatchInstall.storeUri("com.ffmathy.pooparazzi").toString())
    }
}
