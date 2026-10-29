package com.ffmathy.pooparazzi.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A baby whose log the app keeps (BABIES_AND_BACKUP §2.1). [id] is opaque and stable across devices, and it is what
 * every [Entry.babyId] refers to. [name] is empty until the parent sets one. It stays on the parent's own devices:
 * it is synced to the watch inside the settings and never uploaded to the backend.
 */
@Serializable
data class Baby(val id: String, val name: String = "") {
    companion object {
        /** The id of the first baby: every install from before several babies, and every new install. */
        const val FIRST_ID = "default"

        /** A new random UUID for a baby id. */
        fun newId(): String = UUID.randomUUID().toString()
    }
}
