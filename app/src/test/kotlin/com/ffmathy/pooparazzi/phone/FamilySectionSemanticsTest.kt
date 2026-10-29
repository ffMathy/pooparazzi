package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ffmathy.pooparazzi.phone.account.FamilyError
import com.ffmathy.pooparazzi.phone.account.FamilyMember
import com.ffmathy.pooparazzi.phone.ui.family.FamilyActions
import com.ffmathy.pooparazzi.phone.ui.family.FamilyConfirm
import com.ffmathy.pooparazzi.phone.ui.family.JoinActions
import com.ffmathy.pooparazzi.phone.ui.family.JoinScreen
import com.ffmathy.pooparazzi.phone.ui.preview.FamilySamples
import com.ffmathy.pooparazzi.phone.ui.preview.SettingsSamples
import com.ffmathy.pooparazzi.phone.ui.settings.AccountSummary
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsActions
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsScreen
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Family, the privacy row and the join confirmation in words (BABIES_AND_BACKUP §3.2, §3.4–3.5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class FamilySectionSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val family = object : FamilyActions {
        override fun onInvite() { calls += "invite" }
        override fun onLeave() { calls += "leave" }
        override fun onRemove(member: FamilyMember) { calls += "remove:${member.uid}" }
        override fun onConfirm() { calls += "confirm" }
    }

    @Test
    fun theMembersWithYouAndTheCreatorInviteLeaveAndRemove() {
        compose.setContent {
            NightLightTheme(night = false) { SettingsScreen(SettingsSamples.state(), SettingsActions.None, family = FamilySamples.state(), familyActions = family) }
        }
        compose.onNodeWithText("Family").assertExists()
        compose.onNodeWithText("You · Started the family", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("partner@example.com").assertExists()
        compose.onNodeWithContentDescription("Remove partner@example.com").performScrollTo().assertHasClickAction().performClick()
        compose.onNodeWithText("Invite someone").performScrollTo().performClick()
        compose.onNodeWithText("Leave family").performScrollTo().performClick()
        assertEquals(listOf("remove:sample-uid-2", "invite", "leave"), calls)
    }

    @Test
    fun aloneThereIsNothingToLeaveOrRemove() {
        compose.setContent {
            NightLightTheme(night = false) { SettingsScreen(SettingsSamples.state(), SettingsActions.None, family = FamilySamples.alone(), familyActions = family) }
        }
        compose.onNodeWithText("Only you so far. Invite someone to share the log and the babies.").performScrollTo().assertExists()
        compose.onNodeWithText("Leave family").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun othersSeeNoRemove() {
        val notCreator = FamilySamples.family.copy(
            members = FamilySamples.family.members.map { it.copy(creator = it.uid == "sample-uid-2") },
        )
        compose.setContent {
            NightLightTheme(night = false) { SettingsScreen(SettingsSamples.state(), SettingsActions.None, family = FamilySamples.state(notCreator)) }
        }
        compose.onNodeWithText("Started the family").performScrollTo().assertExists()
        compose.onNodeWithText("You").assertExists()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun leavingAsksFirst() {
        compose.setContent {
            NightLightTheme(night = false) {
                SettingsScreen(
                    SettingsSamples.state(), SettingsActions.None,
                    family = FamilySamples.state().copy(confirm = FamilyConfirm.Leave), familyActions = family,
                )
            }
        }
        compose.onNodeWithText("Leave the family?").assertExists()
        compose.onNodeWithText(
            "You'll stop seeing its log and babies. Your phone keeps its log and starts a new family of your own.",
        ).assertExists()
        compose.onNodeWithText("Leave").performClick()
        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun removingSaysWhatTheRemovedMemberKeeps() {
        val partner = FamilySamples.family.members[1]
        compose.setContent {
            NightLightTheme(night = false) {
                SettingsScreen(
                    SettingsSamples.state(), SettingsActions.None,
                    family = FamilySamples.state().copy(confirm = FamilyConfirm.Remove(partner)), familyActions = family,
                )
            }
        }
        compose.onNodeWithText("Remove partner@example.com?").assertExists()
        compose.onNodeWithText("They'll stop seeing the family's log from now on. What's already on their phone stays there.").assertExists()
    }

    @Test
    fun aRefusedInviteSaysWhy() {
        compose.setContent {
            NightLightTheme(night = false) {
                SettingsScreen(
                    SettingsSamples.state(), SettingsActions.None,
                    family = FamilySamples.state().copy(actionError = FamilyError.INVITE_REFUSED), familyActions = family,
                )
            }
        }
        compose.onNodeWithText(
            "Couldn't create an invite. You may have been removed from the family, or your email address isn't verified yet.",
        ).performScrollTo().assertExists()
    }

    @Test
    fun signedOutThereIsNoFamilyAndTheNamesStayHere() {
        compose.setContent {
            NightLightTheme(night = false) {
                SettingsScreen(SettingsSamples.state(account = AccountSummary.SignedOut), SettingsActions.None, family = FamilySamples.state())
            }
        }
        compose.onNodeWithText("Family").assertDoesNotExist()
        compose.onNodeWithText("Names stay on this phone and your watch.").performScrollTo().assertExists()
    }

    @Test
    fun aboutHasThePrivacyPolicy() {
        var opened = 0
        compose.setContent {
            NightLightTheme(night = false) {
                SettingsScreen(SettingsSamples.state(), object : SettingsActions { override fun onOpenPrivacy() { opened++ } })
            }
        }
        compose.onNodeWithText("Privacy policy").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theJoinConfirmationSaysWhatJoiningDoes() {
        var joined = 0
        compose.setContent {
            NightLightTheme(night = false) { JoinScreen(FamilySamples.join, object : JoinActions { override fun onJoin() { joined++ } }) }
        }
        compose.onNodeWithText("Join partner@example.com's family?").assertExists()
        compose.onNodeWithText("Your first baby and the family's first baby become one baby.").assertExists()
        compose.onNodeWithText("Not now").assertExists()
        compose.onNodeWithText("Join").performClick()
        assertEquals(1, joined)
    }
}
