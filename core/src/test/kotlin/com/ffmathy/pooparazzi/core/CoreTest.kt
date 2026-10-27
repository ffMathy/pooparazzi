package com.ffmathy.pooparazzi.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Core works locally without Play Services (Robolectric has none): nothing crashes, logging works. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = Core.reset()

    @Test
    fun initIsIdempotentAndWorksWithoutPlayServices() = runBlocking {
        val clock = FixedClock(SampleData.NOW_1432)
        val graph = Core.init(context, clock)
        assertSame(graph, Core.init(context))
        assertSame(clock, Core.clock)
        assertTrue(Core.isInitialized)

        val feed = Core.repository.logFeed(FeedType.FORMULA, SampleData.NOW_1432)
        assertEquals(listOf(feed), Core.repository.observeEntries().first())
        Core.settings.setBabyName("Noa")
        assertEquals("Noa", Core.settings.current().babyName)
    }

    @Test
    fun ensureInitializedReusesTheGraph() {
        val graph = Core.init(context)
        assertSame(graph, Core.ensureInitialized(context))
    }
}
