package com.ffmathy.pooparazzi.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * A baby whose log the app keeps (BABIES_AND_BACKUP §2.1). [id] is opaque and stable across devices, and it is what
 * every [Entry.babyId] refers to. [name] is empty until the parent sets one. It is synced to the watch inside the
 * settings and, with an account, backed up to the account's family, whose members all see it (FAMILIES).
 *
 * [updatedAtMillis] is when the phone last added or renamed the baby: babies merge one by one, last writer wins
 * ([winsOver]). 0 means "never stamped": a baby from before families, or one adopted from its entries (it carries no
 * name, so any stamped version wins over it).
 */
@Serializable
data class Baby(val id: String, val name: String = "", val updatedAtMillis: Long = 0L) {

    /**
     * True when this version of the baby beats [other] (the same baby): the later stamp wins; on equal stamps the
     * greater name, so every device settles on the same version, and a name always beats no name.
     */
    fun winsOver(other: Baby): Boolean =
        updatedAtMillis > other.updatedAtMillis || (updatedAtMillis == other.updatedAtMillis && name > other.name)

    companion object {
        /**
         * The id of the first baby: every install from before several babies, and every new install. So the first
         * babies of two family members are one baby in the family (usually the same child).
         */
        const val FIRST_ID = "default"

        /** A new random UUID for a baby id. */
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * The JSON form of a [Baby] in the family's backup (`families/{fid}/babies/{id}`): `{"id","name","updatedAtMillis"}`.
 * Lenient on input like [EntryJson]: unknown keys are ignored, so an older app reads babies written by a newer one.
 */
object BabyJson {
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encode(baby: Baby): String = json.encodeToString(Baby.serializer(), baby)

    /** Decodes a baby, or returns null when [text] is not one (or its id is blank). */
    fun decodeOrNull(text: String): Baby? =
        runCatching { json.decodeFromString(Baby.serializer(), text) }.getOrNull()?.takeIf { it.id.isNotBlank() }
}
