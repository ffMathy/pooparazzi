package com.ffmathy.pooparazzi.phone.privacy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

/** The privacy policy (docs/PRIVACY.md): what is stored, where, for how long, and the parent's rights. */
object PrivacyPolicy {
    /** The policy in the language of the app; the Danish one for Danish. */
    fun url(danish: Boolean): String = if (danish) URL_DA else URL_EN

    const val URL_EN = "https://github.com/ffMathy/pooparazzi/blob/main/docs/PRIVACY.md"
    const val URL_DA = "https://github.com/ffMathy/pooparazzi/blob/main/docs/PRIVACY.da.md"
}

/** Opens the privacy policy in the browser, in the app's language. */
fun Context.openPrivacyPolicy() {
    val danish = resources.configuration.locales[0].language == "da"
    val intent = Intent(Intent.ACTION_VIEW, PrivacyPolicy.url(danish).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No browser on the phone: nothing to open it in.
        Log.w("PrivacyPolicy", "No app opens the privacy policy", e)
    }
}
