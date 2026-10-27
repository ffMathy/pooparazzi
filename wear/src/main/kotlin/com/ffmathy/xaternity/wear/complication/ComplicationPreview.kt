package com.ffmathy.xaternity.wear.complication

import android.content.Context
import android.graphics.drawable.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.ui.tooling.preview.WearPreviewLargeRound
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.ffmathy.xaternity.core.domain.Summary
import com.ffmathy.xaternity.core.domain.summarize
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.wear.R
import com.ffmathy.xaternity.wear.surface.SurfaceText
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * What a complication data source sends, read the way a watch face reads it: text, title, image and content
 * description at one instant.
 */
data class ComplicationParts(val text: String, val title: String?, val icon: Icon?, val description: String) {
    companion object {
        fun of(data: ComplicationData, context: Context, at: Instant): ComplicationParts? {
            val res = context.resources
            return when (data) {
                is ShortTextComplicationData -> ComplicationParts(
                    text = data.text.getTextAt(res, at).toString(),
                    title = data.title?.getTextAt(res, at)?.toString(),
                    icon = data.monochromaticImage?.image,
                    description = data.contentDescription?.getTextAt(res, at)?.toString().orEmpty(),
                )
                is LongTextComplicationData -> ComplicationParts(
                    text = data.text.getTextAt(res, at).toString(),
                    title = data.title?.getTextAt(res, at)?.toString(),
                    icon = data.monochromaticImage?.image,
                    description = data.contentDescription?.getTextAt(res, at)?.toString().orEmpty(),
                )
                else -> null
            }
        }
    }
}

/**
 * A **preview of the complication data** (DESIGN_SPEC §3.7) on a neutral stand-in face, after the "Watch ·
 * Complications" mockup: three SHORT_TEXT slots (since feed, last diaper, sleep) and the LONG_TEXT pill. The face is
 * not part of the app; real faces draw the same data in their own style and colours (the images are monochrome).
 */
@Composable
fun ComplicationDataPreview(summary: Summary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val at = Instant.ofEpochMilli(summary.now)
    // Keyed on the configuration: the language or 12/24 h setting changes the text.
    val text = remember(summary.zone, configuration) { SurfaceText.from(context, summary.zone) }
    val parts = remember(summary, text) {
        val content = ComplicationContent(context, text, static = true)
        XComplication.entries.associateWith { ComplicationParts.of(content.build(it, summary, tapAction = null), context, at) }
    }
    val locale = configuration.locales[0]
    val time = text.clock(summary.now)
    val date = DateTimeFormatter.ofPattern("EEE d", locale).format(at.atZone(summary.zone)).uppercase(locale)

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            BasicText(date, style = TextStyle(color = FaceColors.date, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp))
            BasicText(time, style = TextStyle(color = FaceColors.text, fontSize = 46.sp, lineHeight = 50.sp))
            // The label sits where the face is widest, so it fits on one line in both languages.
            BasicText(
                stringResource(R.string.complication_preview_caption),
                style = TextStyle(color = FaceColors.caption, fontSize = 9.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = Modifier.widthIn(max = 200.dp),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(XComplication.SINCE_FEED, XComplication.LAST_DIAPER, XComplication.SLEEP).forEach { slot ->
                    parts[slot]?.let { ShortSlot(it) }
                }
            }
            Spacer(Modifier.height(6.dp))
            parts[XComplication.SLEEP_LONG]?.let { LongSlot(it) }
        }
    }
}

@Composable
private fun ShortSlot(parts: ComplicationParts) {
    Column(
        Modifier
            .size(56.dp)
            .background(FaceColors.slot, CircleShape)
            .semantics { contentDescription = parts.description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        parts.icon?.let { SlotIcon(it, 18) }
        BasicText(
            parts.text,
            style = TextStyle(color = FaceColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun LongSlot(parts: ComplicationParts) {
    Row(
        Modifier
            .width(140.dp)
            .height(32.dp)
            .background(FaceColors.slot, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = parts.description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.icon?.let { SlotIcon(it, 16) }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.clearAndSetSemantics { }) {
            BasicText(parts.text, style = TextStyle(color = FaceColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            parts.title?.let {
                BasicText(it, style = TextStyle(color = FaceColors.date, fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A monochrome complication image, tinted the way a face would (white here). */
@Composable
private fun SlotIcon(icon: Icon, sizeDp: Int) {
    Image(
        painter = painterResource(icon.resId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(FaceColors.text),
        modifier = Modifier.size(sizeDp.dp),
    )
}

/** The neutral stand-in face's greys (the mockup's face, not app tokens). */
private object FaceColors {
    val text = Color(0xFFFFFFFF)
    val date = Color(0xFFBDBDBD)
    val caption = Color(0xFF9E9E9E)
    val slot = Color(0xFF1C1C1C)
}

@WearPreviewLargeRound
@Composable
private fun ComplicationDataIdlePreview() {
    ComplicationDataPreview(summarize(SampleData.scenario(), SampleData.NOW_1432, SampleData.ZONE))
}

@Preview(widthDp = 227, heightDp = 227, locale = "da")
@Composable
private fun ComplicationDataAsleepPreview() {
    ComplicationDataPreview(summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, SampleData.ZONE))
}
