package com.ffmathy.pooparazzi.core.export

import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BabyJson
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Settings › About › "Download my data" (GDPR art. 15 and 20, docs/PRIVACY.md): everything the app holds about the
 * log, in plain machine-readable files. The babies with their names (which never leave the parent's devices
 * otherwise) and every entry that is not deleted, of every baby, in the same JSON shape as the backup's
 * ([com.ffmathy.pooparazzi.core.model.EntryJson]). Times are epoch milliseconds, UTC.
 *
 * The download is one ZIP file ([write]) holding [MANIFEST] (format, version and when), [BABIES], [ENTRIES] and a
 * [README] that explains them. "Import data" reads it back ([read]), and also the single JSON file of version 1.
 */
@Serializable
data class LogExport(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAtMillis: Long,
    val babies: List<Baby>,
    val entries: List<Entry>,
) {
    /** [MANIFEST]: what the ZIP is, without its data. A file without a [format] is not an export. */
    @Serializable
    private data class Manifest(val format: String = "", val version: Int = 1, val exportedAtMillis: Long = 0L)

    companion object {
        const val FORMAT = "pooparazzi-log"
        const val VERSION = 2

        const val MANIFEST = "manifest.json"
        const val BABIES = "babies.json"
        const val ENTRIES = "entries.json"
        const val README = "README.txt"

        /** A ZIP file starts with "PK", 3, 4. */
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        /** No file in an import may be larger than this (a log of years is a few MB). */
        const val MAX_FILE_BYTES = 64L * 1024 * 1024

        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        /** Writes the ZIP to [out] (left open): [entries] oldest first. */
        fun write(out: OutputStream, babies: List<Baby>, entries: List<Entry>, exportedAtMillis: Long) {
            val zip = ZipOutputStream(out)
            fun file(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name).apply { time = exportedAtMillis })
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            file(MANIFEST, json.encodeToString(Manifest.serializer(), Manifest(FORMAT, VERSION, exportedAtMillis)))
            file(BABIES, json.encodeToString(ListSerializer(Baby.serializer()), babies))
            file(ENTRIES, json.encodeToString(ListSerializer(Entry.serializer()), entries.sortedBy { it.startMillis }))
            file(README, README_TEXT)
            zip.finish()
        }

        /**
         * Reads an export from [input]: the ZIP of [write], or the single JSON file of version 1. Entries are
         * normalized like the Data Layer's ([EntryJson]); entries and babies that cannot be read are skipped.
         *
         * @throws IOException when [input] is not a Pooparazzi export.
         */
        fun read(input: InputStream): LogExport {
            val buffered = BufferedInputStream(input)
            buffered.mark(ZIP_MAGIC.size)
            val magic = ByteArray(ZIP_MAGIC.size) { buffered.read().toByte() }
            buffered.reset()
            val isZip = magic.contentEquals(ZIP_MAGIC)
            return if (isZip) readZip(ZipInputStream(buffered)) else decode(buffered.readLimited().toString(Charsets.UTF_8))
        }

        /** The single JSON file of version 1 (and the same object in one file). */
        fun decode(text: String): LogExport {
            val root = parse(text) as? JsonObject ?: throw IOException("Not a Pooparazzi export")
            val manifest = manifest(root)
            return LogExport(
                version = manifest.version,
                exportedAtMillis = manifest.exportedAtMillis,
                babies = babies(root["babies"]),
                entries = entries(root["entries"]),
            )
        }

        private fun readZip(zip: ZipInputStream): LogExport {
            val files = mutableMapOf<String, String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && name in setOf(MANIFEST, BABIES, ENTRIES)) {
                    files[name] = zip.readLimited().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
            val manifest = manifest(parse(files[MANIFEST] ?: throw IOException("No $MANIFEST")) as? JsonObject)
            return LogExport(
                version = manifest.version,
                exportedAtMillis = manifest.exportedAtMillis,
                babies = babies(files[BABIES]?.let(::parse)),
                entries = entries(files[ENTRIES]?.let(::parse)),
            )
        }

        private fun parse(text: String): JsonElement =
            try {
                json.parseToJsonElement(text)
            } catch (e: IllegalArgumentException) {
                throw IOException("Not JSON", e)
            }

        private fun manifest(element: JsonObject?): Manifest {
            val manifest = element?.let { runCatching { json.decodeFromJsonElement(Manifest.serializer(), it) }.getOrNull() }
            if (manifest == null || manifest.format != FORMAT) throw IOException("Not a Pooparazzi export")
            return manifest
        }

        private fun babies(element: JsonElement?): List<Baby> =
            (element as? JsonArray).orEmpty().mapNotNull { BabyJson.decodeOrNull(it.toString()) }

        private fun entries(element: JsonElement?): List<Entry> =
            (element as? JsonArray).orEmpty().mapNotNull { EntryJson.decodeOrNull(it.toString()) }

        /** Reads to the end, failing beyond [MAX_FILE_BYTES]. */
        private fun InputStream.readLimited(): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val n = read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw IOException("File too large")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        private val README_TEXT = """
            Pooparazzi: your data
            =====================

            This ZIP file holds everything Pooparazzi keeps about your log. Settings > About > Import data in the app
            reads it back, and merges it with the log on that phone (the newest version of each entry wins).

            $MANIFEST  what this file is ("format", "version") and when it was made ("exportedAtMillis").
            $BABIES    the babies: "id", "name" and "updatedAtMillis" (when the name last changed).
            $ENTRIES   every entry that is not deleted, of every baby, oldest first. "babyId" says whose it is,
                           "kind" is FEED, SLEEP or DIAPER, and the other fields are the details logged with it.

            Times are milliseconds since 1 January 1970, UTC.
        """.trimIndent() + "\n"
    }
}
