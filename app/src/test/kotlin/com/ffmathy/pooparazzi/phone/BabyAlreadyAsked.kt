package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.account.WelcomePrefs
import org.junit.rules.ExternalResource

/**
 * Before the activity starts: the first run's "Your baby" screen and the introduction count as answered, so a test of
 * a fresh install lands where it did before those screens existed (Home, or the Welcome). [BabyIntroFlowTest] covers
 * them.
 * Use it with an `order` before the activity's compose rule.
 */
class BabyAlreadyAsked : ExternalResource() {
    override fun before() {
        val prefs = WelcomePrefs(ApplicationProvider.getApplicationContext<Application>())
        prefs.babyAsked = true
        prefs.introSeen = true
    }

    companion object {
        /** The same, for a test that starts the activity itself. */
        fun apply() = BabyAlreadyAsked().before()
    }
}
