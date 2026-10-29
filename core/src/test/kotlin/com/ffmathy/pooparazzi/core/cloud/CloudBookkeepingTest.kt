package com.ffmathy.pooparazzi.core.cloud

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloudBookkeepingTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cursor = CloudCursor(1_700_000_000, 5_000, "entry-1")
    private val babies = CloudCursor(1_700_000_000, 6_000, "default")

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun valuesBelongToTheirFamily() {
        val values = CloudBookkeeping(
            cursorFamilyId = "a",
            cursor = cursor,
            babiesCursor = babies,
            fullUploadFamilyId = "a",
            lastSyncFamilyId = "b",
            lastSyncAtMillis = 9,
        )
        assertEquals(cursor, values.cursorFor("a"))
        assertEquals(babies, values.babiesCursorFor("a"))
        assertNull("another family starts from the beginning", values.cursorFor("b"))
        assertNull(values.babiesCursorFor("b"))
        assertEquals(9L, values.lastSyncFor("b"))
        assertNull(values.lastSyncFor("a"))

        assertEquals(CloudBookkeeping(lastSyncFamilyId = "b", lastSyncAtMillis = 9), values.forget("a"))
        assertEquals(values.copy(lastSyncFamilyId = null, lastSyncAtMillis = null), values.forget("b"))

        // A deleted backup stays deleted when the rest is forgotten.
        val deleted = values.copy(deletedFamilyId = "a")
        assertEquals(true, deleted.isDeleted("a"))
        assertEquals(false, deleted.isDeleted("b"))
        assertEquals("a", deleted.forget("a").deletedFamilyId)
    }

    @Test
    fun eachCursorMovesAloneAndBelongsToOneFamily() {
        val values = CloudBookkeeping(cursorFamilyId = "a", cursor = cursor, babiesCursor = babies)
        val later = CloudCursor(1_700_000_001, 0, "entry-9")
        assertEquals(values.copy(cursor = later), values.withCursor("a", later))
        assertEquals(values.copy(babiesCursor = later), values.withBabiesCursor("a", later))
        // A cursor for another family drops the first family's other cursor: the two always belong to one family.
        assertEquals(CloudBookkeeping(cursorFamilyId = "b", cursor = later), values.withCursor("b", later))
        assertEquals(CloudBookkeeping(cursorFamilyId = "b", babiesCursor = later), values.withBabiesCursor("b", later))
    }

    @Test
    fun theDataStoreKeepsAndClearsEveryValue() = runBlocking {
        val file = folder.newFile("cloud.preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = DataStoreCloudBookkeepingStore(dataStore)
        assertEquals(CloudBookkeeping(), store.get())

        val full = CloudBookkeeping("a", cursor, babies, "a", "a", 1234, deletedFamilyId = "a")
        store.update { full }
        assertEquals(full, store.get())
        store.update { it.forget("a") }
        assertEquals(CloudBookkeeping(deletedFamilyId = "a"), store.get())
        store.update { it.copy(deletedFamilyId = null) }
        assertEquals(CloudBookkeeping(), store.get())
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun theBookkeepingFromBeforeFamiliesIsNotReadAndGoesAtTheFirstWrite() = runBlocking {
        val file = folder.newFile("legacy.preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        dataStore.edit {
            it[stringPreferencesKey("cursor_uid")] = "uid-a"
            it[stringPreferencesKey("cursor")] = """{"seconds":1,"nanos":2,"documentId":"x"}"""
            it[stringPreferencesKey("full_upload_uid")] = "uid-a"
            it[stringPreferencesKey("last_sync_uid")] = "uid-a"
            it[longPreferencesKey("last_sync_at")] = 5
            it[stringPreferencesKey("deleted_uid")] = "uid-b"
        }
        val store = DataStoreCloudBookkeepingStore(dataStore)
        // Keyed by account, not by family: the first sync for the family flags everything and pulls from the start.
        assertEquals(CloudBookkeeping(), store.get())

        store.update { it.copy(fullUploadFamilyId = "f") }
        assertEquals(CloudBookkeeping(fullUploadFamilyId = "f"), store.get())
        assertEquals(1, dataStore.data.first().asMap().size)
    }
}
