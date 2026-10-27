package com.ffmathy.xaternity.wear

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The watch application: builds the shared [Core] graph and follows the language chosen on the phone. */
class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val graph = Core.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SyncedLanguage.follow(this, graph.settings, graph.scope)
        }
    }
}

/**
 * Applies the language the phone syncs through `/settings` (V1_ADDITIONS §5) with
 * [LocaleManager.setApplicationLocales] on API 33+; older watches follow the system. Settings never changed on any
 * device leave the watch's own per-app language alone.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object SyncedLanguage {
    fun follow(context: Context, settings: SettingsRepository, scope: CoroutineScope) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        scope.launch {
            settings.settings
                .filter { it.updatedAtMillis > 0 }
                .map { it.language }
                .distinctUntilChanged()
                .collect { language ->
                    val wanted = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
                    if (manager.applicationLocales != wanted) {
                        withContext(Dispatchers.Main) { manager.applicationLocales = wanted }
                    }
                }
        }
    }
}
