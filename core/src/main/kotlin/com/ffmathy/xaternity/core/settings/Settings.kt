package com.ffmathy.xaternity.core.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
 * [updatedAtMillis]). [babyName] is empty until the parent sets it; show the "Baby" hint then.
 */
@Serializable
data class Settings(
    val babyName: String = "",
    val appearance: Appearance = Appearance.AUTO,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** 0 means "never changed on any device". */
    val updatedAtMillis: Long = 0L,
) {
    companion object {
        const val MAX_BABY_NAME_LENGTH = 40

        /** Trims and limits a name typed by the parent. */
        fun sanitizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_BABY_NAME_LENGTH)
    }
}

/** JSON form of [Settings] for the Data Layer. Lenient: unknown keys ignored, unknown enum values fall back to defaults. */
object SettingsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encode(settings: Settings): String = json.encodeToString(Settings.serializer(), settings)

    fun decodeOrNull(text: String): Settings? = runCatching { json.decodeFromString(Settings.serializer(), text) }.getOrNull()
}
