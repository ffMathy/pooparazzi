package com.ffmathy.pooparazzi.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The database of a release from before several babies opens in this one with every row intact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PooparazziDatabase::class.java)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun version1RowsBelongToTheFirstBabyAndAreNotPendingForTheBackend() {
        helper.createDatabase(NAME, 1).apply {
            execSQL(
                "INSERT INTO entries (id, kind, startMillis, endMillis, feedType, breastSide, pee, poop, poopColor, " +
                    "poopTexture, updatedAtMillis, deleted, pendingSync, createdLocally) VALUES " +
                    "('feed', 'FEED', 1000, NULL, 'BREAST', 'LEFT_THEN_RIGHT', 0, 0, NULL, NULL, 1000, 0, 1, 1), " +
                    "('sleep', 'SLEEP', 500, NULL, NULL, NULL, 0, 0, NULL, NULL, 600, 0, 0, 0), " +
                    "('gone', 'DIAPER', 200, NULL, NULL, NULL, 1, 0, NULL, NULL, 300, 1, 0, 1)",
            )
            execSQL("INSERT INTO purged_entries (id, purgedAtMillis) VALUES ('purged', 42)")
            close()
        }

        // Validates the migrated schema against 2.json (columns, defaults and the per-baby index).
        helper.runMigrationsAndValidate(NAME, 2, true).apply {
            query("SELECT id, babyId, cloudPending, pendingSync FROM entries ORDER BY id").use { rows ->
                val read = buildList {
                    while (rows.moveToNext()) add(listOf(rows.getString(0), rows.getString(1), rows.getInt(2), rows.getInt(3)))
                }
                assertEquals(
                    listOf(
                        listOf("feed", Baby.FIRST_ID, 0, 1),
                        listOf("gone", Baby.FIRST_ID, 0, 0),
                        listOf("sleep", Baby.FIRST_ID, 0, 0),
                    ),
                    read,
                )
            }
            close()
        }

        // The app's own database class opens the migrated file, and the repository reads the rows as before.
        val db = Room.databaseBuilder(context, PooparazziDatabase::class.java, NAME).allowMainThreadQueries().build()
        try {
            val repo = RoomEntryRepository(db, FixedClock(SampleData.NOW_1432))
            runBlocking {
                val entries = repo.observeEntries().first()
                assertEquals(listOf("sleep", "feed"), entries.map { it.id })
                assertEquals(Entry.breastfeed(1000, BreastSide.LEFT_THEN_RIGHT, id = "feed", updatedAtMillis = 1000), entries[1])
                assertTrue(repo.get("gone")!!.deleted)
                assertFalse("still running, and still the first baby's", repo.startSleep(SampleData.NOW_1432).isSuccess)
                assertTrue(db.entryDao().isPurged("purged"))
                assertEquals(0, repo.observeCloudPendingCount().first())
                assertEquals(listOf(Baby.FIRST_ID), repo.observeBabyIds().first())
                assertEquals(FeedType.BREAST, repo.get("feed")!!.feedType)
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
