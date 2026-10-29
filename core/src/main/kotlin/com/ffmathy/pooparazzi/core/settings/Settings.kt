package com.ffmathy.pooparazzi.core.settings

import com.ffmathy.pooparazzi.core.model.Baby
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Appearance setting (DESIGN_SPEC §8.1). The watch is always dark and ignores it. */
@Serializable
enum class Appearance { AUTO, NIGHT, DAY }

/** In-app language (V1_ADDITIONS §5). [tag] is the BCP 47 tag for the locale APIs; null follows the system. */
@Serializable
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    EN("en"),
    DA("da"),
    ;

    companion object {
        /** The setting for a locale tag such as "da" or "da-DK"; [SYSTEM] for null, empty or unsupported tags. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return entries.firstOrNull { it.tag != null && it.tag == language } ?: SYSTEM
        }
    }
}

/**
 * User settings, mirrored between phone and watch through the `/settings` data item (last-writer-wins on
 * [updatedAtMillis]). Only the [babies] go further: with an account, the phone backs them up (names included) to the
 * account's family, where every member sees them, and merges the family's babies in one by one ([Baby.winsOver]).
 * The appearance and the language never leave the parent's devices.
 *
 * [babies] are listed in the order they were added (or arrived); there is always at least one ([normalized]). Only the
 * phone edits the list. [removedBabies] are the tombstones of the babies the parent removed ([Baby.removed]): no baby
 * is in both lists, and a removed baby comes back only when a later version of it wins ([withMergedBabies]). Each
 * device has its own current baby, [selectedBabyId], which is never synced or merged.
 */
@Serializable
data class Settings(
    val babies: List<Baby> = listOf(Baby(Baby.FIRST_ID)),
    val appearance: Appearance = Appearance.AUTO,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** 0 means "never changed on any device". */
    val updatedAtMillis: Long = 0L,
    /** The removed babies' tombstones, in the order they were removed (or arrived). */
    val removedBabies: List<Baby> = emptyList(),
    /** This device's current baby. Local to the device: never synced (hence @Transient) and never merged. */
    @Transient val selectedBabyId: String = Baby.FIRST_ID,
) {
    /** The baby this device shows and logs for: the one with [selectedBabyId], else the first. */
    val selectedBaby: Baby
        get() = babies.firstOrNull { it.id == selectedBabyId } ?: babies.firstOrNull() ?: Baby(Baby.FIRST_ID)

    /** The current baby's name; empty until the parent sets it (show its label then, see `LabelFormatter.baby`). */
    val babyName: String get() = selectedBaby.name

    /** True with two or more babies, when the switcher and the extra name lines appear (BABIES_AND_BACKUP §2.2). */
    val hasSeveralBabies: Boolean get() = babies.size > 1

    /** The babies and the removed babies' tombstones: every baby the family's backup knows of. */
    val allBabies: List<Baby> get() = babies + removedBabies

    /** True when [babies] can lose one: there is more than one ([withRemovedBaby]). */
    val canRemoveBaby: Boolean get() = babies.size > 1

    /**
     * These settings as stored: babies with a blank id are dropped, of babies sharing an id only the first is kept,
     * names are [sanitizeName]d, and an empty list becomes the first baby alone. Tombstones are [Baby.removed] and
     * nameless, and one whose baby is in [babies] is dropped. [selectedBabyId] is left as is.
     */
    fun normalized(): Settings {
        val clean = babies.filter { it.id.isNotBlank() }.distinctBy { it.id }
            .map { it.copy(name = sanitizeName(it.name), removed = false) }
            .ifEmpty { listOf(Baby(Baby.FIRST_ID)) }
        val live = clean.mapTo(mutableSetOf()) { it.id }
        val gone = removedBabies.filter { it.id.isNotBlank() && it.id !in live }.distinctBy { it.id }
            .map { it.copy(name = "", removed = true) }
        return copy(babies = clean, removedBabies = gone)
    }

    /**
     * These settings with baby [id] renamed to [name] ([sanitizeName]d); unchanged for an unknown id or the same name.
     * A positive [atMillis] stamps the renamed baby ([Baby.updatedAtMillis]), at least one past its stamp so far so the
     * rename wins everywhere; 0 leaves its stamp as it is.
     */
    fun withBabyName(id: String, name: String, atMillis: Long = 0L): Settings {
        val clean = sanitizeName(name)
        return copy(
            babies = babies.map {
                when {
                    it.id != id || it.name == clean -> it
                    atMillis > 0 -> it.copy(name = clean, updatedAtMillis = maxOf(atMillis, it.updatedAtMillis + 1))
                    else -> it.copy(name = clean)
                }
            },
        )
    }

    /**
     * These settings with unnamed babies appended for the unknown (and not removed), non-blank [ids], in the given order, stamped
     * [atMillis] (0, unstamped, for babies adopted from their entries: any stamped version of them wins).
     */
    fun withBabies(ids: Collection<String>, atMillis: Long = 0L): Settings {
        // A removed baby's entries never bring it back.
        val known = (babies + removedBabies).mapTo(mutableSetOf()) { it.id }
        val added = ids.filter { it.isNotBlank() && known.add(it) }.map { Baby(it, updatedAtMillis = atMillis) }
        return if (added.isEmpty()) this else copy(babies = babies + added)
    }

    /**
     * These settings with [incoming] babies (from the family's backup, or tombstones from the other device) merged in,
     * one by one: unknown babies are appended, and a version that [Baby.winsOver] the stored one replaces it in its
     * place. A winning tombstone moves its baby to [removedBabies] (never the last baby, which stays), and a winning
     * live version of a removed baby brings it back at the end. Names are [sanitizeName]d.
     */
    fun withMergedBabies(incoming: Collection<Baby>): Settings {
        val live = babies.toMutableList()
        val gone = removedBabies.toMutableList()
        for (raw in incoming) {
            if (raw.id.isBlank()) continue
            val baby = if (raw.removed) raw.copy(name = "") else raw.copy(name = sanitizeName(raw.name))
            val liveAt = live.indexOfFirst { it.id == baby.id }
            val goneAt = gone.indexOfFirst { it.id == baby.id }
            val stored = live.getOrNull(liveAt) ?: gone.getOrNull(goneAt)
            if (stored != null && !baby.winsOver(stored)) continue
            when {
                baby.removed && liveAt >= 0 && live.size == 1 -> continue
                baby.removed -> {
                    if (liveAt >= 0) live.removeAt(liveAt)
                    if (goneAt >= 0) gone[goneAt] = baby else gone += baby
                }
                else -> {
                    if (goneAt >= 0) gone.removeAt(goneAt)
                    if (liveAt >= 0) live[liveAt] = baby else live += baby
                }
            }
        }
        return if (live == babies && gone == removedBabies) this else copy(babies = live, removedBabies = gone)
    }

    /**
     * These settings with baby [id] removed: it leaves [babies] and its tombstone ([Baby.tombstone], stamped [atMillis])
     * joins [removedBabies], and the first baby becomes the current one if it was. Unchanged for an unknown id and for
     * the last baby (there is always one).
     */
    fun withRemovedBaby(id: String, atMillis: Long): Settings {
        val baby = babies.firstOrNull { it.id == id } ?: return this
        if (!canRemoveBaby) return this
        val left = babies - baby
        return copy(
            babies = left,
            removedBabies = removedBabies.filter { it.id != id } + baby.tombstone(atMillis),
            selectedBabyId = if (selectedBabyId == id) left.first().id else selectedBabyId,
        )
    }

    /**
     * These settings with what a device from before several babies sent ([SettingsJson.decodeRemoteOrNull] with
     * `legacy`), or null when nothing is taken. Such a device knows one baby only, so the list is never replaced: only
     * the [legacyBaby]'s name and the appearance and language are taken, and only from strictly newer settings (an older
     * device echoes the stamp it was sent). A blank name never erases one. A taken name is stamped with the remote
     * settings' stamp, so it goes on to the family like a rename on this phone.
     */
    fun withLegacyRemote(remote: Settings): Settings? {
        if (remote.updatedAtMillis <= updatedAtMillis) return null
        val name = remote.babies.first().name
        val renamed = if (name.isBlank()) this else withBabyName(legacyBaby.id, name, remote.updatedAtMillis)
        val next = renamed.copy(appearance = remote.appearance, language = remote.language, updatedAtMillis = remote.updatedAtMillis)
        return next.takeIf { it.copy(updatedAtMillis = updatedAtMillis) != this }
    }

    /** The baby a device from before several babies knows as its one baby: the first baby. */
    val legacyBaby: Baby get() = babies.firstOrNull { it.id == Baby.FIRST_ID } ?: babies.first()

    /** These settings with [id] as the current baby; unchanged for an unknown id. */
    fun withSelectedBaby(id: String): Settings =
        if (babies.any { it.id == id }) copy(selectedBabyId = id) else this

    companion object {
        const val MAX_BABY_NAME_LENGTH = 40

        /** The most babies the parent can add (twins, triplets and siblings, with room to spare). */
        const val MAX_BABIES = 8

        /** Trims and limits a name typed by the parent. */
        fun sanitizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_BABY_NAME_LENGTH)
    }
}

/**
 * JSON form of [Settings] for the Data Layer. Lenient: unknown keys ignored, unknown enum values fall back to defaults.
 * [Settings.selectedBabyId] is never written. Settings from a device from before several babies (no `babies`) read as
 * the first baby with their `babyName`; for such devices the JSON also carries `babyName`, the [Settings.legacyBaby]'s
 * name, so they keep it. Each baby carries its [Baby.updatedAtMillis] (babies from before families read as 0); the
 * phone's flags for the family's backup are never written. Decoded settings are [Settings.normalized].
 */
object SettingsJson {
    /** Settings as received, and whether they came in the form of a device from before several babies. */
    data class Remote(val settings: Settings, val legacy: Boolean)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encode(settings: Settings): String {
        val element = json.encodeToJsonElement(Settings.serializer(), settings).jsonObject
        return JsonObject(element + (LEGACY_BABY_NAME to JsonPrimitive(settings.legacyBaby.name))).toString()
    }

    fun decodeOrNull(text: String): Settings? = decodeRemoteOrNull(text)?.settings

    /** [decodeOrNull], telling settings from a device from before several babies (no `babies`) apart. */
    fun decodeRemoteOrNull(text: String): Remote? = runCatching {
        val element = json.parseToJsonElement(text).jsonObject
        val legacy = BABIES !in element
        val legacyName = (element[LEGACY_BABY_NAME] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val upgraded = if (legacy && legacyName != null) {
            JsonObject(element + (BABIES to json.encodeToJsonElement(BABY_LIST, listOf(Baby(Baby.FIRST_ID, legacyName)))))
        } else {
            element
        }
        Remote(json.decodeFromJsonElement(Settings.serializer(), upgraded).normalized(), legacy)
    }.getOrNull()

    /** The JSON of a list of babies (the settings repository stores them so). */
    fun encodeBabies(babies: List<Baby>): String = json.encodeToString(BABY_LIST, babies)

    /** A list of babies from [encodeBabies], or null when [text] is not one. */
    fun decodeBabiesOrNull(text: String): List<Baby>? = runCatching { json.decodeFromString(BABY_LIST, text) }.getOrNull()

    private const val BABIES = "babies"
    private const val LEGACY_BABY_NAME = "babyName"
    private val BABY_LIST = ListSerializer(Baby.serializer())
}
