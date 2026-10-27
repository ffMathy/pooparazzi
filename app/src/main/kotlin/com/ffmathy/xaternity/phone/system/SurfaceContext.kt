package com.ffmathy.xaternity.phone.system

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.ffmathy.xaternity.core.settings.AppLanguage

/**
 * A context whose resources speak the app's [language] (V1_ADDITIONS §5), for surfaces drawn outside an activity:
 * widgets and notifications. AppCompat applies the in-app language to activities only (below API 33), so the
 * application context may still be in the system language. [AppLanguage.SYSTEM] returns this context unchanged.
 */
fun Context.inAppLanguage(language: AppLanguage): Context {
    val tag = language.tag ?: return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList.forLanguageTags(tag))
    return createConfigurationContext(configuration)
}

/** Whether the system dark theme is on (an input of the Auto appearance, DESIGN_SPEC §8.1). */
val Context.systemDarkTheme: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
