package com.ffmathy.xaternity.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.wear.phone.PhoneLink
import com.ffmathy.xaternity.wear.theme.XWearTheme
import com.ffmathy.xaternity.wear.ui.XTimeText
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The watch app's only activity: Material 3 for Wear in the fixed dark theme, the system TimeText and the
 * navigation graph. External entry points (the Tile) pass [EXTRA_OPEN] (and [EXTRA_ENTRY_ID]).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val deepLinks = MutableStateFlow<WearDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Core.init(applicationContext)
        if (savedInstanceState == null) deepLinks.value = deepLinkOf(intent)
        val phoneLink = PhoneLink(applicationContext)
        setContent {
            CompositionLocalProvider(LocalAmbientModeManager provides rememberAmbientModeManager()) {
                XWearTheme {
                    AppScaffold(timeText = { XTimeText() }) {
                        WearNavHost(
                            viewModel = viewModel,
                            phoneLink = phoneLink,
                            deepLinks = deepLinks,
                            onDeepLinkHandled = { deepLinks.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkOf(intent)?.let { deepLinks.value = it }
    }

    companion object {
        /** One of [OPEN_FEED_TYPE], [OPEN_END_FEED], [OPEN_LOGGED], [OPEN_TODAY]. */
        const val EXTRA_OPEN = "com.ffmathy.xaternity.wear.extra.OPEN"

        /** The entry for [OPEN_LOGGED]. */
        const val EXTRA_ENTRY_ID = "com.ffmathy.xaternity.wear.extra.ENTRY_ID"

        const val OPEN_FEED_TYPE = "feed_type"
        const val OPEN_END_FEED = "end_feed"
        const val OPEN_LOGGED = "logged"
        const val OPEN_TODAY = "today"

        /** An intent that opens the app at [open] (and, for [OPEN_LOGGED], the Logged screen of [entryId]). */
        fun intent(context: Context, open: String, entryId: String? = null): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, open)
                .apply { if (entryId != null) putExtra(EXTRA_ENTRY_ID, entryId) }

        internal fun deepLinkOf(intent: Intent?): WearDeepLink? = when (intent?.getStringExtra(EXTRA_OPEN)) {
            OPEN_FEED_TYPE -> WearDeepLink.FeedType
            OPEN_END_FEED -> WearDeepLink.EndFeed
            OPEN_TODAY -> WearDeepLink.Today
            OPEN_LOGGED -> intent.getStringExtra(EXTRA_ENTRY_ID)?.let { WearDeepLink.Logged(it) }
            else -> null
        }
    }
}
