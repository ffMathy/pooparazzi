package com.ffmathy.xaternity.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.ffmathy.xaternity.wear.adjust.AdjustActions
import com.ffmathy.xaternity.wear.adjust.AdjustScreen
import com.ffmathy.xaternity.wear.adjust.SetTimeScreen
import com.ffmathy.xaternity.wear.diaper.DiaperActions
import com.ffmathy.xaternity.wear.diaper.DiaperScreen
import com.ffmathy.xaternity.wear.diaper.PoopOptionScreen
import com.ffmathy.xaternity.wear.diaper.colourOptions
import com.ffmathy.xaternity.wear.diaper.textureOptions
import com.ffmathy.xaternity.wear.feed.FeedTypeActions
import com.ffmathy.xaternity.wear.feed.FeedTypeScreen
import com.ffmathy.xaternity.wear.feed.SideScreen
import com.ffmathy.xaternity.wear.logged.LoggedScreen
import com.ffmathy.xaternity.wear.logged.UndoneScreen
import com.ffmathy.xaternity.wear.main.MainActions
import com.ffmathy.xaternity.wear.main.MainScreen
import com.ffmathy.xaternity.wear.phone.PhoneLink
import com.ffmathy.xaternity.wear.state.WearEvent
import com.ffmathy.xaternity.wear.state.adjustableFields
import com.ffmathy.xaternity.wear.today.TodayScreen
import com.ffmathy.xaternity.wear.ui.perform
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.ffmathy.xaternity.core.R as CoreR

/** Destinations of the watch app (DESIGN_SPEC §3.1). */
object WearRoutes {
    const val MAIN = "main"
    const val FEED_TYPE = "feed"
    const val SIDE = "side"
    const val DIAPER = "diaper"
    const val DIAPER_COLOUR = "diaper/colour"
    const val DIAPER_TEXTURE = "diaper/texture"
    const val LOGGED = "logged"
    const val UNDONE = "undone"
    const val ADJUST = "adjust"
    const val SET_TIME = "adjust/time"
    const val TODAY = "today"
}

/** Where an external entry point (Tile, complication, notification) asks the app to open. */
sealed interface WearDeepLink {
    /** Main (complication and Ongoing Activity taps). */
    data object Main : WearDeepLink

    /** Feed type screen (Tile row A, idle). */
    data object FeedType : WearDeepLink

    /** Side screen for the running breastfeed (Tile row A while it runs). */
    data object EndFeed : WearDeepLink

    /** Logged for an entry the Tile just wrote, with Undo active at once. */
    data class Logged(val entryId: String) : WearDeepLink

    /** Today. */
    data object Today : WearDeepLink
}

/**
 * The app's navigation (DESIGN_SPEC §3.1): Main and its pickers, Logged / Undone, Adjust / Set time and Today, in a
 * [SwipeDismissableNavHost] (swipe right cancels a picker). One-shot [MainViewModel.events] drive haptics and the
 * navigation that depends on a write's outcome.
 */
@Composable
fun WearNavHost(
    viewModel: MainViewModel,
    phoneLink: PhoneLink?,
    deepLinks: StateFlow<WearDeepLink?>? = null,
    onDeepLinkHandled: () -> Unit = {},
    navController: NavHostController = rememberSwipeDismissableNavController(),
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var openOnPhone by remember { mutableStateOf(false) }

    fun go(route: String) = navController.navigate(route) { launchSingleTop = true }
    fun backToMain() {
        if (!navController.popBackStack(WearRoutes.MAIN, inclusive = false)) go(WearRoutes.MAIN)
    }

    LaunchedEffect(viewModel, navController) {
        viewModel.events.collect { event ->
            when (event) {
                is WearEvent.Haptic -> haptics.perform(event.type)
                WearEvent.ShowLogged -> navController.navigate(WearRoutes.LOGGED) {
                    popUpTo(WearRoutes.MAIN)
                    launchSingleTop = true
                }
                WearEvent.ShowUndone -> navController.navigate(WearRoutes.UNDONE) { popUpTo(WearRoutes.MAIN) }
                WearEvent.ShowSide -> go(WearRoutes.SIDE)
                WearEvent.BackToMain -> backToMain()
                is WearEvent.Back -> repeat(event.count) { navController.popBackStack() }
            }
        }
    }

    // External entry points (the Tile, complications, the Ongoing Activity), once each.
    val deepLink = deepLinks?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(deepLink) {
        when (deepLink) {
            null -> return@LaunchedEffect
            WearDeepLink.Main -> backToMain()
            WearDeepLink.FeedType -> {
                viewModel.beginFeed()
                go(WearRoutes.FEED_TYPE)
            }
            WearDeepLink.EndFeed -> viewModel.requestEndFeed()
            is WearDeepLink.Logged -> viewModel.openReceipt(deepLink.entryId, fromTile = true)
            WearDeepLink.Today -> go(WearRoutes.TODAY)
        }
        onDeepLinkHandled()
    }

    // Minute precision in ambient (the regular ticker cannot run while the watch sleeps) and after resuming.
    val ambientManager = LocalAmbientModeManager.current
    if (ambientManager != null) {
        LaunchedEffect(ambientManager, viewModel) {
            // Suspends until each ambient tick (about once a minute while ambient).
            while (true) ambientManager.withAmbientTick { viewModel.refresh() }
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    SwipeDismissableNavHost(navController = navController, startDestination = WearRoutes.MAIN) {
        composable(WearRoutes.MAIN) {
            val state by viewModel.main.collectAsStateWithLifecycle()
            MainScreen(
                state = state,
                actions = MainActions(
                    onFeed = {
                        viewModel.beginFeed()
                        go(WearRoutes.FEED_TYPE)
                    },
                    onDiaper = {
                        viewModel.beginDiaper()
                        go(WearRoutes.DIAPER)
                    },
                    onSleepToggle = { viewModel.toggleSleep() },
                    onEndFeed = { viewModel.requestEndFeed() },
                    onAdjust = { entry, field ->
                        viewModel.beginAdjust(entry.id, listOf(field))
                        go(WearRoutes.ADJUST)
                    },
                    onHistory = { go(WearRoutes.TODAY) },
                ),
            )
        }
        composable(WearRoutes.FEED_TYPE) {
            val draft by viewModel.feedDraft.collectAsStateWithLifecycle()
            draft?.let {
                FeedTypeScreen(it, FeedTypeActions(onType = { type -> viewModel.chooseFeedType(type) }, onEarlier = viewModel::cycleFeedOffset))
            }
        }
        composable(WearRoutes.SIDE) {
            val draft by viewModel.sideDraft.collectAsStateWithLifecycle()
            draft?.let { SideScreen(it, onSide = { side -> viewModel.endFeed(side) }) }
        }
        composable(WearRoutes.DIAPER) {
            val draft by viewModel.diaperDraft.collectAsStateWithLifecycle()
            draft?.let {
                DiaperScreen(
                    it,
                    DiaperActions(
                        onPee = viewModel::setPee,
                        onPoop = viewModel::setPoop,
                        onColour = { go(WearRoutes.DIAPER_COLOUR) },
                        onTexture = { go(WearRoutes.DIAPER_TEXTURE) },
                        onEarlier = viewModel::cycleDiaperOffset,
                        onSave = { viewModel.saveDiaper() },
                    ),
                )
            }
        }
        composable(WearRoutes.DIAPER_COLOUR) {
            val draft by viewModel.diaperDraft.collectAsStateWithLifecycle()
            PoopOptionScreen(
                title = stringResource(CoreR.string.poop_colour),
                options = colourOptions(draft?.color),
                onSelect = { color ->
                    viewModel.setPoopColor(color)
                    navController.popBackStack()
                },
            )
        }
        composable(WearRoutes.DIAPER_TEXTURE) {
            val draft by viewModel.diaperDraft.collectAsStateWithLifecycle()
            PoopOptionScreen(
                title = stringResource(CoreR.string.poop_texture),
                options = textureOptions(draft?.texture),
                onSelect = { texture ->
                    viewModel.setPoopTexture(texture)
                    navController.popBackStack()
                },
            )
        }
        composable(WearRoutes.LOGGED) {
            val receipt by viewModel.receipt.collectAsStateWithLifecycle()
            val main by viewModel.main.collectAsStateWithLifecycle()
            // Undo clears the receipt (so a second Undo tap does nothing); keep showing it while Logged leaves.
            var shown by remember { mutableStateOf(receipt) }
            LaunchedEffect(receipt) { receipt?.let { shown = it } }
            (receipt ?: shown)?.let {
                LoggedScreen(
                    receipt = it,
                    sync = main.sync,
                    onUndo = { viewModel.undo() },
                    onDismiss = ::backToMain,
                    onTimeout = ::backToMain,
                )
            }
        }
        composable(WearRoutes.UNDONE) {
            UndoneScreen(onDone = ::backToMain)
        }
        composable(WearRoutes.ADJUST) {
            val state by viewModel.adjust.collectAsStateWithLifecycle()
            state?.let {
                AdjustScreen(
                    it,
                    AdjustActions(
                        onOption = { field, minutes -> viewModel.adjust(field, minutes) },
                        onSetTime = { field ->
                            viewModel.beginSetTime(field)
                            go(WearRoutes.SET_TIME)
                        },
                    ),
                )
            }
        }
        composable(WearRoutes.SET_TIME) {
            val state by viewModel.setTime.collectAsStateWithLifecycle()
            state?.let { SetTimeScreen(it, onPicked = { time -> viewModel.setTime(time) }) }
        }
        composable(WearRoutes.TODAY) {
            val state by viewModel.today.collectAsStateWithLifecycle()
            val availability = remember(phoneLink) { phoneLink?.available ?: flowOf(false) }
            val available by availability.collectAsStateWithLifecycle(initialValue = false)
            TodayScreen(
                state = state,
                phoneAvailable = available,
                onRow = { entry ->
                    viewModel.beginAdjust(entry.id, entry.adjustableFields(), allowSetTime = true)
                    go(WearRoutes.ADJUST)
                },
                onMoreOnPhone = {
                    scope.launch {
                        if (phoneLink?.openHistory() == true) openOnPhone = true
                    }
                },
            )
        }
    }

    val continueOnPhone = stringResource(R.string.open_on_phone)
    val curvedStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = openOnPhone,
        onDismissRequest = { openOnPhone = false },
        curvedText = { openOnPhoneDialogCurvedText(text = continueOnPhone, style = curvedStyle) },
    )
}
