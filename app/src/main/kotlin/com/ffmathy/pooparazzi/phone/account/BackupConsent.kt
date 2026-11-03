package com.ffmathy.pooparazzi.phone.account

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The explicit consent to the backup and the family sharing (docs/PRIVACY.md §3, BABIES_AND_BACKUP §3.6), per account
 * and per phone. Signing in only creates the account; nothing is uploaded to Firestore (no family is created or joined,
 * no log, babies or email address) until the signed-in account has agreed on this phone. Signing out (or deleting the
 * account) withdraws it: signing in again asks again.
 */
interface BackupConsent {
    /** The accounts (uids) that agreed on this phone. */
    val agreed: StateFlow<Set<String>>

    /** [uid] tapped "I agree". */
    fun agree(uid: String)

    /** [uid] signed out, was deleted, or declined. */
    fun withdraw(uid: String)
}

/** [BackupConsent] in memory: previews, tests and builds without accounts. */
open class MemoryConsent(initial: Set<String> = emptySet()) : BackupConsent {
    private val state = MutableStateFlow(initial)
    override val agreed: StateFlow<Set<String>> = state.asStateFlow()

    override fun agree(uid: String) {
        state.update { it + uid }
        saved(state.value)
    }

    override fun withdraw(uid: String) {
        if (uid !in state.value) return
        state.update { it - uid }
        saved(state.value)
    }

    /** Called with every change, to keep it. */
    protected open fun saved(agreed: Set<String>) = Unit
}

/**
 * [BackupConsent] in shared preferences. Device-local: excluded from backup and device transfer
 * (res/xml/data_extraction_rules.xml), so a new phone asks again.
 *
 * A consent covers the text it was given to. When what is uploaded changes, the key changes ([KEY_AGREED]), so every
 * signed-in account sees the consent screen again, with the new text, before anything more is uploaded; the answers
 * kept under older keys are dropped.
 */
class ConsentPrefs private constructor(private val prefs: SharedPreferences) :
    MemoryConsent(prefs.getStringSet(KEY_AGREED, null).orEmpty().toSet()) {

    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    init {
        // Consents given to an older text do not count for the current one.
        if (OLD_KEYS.any(prefs::contains)) prefs.edit { OLD_KEYS.forEach(::remove) }
    }

    override fun saved(agreed: Set<String>) = prefs.edit { putStringSet(KEY_AGREED, agreed) }

    internal companion object {
        const val FILE = "account_consent"

        /** The current consent text's answers: version 2 (November 2026) added the babies' birth dates. */
        private const val KEY_AGREED = "agreed_v2"

        /** The answers to earlier texts: "agreed" (version 1: the log, the babies' names and the email address). */
        private val OLD_KEYS = listOf("agreed")
    }
}
