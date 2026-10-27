package com.ffmathy.xaternity.phone.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ffmathy.xaternity.core.settings.AppLanguage

/** Applies the in-app language (V1_ADDITIONS §5). An interface so tests can record calls. */
fun interface LocaleApplier {
    fun apply(language: AppLanguage)
}

/**
 * The per-app language through AppCompat: the system's per-app setting on API 33+, and AppCompat's own storage
 * (`autoStoreLocales`) below. Setting a different language recreates the activity.
 */
object AppLocales : LocaleApplier {
    override fun apply(language: AppLanguage) {
        val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
        if (AppCompatDelegate.getApplicationLocales() != locales) AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * The language the app actually runs in, as a setting: the platform's per-app choice (which the parent may also
     * change in system settings on API 33+), or [AppLanguage.SYSTEM] when none is set.
     */
    fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0]?.toLanguageTag())
    }
}
