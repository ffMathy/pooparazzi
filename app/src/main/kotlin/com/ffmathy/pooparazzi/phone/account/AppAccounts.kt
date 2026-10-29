package com.ffmathy.pooparazzi.phone.account

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ffmathy.pooparazzi.core.cloud.CloudBackend
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * The phone's accounts, once per process: [FirebaseAccounts] when the build carries a Firebase configuration
 * ([FirebaseConfig]), else [NoAccounts]. Without a configuration no Firebase class is touched at all (the manifest
 * removes Firebase's own start-up provider; the app initialises Firebase here, and only with a configuration).
 */
object AppAccounts {
    private const val TAG = "AppAccounts"

    /** Set by the unit tests (app/build.gradle.kts): no Firebase, whatever configuration the build has. */
    const val DISABLED_PROPERTY = "pooparazzi.accounts.disabled"

    @Volatile
    private var current: Accounts? = null
    private var backend: CloudBackend? = null

    /** The accounts: [NoAccounts] until [init] (and in builds without a configuration). */
    val accounts: Accounts get() = current ?: NoAccounts

    /**
     * Sets up the accounts and returns the backend the backup needs, or null without a configuration. Call once, from
     * `Application.onCreate()`, before `Core.init`.
     */
    fun init(context: Context): CloudBackend? = synchronized(this) {
        // Once per process: Firestore's settings can be set only before its first use.
        if (current != null) return backend
        val app = context.applicationContext
        if (System.getProperty(DISABLED_PROPERTY) == "true" || !FirebaseConfig.isPresent(app)) {
            current = NoAccounts
            return null
        }
        val backend = try {
            val firebase = FirebaseApp.getApps(app).firstOrNull() ?: FirebaseApp.initializeApp(app)
            if (firebase == null) {
                Log.w(TAG, "The Firebase configuration could not be read: accounts are off")
                null
            } else {
                val auth = FirebaseAuth.getInstance(firebase)
                val firestore = FirebaseFirestore.getInstance(firebase)
                // No disk cache: the backup keeps its own state in Room and reads only from the server.
                firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
                val accounts = FirebaseAccounts(app, auth, FirebaseConfig.webClientId(app), ConsentPrefs(app))
                current = accounts
                CloudBackend(FirestoreCloudStore(firestore, auth), accounts.cloud)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase could not start: accounts are off", e)
            null
        }
        if (backend == null) current = NoAccounts
        this.backend = backend
        backend
    }

    /** Replaces the accounts (a fake) for tests. */
    @VisibleForTesting
    fun install(accounts: Accounts) {
        current = accounts
    }

    @VisibleForTesting
    fun reset() {
        current = null
        backend = null
    }
}

/** Whether the Welcome was answered on this phone. */
interface WelcomeStore {
    var seen: Boolean

    /** For builds and tests that never show it. */
    object Seen : WelcomeStore {
        override var seen: Boolean
            get() = true
            set(_) = Unit
    }
}

/**
 * Whether this phone has shown the Welcome screen (BABIES_AND_BACKUP §3.1). Device-local: excluded from backup and
 * device transfer (res/xml/data_extraction_rules.xml), so a new phone asks again.
 */
class WelcomePrefs(context: Context) : WelcomeStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var seen: Boolean
        get() = prefs.getBoolean(KEY_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_SEEN, value) }

    internal companion object {
        const val FILE = "account_welcome"
        private const val KEY_SEEN = "welcome_seen"
    }
}
