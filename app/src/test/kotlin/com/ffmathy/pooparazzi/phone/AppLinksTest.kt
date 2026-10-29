package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.net.Uri
import com.ffmathy.pooparazzi.phone.account.FirebaseConfig
import com.ffmathy.pooparazzi.phone.account.PrivacyPolicy
import com.ffmathy.pooparazzi.phone.launch.AppLink
import com.ffmathy.pooparazzi.phone.launch.AppLinkInbox
import com.ffmathy.pooparazzi.phone.launch.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The website's App Links (docs/BACKEND.md › App Links): invites and email sign-in links on both of the project's
 * hosts, nothing else; the inbox they wait in; and where the privacy policy is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AppLinksTest {
    private val domain = "demo-pooparazzi.web.app"

    private fun parse(link: String) = AppLinks.parse(Uri.parse(link), domain)

    @Test
    fun invitesOnBothHosts() {
        assertEquals(AppLink.Invite("AbCdEfGhIjKlMnOpQrSt_-"), parse("https://demo-pooparazzi.web.app/join/AbCdEfGhIjKlMnOpQrSt_-"))
        assertEquals(AppLink.Invite("AbCdEfGhIjKlMnOpQrSt_-"), parse("https://demo-pooparazzi.firebaseapp.com/join/AbCdEfGhIjKlMnOpQrSt_-"))
        assertNull(parse("https://demo-pooparazzi.web.app/join/"))
        assertNull(parse("https://demo-pooparazzi.web.app/join/a/b"))
    }

    @Test
    fun emailSignInLinksWhole() {
        val link = "https://demo-pooparazzi.web.app/__/auth/links?link=https%3A%2F%2Fdemo-pooparazzi.firebaseapp.com%2F__%2Fauth%2Faction%3FoobCode%3Dx"
        assertEquals(AppLink.EmailSignIn(link), parse(link))
        val signin = "https://demo-pooparazzi.firebaseapp.com/signin?apiKey=k&oobCode=x&mode=signIn"
        assertEquals(AppLink.EmailSignIn(signin), parse(signin))
    }

    @Test
    fun nothingElse() {
        assertNull(parse("https://evil.example.com/join/AbCdEfGhIjKlMnOpQrSt_-"))
        assertNull(parse("http://demo-pooparazzi.web.app/join/AbCdEfGhIjKlMnOpQrSt_-"))
        assertNull(parse("https://demo-pooparazzi.web.app/privacy"))
        assertNull(parse("https://other-project.web.app/signin"))
        assertNull("no site without a configuration", AppLinks.parse(Uri.parse("https://demo-pooparazzi.web.app/join/x"), null))
        assertNull(AppLinks.parse(null, domain))
    }

    @Test
    fun theHostingDomainComesFromTheProjectId() {
        assertEquals("demo-pooparazzi.web.app", FirebaseConfig.hostingDomainOf("demo-pooparazzi"))
        assertNull(FirebaseConfig.hostingDomainOf("Not a project/id"))
        assertEquals(setOf("demo-pooparazzi.web.app", "demo-pooparazzi.firebaseapp.com"), AppLinks.hostsOf(domain))
    }

    @Test
    fun theInboxKeepsAnInviteUntilHandledAndAnEmailLinkUntilTaken() {
        val inbox = AppLinkInbox()
        inbox.post(AppLink.Invite("code"))
        inbox.post(AppLink.EmailSignIn("link"))
        assertEquals("code", inbox.invite.value)
        assertEquals("link", inbox.takeEmailLink())
        assertNull(inbox.takeEmailLink())
        assertEquals("code", inbox.invite.value)
        inbox.inviteHandled()
        assertNull(inbox.invite.value)
    }

    @Test
    fun thePrivacyPolicy() {
        assertEquals("https://demo-pooparazzi.web.app/privacy", PrivacyPolicy.url(domain))
        assertEquals("https://github.com/ffMathy/pooparazzi/blob/main/docs/PRIVACY.md", PrivacyPolicy.url(null))
    }
}
