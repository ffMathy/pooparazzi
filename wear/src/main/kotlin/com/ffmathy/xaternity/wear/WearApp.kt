package com.ffmathy.xaternity.wear

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.settings.SettingsRepository
import com.ffmathy.xaternity.wear.surface.SurfaceUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The watch application: builds the shared [Core] graph, follows the language chosen on the phone, and keeps the
 * tile, complications and Ongoing Activities in step with the data ([SurfaceUpdater]).
 */
class WearApp : Application() {
    private var surfaces: SurfaceUpdater? = null

    override fun onCreate() {
        super.onCreate()
        val graph = Core.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SyncedLanguage.follow(this, graph.settings, graph.scope)
        }
        surfaces = SurfaceUpdater(this, graph.repository, graph.settings, graph.clock).also { it.start(graph.scope) }
    }

    /** A new language (synced or system), time zone or 12/24 h setting changes the text on every surface. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshSurfaces()
    }

    /** Rebuilds the tile, complications and Ongoing Activities (e.g. once notifications are allowed). */
    fun refreshSurfaces() {
        surfaces?.refresh()
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
