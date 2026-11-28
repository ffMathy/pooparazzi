package com.ffmathy.pooparazzi.checks

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONObject
import com.ffmathy.pooparazzi.phone.launch.AppLinkInbox
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

/**
 * The checks' phone in a build with a backup: the same real app as [Phone], with accounts on and Firebase pointed at
 * the local Authentication and Firestore emulators (a debug build's switch, FirebaseEmulators in the app; the
 * emulators are ail.config.json's service `firebase`, scripts/ail/firebase-emulators.sh), as a release build with a
 * Firebase configuration talks to the real project. Every test starts on an empty backend, as a new project would.
 *
 * What a parent reads outside the app comes from the emulators' own test interfaces, never from the app: the email
 * with the sign-in link is the Authentication emulator's list of sent emails (its out-of-band codes), and the share
 * sheet is what the app hands Android's share sheet. A link is opened as tapping it does: Android hands it to the
 * open app (its link handling), after the browser has followed the emulator's redirect, as it does on a phone.
 */
abstract class BackedUpPhone : Phone() {
    @Before
    fun emptyTheBackend() {
        http("DELETE", "http://$authEmulator/emulator/v1/projects/$PROJECT/accounts")
        http("DELETE", "http://$firestoreEmulator/emulator/v1/projects/$PROJECT/databases/(default)/documents")
    }

    /**
     * The next test is a new install, so a new process, as [Phone] treats the settings: a link the app had not handled
     * yet (an invite put aside) lives in the process's memory and must not reach the next test's app.
     */
    @After
    fun forgetTheLinks() {
        AppLinkInbox.shared.inviteHandled()
        AppLinkInbox.shared.takeEmailLink()
    }

    companion object {
        /** The emulators' demo project (firebase/package.json's tests use the same). */
        const val PROJECT = "demo-pooparazzi"

        /** Where the emulators listen: the service's env (ail.config.json), else firebase/firebase.json's ports. */
        val authEmulator: String = System.getenv("FIREBASE_AUTH_EMULATOR_HOST")?.takeIf { it.isNotBlank() } ?: "127.0.0.1:9099"
        val firestoreEmulator: String = System.getenv("FIRESTORE_EMULATOR_HOST")?.takeIf { it.isNotBlank() } ?: "127.0.0.1:8080"

        private var disabledBefore: String? = null

        /**
         * Before the app starts: the build with a backup, on the emulators. The unit tests turn accounts off for every
         * test (app/build.gradle.kts); these checks are the build that has them.
         */
        @JvmStatic
        @BeforeClass
        fun aBuildWithABackup() {
            disabledBefore = System.getProperty("pooparazzi.accounts.disabled")
            System.setProperty("pooparazzi.accounts.disabled", "false")
            val host = authEmulator.substringBefore(':')
            System.setProperty("pooparazzi.firebase.emulators", "$host:${authEmulator.substringAfter(':')}:${firestoreEmulator.substringAfter(':')}")
        }

        @JvmStatic
        @AfterClass
        fun backToTheBuildWithout() {
            disabledBefore?.let { System.setProperty("pooparazzi.accounts.disabled", it) } ?: System.clearProperty("pooparazzi.accounts.disabled")
            System.clearProperty("pooparazzi.firebase.emulators")
        }

        /** Calls the emulators' test interface; returns the status and the body. */
        fun http(
            method: String,
            url: String,
            followRedirects: Boolean = true,
            json: String? = null,
            owner: Boolean = json == null,
        ): Triple<Int, String, String?> {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.instanceFollowRedirects = followRedirects
            // The emulators' own test interface takes the owner's token (the console's view); a phone's request has none.
            if (owner) connection.setRequestProperty("Authorization", "Bearer owner")
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            if (json != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(json.toByteArray()) }
            }
            return try {
                val status = connection.responseCode
                val body = (if (status < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
                Triple(status, body, connection.getHeaderField("Location"))
            } finally {
                connection.disconnect()
            }
        }
    }
}

/** Every sign-in email sent to [email] so far, oldest first: the links in them (the Authentication emulator's inbox). */
fun BackedUpPhone.signInEmailsTo(email: String): List<String> {
    val (_, body) = BackedUpPhone.http("GET", "http://${BackedUpPhone.authEmulator}/emulator/v1/projects/${BackedUpPhone.PROJECT}/oobCodes")
    val codes = JSONObject(body).getJSONArray("oobCodes")
    return (0 until codes.length()).map { codes.getJSONObject(it) }
        .filter { it.optString("email") == email && it.optString("requestType") == "EMAIL_SIGNIN" }
        .map { it.getString("oobLink") }
}

/**
 * Waits for the next sign-in email to [email] (one more than [before]) and returns where its link leads once the browser
 * has followed it: the emulator's link answers with a redirect to the app's site, `https://demo-pooparazzi.web.app/signin?…`,
 * the App Link that opens the app.
 */
fun BackedUpPhone.waitForSignInLink(email: String, before: Int = 0): String {
    var links = emptyList<String>()
    waitUntil("a sign-in email to $email", 20_000) { links = signInEmailsTo(email); links.size > before }
    val (status, _, location) = BackedUpPhone.http("GET", links.last(), followRedirects = false)
    check(status in 300..399 && location != null) { "the sign-in link did not redirect ($status)" }
    return location
}

/** Opens [link] in the app as tapping it does: Android hands the link to the open app (it is singleTop). */
fun Phone.openLink(link: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE)
    compose.activityRule.scenario.onActivity { activity ->
        val launched = activity.intent
        InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, intent)
        // The harness's bookkeeping, not the app's: ActivityScenario knows its activity by the intent it launched it
        // with, and the app keeps the link's intent (setIntent), so the scenario would never see it close at the end.
        activity.intent = launched
    }
    pass(500)
}

/** Types [text] into the Email field (the first text field on the screen), replacing what it held. */
fun Phone.typeEmail(text: String) {
    compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(text)
    pass(300)
}

/**
 * Signs in by email from a screen showing the email form (the Welcome or the Account screen): types [email], taps
 * "Email me a sign-in link", reads the link from the inbox and opens it. Ends where the app goes next (the consent).
 */
fun BackedUpPhone.signInByEmail(email: String) {
    val before = signInEmailsTo(email).size
    typeEmail(email)
    scrollDownAndTap("Email me a sign-in link")
    waitFor("Check your email", 30_000)
    openLink(waitForSignInLink(email, before))
}

/** The text the app last handed Android's share sheet (what the sheet shows and shares), or null. */
fun Phone.sharedText(): String? {
    ShadowLooper.idleMainLooper()
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
    val started = shadowOf(app).nextStartedActivity ?: return null
    val shared = if (started.action == Intent.ACTION_CHOOSER) started.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else started
    return shared?.getStringExtra(Intent.EXTRA_TEXT)
}

/**
 * Another phone of the parent's asks for a sign-in link to [email], as the app there does (Firebase Authentication's
 * `sendOobCode`, the call behind "Email me a sign-in link"). The checks run one phone, so the other one is this request
 * to the emulator, made the way that phone's app makes it; this phone never asked, so it doesn't know the address.
 */
fun BackedUpPhone.anotherPhoneAsksForASignInLink(email: String) {
    anotherPhoneSends(email)
}

/** The accounts the backend has, as its owner sees them in the console (the Authentication emulator's list). */
fun BackedUpPhone.accountsInTheBackend(): List<String> {
    val (_, body) = BackedUpPhone.http(
        "POST",
        "http://${BackedUpPhone.authEmulator}/identitytoolkit.googleapis.com/v1/projects/${BackedUpPhone.PROJECT}/accounts:query",
        json = "{}", owner = true,
    )
    val users = JSONObject(body).optJSONArray("userInfo") ?: return emptyList()
    return (0 until users.length()).map { users.getJSONObject(it).optString("email") }
}

/** The documents of a top-level [collection] of the backend's database, as its owner sees them in the console. */
fun BackedUpPhone.documentsInTheBackend(collection: String): Int {
    val (_, body) = BackedUpPhone.http("GET", "http://${BackedUpPhone.firestoreEmulator}/v1/projects/${BackedUpPhone.PROJECT}/databases/(default)/documents/$collection")
    return JSONObject(body.ifBlank { "{}" }).optJSONArray("documents")?.length() ?: 0
}

/** The first run after the Welcome (or the consent), skipped as [Phone.freshInstall] skips it: Your baby, the introduction, then Home. */
fun Phone.skipTheFirstRun() {
    waitFor("Skip for now", 30_000)
    tap("Skip for now")
    waitFor("Skip")
    tap("Skip")
    waitFor("Start sleep", 20_000)
    // Home ignores taps for a moment after a screen closes.
    pass(1_000)
}

/** On the consent screen: "I agree". */
fun Phone.agreeToTheBackup() {
    waitFor("I agree", 30_000)
    scrollDownAndTap("I agree")
    pass(1_000)
}

/** A new install signed in by email as [email] from the Welcome, agreed to the backup and its first run skipped: Home. */
fun BackedUpPhone.newInstallSignedInAs(email: String) {
    waitFor("No account needed", 20_000)
    signInByEmail(email)
    agreeToTheBackup()
    skipTheFirstRun()
}

/** From Home: Settings, then its Account row (it reads [row]: the address, or "Not signed in"), the Account screen. */
fun Phone.openTheAccountScreen(row: String) {
    tap("Settings")
    waitFor(row, 30_000)
    tap(row)
    waitFor("Account")
    pass(500)
}

/** From Home: Settings › Family › Invite someone; returns the link in the text the share sheet shares. */
fun Phone.inviteSomeone(): String {
    tap("Settings")
    waitFor("Invite someone", 30_000)
    scrollDownAndTap("Invite someone")
    var text: String? = null
    waitUntil("the share sheet", 30_000) { text = sharedText(); text != null }
    return Regex("https://\\S+").find(text!!)!!.value
}

/** From Settings: the Account row, then Sign out; ends on the Account screen, signed out. */
fun Phone.signOutFromSettings(email: String) {
    waitFor(email, 30_000)
    tap(email)
    waitFor("Sign out")
    scrollDownAndTap("Sign out")
    waitFor("Email me a sign-in link", 30_000)
}

/**
 * A family of two: a new install signs in as [creator] and invites someone; then the phone is handed over: [creator]
 * signs out and [member] signs in on it, agrees, opens the invite link and joins. The checks run one phone, so the
 * second member uses the same one, through the same screens their own phone has. Ends on Home, signed in as [member].
 * Returns the invite link (used now).
 */
fun BackedUpPhone.familyOfTwo(creator: String = "parent@example.com", member: String = "partner@example.com"): String {
    newInstallSignedInAs(creator)
    val link = inviteSomeone()
    signOutFromSettings(creator)
    signInByEmail(member)
    agreeToTheBackup()
    waitFor("Start sleep", 30_000)
    openLink(link)
    waitFor("Join", 30_000)
    tap("Join")
    waitFor("Done", 30_000)
    tap("Done")
    waitFor("Start sleep", 20_000)
    pass(1_000)
    return link
}

private fun BackedUpPhone.anotherPhoneSends(email: String) {
    val (status, body) = BackedUpPhone.http(
        "POST",
        "http://${BackedUpPhone.authEmulator}/identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=demo-api-key",
        json = JSONObject()
            .put("requestType", "EMAIL_SIGNIN").put("email", email)
            .put("continueUrl", "https://${BackedUpPhone.PROJECT}.web.app/signin").put("canHandleCodeInApp", true)
            .toString(),
    )
    check(status == 200) { "the other phone's sign-in email was not sent ($status): $body" }
}
