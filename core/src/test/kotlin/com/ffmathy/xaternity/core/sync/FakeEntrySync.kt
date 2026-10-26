package com.ffmathy.xaternity.core.sync

import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Records pushes; [accept] decides whether a push "reaches" the Data Layer. */
class FakeEntrySync(var accept: Boolean = true) : EntrySync {
    val pushed = mutableListOf<Entry>()
    val pushedSettings = mutableListOf<Settings>()

    /** The Data Layer as this device's node sees it: the last pushed version of each entry. */
    val items = linkedMapOf<String, Entry>()
    private val state = MutableStateFlow<SyncStatus>(SyncStatus.Offline)
    override val status: StateFlow<SyncStatus> = state

    override fun attach(sink: SyncSink) = Unit

    override suspend fun push(entry: Entry): Boolean {
        pushed += entry
        if (accept) items[entry.id] = entry
        return accept
    }

    override suspend fun pushSettings(settings: Settings): Boolean {
        pushedSettings += settings
        return accept
    }

    override suspend fun pullAll(): Int = 0
    override suspend fun receive(items: List<RemoteItem>): Int = 0

    override suspend fun retry() = Unit
}
