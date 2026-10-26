package com.ffmathy.xaternity.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.ui.icons.EventAvatar
import com.ffmathy.xaternity.core.ui.icons.XAvatars
import com.ffmathy.xaternity.core.ui.icons.XDrawables
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.icons.rememberBothIcon
import com.ffmathy.xaternity.core.ui.lamp.Lamp
import com.ffmathy.xaternity.core.ui.lamp.LampEvent
import com.ffmathy.xaternity.core.ui.lamp.LampVariant
import com.ffmathy.xaternity.core.ui.tokens.XTokens
import com.ffmathy.xaternity.core.ui.type.PhoneType
import com.ffmathy.xaternity.core.ui.type.WatchType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render checks for icons, the Lamp and the fonts. Images are written when Roborazzi records
 * (`./gradlew :core:recordRoborazziDebug`) to core/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w600dp-h1000dp-xxhdpi")
class CoreVisualTest {
    private val night = XTokens.Night

    @Test
    fun icons() {
        captureRoboImage("build/outputs/roborazzi/core_icons.png") {
            Column(Modifier.background(night.surfaceContainer).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XIcons.all.entries.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { (name, icon) ->
                            Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    XIcon(icon, null, night.onSurface, size = 24.dp)
                                    XIcon(icon, null, XTokens.NightEvents.feed.strong, Modifier.padding(start = 4.dp), size = 18.dp)
                                }
                                BasicText(name, style = TextStyle(color = night.onSurfaceVariant, fontSize = 9.sp))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val events = XTokens.NightEvents
                    XIcon(rememberBothIcon(events.pee.strong, events.poop.strong), null, Color.Unspecified, size = 40.dp)
                    XIcon(XIcons.breast, null, events.feed.strong, size = 32.dp)
                    XIcon(XIcons.food, null, events.feed.strong, size = 32.dp)
                    XIcon(XIcons.checkbox, null, night.outline, size = 24.dp)
                    XIcon(XIcons.checkboxChecked, null, events.pee.strong, size = 24.dp)
                    XIcon(XIcons.sideLeftRight, null, events.feed.strong, size = 40.dp)
                    XIcon(XIcons.sideRightLeft, null, events.feed.strong, size = 40.dp)
                }
            }
        }
    }

    @Test
    fun lamps() {
        captureRoboImage("build/outputs/roborazzi/core_lamps.png") {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row {
                    LampCell(night.background) { Lamp(LampEvent.FEED, running = false, elapsedMinutes = 130, variant = LampVariant.NIGHT, size = 180.dp) }
                    LampCell(night.background) { Lamp(LampEvent.SLEEP, running = true, elapsedMinutes = 67, variant = LampVariant.NIGHT, size = 180.dp) }
                    LampCell(night.background) { Lamp(LampEvent.FEED, running = true, elapsedMinutes = 18, variant = LampVariant.NIGHT, size = 180.dp) }
                }
                Row {
                    val day = XTokens.Day
                    LampCell(day.background) { Lamp(LampEvent.FEED, running = false, elapsedMinutes = 130, variant = LampVariant.DAY, size = 180.dp) }
                    LampCell(day.background) { Lamp(LampEvent.SLEEP, running = true, elapsedMinutes = 67, variant = LampVariant.DAY, size = 180.dp) }
                    LampCell(day.background) { Lamp(LampEvent.FEED, running = true, elapsedMinutes = 18, variant = LampVariant.DAY, size = 180.dp) }
                }
                Row {
                    LampCell(Color.Black) { Lamp(LampEvent.SLEEP, running = true, elapsedMinutes = 67, variant = LampVariant.WATCH) }
                    LampCell(Color.Black) { Lamp(LampEvent.FEED, running = true, elapsedMinutes = 3, variant = LampVariant.WATCH) }
                    LampCell(Color.Black) { Lamp(LampEvent.SLEEP, running = true, elapsedMinutes = 67, variant = LampVariant.WATCH_AMBIENT) }
                }
            }
        }
    }

    @Test
    fun type() {
        captureRoboImage("build/outputs/roborazzi/core_type.png") {
            Column(Modifier.background(night.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    buildAnnotatedString {
                        withStyle(PhoneType.heroNumeral.toSpanStyle().copy(color = night.onSurface)) { append("2") }
                        withStyle(PhoneType.heroUnit.toSpanStyle().copy(color = night.onSurface)) { append("h ") }
                        withStyle(PhoneType.heroNumeral.toSpanStyle().copy(color = night.onSurface)) { append("10") }
                        withStyle(PhoneType.heroUnit.toSpanStyle().copy(color = night.onSurface)) { append("m") }
                    },
                )
                Sample("Last fed · ago · 12:22 · Sidste måltid", PhoneType.labelLarge)
                Sample("Modermælkserstatning · Tis + bæ · Søvn", PhoneType.headlineSmall)
                Sample("Pee 48m ago · 13:44 · 0123456789", PhoneType.titleMedium)
                Sample("since 02:05 · siden kl. 02:05", PhoneType.dataMedium)
                Sample("Earlier? Tidligere? Hvilken side?", PhoneType.bodyMedium)
                Sample("Breast · Left → Right · Venstre → Højre", PhoneType.titleMedium)
                Sample("Højre → Venstre", PhoneType.headlineSmall)
                Sample("Left → Right · 18m", PhoneType.dataSmall)
                Sample("1h 07m · <1m · 2t 10m", WatchType.lampNumeral)
                Sample("just now · lige nu", WatchType.statusWord)
                Sample("Synced with phone · 14:31 · Venstre → Højre", WatchType.bodySmall)
            }
        }
    }

    @Test
    fun avatars() {
        val entries = SampleData.scenarioWithRunningFeed().distinctBy { listOf(it.kind, it.feedType, it.pee, it.poop) }
        captureRoboImage("build/outputs/roborazzi/core_avatars.png") {
            Column {
                for ((background, events) in listOf(night.surfaceContainer to XTokens.NightEvents, XTokens.Day.surfaceContainer to XTokens.DayEvents)) {
                    Row(
                        Modifier.background(background).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        entries.forEach { EventAvatar(it, size = XAvatars.PhoneSize, events = events) }
                        entries.forEach { EventAvatar(it, size = XAvatars.WatchSize, events = events) }
                    }
                }
            }
        }
    }

    @Test
    fun drawables() {
        captureRoboImage("build/outputs/roborazzi/core_drawables.png") {
            Column(Modifier.background(Color.Black).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XDrawables.all.values.chunked(10).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { Image(painterResource(it), null, Modifier.size(24.dp)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(XDrawables.bothNight), null, Modifier.size(40.dp))
                    Image(painterResource(XDrawables.sleepRunning), null, Modifier.size(56.dp))
                    Image(painterResource(XDrawables.checkboxChecked), null, Modifier.size(40.dp))
                }
            }
        }
    }

    @Composable
    private fun LampCell(background: Color, content: @Composable () -> Unit) {
        Box(Modifier.size(200.dp).background(background), contentAlignment = Alignment.Center) { content() }
    }

    @Composable
    private fun Sample(text: String, style: TextStyle) {
        BasicText(text, style = style.copy(color = night.onSurface))
    }
}
