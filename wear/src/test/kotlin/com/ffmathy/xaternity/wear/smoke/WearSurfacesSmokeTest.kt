package com.ffmathy.xaternity.wear.smoke

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.expression.VersionBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourcesCallback
import androidx.wear.tiles.ResourcesData
import androidx.wear.tiles.ResourcesRequestData
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileCallback
import androidx.wear.tiles.TileData
import androidx.wear.tiles.TileProvider
import androidx.wear.tiles.TileRequestData
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.wear.WearApp
import com.ffmathy.xaternity.wear.complication.LastDiaperComplicationService
import com.ffmathy.xaternity.wear.complication.SinceFeedComplicationService
import com.ffmathy.xaternity.wear.complication.SleepComplicationService
import com.ffmathy.xaternity.wear.complication.SleepLongComplicationService
import com.ffmathy.xaternity.wear.complication.XComplicationService
import com.ffmathy.xaternity.wear.tile.XaternityTileService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * Platform smoke tests of the watch surfaces on every supported Wear OS API level, in a cold process started by the
 * real [WearApp]: the tile service through its real binder (tile request, a tap that logs, the resources request, a
 * renderer without dynamic text) and every complication data source answering a request and a face-editor preview.
 * No fakes: Room on disk, the Data Layer without Play Services.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33, 34, 36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class WearSurfacesSmokeTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // The process as a tile or complication bind starts it: WearApp.onCreate has run (Core, SurfaceUpdater).
        assertTrue("The manifest application is WearApp", app is WearApp)
    }

    @After
    fun tearDown() = Core.reset()

    @Test
    fun tileServiceAnswersThroughItsBinder() {
        val provider = bindTile()

        val first = requestTile(provider, clickId = null)
        assertTrue("timeline has entries", first.tileTimeline!!.timelineEntries.isNotEmpty())
        assertFalse("resources version is set", first.resourcesVersion.isEmpty())

        // The host asks for the resources of that version; the icons the layout uses must be in them.
        val resources = requestResources(provider, first.resourcesVersion)
        assertTrue("tile images are served: ${resources.idToImageMapping.keys}", resources.idToImageMapping.isNotEmpty())

        // A tap on Pee in that layout (the first layout's token is 1) logs a diaper without opening the app.
        val before = diaperCount()
        val second = requestTile(provider, clickId = "log_pee#1")
        assertTrue(second.tileTimeline!!.timelineEntries.isNotEmpty())
        assertEquals("one pee logged from the tile", before + 1, diaperCount())

        // A renderer without dynamic text (schema 1.0) gets a static tile with a refresh interval.
        val static = requestTile(provider, clickId = null, renderer = 1 to 0)
        assertTrue(static.freshnessIntervalMillis > 0)
    }

    @Test
    fun everyComplicationAnswersItsRequest() {
        val services: List<Pair<Class<out XComplicationService>, ComplicationType>> = listOf(
            SinceFeedComplicationService::class.java to ComplicationType.SHORT_TEXT,
            LastDiaperComplicationService::class.java to ComplicationType.SHORT_TEXT,
            SleepComplicationService::class.java to ComplicationType.SHORT_TEXT,
            SleepLongComplicationService::class.java to ComplicationType.LONG_TEXT,
        )
        for ((serviceClass, type) in services) {
            val service = Robolectric.buildService(serviceClass, Intent(ComplicationDataSourceService.ACTION_COMPLICATION_UPDATE_REQUEST))
                .create().get()
            service.onBind(Intent(ComplicationDataSourceService.ACTION_COMPLICATION_UPDATE_REQUEST))

            val data = complication(service, ComplicationRequest(1, type, false))
            assertNotNull(serviceClass.simpleName, data)
            assertEquals(serviceClass.simpleName, type, data!!.type)
            assertNotNull("${serviceClass.simpleName} tap", data.tapAction)

            // A face slot of another type gets no data rather than a crash.
            val other = if (type == ComplicationType.SHORT_TEXT) ComplicationType.LONG_TEXT else ComplicationType.SHORT_TEXT
            assertTrue(complication(service, ComplicationRequest(2, other, false)) is NoDataComplicationData)

            assertEquals(type, service.getPreviewData(type)?.type)
        }
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    /** Diapers in the store the tile service writes to (the process-wide Core graph). */
    private fun diaperCount(): Int = runBlocking { Core.repository.observeEntries().first().count { it.kind == EntryKind.DIAPER } }

    private fun complication(service: XComplicationService, request: ComplicationRequest): ComplicationData? {
        val result = AtomicReference<Result<ComplicationData?>>()
        service.onComplicationRequest(
            request,
            object : ComplicationDataSourceService.ComplicationRequestListener {
                override fun onComplicationData(complicationData: ComplicationData?) {
                    result.set(Result.success(complicationData))
                }
            },
        )
        await { result.get() != null }
        return result.get().getOrThrow()
    }

    /**
     * The bound service. Held for the whole test: the tile binder only keeps a weak reference to its service (the
     * system holds the strong one on a watch), so an unreferenced service can be collected and drop requests.
     */
    private var tileService: XaternityTileService? = null

    private fun bindTile(): TileProvider {
        val intent = Intent(TileService.ACTION_BIND_TILE_PROVIDER)
        val service = Robolectric.buildService(XaternityTileService::class.java, intent).create().get()
        tileService = service
        return TileProvider.Stub.asInterface(service.onBind(intent))
    }

    private fun device(renderer: Pair<Int, Int>) = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(227)
        .setScreenHeightDp(227)
        .setScreenDensity(2f)
        .setFontScale(1f)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
        .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
        .setRendererSchemaVersion(VersionBuilders.VersionInfo.Builder().setMajor(renderer.first).setMinor(renderer.second).build())
        .build()

    private fun requestTile(provider: TileProvider, clickId: String?, renderer: Pair<Int, Int> = 1 to 500): TileBuilders.Tile {
        val builder = RequestBuilders.TileRequest.Builder().setTileId(TILE_ID).setDeviceConfiguration(device(renderer))
        if (clickId != null) builder.setCurrentState(clickedState(clickId))
        val bytes = protoBytes(builder.build())
        val result = AtomicReference<TileData>()
        provider.onTileRequest(
            TILE_ID,
            TileRequestData(bytes, TileRequestData.VERSION_PROTOBUF),
            object : TileCallback.Stub() {
                override fun updateTileData(tileData: TileData) = result.set(tileData)
            },
        )
        await { result.get() != null }
        val proto = Class.forName("androidx.wear.tiles.proto.TileProto\$Tile").getMethod("parseFrom", ByteArray::class.java)
            .invoke(null, result.get().contents)
        return TileBuilders.Tile::class.java.getMethod("fromProto", proto.javaClass).invoke(null, proto) as TileBuilders.Tile
    }

    private fun requestResources(provider: TileProvider, version: String): ResourceBuilders.Resources {
        val request = RequestBuilders.ResourcesRequest.Builder().setTileId(TILE_ID).setVersion(version).setDeviceConfiguration(device(1 to 500)).build()
        val result = AtomicReference<ResourcesData>()
        provider.onResourcesRequest(
            TILE_ID,
            ResourcesRequestData(protoBytes(request), ResourcesRequestData.VERSION_PROTOBUF),
            object : ResourcesCallback.Stub() {
                override fun updateResources(resourcesData: ResourcesData) = result.set(resourcesData)
            },
        )
        await { result.get() != null }
        val proto = Class.forName("androidx.wear.protolayout.proto.ResourceProto\$Resources").getMethod("parseFrom", ByteArray::class.java)
            .invoke(null, result.get().contents)
        return ResourceBuilders.Resources::class.java.getMethod("fromProto", proto.javaClass).invoke(null, proto) as ResourceBuilders.Resources
    }

    /** `lastClickableId` is only settable on the proto (the host fills it in). */
    private fun clickedState(clickId: String): StateBuilders.State {
        val protoClass = Class.forName("androidx.wear.protolayout.proto.StateProto\$State")
        val builder = protoClass.getMethod("newBuilder").invoke(null)
        builder.javaClass.getMethod("setLastClickableId", String::class.java).invoke(builder, clickId)
        val proto = builder.javaClass.getMethod("build").invoke(builder)
        return StateBuilders.State::class.java.getMethod("fromProto", protoClass).invoke(null, proto) as StateBuilders.State
    }

    /** The proto classes are runtime-only dependencies of the tiles library: serialise through reflection. */
    private fun protoBytes(builderOutput: Any): ByteArray {
        val proto = builderOutput.javaClass.getMethod("toProto").invoke(builderOutput)
        return proto.javaClass.getMethod("toByteArray").invoke(proto) as ByteArray
    }

    /** Runs the main looper (service callbacks) while background work (Room, IO) finishes. */
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) break
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for the service" }
            Thread.sleep(10)
        }
    }

    private companion object {
        const val TILE_ID = 7
    }
}
