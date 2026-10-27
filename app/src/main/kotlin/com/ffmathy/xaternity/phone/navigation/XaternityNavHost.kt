package com.ffmathy.xaternity.phone.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ffmathy.xaternity.core.data.Clock
import com.ffmathy.xaternity.phone.app.AppearanceInputs
import com.ffmathy.xaternity.phone.ui.home.HomeRoute
import com.ffmathy.xaternity.phone.ui.stubs.EditStubScreen
import com.ffmathy.xaternity.phone.ui.stubs.HistoryStubScreen
import com.ffmathy.xaternity.phone.ui.stubs.SettingsStubScreen
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

/** The edit sheet (P4) for one entry. */
@Serializable
data class Edit(val entryId: String)

/** The phone app: the resolved Night Light theme around the navigation graph. */
@Composable
fun XaternityPhoneApp(inputs: AppearanceInputs, clock: Clock, onNightResolved: (Boolean) -> Unit) {
    NightLightTheme(appearance = inputs.appearance, sleepRunning = inputs.sleepRunning, clock = clock) {
        val night = LocalNightLight.current.isNight
        LaunchedEffect(night) { onNightResolved(night) }
        XaternityNavHost()
    }
}

@Composable
fun XaternityNavHost(navController: NavHostController = rememberNavController()) {
    fun open(route: Any) = navController.navigate(route) { launchSingleTop = true }
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeRoute(
                onOpenHistory = { open(History) },
                onOpenSettings = { open(Settings) },
                onEditEntry = { id -> open(Edit(id)) },
            )
        }
        composable<History> { HistoryStubScreen(onBack = back) }
        composable<Settings> { SettingsStubScreen(onBack = back) }
        composable<Edit> { entry -> EditStubScreen(entryId = entry.toRoute<Edit>().entryId, onClose = back) }
    }
}
