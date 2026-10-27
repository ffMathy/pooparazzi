package com.ffmathy.pooparazzi.core.data

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryJson

/**
 * The merge rule both devices apply to two versions of one entry. The version with the newer
 * [Entry.updatedAtMillis] wins. On a tie with different content, the version whose JSON encoding is
 * lexicographically larger wins, so both devices pick the same one and cannot swap versions forever.
 * An identical version never "wins" (nothing to write).
 */
internal object LastWriterWins {
    fun incomingWins(stored: Entry, incoming: Entry): Boolean = when {
        incoming.updatedAtMillis > stored.updatedAtMillis -> true
        incoming.updatedAtMillis < stored.updatedAtMillis -> false
        incoming == stored -> false
        else -> EntryJson.encode(incoming) > EntryJson.encode(stored)
    }
}
