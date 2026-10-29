package com.ffmathy.pooparazzi.phone.widget

import androidx.compose.runtime.Composable
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview

/** Glance previews of both widgets in the reference scenario (V1_ADDITIONS §6), at the mockup sizes. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 358, heightDp = 208)
@Composable
private fun LogWidgetIdleNightPreview() = LogWidgetContent(WidgetSamples.idle(night = true))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 358, heightDp = 208)
@Composable
private fun LogWidgetJustLoggedDayPreview() = LogWidgetContent(WidgetSamples.justLogged(night = false))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 358, heightDp = 208)
@Composable
private fun LogWidgetAsleepPreview() = LogWidgetContent(WidgetSamples.asleep())

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 358, heightDp = 208)
@Composable
private fun LogWidgetAfterBreastfeedPreview() = LogWidgetContent(WidgetSamples.afterBreastfeed(night = false))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 276, heightDp = 190)
@Composable
private fun LogWidgetNarrowPreview() = LogWidgetContent(WidgetSamples.asleep())

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 100)
@Composable
private fun QuickWidgetIdlePreview() = QuickWidgetContent(WidgetSamples.idle(night = true))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 100)
@Composable
private fun QuickWidgetAsleepPreview() = QuickWidgetContent(WidgetSamples.asleep())

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 100)
@Composable
private fun QuickWidgetAfterBreastfeedPreview() = QuickWidgetContent(WidgetSamples.afterBreastfeed(night = false))
