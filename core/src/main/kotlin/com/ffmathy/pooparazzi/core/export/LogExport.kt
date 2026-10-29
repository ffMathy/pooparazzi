package com.ffmathy.pooparazzi.core.export

import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Settings › Privacy › "Download my data" (GDPR art. 15 and 20, docs/PRIVACY.md): everything the app holds about the
 * log, in a plain machine-readable file. The babies with their names (which never leave the parent's devices
 * otherwise) and every entry that is not deleted, of every baby, in the same JSON shape as the backup's
 * ([com.ffmathy.pooparazzi.core.model.EntryJson]). Times are epoch milliseconds, UTC.
 */
@Serializable
data class LogExport(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAtMillis: Long,
    val babies: List<Baby>,
    val entries: List<Entry>,
) {
    companion object {
        const val FORMAT = "pooparazzi-log"
        const val VERSION = 1

        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
        }

        /** The file's text: [entries] oldest first. */
        fun encode(babies: List<Baby>, entries: List<Entry>, exportedAtMillis: Long): String = json.encodeToString(
            serializer(),
            LogExport(exportedAtMillis = exportedAtMillis, babies = babies, entries = entries.sortedBy { it.startMillis }),
        )

        fun decode(text: String): LogExport = json.decodeFromString(serializer(), text)
    }
}
