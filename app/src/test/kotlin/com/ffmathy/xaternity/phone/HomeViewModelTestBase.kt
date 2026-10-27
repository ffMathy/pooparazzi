package com.ffmathy.xaternity.phone

import com.ffmathy.xaternity.core.sample.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** Runs each test on a fresh [HomeTestRig] with the test dispatcher as Main (the view model's scope). */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class HomeViewModelTestBase {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()

    protected fun homeTest(now: Long = SampleData.NOW_1432, block: suspend TestScope.(HomeTestRig) -> Unit) = runTest(dispatcher) {
        val rig = HomeTestRig(this, now)
        try {
            rig.start()
            block(rig)
        } finally {
            rig.close()
        }
    }

    protected companion object {
        const val MINUTE = 60_000L
    }
}
