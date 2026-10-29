package com.ffmathy.pooparazzi.phone.account

import android.annotation.SuppressLint
import android.content.Context
import com.ffmathy.pooparazzi.phone.R

/**
 * What the Google services Gradle plugin generated from `app/google-services.json` (docs/BACKEND.md › Connect the
 * app). Read by name, without touching any Firebase class, so builds without the file compile and run with accounts
 * turned off.
 */
object FirebaseConfig {
    /** True when the build carries a Firebase configuration (the plugin's `google_app_id`). */
    fun isPresent(context: Context): Boolean = string(context, "google_app_id") != null

    /** The OAuth web client id Google sign-in needs, present once the Google provider is enabled; else null. */
    fun webClientId(context: Context): String? = string(context, "default_web_client_id")

    @SuppressLint("DiscouragedApi") // The resource exists only in builds with a configuration: no R field to use.
    private fun string(context: Context, name: String): String? {
        val resources = context.resources
        // The app's own resource package (the applicationId), whatever the R namespace is.
        val id = resources.getIdentifier(name, "string", resources.getResourcePackageName(R.string.app_name))
        if (id == 0) return null
        return resources.getString(id).trim().takeIf { it.isNotEmpty() }
    }
}
