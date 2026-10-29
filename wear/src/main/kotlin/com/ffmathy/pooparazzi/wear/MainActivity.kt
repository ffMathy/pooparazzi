package com.ffmathy.pooparazzi.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.AmbientModeManager
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.wear.ongoing.NotificationPermissionRequest
import com.ffmathy.pooparazzi.wear.phone.RemotePhoneLink
import com.ffmathy.pooparazzi.wear.state.MainMode
import com.ffmathy.pooparazzi.wear.theme.AmbientSupport
import com.ffmathy.pooparazzi.wear.theme.WearableAmbientModeManager
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.ui.XTimeText
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The watch app's only activity: Material 3 for Wear in the fixed dark theme, the system TimeText and the
 * navigation graph. External entry points (the Tile, complications, the Ongoing Activity) pass [EXTRA_OPEN] (and
 * [EXTRA_ENTRY_ID]).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val deepLinks = MutableStateFlow<WearDeepLink?>(null)

    /** Constant for the process, so the composition below always takes the same branch. */
    private val ambientSupport = AmbientSupport.detect()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Core.init(applicationContext)
        if (savedInstanceState == null) deepLinks.value = deepLinkOf(intent)
        val phoneLink = RemotePhoneLink(applicationContext)
        // Wear OS 3–5: always-on through the wearable library's observer, registered while the activity is created.
        val wearableAmbient = if (ambientSupport == AmbientSupport.WEARABLE_LIBRARY) {
            WearableAmbientModeManager().also { lifecycle.addObserver(AmbientLifecycleObserver(this, it)) }
        } else {
            null
        }
        setContent {
            // Only Wear OS 6+ has the Wear SDK ambient service that rememberAmbientModeManager() needs (it throws
            // NoClassDefFoundError elsewhere); every screen handles a missing manager as "interactive".
            val ambient: AmbientModeManager? = when (ambientSupport) {
                AmbientSupport.WEAR_SDK -> rememberAmbientModeManager()
                AmbientSupport.WEARABLE_LIBRARY -> wearableAmbient
                AmbientSupport.NONE -> null
            }
            CompositionLocalProvider(LocalAmbientModeManager provides ambient) {
                XWearTheme {
                    AppScaffold(timeText = { XTimeText() }) {
                        WearNavHost(
                            viewModel = viewModel,
                            phoneLink = phoneLink,
                            deepLinks = deepLinks,
                            onDeepLinkHandled = { deepLinks.value = null },
                        )
                    }
                    val main by viewModel.main.collectAsStateWithLifecycle()
                    NotificationPermissionRequest(timerRunning = main.mode != MainMode.IDLE)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkOf(intent)?.let { deepLinks.value = it }
    }

    companion object {
        /** One of [OPEN_MAIN], [OPEN_FEED_TYPE], [OPEN_DIAPER], [OPEN_LOGGED], [OPEN_TODAY]. */
        const val EXTRA_OPEN = "com.ffmathy.pooparazzi.wear.extra.OPEN"

        /** The entry for [OPEN_LOGGED]. */
        const val EXTRA_ENTRY_ID = "com.ffmathy.pooparazzi.wear.extra.ENTRY_ID"

        /** Main, closing any picker (complications, the Ongoing Activity). */
        const val OPEN_MAIN = "main"
        const val OPEN_FEED_TYPE = "feed_type"
        const val OPEN_DIAPER = "diaper"

        /** "End feed" of an older tile (when breastfeeds were timed): opens Main now. */
        private const val LEGACY_OPEN_END_FEED = "end_feed"
        const val OPEN_LOGGED = "logged"
        const val OPEN_TODAY = "today"

        /** An intent that opens the app at [open] (and, for [OPEN_LOGGED], the Logged screen of [entryId]). */
        fun intent(context: Context, open: String, entryId: String? = null): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, open)
                .apply { if (entryId != null) putExtra(EXTRA_ENTRY_ID, entryId) }

        internal fun deepLinkOf(intent: Intent?): WearDeepLink? = when (intent?.getStringExtra(EXTRA_OPEN)) {
            OPEN_MAIN -> WearDeepLink.Main
            OPEN_FEED_TYPE -> WearDeepLink.FeedType
            OPEN_DIAPER -> WearDeepLink.Diaper
            LEGACY_OPEN_END_FEED -> WearDeepLink.Main
            OPEN_TODAY -> WearDeepLink.Today
            OPEN_LOGGED -> intent.getStringExtra(EXTRA_ENTRY_ID)?.let { WearDeepLink.Logged(it) }
            else -> null
        }
    }
}
