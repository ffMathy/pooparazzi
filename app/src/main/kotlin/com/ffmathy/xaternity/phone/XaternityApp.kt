package com.ffmathy.xaternity.phone

import android.app.Application
import com.ffmathy.xaternity.core.Core

/** Builds the shared core graph (Room, DataStore settings, Data Layer sync) once per process. */
class XaternityApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Core.init(this)
    }
}
