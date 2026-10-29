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
 * [updatedAtMillis]). They are never uploaded to the backend, so the babies' names stay on the parent's devices.
 *
 * [babies] are listed in the order they were added; there is always at least one ([normalized]). Only the phone edits
 * the list. Each device has its own current baby, [selectedBabyId], which is never synced or merged.
 */
@Serializable
data class Settings(
    val babies: List<Baby> = listOf(Baby(Baby.FIRST_ID)),
    val appearance: Appearance = Appearance.AUTO,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** 0 means "never changed on any device". */
    val updatedAtMillis: Long = 0L,
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

    /**
     * These settings as stored: babies with a blank id are dropped, of babies sharing an id only the first is kept,
     * names are [sanitizeName]d, and an empty list becomes the first baby alone. [selectedBabyId] is left as is.
     */
    fun normalized(): Settings {
        val clean = babies.filter { it.id.isNotBlank() }.distinctBy { it.id }.map { it.copy(name = sanitizeName(it.name)) }
        return copy(babies = clean.ifEmpty { listOf(Baby(Baby.FIRST_ID)) })
    }

    /** These settings with baby [id] renamed to [name] ([sanitizeName]d); unchanged for an unknown id. */
    fun withBabyName(id: String, name: String): Settings =
        copy(babies = babies.map { if (it.id == id) it.copy(name = sanitizeName(name)) else it })

    /** These settings with unnamed babies appended for the unknown, non-blank [ids], in the given order. */
    fun withBabies(ids: Collection<String>): Settings {
        val known = babies.mapTo(mutableSetOf()) { it.id }
        val added = ids.filter { it.isNotBlank() && known.add(it) }.map { Baby(it) }
        return if (added.isEmpty()) this else copy(babies = babies + added)
    }

    /**
     * These settings with what a device from before several babies sent ([SettingsJson.decodeRemoteOrNull] with
     * `legacy`), or null when nothing is taken. Such a device knows one baby only, so the list is never replaced: only
     * the [legacyBaby]'s name and the appearance and language are taken, and only from strictly newer settings (an older
     * device echoes the stamp it was sent). A blank name never erases one: names are kept on the parent's devices alone
     * and cannot be recovered.
     */
    fun withLegacyRemote(remote: Settings): Settings? {
        if (remote.updatedAtMillis <= updatedAtMillis) return null
        val name = remote.babies.first().name
        val renamed = if (name.isBlank()) this else withBabyName(legacyBaby.id, name)
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
 * name, so they keep it. Decoded settings are [Settings.normalized].
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
