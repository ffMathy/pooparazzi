package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.phone.ui.history.HistoryActions
import com.ffmathy.pooparazzi.phone.ui.history.HistoryScreen
import com.ffmathy.pooparazzi.phone.ui.home.HomeActions
import com.ffmathy.pooparazzi.phone.ui.home.HomeScreen
import com.ffmathy.pooparazzi.phone.ui.home.HomeUiState
import com.ffmathy.pooparazzi.phone.ui.preview.HistorySamples
import com.ffmathy.pooparazzi.phone.ui.preview.HomeSamples
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The top bars' status icons (DESIGN_SPEC §4.5): no visible text, TalkBack reads the full sentence, the cloud only
 * while signed in, and only an error state is a 48 dp retry button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class SyncIconsSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val home = object : HomeActions {
        override fun onRetrySync() { calls += "retry-sync" }
        override fun onRetryBackup() { calls += "retry-backup" }
    }
    private val history = object : HistoryActions {
        override fun onRetrySync() { calls += "retry-sync" }
        override fun onRetryBackup() { calls += "retry-backup" }
    }

    @Before
    fun use24HourClock() {
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun showHome(state: HomeUiState) {
        compose.setContent { NightLightTheme(night = true) { HomeScreen(state, home) } }
        compose.waitForIdle()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val notClickable = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)

    @Test
    fun theWatchIconSpeaksItsStateAndShowsNoText() {
        showHome(HomeSamples.idle())
        compose.onNodeWithContentDescription("Synced with watch · 14:31").assert(notClickable)
        compose.onAllNodesWithText("Synced with watch", substring = true).assertCountEquals(0)
    }

    @Test
    fun signedOutShowsNoCloudIcon() {
        showHome(HomeSamples.syncStates(SyncStatus.Synced(SampleData.SYNCED_1431), CloudStatus.Off))
        compose.onNodeWithContentDescription("Synced with watch · 14:31").assertExists()
        compose.onNodeWithContentDescription("back", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun signedInShowsTheCloudIcon() {
        showHome(HomeSamples.syncStates(SyncStatus.Synced(SampleData.SYNCED_1431), CloudStatus.Synced(SampleData.SYNCED_1431)))
        compose.onNodeWithContentDescription("Backed up · 14:31").assert(notClickable)
        compose.onAllNodesWithText("Backed up", substring = true).assertCountEquals(0)
    }

    @Test
    fun withoutAWatchOnlyTheCloudShows() {
        showHome(HomeSamples.syncStates(SyncStatus.Unavailable, CloudStatus.Syncing))
        compose.onNodeWithContentDescription("watch", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Backing up…").assert(notClickable)
    }

    @Test
    fun homeErrorIconsAre48dpRetryButtons() {
        showHome(HomeSamples.syncStates(SyncStatus.Error, CloudStatus.Error))
        val watch = compose.onNodeWithContentDescription("Couldn't save · Tap to retry")
            .assert(isButton).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val cloud = compose.onNodeWithContentDescription("Couldn't back up · Tap to retry")
            .assert(isButton).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val a = watch.fetchSemanticsNode().boundsInRoot
        val b = cloud.fetchSemanticsNode().boundsInRoot
        assertTrue("$a overlaps $b", a.right <= b.left)
        watch.performClick()
        cloud.performClick()
        assertEquals(listOf("retry-sync", "retry-backup"), calls)
    }

    @Test
    fun aDeletedBackupIsNotARetry() {
        showHome(HomeSamples.syncStates(SyncStatus.Offline, CloudStatus.Deleted))
        compose.onNodeWithContentDescription("Saved on phone · will sync").assert(notClickable)
        compose.onNodeWithContentDescription("Backup deleted · Back up now to start again").assert(notClickable)
    }

    @Test
    fun historyShowsTheSameIcons() {
        compose.setContent { NightLightTheme(night = true) { HistoryScreen(HistorySamples.twins(), history) } }
        compose.onNodeWithContentDescription("Synced with watch · 14:31").assert(notClickable)
        compose.onNodeWithContentDescription("Backed up · 14:31").assert(notClickable)
        compose.onAllNodesWithText("Synced with watch", substring = true).assertCountEquals(0)
    }

    @Test
    fun historyErrorIconsRetry() {
        compose.setContent { NightLightTheme(night = true) { HistoryScreen(HistorySamples.syncStates(SyncStatus.Error, CloudStatus.Error), history) } }
        compose.onNodeWithContentDescription("Couldn't save · Tap to retry").assert(isButton).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Couldn't back up · Tap to retry").assert(isButton).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("retry-sync", "retry-backup"), calls)
    }

    @Test
    @Config(qualifiers = "+da")
    fun danishIconsSpeakDanish() {
        showHome(HomeSamples.syncStates(SyncStatus.Synced(SampleData.SYNCED_1431), CloudStatus.Synced(SampleData.SYNCED_1431)))
        compose.onNodeWithContentDescription("Synkroniseret med ur · 14:31").assertExists()
        compose.onNodeWithContentDescription("Backup taget · 14:31").assertExists()
    }
}
