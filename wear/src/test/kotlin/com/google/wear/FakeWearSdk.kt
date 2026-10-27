// Test-only stand-in for the Wear SDK shared library (`wear-sdk`, `com.google.wear.*`), which Robolectric does not
// provide. `androidx.wear.compose.foundation.rememberAmbientModeManager()` links against it unconditionally, so
// without these classes MainActivity cannot be composed under Robolectric at all (NoClassDefFoundError). Only the
// members that AmbientModeManagerImpl calls exist here, with the same JVM signatures as the platform library.
package com.google.wear

import android.content.Context

class Sdk private constructor() {
    companion object {
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> getWearManager(context: Context, type: Class<T>): T =
            type.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as T
    }
}
