package com.ffmathy.pooparazzi.wear.tile

import android.content.ComponentName
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.ActionBuilders.stringExtra
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders.FontSetting
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_START
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.layout.androidImageResource
import androidx.wear.protolayout.layout.basicImage
import androidx.wear.protolayout.layout.box
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.imageResource
import androidx.wear.protolayout.layout.row
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.ButtonStyle
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.background
import androidx.wear.protolayout.modifiers.border
import androidx.wear.protolayout.modifiers.clearSemantics
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.modifiers.padding
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.protolayout.types.stringLayoutConstraint
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.core.ui.tokens.EventColors
import com.ffmathy.pooparazzi.core.ui.tokens.XTokens
import com.ffmathy.pooparazzi.wear.MainActivity
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.surface.SurfaceText
import com.ffmathy.pooparazzi.wear.surface.TimeText
import com.ffmathy.pooparazzi.wear.ui.SMALL_WATCH_DP
import com.ffmathy.pooparazzi.wear.ui.SpokenLabels
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * The tile's fixed palette (DESIGN_SPEC §3.6, §8.5, §10.13): the watch tokens, never the system's dynamic colours,
 * so a watch-face hue cannot pass for an event colour. Fills are event containers only (night-safe).
 */
object TileTheme {
    private val watch = XTokens.Watch
    val events = XTokens.WatchEvents

    val colorScheme: ColorScheme = ColorScheme(
        primary = watch.primary.layout,
        primaryDim = watch.primaryDim.layout,
        primaryContainer = watch.primaryContainer.layout,
        onPrimary = watch.onPrimary.layout,
        onPrimaryContainer = watch.onPrimaryContainer.layout,
        secondary = watch.primary.layout,
        secondaryDim = watch.primaryDim.layout,
        secondaryContainer = watch.primaryContainer.layout,
        onSecondary = watch.onPrimary.layout,
        onSecondaryContainer = watch.onPrimaryContainer.layout,
        tertiary = watch.primary.layout,
        tertiaryDim = watch.primaryDim.layout,
        tertiaryContainer = watch.primaryContainer.layout,
        onTertiary = watch.onPrimary.layout,
        onTertiaryContainer = watch.onPrimaryContainer.layout,
        surfaceContainerLow = watch.surfaceContainerLow.layout,
        surfaceContainer = watch.surfaceContainer.layout,
        surfaceContainerHigh = watch.surfaceContainerHigh.layout,
        onSurface = watch.onSurface.layout,
        onSurfaceVariant = watch.onSurfaceVariant.layout,
        outline = watch.outline.layout,
        outlineVariant = watch.outlineVariant.layout,
        background = watch.background.layout,
        onBackground = watch.onSurface.layout,
        error = watch.error.layout,
        errorDim = watch.errorDim.layout,
        errorContainer = watch.errorContainer.layout,
        onError = watch.onError.layout,
        onErrorContainer = watch.onErrorContainer.layout,
    )

    val onSurface: LayoutColor = watch.onSurface.layout
}

/** A Compose token as a ProtoLayout colour. */
internal val Color.layout: LayoutColor get() = toArgb().argb

/**
 * The tile (DESIGN_SPEC §3.6, §10.9; V1_ADDITIONS §2.2) as a Material 3 `primaryLayout`:
 * - **title**: the guard, the just-logged confirmation ("Pee 14:32 · Undo"), "Asleep 1h 07m", the baby's name, or the
 *   last diaper; hidden below 225 dp;
 * - **Row A**: Feed (opens the Feed type screen) with the time since the last feed;
 * - **Row B**: Pee | Poop | Both, one tap each;
 * - **edge**: the sleep toggle.
 *
 * No button ever relabels or moves for feedback (§3.6): only the title changes, and the background opens Logged with
 * Undo while a confirmation shows. Below 225 dp, where there is no title, Row A's secondary label carries the
 * confirmation instead ("Pee 14:32 · Undo"), as §3.6 allows.
 *
 * @param token makes this layout's clickable ids unique (see [TileMemory.claim]).
 * @param dynamic false renders every time-dependent text as its value at [TileUiState.now] only (screenshots;
 *   renderers too old for dynamic expressions get the static values anyway).
 */
fun MaterialScope.pooparazziTile(state: TileUiState, text: SurfaceText, token: Long, dynamic: Boolean = true): LayoutElement {
    val small = deviceConfiguration.screenWidthDp < SMALL_WATCH_DP
    val builder = TileContent(this, state, text, token, dynamic, confirmInRowA = small)
    val undoEntry = state.undoEntryId
    val title = if (small) null else builder.title()
    return primaryLayout(
        titleSlot = title,
        mainSlot = { builder.rows(withTitle = title != null) },
        bottomSlot = { builder.sleepToggle() },
        onClick = undoEntry?.let { builder.openApp(MainActivity.OPEN_LOGGED, CLICK_UNDO, it) },
    )
}

private const val CLICK_OPEN_FEED = "open_feed"
private const val CLICK_UNDO = "open_undo"

/** Builds the pieces of [pooparazziTile] (one instance per layout). */
private class TileContent(
    private val scope: MaterialScope,
    private val state: TileUiState,
    private val text: SurfaceText,
    private val token: Long,
    private val useDynamic: Boolean,
    /** No title on this screen: Row A's secondary label shows the just-logged confirmation. */
    private val confirmInRowA: Boolean,
) {
    private val context = scope.context
    private val res = context.resources
    private val events = TileTheme.events
    private val now = state.now

    // ---- Title ---------------------------------------------------------------------------------

    fun title(): (MaterialScope.() -> LayoutElement)? = when (val title = state.title) {
        is TileTitle.Guard -> {
            {
                val asleep = title.kind == GuardKind.ALREADY_ASLEEP
                titleRow(
                    icon = if (asleep) XDrawables.sleep else XDrawables.awake,
                    iconTint = events.sleep.strong,
                    label = res.getString(if (asleep) R.string.tile_guard_already_asleep else R.string.tile_guard_already_awake).layoutString,
                    color = events.sleep.text,
                    spoken = null,
                )
            }
        }
        is TileTitle.Logged -> { { loggedTitle(title.feedback) } }
        is TileTitle.Asleep -> {
            {
                val asleep = text.template(SurfaceText.Template.of(res, R.string.tile_title_asleep), text.duration(title.sleep.startMillis, now))
                val spoken = text.template(SurfaceText.Template.of(res, R.string.tile_cd_asleep), text.spokenDuration(title.sleep.startMillis, now))
                titleRow(
                    icon = XDrawables.sleepRunning,
                    iconTint = events.sleep.strong,
                    // Start-aligned in its constraint box, so the text stays next to its icon while it grows.
                    label = asleep.layout(TEXT_ALIGN_START),
                    color = events.sleep.text,
                    spoken = spoken,
                )
            }
        }
        is TileTitle.Name -> { { scope.text(title.name.layoutString, typography = Typography.TITLE_SMALL, color = TileTheme.onSurface) } }
        is TileTitle.LastDiaper -> {
            {
                val diaper = title.diaper
                val kind = diaper.diaperKind ?: DiaperKind.PLAIN
                val word = res.getString(Labels.diaperKindStatus(kind))
                val line = text.template(SurfaceText.Template(res.getString(R.string.tile_title_last_diaper, word, SurfaceText.Template.PLACEHOLDER)), text.ago(diaper.startMillis, now))
                // Read as "pee and poop", never the status word "Both" or a "+".
                val spokenWord = res.getString(SpokenLabels.diaperKind(kind)).lowercase(text.formatters.clock.locale)
                val spoken = text.template(
                    SurfaceText.Template(res.getString(R.string.tile_cd_last_diaper, spokenWord, SurfaceText.Template.PLACEHOLDER)),
                    text.spokenAgo(diaper.startMillis, now),
                )
                scope.text(
                    line.layout(TEXT_ALIGN_CENTER),
                    modifier = LayoutModifier.contentDescription(spoken.static, spoken.dynamic.takeIf { useDynamic }),
                    typography = Typography.TITLE_SMALL,
                    color = TileTheme.onSurface,
                )
            }
        }
        TileTitle.None -> null
    }

    /** What a just-logged confirmation says: its glyph, word, time (none for "Sleep cancelled") and spoken form. */
    private class Confirmation(@DrawableRes val glyph: Int, val glyphTint: Color, val label: String, val time: String?, val spoken: String)

    private fun confirmation(feedback: TileFeedback): Confirmation {
        val undoHint = { said: String -> res.getString(R.string.tile_cd_logged, said) }
        val atTime = { said: String, time: String -> res.getString(CoreR.string.receipt_at_time, said, time) }
        return when (feedback.kind) {
            TileFeedbackKind.DIAPER -> {
                val kind = feedback.diaperKind ?: DiaperKind.PLAIN
                val time = text.clock(feedback.atMillis)
                // Shown as the status word ("Both"), read as the receipt says it ("Pee and poop").
                Confirmation(
                    XDrawables.check, events.diaper.strong, res.getString(Labels.diaperKindStatus(kind)), time,
                    undoHint(atTime(res.getString(SpokenLabels.diaperKind(kind)), time)),
                )
            }
            TileFeedbackKind.WOKE_UP -> {
                val time = text.clock(feedback.atMillis)
                val label = res.getString(CoreR.string.action_woke_up)
                Confirmation(XDrawables.check, events.sleep.strong, label, time, undoHint(atTime(label, time)))
            }
            TileFeedbackKind.SLEEP_CANCELLED -> {
                val label = res.getString(CoreR.string.receipt_sleep_cancelled)
                Confirmation(XDrawables.wake, events.sleep.strong, label, null, undoHint(label))
            }
        }
    }

    /**
     * "✓ Pee 14:32 · ↶ Undo" (DESIGN_SPEC §3.6 just-logged title), "Sleep cancelled · Undo" (§4.4: with Undo). The
     * background tap is the undo path.
     */
    private fun MaterialScope.loggedTitle(feedback: TileFeedback): LayoutElement {
        val confirmation = confirmation(feedback)
        val label = confirmation.label
        val time = confirmation.time
        val undo = res.getString(CoreR.string.action_undo)
        // Drops the glyphs (then the time) where the language's words would not fit the title slot.
        val parts = LoggedTitleParts.fitting(
            label = label,
            time = time,
            separator = DOT_SEPARATOR,
            undo = undo,
            maxWidthDp = TileTextMeasure.titleSlotWidthDp(deviceConfiguration),
            measure = TileTextMeasure(deviceConfiguration.fontScale),
        )
        val lead = if (parts.time && time != null) res.getString(R.string.tile_title_logged, label, time) else label
        val pieces = buildList {
            if (parts.check) {
                add(glyph(confirmation.glyph, confirmation.glyphTint, TITLE_ICON))
                add(spacer(width = dp(LoggedTitleParts.CHECK_GAP_DP)))
            }
            add(plainText((lead + DOT_SEPARATOR).layoutString, Typography.TITLE_SMALL, TileTheme.onSurface))
            if (parts.undoGlyph) {
                add(glyph(XDrawables.undo, XTokens.Watch.onSurface, TITLE_ICON))
                add(spacer(width = dp(LoggedTitleParts.UNDO_GAP_DP)))
            }
            add(plainText(undo.layoutString, Typography.TITLE_SMALL, TileTheme.onSurface, bold = true))
        }
        return row(
            *pieces.toTypedArray(),
            modifier = LayoutModifier.contentDescription(confirmation.spoken),
            verticalAlignment = VERTICAL_ALIGN_CENTER,
        )
    }

    /** Icon 14 + 4 + one line of titleSmall, read as one sentence. */
    private fun MaterialScope.titleRow(@DrawableRes icon: Int, iconTint: Color, label: LayoutString, color: Color, spoken: TimeText?): LayoutElement =
        row(
            glyph(icon, iconTint, TITLE_ICON),
            spacer(width = dp(4f)),
            plainText(label, Typography.TITLE_SMALL, color.layout),
            modifier = when {
                spoken != null -> LayoutModifier.contentDescription(spoken.static, spoken.dynamic.takeIf { useDynamic })
                else -> LayoutModifier.contentDescription(label.staticValue, label.dynamicValue)
            },
            verticalAlignment = VERTICAL_ALIGN_CENTER,
        )

    // ---- Main slot ---------------------------------------------------------------------------

    fun rows(withTitle: Boolean): LayoutElement = column(
        rowA(withTitle),
        spacer(height = dp(ROW_GAP)),
        rowB(withTitle),
        width = expand(),
        height = expand(),
        horizontalAlignment = HORIZONTAL_ALIGN_CENTER,
    )

    /**
     * Row A: Feed opens the app's Feed type screen; feeds are never logged from the tile.
     * Without a title (below 225 dp) its secondary label shows a just-logged confirmation for its 10 s.
     */
    private fun rowA(withTitle: Boolean): LayoutElement = with(scope) {
        val feed = events.feed
        val lastFeed = state.lastFeed
        val label: String
        val secondary: LayoutString
        val spoken: TimeText?
        val spokenStatic: String
        if (lastFeed != null) {
            label = res.getString(CoreR.string.kind_feed)
            secondary = text.ago(lastFeed.startMillis, now).layout(TEXT_ALIGN_START)
            spoken = text.template(SurfaceText.Template.of(res, R.string.tile_cd_feed), text.spokenAgo(lastFeed.startMillis, now))
            spokenStatic = spoken.static
        } else {
            label = res.getString(CoreR.string.kind_feed)
            secondary = res.getString(R.string.status_no_feed_yet).layoutString
            spoken = null
            spokenStatic = res.getString(R.string.tile_cd_feed_none)
        }
        val confirmed = (state.title as? TileTitle.Logged)?.feedback?.takeIf { confirmInRowA }?.let(::confirmation)
        val description = if (confirmed != null) {
            // The confirmation first: on these watches nothing else says it.
            LayoutModifier.contentDescription(res.getString(R.string.spoken_then_action, confirmed.spoken, spokenStatic))
        } else {
            LayoutModifier.contentDescription(spokenStatic, spoken?.dynamic?.takeIf { useDynamic })
        }
        val shownSecondary = confirmed?.let {
            RowAConfirmation.text(
                label = it.label,
                labelWithTime = it.time?.let { time -> res.getString(R.string.tile_title_logged, it.label, time) },
                separator = DOT_SEPARATOR,
                undo = res.getString(CoreR.string.action_undo),
                maxWidthDp = TileTextMeasure.rowATextWidthDp(deviceConfiguration, withTitle),
                measure = TileTextMeasure.labelSmall(deviceConfiguration.fontScale),
            ).layoutString
        } ?: secondary
        button(
            onClick = openApp(MainActivity.OPEN_FEED_TYPE, CLICK_OPEN_FEED),
            modifier = description,
            iconContent = { glyph(XDrawables.feed, feed.strong, ROW_A_ICON) },
            labelContent = { plainText(label.layoutString, Typography.LABEL_MEDIUM, feed.onContainer.layout, start = true) },
            secondaryLabelContent = { plainText(shownSecondary, Typography.LABEL_SMALL, feed.onContainer.layout, start = true) },
            width = expand(),
            height = weight(1f),
            colors = ButtonColors(
                containerColor = feed.container.layout,
                iconColor = feed.strong.layout,
                labelColor = feed.onContainer.layout,
                secondaryLabelColor = feed.onContainer.layout,
            ),
            style = ButtonStyle.smallButtonStyle(),
            contentPadding = padding(start = ROW_A_START_PADDING, top = 4f, end = 12f, bottom = 4f),
        )
    }

    /**
     * Row B: Pee | Poop | Both (DESIGN_SPEC §3.6), each one tap with no colour or texture (V1_ADDITIONS §2.2). The
     * labels keep clear of the rounded edges: a smaller style, or the icons alone, where a word would not fit.
     */
    private fun rowB(withTitle: Boolean): LayoutElement {
        val kinds = listOf(DiaperKind.PEE, DiaperKind.POOP, DiaperKind.BOTH)
        val labels = RowBLabels.fitting(
            labels = kinds.map { res.getString(Labels.diaperKindStatus(it)) },
            maxWidthDp = TileTextMeasure.rowBLabelWidthDp(scope.deviceConfiguration, withTitle, ROW_GAP, ROW_B_SIDE_PADDING),
            fontScale = scope.deviceConfiguration.fontScale,
        )
        return row(
            diaperButton(DiaperKind.PEE, TileAction.LOG_PEE, R.string.tile_cd_log_pee, labels),
            spacer(width = dp(ROW_GAP)),
            diaperButton(DiaperKind.POOP, TileAction.LOG_POOP, R.string.tile_cd_log_poop, labels),
            spacer(width = dp(ROW_GAP)),
            diaperButton(DiaperKind.BOTH, TileAction.LOG_BOTH, R.string.tile_cd_log_both, labels),
            width = expand(),
            height = weight(1f),
        )
    }

    private fun diaperButton(kind: DiaperKind, action: TileAction, description: Int, labels: RowBLabels): LayoutElement = with(scope) {
        val colors: EventColors = if (kind == DiaperKind.POOP) events.poop else events.pee
        val labelColor = if (kind == DiaperKind.PEE) events.pee.onContainer else events.poop.onContainer
        button(
            onClick = Clickable.Builder().setId(action.clickId(token)).setOnClick(loadAction()).build(),
            modifier = LayoutModifier.contentDescription(res.getString(description)),
            labelContent = {
                val icon = if (kind == DiaperKind.BOTH) {
                    // Two-tone: droplet in PEE strong, mound in POOP strong (fixed colours, no tint).
                    protoLayoutScope.basicImage(imageResource(androidImageResource(XDrawables.bothNight)), dp(ROW_B_ICON), dp(ROW_B_ICON))
                } else {
                    glyph(XDrawables.forDiaperKind(kind), colors.strong, ROW_B_ICON)
                }
                val typography = labels.typography
                if (typography == null) {
                    column(icon, horizontalAlignment = HORIZONTAL_ALIGN_CENTER, modifier = LayoutModifier.clearSemantics())
                } else {
                    column(
                        icon,
                        plainText(res.getString(Labels.diaperKindStatus(kind)).layoutString, typography, labelColor.layout),
                        horizontalAlignment = HORIZONTAL_ALIGN_CENTER,
                        modifier = LayoutModifier.clearSemantics(),
                    )
                }
            },
            width = weight(1f),
            height = expand(),
            colors = ButtonColors(containerColor = colors.container.layout, labelColor = labelColor.layout),
            backgroundContent = if (kind == DiaperKind.BOTH) {
                // The split fill: PEE container | POOP container, hard stop in the middle.
                {
                    row(
                        box(width = weight(1f), height = expand(), modifier = LayoutModifier.background(events.bothStartContainer.layout)),
                        box(width = weight(1f), height = expand(), modifier = LayoutModifier.background(events.bothEndContainer.layout)),
                        width = expand(),
                        height = expand(),
                    )
                }
            } else {
                null
            },
            horizontalAlignment = HORIZONTAL_ALIGN_CENTER,
            contentPadding = padding(horizontal = ROW_B_SIDE_PADDING, vertical = 4f),
        )
    }

    // ---- Edge ------------------------------------------------------------------------------------

    /** The sleep toggle: "Start sleep" (SLEEP container) or "Woke up" (containerLit + 1.5 dp strong border). */
    fun sleepToggle(): LayoutElement = with(scope) {
        val sleep = events.sleep
        val asleep = state.runningSleep != null
        val label = res.getString(if (asleep) CoreR.string.action_woke_up else CoreR.string.action_start_sleep)
        val description = res.getString(if (asleep) R.string.cd_woke_up_end_sleep else CoreR.string.action_start_sleep)
        val modifier = LayoutModifier.contentDescription(description)
        // The tap says what it showed: "Woke up" for this sleep, or "Start sleep" (see TileActions).
        val clickId = state.runningSleep?.let { TileAction.SLEEP_END.clickId(token, it.id) } ?: TileAction.SLEEP_START.clickId(token)
        textEdgeButton(
            onClick = Clickable.Builder().setId(clickId).setOnClick(loadAction()).build(),
            modifier = if (asleep) modifier.border(LIT_BORDER, sleep.strong.layout) else modifier,
            colors = ButtonColors(
                containerColor = (if (asleep) sleep.containerLit else sleep.container).layout,
                labelColor = sleep.onContainer.layout,
            ),
            labelContent = { plainText(label.layoutString, Typography.LABEL_MEDIUM, sleep.onContainer.layout) },
        )
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    /** A launch of the app at [open] (Feed type, Side, or Logged for [entryId] with Undo active at once). */
    fun openApp(open: String, id: String, entryId: String? = null): Clickable {
        val extras = buildMap {
            put(MainActivity.EXTRA_OPEN, stringExtra(open))
            entryId?.let { put(MainActivity.EXTRA_ENTRY_ID, stringExtra(it)) }
        }
        return Clickable.Builder()
            .setId(id)
            .setOnClick(launchAction(ComponentName(context, MainActivity::class.java), extras))
            .build()
    }

    private fun MaterialScope.glyph(@DrawableRes res: Int, tint: Color, size: Float): LayoutElement =
        icon(imageResource(androidImageResource(res)), width = dp(size), height = dp(size), tintColor = tint.layout)

    private fun MaterialScope.plainText(
        value: LayoutString,
        typography: Int,
        color: LayoutColor,
        start: Boolean = false,
        bold: Boolean = false,
    ): LayoutElement = text(
        value,
        typography = typography,
        color = color,
        alignment = if (start) TEXT_ALIGN_START else TEXT_ALIGN_CENTER,
        settings = if (bold) listOf(FontSetting.weight(BOLD)) else emptyList(),
        modifier = LayoutModifier.clearSemantics(),
    )

    /**
     * A [TimeText] as tile text: dynamic with its static fallback, sized for the widest value it can tick to
     * ("9m ago" → "23h 59m ago"), or the static value only.
     */
    private fun TimeText.layout(alignment: Int): LayoutString =
        if (useDynamic) {
            LayoutString(static, dynamic, stringLayoutConstraint(longest, alignment))
        } else {
            static.layoutString
        }

    companion object {
        const val TITLE_ICON = TileTextMeasure.TITLE_ICON_DP
        const val ROW_A_ICON = 24f
        const val ROW_B_ICON = 20f
        const val ROW_GAP = 6f

        /** Keeps Row B's labels off the buttons' rounded edges. */
        const val ROW_B_SIDE_PADDING = 6f
        const val ROW_A_START_PADDING = 16f
        const val LIT_BORDER = 1.5f
        const val BOLD = 700
    }
}
