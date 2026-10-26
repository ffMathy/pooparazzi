package com.ffmathy.xaternity.core.model

import com.ffmathy.xaternity.core.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryJsonTest {

    @Test
    fun everySampleEntryRoundTrips() {
        val entries = SampleData.scenarioWithRunningFeed() + SampleData.scenarioAt0312()
        for (entry in entries) {
            assertEquals(entry, EntryJson.decode(EntryJson.encode(entry)))
        }
    }

    @Test
    fun deletedAndFullyPopulatedEntriesRoundTrip() {
        val diaper = Entry.diaper(1_000L, pee = true, poop = true, color = PoopColor.PALE, texture = PoopTexture.HARD, id = "d")
            .copy(deleted = true, updatedAtMillis = 5_000L)
        assertEquals(diaper, EntryJson.decode(EntryJson.encode(diaper)))
        val feed = Entry.breastfeed(1_000L, 2_000_000L, BreastSide.RIGHT_THEN_LEFT, id = "b")
        assertEquals(feed, EntryJson.decode(EntryJson.encode(feed)))
    }

    @Test
    fun encodesEnumsByNameAndAllFields() {
        val json = EntryJson.encode(Entry.feed(FeedType.FORMULA, 42L, id = "f"))
        assertTrue(json, json.contains("\"kind\":\"FEED\""))
        assertTrue(json, json.contains("\"feedType\":\"FORMULA\""))
        assertTrue(json, json.contains("\"updatedAtMillis\":42"))
    }

    @Test
    fun readsEntriesFromNewerVersionsLeniently() {
        val json = """{"id":"x","kind":"DIAPER","startMillis":10,"pee":false,"poop":true,"poopColor":"PURPLE",
            |"poopTexture":"SEEDY","updatedAtMillis":11,"futureField":{"a":1}}""".trimMargin()
        val entry = EntryJson.decode(json)
        assertNull(entry.poopColor)
        assertEquals(PoopTexture.SEEDY, entry.poopTexture)
        assertTrue(entry.poop)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(EntryJson.decodeOrNull("not json"))
        assertNull(EntryJson.decodeOrNull("""{"id":"x","kind":"NAP","startMillis":1,"updatedAtMillis":1}"""))
    }
}

class EntryExtensionsTest {

    @Test
    fun intervalsAndRunning() {
        val sleep = Entry.sleep(0L)
        assertTrue(sleep.isInterval)
        assertTrue(sleep.isRunning)
        assertFalse(sleep.copy(deleted = true).isRunning)
        assertFalse(sleep.copy(endMillis = 10L).isRunning)

        val breast = Entry.breastfeed(0L)
        assertTrue(breast.isInterval)
        assertTrue(breast.isRunning)

        val formula = Entry.feed(FeedType.FORMULA, 0L)
        assertFalse(formula.isInterval)
        assertFalse(formula.isRunning)
        assertFalse(Entry.diaper(0L, pee = true, poop = false).isInterval)
    }

    @Test
    fun durations() {
        assertEquals(500L, Entry.sleep(1_000L).durationMillis(now = 1_500L))
        assertEquals(200L, Entry.sleep(1_000L, 1_200L).durationMillis(now = 9_999L))
        assertEquals(0L, Entry.sleep(1_000L).durationMillis(now = 500L))
        assertEquals(0L, Entry.feed(FeedType.FOOD, 1_000L).durationMillis(now = 9_999L))
    }

    @Test
    fun diaperKinds() {
        assertEquals(DiaperKind.PEE, Entry.diaper(0, pee = true, poop = false).diaperKind)
        assertEquals(DiaperKind.POOP, Entry.diaper(0, pee = false, poop = true).diaperKind)
        assertEquals(DiaperKind.BOTH, Entry.diaper(0, pee = true, poop = true).diaperKind)
        assertNull(Entry.sleep(0).diaperKind)
    }

    @Test
    fun diaperFactoryDropsPoopDetailsWithoutPoop() {
        val entry = Entry.diaper(0, pee = true, poop = false, color = PoopColor.RED, texture = PoopTexture.HARD)
        assertNull(entry.poopColor)
        assertNull(entry.poopTexture)
    }
}

class EntryValidationTest {

    private fun violation(entry: Entry) = validate(entry)

    @Test
    fun sampleScenarioIsValid() {
        (SampleData.scenarioWithRunningFeed() + SampleData.scenarioAt0312()).forEach { assertNull(it.id, validate(it)) }
    }

    @Test
    fun intervals() {
        assertEquals(EntryViolation.END_BEFORE_START, violation(Entry.sleep(10L, 5L)))
        assertNull(violation(Entry.sleep(10L, 10L)))
        assertEquals(EntryViolation.INSTANT_HAS_END, violation(Entry.feed(FeedType.FORMULA, 10L).copy(endMillis = 20L)))
        assertEquals(EntryViolation.INSTANT_HAS_END, violation(Entry.diaper(10L, pee = true, poop = false).copy(endMillis = 20L)))
    }

    @Test
    fun breastSide() {
        assertNull(violation(Entry.breastfeed(0L)))
        assertEquals(EntryViolation.SIDE_WHILE_RUNNING, violation(Entry.breastfeed(0L).copy(breastSide = BreastSide.LEFT)))
        assertEquals(EntryViolation.SIDE_MISSING, violation(Entry.breastfeed(0L, 120_000L, side = null)))
        assertNull(violation(Entry.breastfeed(0L, 120_000L, BreastSide.LEFT)))
        assertEquals(
            EntryViolation.SIDE_ON_NON_BREAST,
            violation(Entry.feed(FeedType.FORMULA, 0L).copy(breastSide = BreastSide.RIGHT)),
        )
    }

    @Test
    fun feedFields() {
        assertEquals(EntryViolation.FEED_TYPE_MISSING, violation(Entry(id = "x", kind = EntryKind.FEED, startMillis = 0, updatedAtMillis = 0)))
        assertEquals(EntryViolation.FEED_FIELDS_ON_NON_FEED, violation(Entry.sleep(0L).copy(feedType = FeedType.FOOD)))
    }

    @Test
    fun diapers() {
        assertEquals(EntryViolation.DIAPER_EMPTY, violation(Entry.diaper(0L, pee = false, poop = false)))
        assertEquals(
            EntryViolation.POOP_DETAILS_WITHOUT_POOP,
            violation(Entry.diaper(0L, pee = true, poop = false).copy(poopColor = PoopColor.GREEN)),
        )
        assertEquals(
            EntryViolation.POOP_DETAILS_WITHOUT_POOP,
            violation(Entry.diaper(0L, pee = true, poop = false).copy(poopTexture = PoopTexture.WATERY)),
        )
        assertNull(violation(Entry.diaper(0L, pee = false, poop = true, color = PoopColor.BLACK)))
        assertEquals(EntryViolation.DIAPER_FIELDS_ON_NON_DIAPER, violation(Entry.sleep(0L).copy(pee = true)))
    }

    @Test
    fun basics() {
        assertEquals(EntryViolation.BLANK_ID, violation(Entry.sleep(0L, id = " ")))
        assertEquals(EntryViolation.NEGATIVE_TIME, violation(Entry.sleep(-1L)))
    }
}
