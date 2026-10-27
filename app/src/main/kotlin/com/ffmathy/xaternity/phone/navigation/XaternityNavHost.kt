package com.ffmathy.xaternity.phone.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ffmathy.xaternity.core.data.Clock
import com.ffmathy.xaternity.phone.app.AppearanceInputs
import com.ffmathy.xaternity.phone.launch.HomeLaunch
import com.ffmathy.xaternity.phone.ui.history.HistoryRoute
import com.ffmathy.xaternity.phone.ui.home.HomeRoute
import com.ffmathy.xaternity.phone.ui.settings.LicencesRoute
import com.ffmathy.xaternity.phone.ui.settings.SettingsRoute
import com.ffmathy.xaternity.phone.ui.theme.LocalNightLight
import com.ffmathy.xaternity.phone.ui.theme.NightLightTheme
import kotlinx.serialization.Serializable

/** Home: the start destination (DESIGN_SPEC §3.1). */
@Serializable
data object Home

/** History (P3). */
@Serializable
data object History

/** Settings. */
@Serializable
data object Settings

/** The font licences (Settings › About). */
@Serializable
data object Licences

/**
 * The phone app: the resolved Night Light theme around the navigation graph.
 *
 * @param launch a deep link from a widget or notification that Home has not handled yet; [onLaunchHandled] reports it.
 */
@Composable
fun XaternityPhoneApp(
    inputs: AppearanceInputs,
    clock: Clock,
    onNightResolved: (Boolean) -> Unit,
    launch: HomeLaunch? = null,
    onLaunchHandled: (HomeLaunch) -> Unit = {},
) {
    NightLightTheme(appearance = inputs.appearance, sleepRunning = inputs.sleepRunning, clock = clock) {
        val night = LocalNightLight.current.isNight
        LaunchedEffect(night) { onNightResolved(night) }
        XaternityNavHost(launch = launch, onLaunchHandled = onLaunchHandled)
    }
}

/**
 * Home, History, Settings and the font licences. The edit sheet (P4) is not a destination: it opens over the screen
 * that asked for it (Home's since-rows and Adjust card, History's rows and FAB), with its scrim over that screen.
 * A deep link returns to Home first, which then opens the sheet or card it asks for.
 */
@Composable
fun XaternityNavHost(
    navController: NavHostController = rememberNavController(),
    launch: HomeLaunch? = null,
    onLaunchHandled: (HomeLaunch) -> Unit = {},
) {
    fun open(route: Any) = navController.navigate(route) { launchSingleTop = true }
    val back: () -> Unit = { navController.popBackStack() }
    LaunchedEffect(launch) {
        if (launch != null) navController.popBackStack<Home>(inclusive = false)
    }
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeRoute(
                onOpenHistory = { open(History) },
                onOpenSettings = { open(Settings) },
                launch = launch,
                onLaunchHandled = onLaunchHandled,
            )
        }
        composable<History> { HistoryRoute(onBack = back) }
        composable<Settings> { SettingsRoute(onBack = back, onOpenLicences = { open(Licences) }) }
        composable<Licences> { LicencesRoute(onBack = back) }
    }
}
