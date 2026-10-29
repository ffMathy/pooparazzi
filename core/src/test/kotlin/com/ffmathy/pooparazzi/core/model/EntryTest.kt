package com.ffmathy.pooparazzi.core.model

import com.ffmathy.pooparazzi.core.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryJsonTest {

    @Test
    fun everySampleEntryRoundTrips() {
        val entries = SampleData.scenarioWithRecentFeed() + SampleData.scenarioAt0312()
        for (entry in entries) {
            assertEquals(entry, EntryJson.decode(EntryJson.encode(entry)))
        }
    }

    @Test
    fun deletedAndFullyPopulatedEntriesRoundTrip() {
        val diaper = Entry.diaper(1_000L, pee = true, poop = true, color = PoopColor.PALE, texture = PoopTexture.HARD, id = "d")
            .copy(deleted = true, updatedAtMillis = 5_000L)
        assertEquals(diaper, EntryJson.decode(EntryJson.encode(diaper)))
        val feed = Entry.breastfeed(1_000L, BreastSide.RIGHT_THEN_LEFT, id = "b")
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
    fun minimalEntriesRoundTripAndOlderJsonStillDecodes() {
        val minimal = listOf(
            Entry.feed(null, 1_000L, id = "untyped"),
            Entry.diaper(2_000L, pee = false, poop = false, id = "plain"),
            Entry.breastfeed(1_000L, side = null, id = "no-side"),
        )
        for (entry in minimal) {
            assertEquals(entry, EntryJson.decode(EntryJson.encode(entry)))
            assertTrue(entry.isValid)
        }
        // An untyped feed is written with "feedType":null, which older apps read as a feed without a type.
        assertTrue(EntryJson.encode(minimal[0]).contains("\"feedType\":null"))
        // JSON written before the type, side and flags became optional (every field set) still decodes; the timed
        // breastfeed of that version reads as an instant at its start.
        val older = """{"id":"b","kind":"FEED","startMillis":1000,"endMillis":2000000,"feedType":"BREAST",
            |"breastSide":"LEFT_THEN_RIGHT","pee":false,"poop":false,"poopColor":null,"poopTexture":null,
            |"updatedAtMillis":2000000,"deleted":false}""".trimMargin()
        assertEquals(
            Entry.breastfeed(1_000L, BreastSide.LEFT_THEN_RIGHT, id = "b", updatedAtMillis = 2_000_000L),
            EntryJson.decode(older),
        )
    }

    @Test
    fun legacyTimedBreastfeedsDecodeAsInstants() {
        // Ended, with a side: the end is dropped, the start and side are kept, and it is valid.
        val ended = EntryJson.decode(
            """{"id":"e","kind":"FEED","startMillis":5000,"endMillis":900000,"feedType":"BREAST","breastSide":"RIGHT","updatedAtMillis":900000}""",
        )
        assertNull(ended.endMillis)
        assertEquals(5_000L, ended.startMillis)
        assertEquals(BreastSide.RIGHT, ended.breastSide)
        assertFalse(ended.isInterval)
        assertFalse(ended.isRunning)
        assertTrue(ended.isValid)
        // Still "running" in the old app (no end): an instant at its start, never running.
        val running = EntryJson.decode("""{"id":"r","kind":"FEED","startMillis":7000,"feedType":"BREAST","updatedAtMillis":7000}""")
        assertEquals(Entry.breastfeed(7_000L, id = "r"), running)
        assertFalse(running.isRunning)
        assertTrue(running.isValid)
        // A sleep keeps its end.
        assertEquals(20L, EntryJson.decode(EntryJson.encode(Entry.sleep(10L, 20L, id = "s"))).endMillis)
    }

    @Test
    fun theBabyRoundTripsAndOlderJsonIsTheFirstBaby() {
        val twin = Entry.diaper(1_000L, pee = true, poop = false, id = "t", babyId = "baby-2")
        assertTrue(EntryJson.encode(twin).contains("\"babyId\":\"baby-2\""))
        assertEquals(twin, EntryJson.decode(EntryJson.encode(twin)))
        for (entry in listOf(Entry.sleep(0L, babyId = "b"), Entry.breastfeed(0L, babyId = "b"), Entry.feed(null, 0L, babyId = "b"))) {
            assertEquals("b", entry.babyId)
            assertEquals(entry, EntryJson.decode(EntryJson.encode(entry)))
        }
        // JSON from before several babies has no babyId: the first baby's. A blank one is read the same way.
        val older = EntryJson.decode("""{"id":"x","kind":"FEED","startMillis":1,"feedType":"FORMULA","updatedAtMillis":1}""")
        assertEquals(Baby.FIRST_ID, older.babyId)
        assertEquals(Entry.feed(FeedType.FORMULA, 1L, id = "x"), older)
        val blank = EntryJson.decode("""{"id":"y","kind":"DIAPER","startMillis":1,"updatedAtMillis":1,"babyId":" "}""")
        assertEquals(Baby.FIRST_ID, blank.babyId)
        assertEquals(Baby.FIRST_ID, Entry.sleep(0L).copy(babyId = "").normalized().babyId)
        assertEquals(twin, twin.normalized())
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

        val breast = Entry.breastfeed(0L, BreastSide.LEFT)
        assertFalse(breast.isInterval)
        assertFalse(breast.isRunning)

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
        assertEquals(0L, Entry.breastfeed(1_000L).durationMillis(now = 9_999L))
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
        (SampleData.scenarioWithRecentFeed() + SampleData.scenarioAt0312()).forEach { assertNull(it.id, validate(it)) }
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
        // The side is optional: a breastfeed may record only its time.
        assertNull(violation(Entry.breastfeed(0L)))
        assertNull(violation(Entry.breastfeed(0L, BreastSide.LEFT)))
        assertNull(violation(Entry.feed(FeedType.BREAST, 0L, BreastSide.RIGHT_THEN_LEFT)))
        // A breastfeed is an instant: new data never has an end.
        assertEquals(EntryViolation.INSTANT_HAS_END, violation(Entry.breastfeed(0L).copy(endMillis = 120_000L)))
        // The factory drops a side on other feed types.
        assertNull(Entry.feed(FeedType.FORMULA, 0L, BreastSide.LEFT).breastSide)
        assertEquals(
            EntryViolation.SIDE_ON_NON_BREAST,
            violation(Entry.feed(FeedType.FORMULA, 0L).copy(breastSide = BreastSide.RIGHT)),
        )
    }

    @Test
    fun formulaAmount() {
        // The amount is optional, positive, and only on formula.
        assertNull(violation(Entry.feed(FeedType.FORMULA, 0L)))
        assertNull(violation(Entry.feed(FeedType.FORMULA, 0L, amountMl = 120)))
        assertEquals(120, EntryJson.decode(EntryJson.encode(Entry.feed(FeedType.FORMULA, 0L, id = "f", amountMl = 120))).amountMl)
        // The factory drops an amount on other feed types.
        assertNull(Entry.feed(FeedType.FOOD, 0L, amountMl = 120).amountMl)
        assertNull(Entry.breastfeed(0L).amountMl)
        assertEquals(EntryViolation.AMOUNT_NOT_POSITIVE, violation(Entry.feed(FeedType.FORMULA, 0L).copy(amountMl = 0)))
        assertEquals(EntryViolation.AMOUNT_ON_NON_FORMULA, violation(Entry.feed(null, 0L).copy(amountMl = 60)))
        assertEquals(EntryViolation.AMOUNT_ON_NON_FORMULA, violation(Entry.sleep(0L).copy(amountMl = 60)))
    }

    @Test
    fun feedFields() {
        // The type is optional: a feed with just its time is a valid instant, never with an end or a side.
        val untyped = Entry(id = "x", kind = EntryKind.FEED, startMillis = 0, updatedAtMillis = 0)
        assertNull(violation(untyped))
        assertNull(violation(Entry.feed(null, 0L)))
        assertFalse(untyped.isInterval)
        assertEquals(EntryViolation.INSTANT_HAS_END, violation(untyped.copy(endMillis = 10L)))
        assertEquals(EntryViolation.SIDE_ON_NON_BREAST, violation(untyped.copy(breastSide = BreastSide.LEFT)))
        assertEquals(EntryViolation.FEED_FIELDS_ON_NON_FEED, violation(Entry.sleep(0L).copy(feedType = FeedType.FOOD)))
    }

    @Test
    fun diapers() {
        // Pee and poop are optional: neither is a plain diaper change.
        val plain = Entry.diaper(0L, pee = false, poop = false)
        assertNull(violation(plain))
        assertEquals(DiaperKind.PLAIN, plain.diaperKind)
        assertEquals(EntryViolation.POOP_DETAILS_WITHOUT_POOP, violation(plain.copy(poopColor = PoopColor.RED)))
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
    fun tickOrderMapsToSide() {
        val l = Breast.LEFT
        val r = Breast.RIGHT
        assertNull(BreastSide.fromOrder(emptyList()))
        assertEquals(BreastSide.LEFT, BreastSide.fromOrder(listOf(l)))
        assertEquals(BreastSide.RIGHT, BreastSide.fromOrder(listOf(r)))
        assertEquals(BreastSide.LEFT_THEN_RIGHT, BreastSide.fromOrder(listOf(l, r)))
        assertEquals(BreastSide.RIGHT_THEN_LEFT, BreastSide.fromOrder(listOf(r, l)))
        assertEquals(BreastSide.LEFT_THEN_RIGHT, BreastSide.fromOrder(listOf(l, r, l)))
        // Round trip: every stored side is its own tick order.
        BreastSide.entries.forEach { assertEquals(it, BreastSide.fromOrder(it.order)) }
    }

    @Test
    fun tickingAppendsAndUntickingRemoves() {
        val l = Breast.LEFT
        val r = Breast.RIGHT
        var ticks = BreastSide.toggle(emptyList(), r)
        ticks = BreastSide.toggle(ticks, l)
        assertEquals(listOf(r, l), ticks)
        // Unticking Right leaves Left alone; ticking Right again puts it second.
        ticks = BreastSide.toggle(ticks, r)
        assertEquals(listOf(l), ticks)
        ticks = BreastSide.toggle(ticks, r)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, BreastSide.fromOrder(ticks))
        assertEquals(emptyList<Breast>(), BreastSide.toggle(BreastSide.toggle(ticks, l), r))
    }

    @Test
    fun basics() {
        assertEquals(EntryViolation.BLANK_ID, violation(Entry.sleep(0L, id = " ")))
        assertEquals(EntryViolation.NEGATIVE_TIME, violation(Entry.sleep(-1L)))
    }
}
