package com.ffmathy.pooparazzi.core.cloud

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloudBookkeepingTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cursor = CloudCursor(1_700_000_000, 5_000, "entry-1")

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun valuesBelongToTheirAccount() {
        val values = CloudBookkeeping(cursorUid = "a", cursor = cursor, fullUploadUid = "a", lastSyncUid = "b", lastSyncAtMillis = 9)
        assertEquals(cursor, values.cursorFor("a"))
        assertNull("another account starts from the beginning", values.cursorFor("b"))
        assertEquals(9L, values.lastSyncFor("b"))
        assertNull(values.lastSyncFor("a"))

        assertEquals(CloudBookkeeping(lastSyncUid = "b", lastSyncAtMillis = 9), values.forget("a"))
        assertEquals(values.copy(lastSyncUid = null, lastSyncAtMillis = null), values.forget("b"))

        // A deleted backup stays deleted when the rest is forgotten.
        val deleted = values.copy(deletedUid = "a")
        assertEquals(true, deleted.isDeleted("a"))
        assertEquals(false, deleted.isDeleted("b"))
        assertEquals("a", deleted.forget("a").deletedUid)
    }

    @Test
    fun theDataStoreKeepsAndClearsEveryValue() = runBlocking {
        val file = folder.newFile("cloud.preferences_pb").also { it.delete() }
        val store = DataStoreCloudBookkeepingStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(CloudBookkeeping(), store.get())

        val full = CloudBookkeeping("a", cursor, "a", "a", 1234, deletedUid = "a")
        store.update { full }
        assertEquals(full, store.get())
        store.update { it.forget("a") }
        assertEquals(CloudBookkeeping(deletedUid = "a"), store.get())
        store.update { it.copy(deletedUid = null) }
        assertEquals(CloudBookkeeping(), store.get())
    }
}
