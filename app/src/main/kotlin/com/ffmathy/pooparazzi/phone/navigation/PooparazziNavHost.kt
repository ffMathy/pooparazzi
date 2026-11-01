package com.ffmathy.pooparazzi.phone.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.ffmathy.pooparazzi.phone.ui.account.ConsentRoute
import com.ffmathy.pooparazzi.phone.ui.account.WelcomeRoute
import com.ffmathy.pooparazzi.phone.ui.family.JoinRoute
import com.ffmathy.pooparazzi.phone.ui.history.HistoryRoute
import com.ffmathy.pooparazzi.phone.ui.home.HomeRoute
import com.ffmathy.pooparazzi.phone.ui.settings.LicencesRoute
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsRoute
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsRoute
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLight
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import kotlinx.serialization.Serializable

/** Home: the start destination (DESIGN_SPEC §3.1). */
@Serializable
data object Home

/** History (P3). */
@Serializable
data object History

/** Statistics (P8), opened from Home's `chart` button or History's Statistics button. */
@Serializable
data object Statistics

/** Settings. */
@Serializable
data object Settings

/** The font licences (Settings › About). */
@Serializable
data object Licences

/** Settings › Account (BABIES_AND_BACKUP §3.3). */
@Serializable
data object Account

/** The join confirmation of an invite link (BABIES_AND_BACKUP §3.5). */
@Serializable
data object Join

/**
 * The website's App Links waiting to be handled (BABIES_AND_BACKUP §3.3, §3.5).
 *
 * @property invite an invite code: signed in, the join confirmation opens; signed out, the Account screen asks to sign
 *   in first ("Sign in to join the family.").
 * @property emailLinkWaiting an email sign-in link waits: the Account screen opens and takes it.
 * @property onInviteHandled the invite was joined or put aside.
 */
@Immutable
data class AccountLinks(
    val invite: String? = null,
    val emailLinkWaiting: Boolean = false,
    val signedIn: Boolean = false,
    val onInviteHandled: () -> Unit = {},
)

/**
 * The phone app: the resolved Night Light theme around the navigation graph.
 *
 * @param launch a deep link from a widget or notification that Home has not handled yet; [onLaunchHandled] reports it.
 * @param welcome the first-run Welcome (BABIES_AND_BACKUP §3.1) comes before everything else; [onWelcomeDone] when it
 *   has been answered. A deep link waits behind it.
 * @param consentNeeded the signed-in account has not agreed to the backup yet (BABIES_AND_BACKUP §3.6): the consent
 *   screen comes next, before everything else; links wait behind it.
 * @param onLaunchedSheetClosed a sheet such a link opened has closed (see `HomeViewModel.launchedSheetClosed`).
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
    consentNeeded: Boolean = false,
    onLaunchedSheetClosed: () -> Unit = {},
    links: AccountLinks = AccountLinks(),
) {
    NightLightTheme(appearance = inputs.appearance, sleepRunning = inputs.sleepRunning, clock = clock) {
        val night = LocalNightLight.current.isNight
        LaunchedEffect(night) { onNightResolved(night) }
        if (welcome) {
            WelcomeRoute(onDone = onWelcomeDone)
        } else if (consentNeeded) {
            ConsentRoute()
        } else {
            PooparazziNavHost(
                launch = launch,
                onLaunchHandled = onLaunchHandled,
                onLaunchedSheetClosed = onLaunchedSheetClosed,
                links = links,
            )
        }
    }
}

/**
 * Home, History, Statistics (over Home or History), Settings, the font licences, the Account screen and the join confirmation. The edit sheet (P4) is
 * not a destination: it opens over the screen that asked for it (Home's since-rows and Adjust card, History's rows and
 * FAB), with its scrim over that screen. A deep link returns to Home first, which then opens the sheet or card it asks
 * for; [HomeLaunch.History] (the watch's "More on phone") opens History over Home instead.
 */
@Composable
fun PooparazziNavHost(
    navController: NavHostController = rememberNavController(),
    launch: HomeLaunch? = null,
    onLaunchHandled: (HomeLaunch) -> Unit = {},
    onLaunchedSheetClosed: () -> Unit = {},
    links: AccountLinks = AccountLinks(),
) {
    fun open(route: Any) = navController.navigate(route) { launchSingleTop = true }
    // An email sign-in link: the Account screen takes it (signs in, or completes the account deletion).
    LaunchedEffect(links.emailLinkWaiting) {
        if (links.emailLinkWaiting) open(Account)
    }
    // An invite: the join confirmation once signed in, the Account screen's sign-in before.
    LaunchedEffect(links.invite, links.signedIn) {
        if (links.invite == null) return@LaunchedEffect
        if (links.signedIn) {
            navController.navigate(Join) {
                launchSingleTop = true
                popUpTo<Home>()
            }
        } else {
            open(Account)
        }
    }
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
                onOpenStatistics = { open(Statistics) },
                launch = launch?.takeUnless { it == HomeLaunch.History },
                onLaunchHandled = onLaunchHandled,
                onLaunchedSheetClosed = onLaunchedSheetClosed,
            )
        }
        composable<History> { HistoryRoute(onBack = back, onOpenStatistics = { open(Statistics) }) }
        composable<Statistics> { StatisticsRoute(onBack = back) }
        composable<Settings> {
            SettingsRoute(onBack = back, onOpenLicences = { open(Licences) }, onOpenAccount = { open(Account) })
        }
        composable<Licences> { LicencesRoute(onBack = back) }
        composable<Account> { AccountRoute(onBack = back) }
        composable<Join> {
            val code = links.invite
            val handled by rememberUpdatedState(links.onInviteHandled)
            if (code != null) {
                JoinRoute(code = code, onClose = {
                    handled()
                    navController.popBackStack<Home>(inclusive = false)
                })
            } else {
                // Handled already (the screen is on its way out).
                LaunchedEffect(Unit) { navController.popBackStack<Home>(inclusive = false) }
            }
        }
    }
}
