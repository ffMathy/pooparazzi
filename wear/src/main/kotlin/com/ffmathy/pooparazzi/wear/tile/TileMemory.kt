package com.ffmathy.pooparazzi.wear.tile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ffmathy.pooparazzi.wear.state.GuardKind

/**
 * What the tile remembers between requests: the service is created and destroyed by the system at will, so the
 * click ids already handled, the last confirmation, the guard and the last toggle time live in preferences.
 */
interface TileMemory {
    /**
     * Claims a clickable id for handling. Returns false when it was handled before: the tile host may send the same
     * `lastClickableId` again (e.g. with a later refresh), and a replay must never log twice (DESIGN_SPEC §10.7).
     */
    fun claim(clickId: String): Boolean

    /** A fresh token for the clickable ids of the next layout, so every tap on a new layout is a new id. */
    fun nextToken(): Long

    var feedback: TileFeedback?
    var guard: TileGuard?

    /** When the tile last toggled the sleep (start or end), for the 2 s double-toggle guard. */
    var lastSleepToggleAt: Long
}

/** [TileMemory] in a private preferences file. */
class PreferencesTileMemory(private val prefs: SharedPreferences) : TileMemory {

    override fun claim(clickId: String): Boolean = synchronized(LOCK) {
        val handled = prefs.getString(KEY_HANDLED, "").orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }
        if (clickId in handled) return false
        val next = (handled + clickId).takeLast(MAX_HANDLED)
        prefs.edit(commit = true) { putString(KEY_HANDLED, next.joinToString(SEPARATOR)) }
        true
    }

    override fun nextToken(): Long = synchronized(LOCK) {
        val next = prefs.getLong(KEY_TOKEN, 0L) + 1
        prefs.edit { putLong(KEY_TOKEN, next) }
        next
    }

    override var feedback: TileFeedback?
        get() {
            val kind = prefs.getString(KEY_FEEDBACK_KIND, null)?.let { name -> TileFeedbackKind.entries.firstOrNull { it.name == name } }
            val id = prefs.getString(KEY_FEEDBACK_ENTRY, null)
            if (kind == null || id == null) return null
            return TileFeedback(
                kind = kind,
                entryId = id,
                atMillis = prefs.getLong(KEY_FEEDBACK_AT, 0L),
                untilMillis = prefs.getLong(KEY_FEEDBACK_UNTIL, 0L),
                babyId = prefs.getString(KEY_FEEDBACK_BABY, null),
            )
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_FEEDBACK_KIND)
                remove(KEY_FEEDBACK_ENTRY)
                remove(KEY_FEEDBACK_AT)
                remove(KEY_FEEDBACK_UNTIL)
                remove(KEY_FEEDBACK_BABY)
            } else {
                putString(KEY_FEEDBACK_KIND, value.kind.name)
                putString(KEY_FEEDBACK_ENTRY, value.entryId)
                putLong(KEY_FEEDBACK_AT, value.atMillis)
                putLong(KEY_FEEDBACK_UNTIL, value.untilMillis)
                putString(KEY_FEEDBACK_BABY, value.babyId)
            }
        }

    override var guard: TileGuard?
        get() {
            val kind = prefs.getString(KEY_GUARD_KIND, null)?.let { name -> GuardKind.entries.firstOrNull { it.name == name } }
            return kind?.let { TileGuard(it, prefs.getLong(KEY_GUARD_UNTIL, 0L)) }
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_GUARD_KIND)
                remove(KEY_GUARD_UNTIL)
            } else {
                putString(KEY_GUARD_KIND, value.kind.name)
                putLong(KEY_GUARD_UNTIL, value.untilMillis)
            }
        }

    override var lastSleepToggleAt: Long
        get() = prefs.getLong(KEY_LAST_SLEEP_TOGGLE, Long.MIN_VALUE / 2)
        set(value) = prefs.edit { putLong(KEY_LAST_SLEEP_TOGGLE, value) }

    companion object {
        private const val FILE = "pooparazzi_tile"
        private const val SEPARATOR = "|"
        private const val MAX_HANDLED = 16
        private const val KEY_HANDLED = "handled_clicks"
        private const val KEY_TOKEN = "layout_token"
        private const val KEY_FEEDBACK_KIND = "feedback_kind"
        private const val KEY_FEEDBACK_ENTRY = "feedback_entry"
        private const val KEY_FEEDBACK_AT = "feedback_at"
        private const val KEY_FEEDBACK_UNTIL = "feedback_until"
        private const val KEY_FEEDBACK_BABY = "feedback_baby"
        private const val KEY_GUARD_KIND = "guard_kind"
        private const val KEY_GUARD_UNTIL = "guard_until"
        private const val KEY_LAST_SLEEP_TOGGLE = "last_sleep_toggle"
        private val LOCK = Any()

        fun of(context: Context): PreferencesTileMemory =
            PreferencesTileMemory(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}

/** [TileMemory] in memory (tests). */
class InMemoryTileMemory : TileMemory {
    private val handled = ArrayDeque<String>()
    private var token = 0L

    override fun claim(clickId: String): Boolean {
        if (clickId in handled) return false
        handled.addLast(clickId)
        return true
    }

    override fun nextToken(): Long = ++token

    override var feedback: TileFeedback? = null
    override var guard: TileGuard? = null
    override var lastSleepToggleAt: Long = Long.MIN_VALUE / 2
}
