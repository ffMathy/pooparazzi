package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.content.res.Configuration
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.system.SystemSurfaces

/**
 * Builds the shared core graph (Room, DataStore settings, Data Layer sync, and the backup when the build has a
 * Firebase configuration) once per process and starts the system surfaces (widgets, timer notifications) that follow
 * it.
 */
class PooparazziApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The optional account and backup (BABIES_AND_BACKUP §3–4): null in builds without a Firebase configuration.
        val backend = AppAccounts.init(this)
        val graph = Core.init(this, cloud = backend)
        // The phone keeps a baby for every baby id among its entries (BABIES_AND_BACKUP §2.1); the watch never adopts.
        graph.adoptBabiesFromEntries()
        SystemSurfaces.start(this, graph)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The widgets resolve Auto with the system dark theme and speak the app language: re-render them.
        if (Core.isInitialized) SystemSurfaces.onConfigurationChanged(this, Core.graph)
    }
}
