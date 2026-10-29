package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.isBreastfeed
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.core.ui.tokens.XPalette
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.launch.DeepLinks
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * The widget palette (DESIGN_SPEC §8.5, §10.11): the fixed Night or Day tokens chosen at update time from the app's
 * Appearance, each a single-value colour. No day/night pairs and no dynamic colour, so the launcher's theme never
 * changes them.
 */
internal class WidgetColors(val night: Boolean) {
    private val palette = if (night) XPalette.Night else XPalette.Day
    val surface: Color = palette.colors.surfaceContainer
    val onSurface: Color = palette.colors.onSurface
    val onSurfaceVariant: Color = palette.colors.onSurfaceVariant
    val primary: Color = palette.colors.primary
    val sleep: EventColors = palette.events.sleep
    val feed: EventColors = palette.events.feed
    val pee: EventColors = palette.events.pee
    val poop: EventColors = palette.events.poop

    /** The two-tone "both" glyph in this palette's PEE and POOP strong (drawn untinted). */
    @DrawableRes val bothIcon: Int = if (night) XDrawables.bothNight else XDrawables.bothDay

    val diaper: EventColors = palette.events.diaper

    /** PEE or POOP colours; a plain change (neither) takes the DIAPER colours. */
    fun diaper(kind: DiaperKind): EventColors = when (kind) {
        DiaperKind.PEE -> pee
        DiaperKind.PLAIN -> diaper
        DiaperKind.POOP, DiaperKind.BOTH -> poop
    }
}

internal fun Color.provider(): ColorProvider = ColorProvider(this)

/**
 * The widget texts in the app language (V1_ADDITIONS §5): absolute clock times only, always with "at" (DESIGN_SPEC
 * §3.3.6), built from core's shared words.
 */
internal class WidgetStrings(private val context: Context, private val zone: ZoneId) {
    private val clock = ClockFormatter.from(context)
    private val labels = LabelFormatter(context)

    fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    fun time(millis: Long): String = clock.format(millis, zone)

    /** "Fed at 12:22", "Pee at 13:44", "Formula logged at 14:32". */
    private fun atTime(label: String, millis: Long): String = string(CoreR.string.receipt_at_time, label, time(millis))

    /** "Asleep since 02:05", "Awake since 13:27". */
    private fun since(label: String, millis: Long): String =
        string(R.string.join_space, label, string(CoreR.string.rel_since_format, time(millis)))

    /** "Fed at 12:22", plus the breast order " · L → R" when one was recorded. */
    fun fedAt(feed: Entry): String = atTime(string(CoreR.string.status_fed), feed.startMillis) +
        (feed.breastSide?.takeIf { feed.isBreastfeed }?.let { DOT_SEPARATOR + labels.sideShort(it) } ?: "")
    fun diaperAt(kind: DiaperKind, millis: Long): String = atTime(labels.diaperKindStatus(kind), millis)
    fun noFeeds(): String = string(R.string.lamp_no_feed)
    fun asleepSince(millis: Long): String = since(string(CoreR.string.status_asleep), millis)
    fun awakeSince(millis: Long): String = since(string(CoreR.string.status_awake), millis)

    fun receipt(receipt: WidgetReceipt): String = when (receipt.kind) {
        ReceiptKind.LOGGED -> atTime(string(CoreR.string.receipt_logged, labels.diaperKind(receipt.diaperKind ?: DiaperKind.PLAIN)), receipt.atMillis)
        ReceiptKind.SLEEP_STARTED -> atTime(string(CoreR.string.receipt_sleep_started), receipt.atMillis)
        ReceiptKind.WOKE_UP -> atTime(string(CoreR.string.action_woke_up), receipt.atMillis)
        ReceiptKind.SLEEP_CANCELLED -> string(CoreR.string.receipt_sleep_cancelled)
    }

    /** The receipt without its verb, where the full line does not fit beside Undo: "Pee + poop at 14:32". */
    fun receiptShort(receipt: WidgetReceipt): String = when (receipt.kind) {
        ReceiptKind.LOGGED -> atTime(labels.diaperKind(receipt.diaperKind ?: DiaperKind.PLAIN), receipt.atMillis)
        else -> receipt(receipt)
    }

    /** TalkBack name of the receipt's Undo: "Undo poop", "Undo sleep start". */
    fun undoDescription(receipt: WidgetReceipt): String = string(
        when (receipt.kind) {
            ReceiptKind.LOGGED -> when (receipt.diaperKind) {
                DiaperKind.POOP -> R.string.widget_undo_poop
                DiaperKind.BOTH -> R.string.widget_undo_both
                else -> R.string.widget_undo_pee
            }
            ReceiptKind.SLEEP_STARTED -> R.string.widget_undo_sleep_start
            ReceiptKind.WOKE_UP -> R.string.widget_undo_woke_up
            ReceiptKind.SLEEP_CANCELLED -> R.string.widget_undo_sleep_cancelled
        },
    )

    fun notice(notice: WidgetNotice): String = when (notice.type) {
        NoticeType.ALREADY_ASLEEP -> string(CoreR.string.guard_already_asleep, time(notice.atMillis))
        NoticeType.ALREADY_AWAKE -> string(CoreR.string.guard_already_awake, time(notice.atMillis))
        NoticeType.ALREADY_LOGGED -> string(CoreR.string.guard_already_logged, time(notice.atMillis))
        NoticeType.UNDONE -> string(CoreR.string.receipt_undone)
    }
}

/** Widget type: the device font (no tnum, ROND or opsz; DESIGN_SPEC §10.11), sizes in sp as drawn. */
@Composable
internal fun WidgetText(
    text: String,
    color: Color,
    size: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    weight: FontWeight = FontWeight.Medium,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color.provider(), fontSize = size, fontWeight = weight, textAlign = align, fontFamily = FontFamily.SansSerif),
        maxLines = maxLines,
    )
}

/** One of the core icon drawables, tinted with [color] (null draws it as is: the two-tone "both"). Decorative. */
@Composable
internal fun WidgetIcon(@DrawableRes id: Int, color: Color?, size: Dp, modifier: GlanceModifier = GlanceModifier) {
    Image(
        provider = ImageProvider(id),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = color?.let { ColorFilter.tint(it.provider()) },
    )
}

/**
 * A widget button: a rounded fill, or the checked style (DESIGN_SPEC §10.11): `GlanceModifier` has no border, so the
 * 2 dp strong stroke is the outer box showing around the lit fill. Rounded corners need Android 12; on Android 11 the
 * buttons are square. [description] is the TalkBack name (the button's text is not read separately).
 */
@Composable
internal fun WidgetButton(
    modifier: GlanceModifier,
    fill: Color,
    radius: Dp,
    action: Action,
    description: String,
    border: Color? = null,
    content: @Composable () -> Unit,
) {
    val clickable = modifier.clickable(action).semantics { contentDescription = description }
    if (border == null) {
        Box(clickable.cornerRadius(radius).background(fill.provider()), contentAlignment = Alignment.Center) { content() }
    } else {
        Box(clickable.cornerRadius(radius).background(border.provider()).padding(BorderWidth)) {
            Box(GlanceModifier.fillMaxSize().cornerRadius(radius - BorderWidth).background(fill.provider()), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

/**
 * The widget surface: surfaceContainer with the system widget corner radius (reference r 24). A tap outside the buttons
 * opens the app (with the undo card for the last widget log, DESIGN_SPEC §3.3.6).
 */
@Composable
internal fun WidgetSurface(colors: WidgetColors, launch: HomeLaunch, modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var surface = GlanceModifier.fillMaxSize().appWidgetBackground().background(colors.surface.provider())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) surface = surface.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    Box(surface.clickable(actionStartActivity(DeepLinks.intent(context, launch))).then(modifier)) { content() }
}

private val BorderWidth = 2.dp
