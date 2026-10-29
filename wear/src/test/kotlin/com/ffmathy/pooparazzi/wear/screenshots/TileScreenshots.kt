package com.ffmathy.pooparazzi.wear.screenshots

import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.expression.VersionBuilders.VersionInfo
import androidx.wear.protolayout.material3.materialScopeWithResources
import androidx.wear.tiles.renderer.TileRenderer
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.Settings as CoreSettings
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.core.ui.tokens.XTokens
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.surface.SurfaceText
import com.ffmathy.pooparazzi.wear.tile.TileTheme
import com.ffmathy.pooparazzi.wear.tile.TileUiState
import com.ffmathy.pooparazzi.wear.tile.pooparazziTile
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The tile rendered by the real ProtoLayout renderer (`tiles-renderer`'s [TileRenderer]) into a view, under
 * Robolectric, and captured with Roborazzi: `wear/screenshots/<lang>/12*-tile*.png`.
 *
 * Two things differ from a watch, both because there is no tile host here:
 * - time-dependent text is rendered as its static value at the sample time (the renderer would otherwise evaluate the
 *   dynamic expressions against the real clock, not the scenario's 14:32);
 * - the header icon is drawn by the system on a watch; here the harness draws the app's `lamp` glyph into the icon
 *   slot the layout reserves for it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
abstract class TileScreenshots(private val lang: String) {

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun s12_tile() = capture("12-tile", WearSamples.tileIdle())

    @Test
    fun s12b_tileAsleep() = capture("12b-tile-asleep", WearSamples.tileAsleep())

    @Test
    fun s12c_tileJustLogged() = capture("12c-tile-just-logged", WearSamples.tileJustLogged())

    /** After the 14:32 breast feed: Row A stays "Feed", with "18m ago" (nothing runs). */
    @Test
    fun s12d_tileAfterBreast() = capture("12d-tile-after-breast", WearSamples.tileAfterBreastfeed())

    /** No baby name set: the title is the last diaper, as in the approved mockup. */
    @Test
    fun s12e_tileNoName() = capture("12e-tile-no-name", WearSamples.tileIdle(babyName = ""))

    /** Twins, both unnamed, the second current: the idle title is its label, "Baby 2" (BABIES_AND_BACKUP §2.5). */
    @Test
    fun s12j_tileSecondBaby() {
        val twins = CoreSettings(babies = listOf(Baby(Baby.FIRST_ID), Baby(SampleData.TWIN_ID)), selectedBabyId = SampleData.TWIN_ID)
        capture("12j-tile-second-baby", WearSamples.tileIdle(babyName = TileUiState.titleName(twins, LabelFormatter(context))))
    }

    @Test
    fun s12f_tileGuard() = capture("12f-tile-guard", WearSamples.tileGuard())

    /** Below 225 dp the title is hidden (DESIGN_SPEC §3.5.10). */
    @Test
    fun s12g_tileSmall() = capture("12g-tile-small", WearSamples.tileIdle(), sizeDp = 192)

    /** Below 225 dp a tile log is confirmed in Row A's secondary label, since there is no title (DESIGN_SPEC §3.6). */
    @Test
    fun s12i_tileSmallJustLogged() = capture("12i-tile-small-just-logged", WearSamples.tileJustLogged(), sizeDp = 192)

    /** Large text (1.3): titles and labels stay whole; the just-logged title drops its glyphs first. */
    @Test
    @Config(fontScale = 1.3f)
    fun s12h_tileLargeFont() = capture("12h-tile-large-font", WearSamples.tileJustLogged())

    private fun capture(name: String, state: TileUiState, sizeDp: Int = 227) {
        val density = context.resources.displayMetrics.density
        val device = DeviceParameters.Builder()
            .setScreenWidthDp(sizeDp)
            .setScreenHeightDp(sizeDp)
            .setScreenDensity(density)
            .setScreenShape(SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DEVICE_PLATFORM_WEAR_OS)
            .setFontScale(context.resources.configuration.fontScale)
            .setRendererSchemaVersion(VersionInfo.Builder().setMajor(1).setMinor(RENDERER_MINOR).build())
            .build()
        val scope = ProtoLayoutScope()
        val root = materialScopeWithResources(context, scope, device, allowDynamicTheme = false, defaultColorScheme = TileTheme.colorScheme) {
            pooparazziTile(state, SurfaceText.from(context, state.zone), token = 1, dynamic = false)
        }
        val sizePx = (sizeDp * density).roundToInt()
        // Roborazzi captures views inside an activity window.
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
        activity.setContentView(parent, ViewGroup.LayoutParams(sizePx, sizePx))
        val renderer = TileRenderer(activity, Runnable::run) { }
        val inflated = renderer.inflateAsync(Layout.Builder().setRoot(root).build(), scope.collectResources(), parent)
        shadowOf(Looper.getMainLooper()).idle()
        inflated.get(10, TimeUnit.SECONDS)
        addSystemIcon(activity, parent, sizeDp, density)
        layout(parent, sizePx)
        shadowOf(Looper.getMainLooper()).idle()
        parent.captureRoboImage(filePath = "screenshots/$lang/$name.png", roborazziOptions = RoundWatch)
    }

    /** The header icon the tile host draws above the title (DESIGN_SPEC §3.6: `lamp` 24, onSurface). */
    private fun addSystemIcon(activity: Context, parent: FrameLayout, sizeDp: Int, density: Float) {
        val iconPx = (ICON_DP * density).roundToInt()
        val icon = ImageView(activity).apply {
            setImageResource(XDrawables.lamp)
            setColorFilter(XTokens.Watch.onSurface.toArgb())
        }
        parent.addView(
            icon,
            FrameLayout.LayoutParams(iconPx, iconPx).apply {
                leftMargin = ((sizeDp - ICON_DP) / 2f * density).roundToInt()
                topMargin = (HEADER_TOP_DP * density).roundToInt()
            },
        )
    }

    private fun layout(view: View, sizePx: Int) {
        val spec = View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, sizePx, sizePx)
    }

    private companion object {
        const val RENDERER_MINOR = 500
        const val ICON_DP = 24f

        /** Where primaryLayout reserves the 24 dp icon slot: its 3 dp header margin. */
        const val HEADER_TOP_DP = 3f
    }
}

@Config(qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class EnglishTileScreenshots : TileScreenshots("en")

@Config(qualifiers = "da-w227dp-h227dp-round-watch-xhdpi")
class DanishTileScreenshots : TileScreenshots("da")
