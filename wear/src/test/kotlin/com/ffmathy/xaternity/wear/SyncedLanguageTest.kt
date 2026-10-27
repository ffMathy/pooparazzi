package com.ffmathy.xaternity.wear

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.settings.AppLanguage
import com.ffmathy.xaternity.core.settings.Settings
import com.ffmathy.xaternity.wear.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The language chosen on the phone reaches the watch through `/settings` and is applied with
 * [LocaleManager.setApplicationLocales] (V1_ADDITIONS §5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncedLanguageTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager: LocaleManager get() = context.getSystemService(LocaleManager::class.java)
    private val settings = FakeSettingsRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        SyncedLanguage.follow(context, settings, scope)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun awaitLocales(expected: LocaleList) = runBlocking {
        withTimeout(5_000) {
            while (manager.applicationLocales != expected) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }
    }

    @Test
    fun danishFromThePhoneIsApplied() {
        settings.state.value = Settings(language = AppLanguage.DA, updatedAtMillis = 10)
        awaitLocales(LocaleList.forLanguageTags("da"))
        assertEquals("da", manager.applicationLocales[0].language)
    }

    @Test
    fun systemDefaultFromThePhoneClearsThePerAppLanguage() {
        settings.state.value = Settings(language = AppLanguage.EN, updatedAtMillis = 10)
        awaitLocales(LocaleList.forLanguageTags("en"))
        settings.state.value = Settings(language = AppLanguage.SYSTEM, updatedAtMillis = 20)
        awaitLocales(LocaleList.getEmptyLocaleList())
    }

    @Test
    fun settingsNeverChangedLeaveTheWatchLanguageAlone() {
        manager.applicationLocales = LocaleList.forLanguageTags("da")
        // Default settings (never changed on any device) must not reset it.
        settings.state.value = Settings(language = AppLanguage.SYSTEM, updatedAtMillis = 0)
        runBlocking { delay(200) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LocaleList.forLanguageTags("da"), manager.applicationLocales)
    }
}
