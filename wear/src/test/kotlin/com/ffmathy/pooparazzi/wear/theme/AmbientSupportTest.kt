package com.ffmathy.pooparazzi.wear.theme

import android.os.Build
import androidx.wear.ambient.AmbientLifecycleObserver.AmbientDetails
import androidx.wear.compose.foundation.AmbientMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which always-on mechanism the app picks, and the Wear OS 3–5 manager it builds on the wearable library. */
class AmbientSupportTest {
    private val wearSdk = setOf(
        "com.google.wear.services.ambient.AmbientManager",
        "com.google.wear.services.ambient.AmbientComponentState",
    )
    private val wearable = setOf("com.google.android.wearable.compat.WearableActivityController")

    @Test
    fun theWearSdkManagerIsOnlyUsedWhereTheWearSdkAmbientServiceExists() {
        val all = wearSdk + wearable
        assertEquals(AmbientSupport.WEAR_SDK, AmbientSupport.detect(Build.VERSION_CODES.BAKLAVA) { it in all })
        // Wear OS 3–5: the wearable library, even if some Wear SDK classes are around.
        assertEquals(AmbientSupport.WEARABLE_LIBRARY, AmbientSupport.detect(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { it in all })
        assertEquals(AmbientSupport.WEARABLE_LIBRARY, AmbientSupport.detect(Build.VERSION_CODES.R) { it in wearable })
        // An API 36 watch without the Wear SDK library.
        assertEquals(AmbientSupport.WEARABLE_LIBRARY, AmbientSupport.detect(Build.VERSION_CODES.BAKLAVA) { it in wearable })
        assertEquals(AmbientSupport.NONE, AmbientSupport.detect(Build.VERSION_CODES.BAKLAVA) { false })
    }

    @Test
    fun theWearableManagerFollowsTheObserverCallbacks() = runBlocking {
        val manager = WearableAmbientModeManager()
        assertEquals(AmbientMode.Interactive, manager.currentAmbientMode)

        manager.onEnterAmbient(AmbientDetails(burnInProtectionRequired = true, deviceHasLowBitAmbient = false))
        val ambient = manager.currentAmbientMode
        assertTrue(ambient is AmbientMode.Ambient && ambient.isBurnInProtectionRequired && !ambient.isLowBitAmbientSupported)

        var ticks = 0
        val waiting = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { manager.withAmbientTick { ticks++ } }
        assertEquals(0, ticks)
        manager.onUpdateAmbient()
        waiting.await()
        yield()
        assertEquals(1, ticks)
        // A tick with nobody waiting does nothing.
        manager.onUpdateAmbient()
        assertEquals(1, ticks)

        manager.onExitAmbient()
        assertEquals(AmbientMode.Interactive, manager.currentAmbientMode)
    }
}
