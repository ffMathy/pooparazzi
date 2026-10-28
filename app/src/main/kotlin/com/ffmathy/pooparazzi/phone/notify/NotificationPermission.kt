package com.ffmathy.pooparazzi.phone.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
import com.ffmathy.pooparazzi.phone.system.SystemSurfaces

/** Whether the app may post notifications (always below Android 13, where there is no runtime permission). */
fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Asks for the notification permission in context, the first time a sleep starts (DESIGN_SPEC §3.3.1,
 * §10.5), so the running timer can show its ongoing notification. Asked once: a denial is respected and the timers run
 * without a notification. When granted, the notification for the timer that just started is posted at once.
 *
 * Returns the function to call when a timer starts. It does nothing below Android 13.
 */
@Composable
fun rememberNotificationPermissionPrompt(): () -> Unit {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { {} }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) SystemSurfaces.refreshNotifications()
    }
    return remember(context, launcher) {
        {
            val prefs = SurfacePrefs(context)
            if (!context.hasNotificationPermission() && !prefs.notificationPermissionAsked) {
                prefs.notificationPermissionAsked = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
