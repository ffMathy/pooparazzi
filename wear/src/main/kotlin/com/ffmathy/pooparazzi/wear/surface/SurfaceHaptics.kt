package com.ffmathy.pooparazzi.wear.surface

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.ffmathy.pooparazzi.wear.state.XHaptic

/**
 * Haptics for taps the app handles outside its own UI (the tile's one-tap logs), where Compose's
 * `HapticFeedback` is not available (DESIGN_SPEC §5.11): a predefined system effect per app haptic. Only ever played
 * in response to the parent's own tap; no sound.
 */
object SurfaceHaptics {
    private const val TAG = "SurfaceHaptics"

    fun play(context: Context, haptic: XHaptic) {
        val effect = when (haptic) {
            XHaptic.CONFIRM, XHaptic.TOGGLE_ON, XHaptic.TOGGLE_OFF -> VibrationEffect.EFFECT_CLICK
            XHaptic.REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK
            XHaptic.SEGMENT_TICK -> VibrationEffect.EFFECT_TICK
        }
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createPredefined(effect))
        } catch (e: SecurityException) {
            Log.w(TAG, "Vibration not allowed", e)
        }
    }
}
