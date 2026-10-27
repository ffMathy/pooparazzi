package com.ffmathy.xaternity.wear.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ffmathy.xaternity.wear.state.XHaptic

/** The Compose haptic for each app haptic (DESIGN_SPEC §5.11). No sound, ever. */
fun XHaptic.toFeedbackType(): HapticFeedbackType = when (this) {
    XHaptic.CONFIRM -> HapticFeedbackType.Confirm
    XHaptic.REJECT -> HapticFeedbackType.Reject
    XHaptic.TOGGLE_ON -> HapticFeedbackType.ToggleOn
    XHaptic.TOGGLE_OFF -> HapticFeedbackType.ToggleOff
    XHaptic.SEGMENT_TICK -> HapticFeedbackType.SegmentTick
}

fun HapticFeedback.perform(haptic: XHaptic) = performHapticFeedback(haptic.toFeedbackType())
