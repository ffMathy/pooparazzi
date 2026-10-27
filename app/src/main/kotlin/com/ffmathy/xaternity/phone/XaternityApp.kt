package com.ffmathy.xaternity.phone

import android.app.Application
import android.content.res.Configuration
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.phone.system.SystemSurfaces

/**
 * Builds the shared core graph (Room, DataStore settings, Data Layer sync) once per process and starts the system
 * surfaces (widgets, timer notifications) that follow it.
 */
class XaternityApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SystemSurfaces.start(this, Core.init(this))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The widgets resolve Auto with the system dark theme and speak the app language: re-render them.
        if (Core.isInitialized) SystemSurfaces.onConfigurationChanged(this, Core.graph)
    }
}
