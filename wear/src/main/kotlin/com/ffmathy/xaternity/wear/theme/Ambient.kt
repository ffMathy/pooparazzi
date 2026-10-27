package com.ffmathy.xaternity.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager

/**
 * True while the watch is in ambient (always-on) mode. Apps targeting SDK 36 are always-on, so every screen has an
 * ambient rendering (DESIGN_SPEC §10.6): no fills, no glow or motion, outlines, text in [WatchAmbient.content].
 * False in previews and tests, where no ambient manager is provided.
 */
@Composable
@ReadOnlyComposable
fun isAmbient(): Boolean = LocalAmbientModeManager.current?.currentAmbientMode is AmbientMode.Ambient
