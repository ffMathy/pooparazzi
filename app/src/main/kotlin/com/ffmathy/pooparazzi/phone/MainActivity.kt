package com.ffmathy.pooparazzi.phone

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.phone.app.AppLocales
import com.ffmathy.pooparazzi.phone.app.AppViewModel
import com.ffmathy.pooparazzi.phone.launch.DeepLinks
import com.ffmathy.pooparazzi.phone.navigation.PooparazziPhoneApp
import com.ffmathy.pooparazzi.phone.system.SystemSurfaces
import kotlinx.coroutines.launch

/**
 * The single activity. An [AppCompatActivity] so the in-app language works on API < 33 (V1_ADDITIONS §5).
 * Draws edge to edge; the system bar icons follow the app's own Night/Day, not the system theme. Deep links from the
 * widgets and notifications (`pooparazzi://home/…`) open Home with a sheet or an undo card.
 */
class MainActivity : AppCompatActivity() {
    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }
    private var appliedNight: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { appViewModel.appearance.value == null }
        enableEdgeToEdge()
        // A recreated activity (language change, process restore) gets its old intent again: handle it once only.
        if (savedInstanceState == null) handle(intent)
        setContent {
            val inputs by appViewModel.appearance.collectAsStateWithLifecycle()
            val launch by appViewModel.launch.collectAsStateWithLifecycle()
            val welcome by appViewModel.welcome.collectAsStateWithLifecycle()
            inputs?.let {
                PooparazziPhoneApp(
                    inputs = it,
                    clock = Core.clock,
                    onNightResolved = ::applyNight,
                    launch = launch,
                    onLaunchHandled = appViewModel::consume,
                    welcome = welcome,
                    onWelcomeDone = appViewModel::finishWelcome,
                    onLaunchedSheetClosed = ::onLaunchedSheetClosed,
                )
            }
        }
    }

    /**
     * A sheet a widget opened has closed. On a Samsung flip's cover screen (a display other than the main one) the
     * app has no room for Home, so it goes back to the widget there; on the main screen Home stays, with its undo card.
     */
    private fun onLaunchedSheetClosed() {
        if (onCoverScreen()) finish()
    }

    /** The window's display, as the view sees it (null before attach, which counts as the main screen). */
    private fun onCoverScreen(): Boolean =
        (window?.decorView?.display?.displayId ?: Display.DEFAULT_DISPLAY) != Display.DEFAULT_DISPLAY

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        DeepLinks.parse(intent?.data)?.let(appViewModel::request)
    }

    override fun onResume() {
        super.onResume()
        // Notifications may have been turned on in system settings while the app was away.
        SystemSurfaces.onAppResumed(this)
        // The backup pulls what changed elsewhere (another phone on the account) at most once a minute (§4.4).
        Core.graph.cloud.requestSync()
        // The language the app runs in is the platform's per-app choice (the parent may change it in system settings
        // on API 33+). Mirror it into the settings, which sync it to the watch (V1_ADDITIONS §5).
        val running = AppLocales.current()
        lifecycleScope.launch {
            val settings = Core.settings
            if (settings.current().language != running) settings.setLanguage(running)
        }
    }

    /** System bars and the next launch's splash follow the resolved appearance (DESIGN_SPEC §3.3.8, §8.3). */
    private fun applyNight(night: Boolean) {
        if (appliedNight == night) return
        appliedNight = night
        val transparent = Color.TRANSPARENT
        val style = if (night) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Persisted by the system for the next cold start: a dark splash after a night-time session even when the
            // system theme is light.
            splashScreen.setSplashScreenTheme(if (night) R.style.Theme_Pooparazzi_Starting_Night else R.style.Theme_Pooparazzi_Starting_Day)
        }
    }
}
