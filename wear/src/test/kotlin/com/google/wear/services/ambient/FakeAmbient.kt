// Test-only stand-in for the Wear SDK ambient service (see com.google.wear.FakeWearSdk): never enters ambient.
package com.google.wear.services.ambient

import android.app.Activity
import android.content.Context

open class AmbientComponentState internal constructor() {
    class ActivityStateRegistry internal constructor() {
        fun onPause() = Unit
        fun onResume() = Unit
    }

    companion object {
        @JvmStatic
        fun makeActivityStateRegistry(): ActivityStateRegistry = ActivityStateRegistry()
    }
}

open class AmbientManager internal constructor() {
    open fun createController(context: Context, options: AmbientOptions): Controller = Controller()

    class Controller internal constructor() {
        fun destroy() = Unit
    }

    interface ConfigurationDetails {
        fun isBurnInProtectionEnabled(): Boolean
        fun isLowBitDepthEnabled(): Boolean
    }

    interface AmbientComponentListener {
        fun onEnterAmbient(details: ConfigurationDetails?, transitionComplete: Boolean) = Unit
        fun onExitAmbient() = Unit
        fun onUpdateAmbient() = Unit
    }

    interface AmbientTransitionListener
}

class AmbientOptions internal constructor() {
    companion object {
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun makeActivityOptions(
            activity: Activity,
            registry: AmbientComponentState.ActivityStateRegistry,
            listener: AmbientManager.AmbientComponentListener,
            transitionListener: AmbientManager.AmbientTransitionListener?,
        ): AmbientOptions = AmbientOptions()
    }
}
