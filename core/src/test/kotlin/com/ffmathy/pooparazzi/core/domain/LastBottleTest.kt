package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.BottleContents
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_1432
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [lastBottleAmountMl]: the newest bottle with an amount in the past week, for the watch's amount chips. */
class LastBottleTest {
    private val hour = 60 * 60_000L
    private val day = 24 * hour

    private fun bottle(at: Long, ml: Int?) = Entry.feed(FeedType.BOTTLE, at, amountMl = ml, contents = BottleContents.FORMULA)

    @Test
    fun theNewestBottleWithAnAmountWins() {
        val entries = listOf(bottle(NOW_1432 - 5 * hour, 90), bottle(NOW_1432 - 2 * hour, 120), bottle(NOW_1432 - 3 * hour, 150))
        assertEquals(120, lastBottleAmountMl(entries, NOW_1432))
    }

    @Test
    fun otherFeedsAmountLessAndDeletedBottlesAreSkipped() {
        val entries = listOf(
            bottle(NOW_1432 - 4 * hour, 100),
            bottle(NOW_1432 - 3 * hour, null),
            bottle(NOW_1432 - 2 * hour, 130).copy(deleted = true),
            Entry.feed(FeedType.FOOD, NOW_1432 - hour),
            Entry.breastfeed(NOW_1432 - hour / 2),
            Entry.diaper(NOW_1432 - hour / 4, pee = true, poop = false),
        )
        assertEquals(100, lastBottleAmountMl(entries, NOW_1432))
    }

    @Test
    fun onlyTheLastSevenDaysCount() {
        assertEquals("exactly seven days ago still counts", 80, lastBottleAmountMl(listOf(bottle(NOW_1432 - 7 * day, 80)), NOW_1432))
        assertNull(lastBottleAmountMl(listOf(bottle(NOW_1432 - 7 * day - 1, 80)), NOW_1432))
        assertNull("nothing in the future", lastBottleAmountMl(listOf(bottle(NOW_1432 + 1, 80)), NOW_1432))
        assertEquals(60, lastBottleAmountMl(listOf(bottle(NOW_1432 - 2 * day, 60)), NOW_1432, days = 3))
    }

    @Test
    fun noBottleIsNull() {
        assertNull(lastBottleAmountMl(emptyList(), NOW_1432))
    }
}
