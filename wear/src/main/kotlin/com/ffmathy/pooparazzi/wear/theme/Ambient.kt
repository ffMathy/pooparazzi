package com.ffmathy.pooparazzi.wear.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientModeManager
import androidx.wear.compose.foundation.LocalAmbientModeManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * True while the watch is in ambient (always-on) mode. Apps targeting SDK 36 are always-on, so every screen has an
 * ambient rendering (DESIGN_SPEC §10.6): no fills, no glow or motion, outlines, text in [WatchAmbient.content].
 * False in previews and tests, and on watches without ambient support, where no ambient manager is provided.
 */
@Composable
@ReadOnlyComposable
fun isAmbient(): Boolean = LocalAmbientModeManager.current?.currentAmbientMode is AmbientMode.Ambient

/**
 * Which always-on mechanism this watch offers. `rememberAmbientModeManager()` (wear-compose-foundation) builds on the
 * Wear SDK's ambient service, which the optional `wear-sdk` shared library only provides on Wear OS 6 and later
 * (API 36): calling it anywhere else throws `NoClassDefFoundError` on the first composition. Earlier watches (minSdk
 * 30 is Wear OS 3) have the wearable shared library and its `AmbientLifecycleObserver` instead.
 */
enum class AmbientSupport {
    /** Wear OS 6+: wear-compose's own manager on the Wear SDK ambient service. */
    WEAR_SDK,

    /** Wear OS 3–5: [WearableAmbientModeManager] on `AmbientLifecycleObserver`. */
    WEARABLE_LIBRARY,

    /** Neither (e.g. tests): no always-on; every screen renders interactive. */
    NONE,
    ;

    companion object {
        private const val WEAR_SDK_AMBIENT_MANAGER = "com.google.wear.services.ambient.AmbientManager"
        private const val WEAR_SDK_AMBIENT_STATE = "com.google.wear.services.ambient.AmbientComponentState"
        private const val WEARABLE_CONTROLLER = "com.google.android.wearable.compat.WearableActivityController"

        /** The support on this device ([hasClass] tells whether a shared-library class can be loaded). */
        fun detect(sdkInt: Int = Build.VERSION.SDK_INT, hasClass: (String) -> Boolean = ::classPresent): AmbientSupport = when {
            sdkInt >= Build.VERSION_CODES.BAKLAVA && hasClass(WEAR_SDK_AMBIENT_MANAGER) && hasClass(WEAR_SDK_AMBIENT_STATE) -> WEAR_SDK
            hasClass(WEARABLE_CONTROLLER) -> WEARABLE_LIBRARY
            else -> NONE
        }

        private fun classPresent(name: String): Boolean = try {
            Class.forName(name, false, AmbientSupport::class.java.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }
}

/**
 * The [AmbientModeManager] the app's screens read ([isAmbient], the minute tick in ambient) on Wear OS 3–5, fed by
 * an `AmbientLifecycleObserver` the activity registers (see [AmbientSupport.WEARABLE_LIBRARY]). Callbacks arrive on
 * the main thread.
 */
class WearableAmbientModeManager : AmbientModeManager, AmbientLifecycleObserver.AmbientLifecycleCallback {
    private var mode: AmbientMode by mutableStateOf(AmbientMode.Interactive)
    private val tickCallbacks = mutableListOf<() -> Unit>()

    override val currentAmbientMode: AmbientMode get() = mode

    /** Suspends until the next ambient tick (about once a minute while ambient), then runs [block]. */
    override suspend fun withAmbientTick(block: () -> Unit) = suspendCancellableCoroutine { continuation ->
        val callback: () -> Unit = {
            block()
            continuation.resume(Unit)
        }
        synchronized(tickCallbacks) { tickCallbacks += callback }
        continuation.invokeOnCancellation { synchronized(tickCallbacks) { tickCallbacks -= callback } }
    }

    override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
        mode = AmbientMode.Ambient(ambientDetails.burnInProtectionRequired, ambientDetails.deviceHasLowBitAmbient)
    }

    override fun onUpdateAmbient() {
        val due = synchronized(tickCallbacks) { tickCallbacks.toList().also { tickCallbacks.clear() } }
        due.forEach { it() }
    }

    override fun onExitAmbient() {
        mode = AmbientMode.Interactive
    }
}
