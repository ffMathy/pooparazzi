package com.ffmathy.pooparazzi.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.ffmathy.pooparazzi.wear.adjust.AdjustActions
import com.ffmathy.pooparazzi.wear.adjust.AdjustScreen
import com.ffmathy.pooparazzi.wear.adjust.SetTimeScreen
import com.ffmathy.pooparazzi.wear.diaper.DiaperActions
import com.ffmathy.pooparazzi.wear.diaper.DiaperScreen
import com.ffmathy.pooparazzi.wear.diaper.PoopOptionScreen
import com.ffmathy.pooparazzi.wear.diaper.colourOptions
import com.ffmathy.pooparazzi.wear.diaper.textureOptions
import com.ffmathy.pooparazzi.wear.feed.FeedTypeActions
import com.ffmathy.pooparazzi.wear.feed.FeedTypeScreen
import com.ffmathy.pooparazzi.wear.feed.SideActions
import com.ffmathy.pooparazzi.wear.feed.SideScreen
import com.ffmathy.pooparazzi.wear.logged.LoggedScreen
import com.ffmathy.pooparazzi.wear.logged.UndoneScreen
import com.ffmathy.pooparazzi.wear.main.MainActions
import com.ffmathy.pooparazzi.wear.main.MainScreen
import com.ffmathy.pooparazzi.wear.phone.PhoneLink
import com.ffmathy.pooparazzi.wear.state.WearEvent
import com.ffmathy.pooparazzi.wear.state.XHaptic
import com.ffmathy.pooparazzi.wear.state.adjustableFields
import com.ffmathy.pooparazzi.wear.today.TodayScreen
import com.ffmathy.pooparazzi.wear.ui.perform
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.ffmathy.pooparazzi.core.R as CoreR

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

    /** Diaper screen (Tile row B). */
    data object Diaper : WearDeepLink

    /** Logged for an entry the Tile just wrote, with Undo active at once. */
    data class Logged(val entryId: String) : WearDeepLink

    /** Today. */
    data object Today : WearDeepLink
}

/**
 * The app's navigation (DESIGN_SPEC §3.1): Main and its pickers, Logged / Undone, Adjust / Set time and Today, in a
 * [SwipeDismissableNavHost] (swipe right cancels a picker). One-shot [MainViewModel.events] drive haptics and the
 * navigation that depends on a write's outcome.
 *
 * Double taps: a tap only counts on the screen on top of the back stack (a tap that reaches a screen already leaving
 * is dropped), every return pops by route (a repeat finds nothing left to pop), and pickers keep showing their last
 * state while they leave.
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
    var phoneFailed by remember { mutableStateOf(false) }

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
                // Adjust and Set time above it; nothing when Adjust is already gone.
                WearEvent.CloseAdjust -> navController.popBackStack(WearRoutes.ADJUST, inclusive = true)
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
            WearDeepLink.Diaper -> {
                viewModel.beginDiaper()
                go(WearRoutes.DIAPER)
            }
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
        composable(WearRoutes.MAIN) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val state by viewModel.main.collectAsStateWithLifecycle()
            MainScreen(
                state = state,
                actions = MainActions(
                    onFeed = gate.tap {
                        viewModel.beginFeed()
                        go(WearRoutes.FEED_TYPE)
                    },
                    onDiaper = gate.tap {
                        viewModel.beginDiaper()
                        go(WearRoutes.DIAPER)
                    },
                    onSleepToggle = gate.tapWith { shown -> viewModel.toggleSleep(shown) },
                    onAdjust = gate.tapWith2 { entry, field ->
                        viewModel.beginAdjust(entry.id, listOf(field))
                        go(WearRoutes.ADJUST)
                    },
                    onHistory = gate.tap { go(WearRoutes.TODAY) },
                ),
            )
        }
        composable(WearRoutes.FEED_TYPE) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val draft by viewModel.feedDraft.collectAsStateWithLifecycle()
            // The draft is used up by the tap that leaves the screen: keep showing it during the exit.
            rememberLast(draft)?.let {
                FeedTypeScreen(
                    it,
                    FeedTypeActions(
                        onType = gate.tapWith { type -> viewModel.chooseFeedType(type) },
                        onEarlier = gate.tap { viewModel.cycleFeedOffset() },
                    ),
                )
            }
        }
        composable(WearRoutes.SIDE) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val draft by viewModel.sideDraft.collectAsStateWithLifecycle()
            rememberLast(draft)?.let {
                SideScreen(
                    it,
                    SideActions(
                        onTick = gate.tapWith { breast -> viewModel.tickSide(breast) },
                        onSave = gate.tap { viewModel.saveBreastfeed() },
                    ),
                )
            }
        }
        composable(WearRoutes.DIAPER) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val draft by viewModel.diaperDraft.collectAsStateWithLifecycle()
            rememberLast(draft)?.let {
                DiaperScreen(
                    it,
                    DiaperActions(
                        onPee = gate.tapWith { on -> viewModel.setPee(on) },
                        onPoop = gate.tapWith { on -> viewModel.setPoop(on) },
                        onColour = gate.tap { go(WearRoutes.DIAPER_COLOUR) },
                        onTexture = gate.tap { go(WearRoutes.DIAPER_TEXTURE) },
                        onEarlier = gate.tap { viewModel.cycleDiaperOffset() },
                        onSave = gate.tap { viewModel.saveDiaper() },
                    ),
                )
            }
        }
        composable(WearRoutes.DIAPER_COLOUR) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val draft by viewModel.diaperDraft.collectAsStateWithLifecycle()
            PoopOptionScreen(
                title = stringResource(CoreR.string.poop_colour),
                options = colourOptions(draft?.color),
                onSelect = gate.tapWith { color ->
                    viewModel.setPoopColor(color)
                    // By route: a second tap never pops the Diaper screen (and its unsaved draft) as well.
                    navController.popBackStack(WearRoutes.DIAPER_COLOUR, inclusive = true)
                },
            )
        }
        composable(WearRoutes.DIAPER_TEXTURE) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val draft by viewModel.diaperDraft.collectAsStateWithLifecycle()
            PoopOptionScreen(
                title = stringResource(CoreR.string.poop_texture),
                options = textureOptions(draft?.texture),
                onSelect = gate.tapWith { texture ->
                    viewModel.setPoopTexture(texture)
                    navController.popBackStack(WearRoutes.DIAPER_TEXTURE, inclusive = true)
                },
            )
        }
        composable(WearRoutes.LOGGED) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val receipt by viewModel.receipt.collectAsStateWithLifecycle()
            val main by viewModel.main.collectAsStateWithLifecycle()
            // Undo clears the receipt (so a second Undo tap does nothing); keep showing it while Logged leaves.
            var shown by remember { mutableStateOf(receipt) }
            LaunchedEffect(receipt) { receipt?.let { shown = it } }
            (receipt ?: shown)?.let {
                LoggedScreen(
                    receipt = it,
                    sync = main.sync,
                    onUndo = gate.tap { viewModel.undo() },
                    onDismiss = gate.tap { backToMain() },
                    onTimeout = ::backToMain,
                )
            }
        }
        composable(WearRoutes.UNDONE) {
            UndoneScreen(onDone = ::backToMain)
        }
        composable(WearRoutes.ADJUST) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val state by viewModel.adjust.collectAsStateWithLifecycle()
            // An applied choice clears the request: keep showing the screen while it leaves.
            rememberLast(state)?.let {
                AdjustScreen(
                    it,
                    AdjustActions(
                        onOption = gate.tapWith2 { field, at -> viewModel.adjust(field, at) },
                        onSetTime = gate.tapWith { field ->
                            viewModel.beginSetTime(field)
                            go(WearRoutes.SET_TIME)
                        },
                    ),
                )
            }
        }
        composable(WearRoutes.SET_TIME) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val state by viewModel.setTime.collectAsStateWithLifecycle()
            rememberLast(state)?.let { SetTimeScreen(it, onPicked = gate.tapWith { time -> viewModel.setTime(time) }) }
        }
        composable(WearRoutes.TODAY) { backStackEntry ->
            val gate = rememberTopGate(navController, backStackEntry)
            val state by viewModel.today.collectAsStateWithLifecycle()
            val availability = remember(phoneLink) { phoneLink?.available ?: flowOf(false) }
            val available by availability.collectAsStateWithLifecycle(initialValue = false)
            TodayScreen(
                state = state,
                phoneAvailable = available,
                onRow = gate.tapWith { entry ->
                    viewModel.beginAdjust(entry.id, entry.adjustableFields(), allowSetTime = true)
                    go(WearRoutes.ADJUST)
                },
                onMoreOnPhone = gate.tap {
                    scope.launch {
                        // Never a dead button: say so when the phone could not open History.
                        if (phoneLink?.openHistory() == true) {
                            openOnPhone = true
                        } else {
                            haptics.perform(XHaptic.REJECT)
                            phoneFailed = true
                        }
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
    val couldNotOpen = stringResource(R.string.open_on_phone_failed)
    val failedStyle = ConfirmationDialogDefaults.curvedTextStyle
    FailureConfirmationDialog(
        visible = phoneFailed,
        onDismissRequest = { phoneFailed = false },
        curvedText = { confirmationDialogCurvedText(couldNotOpen, failedStyle) },
        modifier = Modifier.semantics { contentDescription = couldNotOpen },
    )
}

/** The last non-null [value]: a screen keeps showing its state while it leaves after the state was used up. */
@Composable
private fun <T : Any> rememberLast(value: T?): T? {
    // A plain holder, not state: nothing observes it, the screen recomposes because [value] changed.
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return value ?: last.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

/**
 * Lets taps through only while [entry]'s screen is on top of the back stack, so a double tap whose second tap reaches
 * a screen that is already leaving does nothing (it would act twice, or pop a screen too many). The back stack changes
 * at once on navigate and pop, unlike the entry's lifecycle, which reaches RESUMED only when the screen transition has
 * finished (so lifecycle's `dropUnlessResumed` would also drop taps on a screen that is still arriving).
 */
private class TopGate(private val navController: NavHostController, private val entry: NavBackStackEntry) {
    private val onTop: Boolean get() = navController.currentBackStackEntry?.id == entry.id

    fun tap(block: () -> Unit): () -> Unit = { if (onTop) block() }

    fun <A> tapWith(block: (A) -> Unit): (A) -> Unit = { a -> if (onTop) block(a) }

    fun <A, B> tapWith2(block: (A, B) -> Unit): (A, B) -> Unit = { a, b -> if (onTop) block(a, b) }
}

@Composable
private fun rememberTopGate(navController: NavHostController, entry: NavBackStackEntry): TopGate =
    remember(navController, entry) { TopGate(navController, entry) }
