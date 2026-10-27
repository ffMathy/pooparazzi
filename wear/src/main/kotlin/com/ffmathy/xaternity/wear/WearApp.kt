package com.ffmathy.xaternity.wear

import android.app.Application
import android.app.LocaleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.settings.SettingsRepository
import com.ffmathy.xaternity.wear.surface.SurfaceUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The watch application: builds the shared [Core] graph, follows the language chosen on the phone, and keeps the
 * tile, complications and Ongoing Activities in step with the data ([SurfaceUpdater]).
 */
class WearApp : Application() {
    private var surfaces: SurfaceUpdater? = null
    private val _timeSettingsChanges = MutableStateFlow(0)

    /**
     * Bumped when the time zone or the 12/24 h setting changes. Neither is a configuration change, and the activity
     * can live for days: the app re-reads both on each bump (see [MainViewModel]).
     */
    val timeSettingsChanges: StateFlow<Int> = _timeSettingsChanges.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        val graph = Core.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SyncedLanguage.follow(this, graph.settings, graph.scope)
        }
        surfaces = SurfaceUpdater(this, graph.repository, graph.settings, graph.clock).also { it.start(graph.scope) }
        // System broadcasts only (the 12/24 h toggle is sent as TIME_SET); nothing from other apps.
        val timeSettings = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(TimeSettingsReceiver(), timeSettings, RECEIVER_NOT_EXPORTED)
        } else {
            // Protected system broadcasts: no export flag (nor its signature permission) needed before API 33.
            registerReceiver(TimeSettingsReceiver(), timeSettings)
        }
    }

    /** A new language (synced or system) changes the text on every surface. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshSurfaces()
    }

    /** Rebuilds the tile, complications and Ongoing Activities (e.g. once notifications are allowed). */
    fun refreshSurfaces() {
        surfaces?.refresh()
    }

    /** A new time zone or 12/24 h setting: the Ongoing Activity's "since 02:05", the tile, complications and app. */
    internal fun onTimeSettingsChanged() {
        refreshSurfaces()
        _timeSettingsChanges.update { it + 1 }
    }

    /**
     * Brings the surfaces back after a reboot or an app update (see [SurfacesRestoreReceiver]): nothing else starts
     * the process then, and notifications do not survive either. [done] runs once they are up to date.
     */
    internal fun restoreSurfaces(done: () -> Unit) {
        val updater = surfaces ?: return done()
        Core.graph.scope.launch {
            try {
                updater.updateNow()
            } finally {
                done()
            }
        }
    }

    private inner class TimeSettingsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onTimeSettingsChanged()
    }
}

/**
 * Restores the Ongoing Activity of a running sleep or breastfeed after a reboot (overnight OS updates fall in the
 * sleep window) or an app update: notifications are gone then, and nothing else would start the process until the
 * user, a tile or a sync happens to. Not exported: only the system sends these.
 */
class SurfacesRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as? WearApp ?: return
        val pending = goAsync()
        app.restoreSurfaces { pending.finish() }
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
