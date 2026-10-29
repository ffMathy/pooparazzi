package com.ffmathy.pooparazzi.phone.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.phone.app.AppearanceInputs
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.ui.account.AccountRoute
import com.ffmathy.pooparazzi.phone.ui.account.WelcomeRoute
import com.ffmathy.pooparazzi.phone.ui.history.HistoryRoute
import com.ffmathy.pooparazzi.phone.ui.home.HomeRoute
import com.ffmathy.pooparazzi.phone.ui.settings.LicencesRoute
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsRoute
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLight
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
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

/** Settings › Account (BABIES_AND_BACKUP §3.3). */
@Serializable
data object Account

/**
 * The phone app: the resolved Night Light theme around the navigation graph.
 *
 * @param launch a deep link from a widget or notification that Home has not handled yet; [onLaunchHandled] reports it.
 * @param welcome the first-run Welcome (BABIES_AND_BACKUP §3.1) comes before everything else; [onWelcomeDone] when it
 *   has been answered. A deep link waits behind it.
 */
@Composable
fun PooparazziPhoneApp(
    inputs: AppearanceInputs,
    clock: Clock,
    onNightResolved: (Boolean) -> Unit,
    launch: HomeLaunch? = null,
    onLaunchHandled: (HomeLaunch) -> Unit = {},
    welcome: Boolean = false,
    onWelcomeDone: () -> Unit = {},
) {
    NightLightTheme(appearance = inputs.appearance, sleepRunning = inputs.sleepRunning, clock = clock) {
        val night = LocalNightLight.current.isNight
        LaunchedEffect(night) { onNightResolved(night) }
        if (welcome) {
            WelcomeRoute(onDone = onWelcomeDone)
        } else {
            PooparazziNavHost(launch = launch, onLaunchHandled = onLaunchHandled)
        }
    }
}

/**
 * Home, History, Settings, the font licences and the Account screen. The edit sheet (P4) is not a destination: it
 * opens over the screen that asked for it (Home's since-rows and Adjust card, History's rows and FAB), with its scrim
 * over that screen.
 * A deep link returns to Home first, which then opens the sheet or card it asks for; [HomeLaunch.History] (the watch's
 * "More on phone") opens History over Home instead.
 */
@Composable
fun PooparazziNavHost(
    navController: NavHostController = rememberNavController(),
    launch: HomeLaunch? = null,
    onLaunchHandled: (HomeLaunch) -> Unit = {},
) {
    fun open(route: Any) = navController.navigate(route) { launchSingleTop = true }
    val back: () -> Unit = { navController.popBackStack() }
    val handled by rememberUpdatedState(onLaunchHandled)
    LaunchedEffect(launch) {
        when (launch) {
            null -> Unit
            HomeLaunch.History -> {
                // Back from History returns to Home, whatever was open before.
                navController.navigate(History) {
                    launchSingleTop = true
                    popUpTo<Home>()
                }
                handled(launch)
            }
            else -> navController.popBackStack<Home>(inclusive = false)
        }
    }
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeRoute(
                onOpenHistory = { open(History) },
                onOpenSettings = { open(Settings) },
                launch = launch?.takeUnless { it == HomeLaunch.History },
                onLaunchHandled = onLaunchHandled,
            )
        }
        composable<History> { HistoryRoute(onBack = back) }
        composable<Settings> {
            SettingsRoute(onBack = back, onOpenLicences = { open(Licences) }, onOpenAccount = { open(Account) })
        }
        composable<Licences> { LicencesRoute(onBack = back) }
        composable<Account> { AccountRoute(onBack = back) }
    }
}
