package com.ffmathy.pooparazzi.phone.account

import android.content.Context
import com.ffmathy.pooparazzi.phone.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.util.concurrent.atomic.AtomicInteger

/**
 * The local Firebase emulators (Authentication and Firestore, `firebase/firebase.json`) in place of a real project, for
 * debug builds only (docs/BACKEND.md › Running the app against the emulators): the AIL account checks run the app
 * against them (checks/e2e/account/), and a debug build on a device can too. Release builds never read the switch
 * ([BuildConfig.DEBUG] is false there, and their FIREBASE_EMULATORS is empty), so they behave exactly as before.
 *
 * The switch is `host`, or `host:authPort:firestorePort` (default ports 9099 and 8080, as `firebase.json` has them):
 * the system property [PROPERTY] (the unit tests and checks), else the Gradle property of the same name at build time
 * (BuildConfig.FIREBASE_EMULATORS), e.g. `10.0.2.2` for the emulators on the computer of an Android emulator.
 */
internal class FirebaseEmulators(val host: String, val authPort: Int, val firestorePort: Int) {
    /**
     * A Firebase app of its own for the demo project: `demo-` projects need no credentials and reach no real project,
     * and the build needs no app/google-services.json. A new one per [AppAccounts.init] (named), so an app that starts
     * again in the same process (each Robolectric test) starts signed out, with a Firestore not used yet.
     */
    fun newApp(context: Context): FirebaseApp = FirebaseApp.initializeApp(context, OPTIONS, "emulators-${count.incrementAndGet()}")

    companion object {
        const val PROPERTY = "pooparazzi.firebase.emulators"

        /** The demo project; its Hosting site (the links' domain) is `demo-pooparazzi.web.app`. */
        const val PROJECT_ID = "demo-pooparazzi"

        private val OPTIONS by lazy {
            FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-api-key")
                .build()
        }

        /**
         * A placeholder for the web client id, so the screens offer "Continue with Google" as a release build does. The
         * emulators can't sign in with Google from the app: the account picker needs Google Play services and a real
         * OAuth client, so tapping it ends in an error.
         */
        const val WEB_CLIENT_ID = "demo-web-client-id"

        private val count = AtomicInteger()

        /** The emulators this debug build or test is told to use, or null (always in release builds). */
        fun configured(): FirebaseEmulators? {
            if (!BuildConfig.DEBUG) return null
            val value = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() } ?: BuildConfig.FIREBASE_EMULATORS
            return parse(value)
        }

        fun parse(value: String): FirebaseEmulators? {
            val parts = value.trim().split(':')
            val host = parts[0].takeIf { it.isNotEmpty() } ?: return null
            return FirebaseEmulators(host, parts.getOrNull(1)?.toIntOrNull() ?: 9099, parts.getOrNull(2)?.toIntOrNull() ?: 8080)
        }
    }
}
