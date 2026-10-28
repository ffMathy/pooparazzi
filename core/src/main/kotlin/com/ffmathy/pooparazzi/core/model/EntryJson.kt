package com.ffmathy.pooparazzi.core.model

import kotlinx.serialization.json.Json

/**
 * The JSON form of an [Entry] used on the Wearable Data Layer (`/entries/<id>`).
 * Lenient on input: unknown keys are ignored and unknown values of optional enums become null,
 * so an older app can read entries written by a newer one. Decoded entries are [normalized], so a timed breastfeed
 * from an older app reads as an instant.
 */
object EntryJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encode(entry: Entry): String = json.encodeToString(Entry.serializer(), entry)

    /** Decodes an entry; throws [kotlinx.serialization.SerializationException] or [IllegalArgumentException] on bad input. */
    fun decode(text: String): Entry = json.decodeFromString(Entry.serializer(), text).normalized()

    /** Decodes an entry, or returns null when [text] is not a valid entry. */
    fun decodeOrNull(text: String): Entry? = runCatching { decode(text) }.getOrNull()
}
