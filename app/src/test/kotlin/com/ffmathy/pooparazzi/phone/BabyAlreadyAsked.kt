package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.account.WelcomePrefs
import org.junit.rules.ExternalResource

/**
 * Before the activity starts: the first run's "Your baby" screen counts as answered, so a test of a fresh install
 * lands where it did before that screen existed (Home, or the Welcome). [BabyIntroFlowTest] covers the screen itself.
 * Use it with an `order` before the activity's compose rule.
 */
class BabyAlreadyAsked : ExternalResource() {
    override fun before() {
        WelcomePrefs(ApplicationProvider.getApplicationContext<Application>()).babyAsked = true
    }

    companion object {
        /** The same, for a test that starts the activity itself. */
        fun apply() = BabyAlreadyAsked().before()
    }
}
