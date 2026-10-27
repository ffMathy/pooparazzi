package com.ffmathy.pooparazzi.phone.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.model.isFeed
import com.ffmathy.pooparazzi.phone.app.launchMinuteTicker
import com.ffmathy.pooparazzi.phone.ui.home.HomeHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

/** One opening of the edit sheet. Saved across process death, so a half-made correction survives. */
@Serializable
internal data class EditSession(
    val key: Long,
    val mode: EditMode,
    val original: Entry?,
    val draft: EditDraft,
    val saveFailed: Boolean = false,
    /** The entry changed or was deleted on the watch after the sheet opened (found on Save). */
    val conflict: EditConflict? = null,
)

/**
 * The edit sheet's state machine (DESIGN_SPEC §3.3.4, V1_ADDITIONS §4): edit or delete an entry, or add a past one.
 * One instance per screen that hosts the sheet (Home, History); [openEntry] / [openNew] start a session and
 * [state] is null while the sheet is closed. Writes go through [repository], which enforces the invariants again.
 * Results the host acts on (a delete to offer Undo for) arrive on [events].
 */
class EditViewModel(
    private val repository: EntryRepository,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel(), EditActions {

    private val session = MutableStateFlow(savedState.get<String>(KEY_SESSION)?.let(::decode))
    private val now = MutableStateFlow(clock.now())
    private val eventChannel = Channel<EditEvent>(Channel.BUFFERED)
    private val hapticChannel = Channel<HomeHaptic>(Channel.BUFFERED)
    private val actions = Mutex()
    private var nextKey = session.value?.key ?: 0L

    /** The open sheet, or null while it is closed. */
    val state: StateFlow<EditUiState?> = combine(session, repository.observeEntries(), now) { s, entries, t ->
        s?.let { EditRules.state(it.key, it.mode, it.draft, it.original, it.saveFailed, entries, t, zone, it.conflict) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saved and deleted entries, for the host screen. */
    val events: Flow<EditEvent> = eventChannel.receiveAsFlow()

    /** Haptics to play (DESIGN_SPEC §5.11): nudges tick; a refused save rejects. */
    val haptics: Flow<HomeHaptic> = hapticChannel.receiveAsFlow()

    init {
        viewModelScope.launchMinuteTicker(clock, now)
    }

    /** Opens the sheet for [entryId] (a History row, a since-row, the Adjust card's Edit chip). */
    fun openEntry(entryId: String) = act {
        val entry = attempt { repository.get(entryId) }?.takeUnless { it.deleted } ?: return@act
        setSession(EditSession(++nextKey, EditMode.EDIT, entry, EditDraft.of(entry)))
    }

    /**
     * Opens "Add past entry" with Feed preselected at [defaultStart] (now − 15 min, DESIGN_SPEC §3.3.4). The feed type
     * starts as the last feed's, as the parent most likely adds another of those.
     */
    fun openNew(defaultStart: Long) = act {
        val start = EditRules.floorToMinute(minOf(defaultStart, clock.now()))
        val draft = EditRules.newDraft(Entry.newId(), EntryKind.FEED, start, clock.now(), lastFeedType())
        setSession(EditSession(++nextKey, EditMode.ADD, null, draft))
    }

    override fun onDismiss() = act { setSession(null) }

    override fun onSave() = act {
        val current = session.value ?: return@act
        val ui = EditRules.state(current.key, current.mode, current.draft, current.original, false, entries(), clock.now(), zone, current.conflict)
        if (!ui.canSave) {
            haptic(HomeHaptic.REJECT)
            return@act
        }
        val original = current.original
        if (current.mode == EditMode.EDIT && original != null) {
            if (EditRules.unchanged(current.draft, original)) {
                setSession(null)
                return@act
            }
            // The sheet holds the entry as it was when it opened. If the watch changed it meanwhile (e.g. Woke up on a
            // sleep whose start is being corrected here), do not overwrite that: show the merged version and let the
            // parent save again. A deleted entry is not brought back.
            val stored = attempt { repository.get(original.id) }
            if (stored == null || stored.deleted) {
                setSession(current.copy(saveFailed = false, conflict = EditConflict.DELETED))
                haptic(HomeHaptic.REJECT)
                return@act
            }
            if (EditDraft.of(stored) != EditDraft.of(original)) {
                val merged = EditRules.rebase(current.draft, original, stored)
                setSession(current.copy(original = stored, draft = merged, saveFailed = false, conflict = EditConflict.CHANGED))
                haptic(HomeHaptic.REJECT)
                return@act
            }
        }
        val entry = current.draft.toEntry(updatedAtMillis = clock.now())
        val written = attempt {
            when (current.mode) {
                EditMode.EDIT -> repository.update(entry)
                EditMode.ADD -> repository.add(entry)
            }
        }
        if (written == null) {
            session.update { it?.copy(saveFailed = true) }.also { persist() }
            haptic(HomeHaptic.REJECT)
            return@act
        }
        setSession(null)
        eventChannel.trySend(EditEvent.Saved(written))
    }

    override fun onDelete() = act {
        val current = session.value?.takeIf { it.mode == EditMode.EDIT } ?: return@act
        val previous = attempt { repository.delete(current.draft.id) }
        setSession(null)
        if (previous != null && !previous.deleted) eventChannel.trySend(EditEvent.Deleted(previous))
    }

    override fun onKind(kind: EntryKind) = edit { s ->
        if (s.mode != EditMode.ADD || s.draft.kind == kind) return@edit s.draft
        EditRules.newDraft(s.draft.id, kind, s.draft.start, clock.now(), lastFeedType())
    }

    override fun onFeedType(type: FeedType) = edit { s ->
        EditRules.withFeedType(s.draft, EditRules.tappedFeedType(s.draft, type), s.original, clock.now())
    }

    override fun onSideTick(breast: Breast) = edit { s -> EditRules.withSideTick(s.draft, breast) }

    override fun onNudge(field: TimeField, minutes: Int) = edit(HomeHaptic.SEGMENT_TICK) { s -> EditRules.nudge(s.draft, field, minutes) }

    override fun onTimeSet(field: TimeField, hour: Int, minute: Int) = edit { s -> EditRules.setTime(s.draft, field, hour, minute, zone) }

    override fun onDaySet(field: TimeField, date: LocalDate) = edit { s -> EditRules.setDay(s.draft, field, date, zone) }

    override fun onUseYesterday() = edit { s ->
        val issue = EditRules.validate(s.draft, entries(), clock.now()) as? EditIssue.InFuture ?: return@edit s.draft
        val day = localDate(issue.at, zone).minusDays(1)
        EditRules.setDay(s.draft, issue.field, day, zone)
    }

    override fun onEndNow() = edit { s ->
        if (s.draft.running) s.draft.copy(end = maxOf(s.draft.start, EditRules.floorToMinute(clock.now()))) else s.draft
    }

    override fun onPee(checked: Boolean) = edit { s -> if (s.draft.kind == EntryKind.DIAPER) s.draft.copy(pee = checked) else s.draft }

    override fun onPoop(checked: Boolean) = edit { s ->
        when {
            s.draft.kind != EntryKind.DIAPER -> s.draft
            // Colour and texture belong to poop only (V1_ADDITIONS §1).
            checked -> s.draft.copy(poop = true)
            else -> s.draft.copy(poop = false, color = null, texture = null)
        }
    }

    override fun onPoopColor(color: PoopColor?) = edit { s -> if (s.draft.poop) s.draft.copy(color = color) else s.draft }

    override fun onPoopTexture(texture: PoopTexture?) = edit { s -> if (s.draft.poop) s.draft.copy(texture = texture) else s.draft }

    // ---- Helpers ------------------------------------------------------------------------------

    /** Applies [transform] to the open draft; any change clears a refused save's message. */
    private fun edit(haptic: HomeHaptic? = null, transform: suspend (EditSession) -> EditDraft) = act {
        val current = session.value ?: return@act
        val next = transform(current)
        if (next != current.draft) {
            // A change clears a refused save and the "changed on the watch" note; a deletion there stays.
            setSession(current.copy(draft = next, saveFailed = false, conflict = current.conflict?.takeIf { it == EditConflict.DELETED }))
            haptic?.let(::haptic)
        }
    }

    private fun setSession(value: EditSession?) {
        session.value = value
        persist()
    }

    private fun persist() {
        savedState[KEY_SESSION] = session.value?.let { json.encodeToString(EditSession.serializer(), it) }
    }

    private suspend fun entries(): List<Entry> = repository.observeEntries().first()

    /** The last feed's type (null when it had none); Breast before the first feed. */
    private suspend fun lastFeedType(): FeedType? {
        val last = entries().filter { it.isFeed && it.startMillis <= clock.now() }.maxByOrNull { it.startMillis }
        return if (last == null) FeedType.BREAST else last.feedType
    }

    private fun haptic(type: HomeHaptic) {
        hapticChannel.trySend(type)
    }

    /** Runs [block] after the actions before it, then refreshes "now". */
    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            actions.withLock {
                now.value = clock.now()
                block()
                now.value = clock.now()
            }
        }
    }

    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val KEY_SESSION = "edit_session"
        private val json = Json { ignoreUnknownKeys = true }

        private fun decode(text: String): EditSession? =
            runCatching { json.decodeFromString(EditSession.serializer(), text) }.getOrNull()

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { EditViewModel(Core.repository, Core.clock, savedState = createSavedStateHandle()) }
        }
    }
}
