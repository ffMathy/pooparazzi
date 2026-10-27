package com.ffmathy.pooparazzi.core.sync

import android.net.Uri
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataItemAsset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntrySyncListenerServiceTest {
    private class Item(private val uri: Uri, private var data: ByteArray?) : DataItem {
        override fun getUri(): Uri = uri
        override fun getData(): ByteArray? = data
        override fun setData(data: ByteArray?): DataItem = apply { this.data = data }
        override fun getAssets(): Map<String, DataItemAsset> = emptyMap()
        override fun freeze(): DataItem = this
        override fun isDataValid(): Boolean = true
    }

    private class Event(private val type: Int, private val item: DataItem) : DataEvent {
        override fun getType(): Int = type
        override fun getDataItem(): DataItem = item
        override fun freeze(): DataEvent = this
        override fun isDataValid(): Boolean = true
    }

    @Test
    fun copiesChangedItemsWithTheirNodeAndSkipsDeletionsAndEmptyItems() {
        val payload = "{}".encodeToByteArray()
        val events = listOf(
            Event(DataEvent.TYPE_CHANGED, Item(Uri.parse("wear://watch-node/entries/a"), payload)),
            Event(DataEvent.TYPE_DELETED, Item(Uri.parse("wear://watch-node/entries/b"), payload)),
            Event(DataEvent.TYPE_CHANGED, Item(Uri.parse("wear://watch-node/entries/c"), null)),
            Event(DataEvent.TYPE_CHANGED, Item(Uri.parse("wear://phone-node/settings"), payload)),
        )
        val items = EntrySyncListenerService.remoteItems(events)
        assertEquals(listOf("/entries/a" to "watch-node", "/settings" to "phone-node"), items.map { it.path to it.sourceNodeId })
        assertArrayEquals(payload, items.first().data)
    }
}
