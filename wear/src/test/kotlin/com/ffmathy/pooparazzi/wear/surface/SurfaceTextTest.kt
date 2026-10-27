package com.ffmathy.pooparazzi.wear.surface

import android.content.Context
import android.icu.util.ULocale
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.expression.pipeline.DynamicTypeBindingRequest
import androidx.wear.protolayout.expression.pipeline.DynamicTypeEvaluator
import androidx.wear.protolayout.expression.pipeline.DynamicTypeValueReceiver
import androidx.wear.protolayout.expression.pipeline.PlatformTimeUpdateNotifier
import com.ffmathy.pooparazzi.core.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.Executor

/**
 * The tile and complication text: the dynamic expressions the system evaluates must read exactly like the app's own
 * formatters at every duration (DESIGN_SPEC §7, §9.5), in English and Danish. Evaluated with ProtoLayout's own
 * expression evaluator on a fixed clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
abstract class SurfaceTextTest(private val locale: ULocale) {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var text: SurfaceText

    @Before
    fun setUp() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        text = SurfaceText.from(context, SampleData.ZONE)
    }

    /** Evaluates [expression] with the platform time at [now]. */
    private fun evaluate(expression: DynamicString, now: Long): String {
        val direct = Executor { it.run() }
        val evaluator = DynamicTypeEvaluator(
            DynamicTypeEvaluator.Config.Builder()
                .setClock { Instant.ofEpochMilli(now) }
                .setPlatformTimeUpdateNotifier(
                    object : PlatformTimeUpdateNotifier {
                        override fun setReceiver(executor: Executor, tick: Runnable) = executor.execute(tick)
                        override fun clearReceiver() = Unit
                    },
                )
                .build(),
        )
        var result: String? = null
        val bound = evaluator.bind(
            DynamicTypeBindingRequest.forDynamicString(
                expression,
                locale,
                direct,
                object : DynamicTypeValueReceiver<String> {
                    override fun onData(newData: String) {
                        result = newData
                    }

                    override fun onInvalidated() = Unit
                },
            ),
        )
        bound.startEvaluation()
        bound.close()
        return checkNotNull(result) { "No value for $expression" }
    }

    /** Start times relative to 14:32:20 that cover every branch of the grammar. */
    private val now = SampleData.NOW_1432 + 20_000
    private val starts = listOf(
        now - 5_000, // same minute: "<1m" / "just now"
        now - 30_000, // previous clock minute: 1m
        now - 48 * MINUTE,
        now - 59 * MINUTE,
        now - 60 * MINUTE,
        now - 130 * MINUTE,
        now - (23 * 60 + 59) * MINUTE,
        now - 24 * 60 * MINUTE,
        now - (27 * 60 + 5) * MINUTE,
        now + 90_000, // clock skew: a start in the future
    )

    @Test
    fun durationMatchesTheFormatter() {
        for (start in starts) {
            val expected = text.duration(start, now)
            assertEquals("from $start", expected.static, evaluate(expected.dynamic, now))
        }
    }

    @Test
    fun agoMatchesTheFormatter() {
        for (start in starts) {
            val expected = text.ago(start, now)
            assertEquals("from $start", expected.static, evaluate(expected.dynamic, now))
        }
    }

    @Test
    fun spokenFormsMatchTheFormatter() {
        for (start in starts) {
            val duration = text.spokenDuration(start, now)
            assertEquals("from $start", duration.static, evaluate(duration.dynamic, now))
            val ago = text.spokenAgo(start, now)
            assertEquals("from $start", ago.static, evaluate(ago.dynamic, now))
        }
    }

    @Test
    fun templatesPlaceTheValue() {
        val template = SurfaceText.Template("Asleep %1\$s")
        val value = text.template(template, text.duration(now - 67 * MINUTE, now))
        assertEquals(template.fill(text.formatters.duration.format(67 * MINUTE)), value.static)
        assertEquals(value.static, evaluate(value.dynamic, now))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}

@Config(qualifiers = "en")
class EnglishSurfaceTextTest : SurfaceTextTest(ULocale.ENGLISH) {
    @Test
    fun englishExamples() {
        val text = SurfaceText.from(ApplicationProvider.getApplicationContext(), SampleData.ZONE)
        assertEquals("2h 10m ago", text.ago(SampleData.at(12, 22), SampleData.NOW_1432).static)
        assertEquals("2 hours 10 minutes ago", text.spokenAgo(SampleData.at(12, 22), SampleData.NOW_1432).static)
        // Tile text is laid out for the widest value it ticks to.
        assertEquals("00h 00m ago", text.ago(SampleData.at(14, 30), SampleData.NOW_1432).longest)
        assertEquals("Asleep 00h 00m", text.template(SurfaceText.Template("Asleep %1\$s"), text.duration(SampleData.at(14, 0), SampleData.NOW_1432)).longest)
    }
}

@Config(qualifiers = "da")
class DanishSurfaceTextTest : SurfaceTextTest(ULocale("da")) {
    @Test
    fun danishExamples() {
        val text = SurfaceText.from(ApplicationProvider.getApplicationContext(), SampleData.ZONE)
        assertEquals("2t 10m siden", text.ago(SampleData.at(12, 22), SampleData.NOW_1432).static)
        assertEquals("1 time 7 minutter", text.spokenDuration(SampleData.at(2, 5), SampleData.NOW_0312).static)
    }
}
