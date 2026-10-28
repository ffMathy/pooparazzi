package com.ffmathy.pooparazzi.wear.tile

import android.content.Context
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.material3.materialScopeWithResources
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tiles.tooling.preview.TilePreviewHelper
import androidx.wear.tooling.preview.devices.WearDevices
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.surface.SurfaceText

/*
 * Tile previews (Android Studio) for the reference scenario (V1_ADDITIONS §6). Time-dependent text shows its value at
 * the sample time, like the screenshots in wear/screenshots/<lang>/12*-tile*.png.
 */

@Preview(device = WearDevices.LARGE_ROUND, name = "Idle 14:32")
@Preview(device = WearDevices.SMALL_ROUND, name = "Idle 14:32, small")
@Preview(device = WearDevices.LARGE_ROUND, name = "Idle 14:32, Danish", locale = "da")
internal fun tileIdlePreview(context: Context) = tilePreview(context, WearSamples.tileIdle())

@Preview(device = WearDevices.LARGE_ROUND, name = "No baby name")
internal fun tileNoNamePreview(context: Context) = tilePreview(context, WearSamples.tileIdle(babyName = ""))

@Preview(device = WearDevices.LARGE_ROUND, name = "Asleep 03:12")
internal fun tileAsleepPreview(context: Context) = tilePreview(context, WearSamples.tileAsleep())

@Preview(device = WearDevices.LARGE_ROUND, name = "Just logged")
internal fun tileJustLoggedPreview(context: Context) = tilePreview(context, WearSamples.tileJustLogged())

@Preview(device = WearDevices.LARGE_ROUND, name = "After a breast feed 14:50")
internal fun tileAfterBreastfeedPreview(context: Context) = tilePreview(context, WearSamples.tileAfterBreastfeed())

@Preview(device = WearDevices.LARGE_ROUND, name = "Guard")
internal fun tileGuardPreview(context: Context) = tilePreview(context, WearSamples.tileGuard())

@Preview(device = WearDevices.LARGE_ROUND, name = "Large font", fontScale = 1.3f)
internal fun tileLargeFontPreview(context: Context) = tilePreview(context, WearSamples.tileIdle())

private fun tilePreview(context: Context, state: TileUiState) = TilePreviewData { request ->
    val root = materialScopeWithResources(
        context = context,
        protoLayoutScope = request.scope,
        deviceConfiguration = request.deviceConfiguration,
        allowDynamicTheme = false,
        defaultColorScheme = TileTheme.colorScheme,
    ) {
        pooparazziTile(state, SurfaceText.from(context, state.zone), token = 0, dynamic = false)
    }
    TilePreviewHelper.singleTimelineEntryTileBuilder(Layout.Builder().setRoot(root).build()).build()
}
