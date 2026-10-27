package com.ffmathy.xaternity.wear.ongoing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ffmathy.xaternity.wear.WearApp

/**
 * Asks for `POST_NOTIFICATIONS` (API 33+) in context: the first time a sleep or breastfeed runs, since the Ongoing
 * Activity is what shows it on the watch face (DESIGN_SPEC §10.5 asks the same on the phone). Asked once; a refusal
 * only hides the Ongoing Activity, logging works as usual. On grant the activity appears at once.
 */
@Composable
fun NotificationPermissionRequest(timerRunning: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) (context.applicationContext as? WearApp)?.refreshSurfaces()
    }
    LaunchedEffect(timerRunning) {
        if (!timerRunning || context.notificationsGranted() || context.alreadyAsked()) return@LaunchedEffect
        context.markAsked()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val PREFS = "xaternity_wear"
private const val KEY_ASKED = "notifications_asked"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Context.notificationsGranted(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.alreadyAsked(): Boolean = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

private fun Context.markAsked() = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }
