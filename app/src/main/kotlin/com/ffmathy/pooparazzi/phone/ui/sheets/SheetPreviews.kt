package com.ffmathy.pooparazzi.phone.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.preview.HomeSamples
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme

/** Sheet contents without the modal window (previews cannot show dialogs). */
@Composable
private fun SheetPreview(night: Boolean, content: @Composable () -> Unit) {
    NightLightTheme(night = night) {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column { content() }
        }
    }
}

@Preview(name = "Feed sheet", widthDp = 390)
@Composable
private fun FeedSheetPreview() = SheetPreview(night = true) {
    val formatters = rememberFormatters(SampleData.ZONE)
    FeedSheetContent(HomeSamples.feedSheet().sheet as HomeSheetUi.Feed, SampleData.ZONE, formatters, NoFeedCallbacks)
}

@Preview(name = "Feed sheet · Breast step", widthDp = 390)
@Composable
private fun BreastSheetPreview() = SheetPreview(night = false) {
    val formatters = rememberFormatters(SampleData.ZONE)
    FeedSheetContent(HomeSamples.breastSheet().sheet as HomeSheetUi.Feed, SampleData.ZONE, formatters, NoFeedCallbacks)
}

private val NoFeedCallbacks = FeedSheetCallbacks({}, {}, {}, {}, {})

@Preview(name = "Diaper sheet", widthDp = 390)
@Composable
private fun DiaperSheetPreview() = SheetPreview(night = true) {
    val formatters = rememberFormatters(SampleData.ZONE)
    DiaperSheetContent(
        HomeSamples.diaperSheet().sheet as HomeSheetUi.Diaper,
        SampleData.ZONE,
        formatters,
        DiaperSheetCallbacks({}, {}, {}, {}, {}, {}, {}),
    )
}

@Preview(name = "Diaper sheet · Danish · hint", widthDp = 390, locale = "da")
@Composable
private fun DiaperSheetHintPreview() = SheetPreview(night = true) {
    val formatters = rememberFormatters(SampleData.ZONE)
    DiaperSheetContent(
        HomeSamples.diaperSheetHint().sheet as HomeSheetUi.Diaper,
        SampleData.ZONE,
        formatters,
        DiaperSheetCallbacks({}, {}, {}, {}, {}, {}, {}),
    )
}
