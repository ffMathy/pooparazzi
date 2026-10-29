package com.ffmathy.pooparazzi.phone.account

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Which accounts agreed to the backup on this phone (BABIES_AND_BACKUP §3.4), and when: the consent version per
 * account id. The backend keeps its own record of it ([com.ffmathy.pooparazzi.core.cloud.CloudStore.recordConsent]).
 */
interface ConsentStore {
    /** The consent version [uid] agreed to on this phone, or null. */
    fun versionFor(uid: String): Int?

    fun record(uid: String, version: Int, atMillis: Long)

    /** In memory: tests and previews. */
    class InMemory : ConsentStore {
        private val versions = mutableMapOf<String, Int>()
        override fun versionFor(uid: String): Int? = versions[uid]
        override fun record(uid: String, version: Int, atMillis: Long) {
            versions[uid] = version
        }
    }
}

/**
 * [ConsentStore] in shared preferences. Device-local: excluded from backup and device transfer
 * (res/xml/data_extraction_rules.xml), so a new phone asks again when the parent signs in there.
 */
class ConsentPrefs(context: Context) : ConsentStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun versionFor(uid: String): Int? = prefs.getInt(versionKey(uid), 0).takeIf { it > 0 }

    override fun record(uid: String, version: Int, atMillis: Long) = prefs.edit {
        putInt(versionKey(uid), version)
        putLong(atKey(uid), atMillis)
    }

    internal companion object {
        const val FILE = "backup_consent"
        private fun versionKey(uid: String) = "version:$uid"
        private fun atKey(uid: String) = "at:$uid"
    }
}
