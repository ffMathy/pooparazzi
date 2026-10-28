package com.ffmathy.pooparazzi.core.format

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.ffmathy.pooparazzi.core.R
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.isBreastfeed
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import java.time.LocalDate
import java.time.ZoneId

/** Looks up string and plural resources (an indirection so labels can be tested per locale). */
interface StringResolver {
    fun string(@StringRes id: Int): String
    fun plural(@PluralsRes id: Int, count: Int): String

    companion object {
        fun of(resources: Resources): StringResolver = object : StringResolver {
            override fun string(id: Int): String = resources.getString(id)
            override fun plural(id: Int, count: Int): String = resources.getQuantityString(id, count, count)
        }
    }
}

/** Which device the sync line names: the phone says "with watch", the watch "with phone". */
enum class SyncPeer { WATCH, PHONE }

/** The middle-dot separator used between facts ("13:44 · Yellow · Seedy"), U+00B7 with spaces. */
const val DOT_SEPARATOR = " · "

/** String resource ids for the shared enums; use them directly in Compose with `stringResource`. */
object Labels {
    @StringRes fun kind(kind: EntryKind): Int = when (kind) {
        EntryKind.SLEEP -> R.string.kind_sleep
        EntryKind.FEED -> R.string.kind_feed
        EntryKind.DIAPER -> R.string.kind_diaper
    }

    /** The feed type's word; a feed with no type (null) is plain "Feed" ("Måltid"). */
    @StringRes fun feedType(type: FeedType?, compact: Boolean = false): Int = when (type) {
        null -> R.string.kind_feed
        FeedType.BREAST -> R.string.feed_type_breast
        FeedType.FORMULA -> if (compact) R.string.feed_type_formula_compact else R.string.feed_type_formula
        FeedType.FOOD -> R.string.feed_type_food
    }

    @StringRes fun side(side: BreastSide): Int = when (side) {
        BreastSide.LEFT -> R.string.side_left
        BreastSide.RIGHT -> R.string.side_right
        BreastSide.LEFT_THEN_RIGHT -> R.string.side_left_then_right
        BreastSide.RIGHT_THEN_LEFT -> R.string.side_right_then_left
    }

    @StringRes fun poopColor(color: PoopColor): Int = when (color) {
        PoopColor.YELLOW -> R.string.poop_colour_yellow
        PoopColor.GREEN -> R.string.poop_colour_green
        PoopColor.BROWN -> R.string.poop_colour_brown
        PoopColor.BLACK -> R.string.poop_colour_black
        PoopColor.RED -> R.string.poop_colour_red
        PoopColor.PALE -> R.string.poop_colour_pale
    }

    @StringRes fun poopTexture(texture: PoopTexture): Int = when (texture) {
        PoopTexture.WATERY -> R.string.poop_texture_watery
        PoopTexture.SEEDY -> R.string.poop_texture_seedy
        PoopTexture.MUSHY -> R.string.poop_texture_mushy
        PoopTexture.FORMED -> R.string.poop_texture_formed
        PoopTexture.HARD -> R.string.poop_texture_hard
    }

    /** Receipts and History: "Pee", "Poop", "Pee + poop", or "Diaper" for a plain change. */
    @StringRes fun diaperKind(kind: DiaperKind): Int = when (kind) {
        DiaperKind.PEE -> R.string.diaper_pee
        DiaperKind.POOP -> R.string.diaper_poop
        DiaperKind.BOTH -> R.string.diaper_pee_and_poop
        DiaperKind.PLAIN -> R.string.kind_diaper
    }

    /** Status surfaces (since-row 2, tile, complications): "Pee", "Poop", "Both", or "Diaper" for a plain change. */
    @StringRes fun diaperKindStatus(kind: DiaperKind): Int = when (kind) {
        DiaperKind.PEE -> R.string.diaper_pee
        DiaperKind.POOP -> R.string.diaper_poop
        DiaperKind.BOTH -> R.string.diaper_both
        DiaperKind.PLAIN -> R.string.kind_diaper
    }

    @StringRes fun appearance(appearance: Appearance): Int = when (appearance) {
        Appearance.AUTO -> R.string.appearance_auto
        Appearance.NIGHT -> R.string.appearance_night
        Appearance.DAY -> R.string.appearance_day
    }

    @StringRes fun language(language: AppLanguage): Int = when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.EN -> R.string.language_english
        AppLanguage.DA -> R.string.language_danish
    }
}

/** Localised labels for enums and entries (V1_ADDITIONS §3.3, §4). */
class LabelFormatter(private val strings: StringResolver) {

    constructor(context: Context) : this(StringResolver.of(context.resources))

    fun kind(kind: EntryKind): String = strings.string(Labels.kind(kind))

    /**
     * "Formula" / "Modermælkserstatning"; [compact] adds a soft hyphen for tight places ("Modermælks­erstatning").
     * An untyped feed (null) is "Feed" / "Måltid".
     */
    fun feedType(type: FeedType?, compact: Boolean = false): String = strings.string(Labels.feedType(type, compact))

    fun side(side: BreastSide): String = strings.string(Labels.side(side))

    /** The side, or "No side" when none was recorded. */
    fun sideOrNone(side: BreastSide?): String = side?.let(::side) ?: strings.string(R.string.side_none)

    fun poopColor(color: PoopColor): String = strings.string(Labels.poopColor(color))

    fun poopTexture(texture: PoopTexture): String = strings.string(Labels.poopTexture(texture))

    /** "Pee", "Poop", "Pee + poop", "Diaper". */
    fun diaperKind(kind: DiaperKind): String = strings.string(Labels.diaperKind(kind))

    /** "Pee", "Poop", "Both", "Diaper". */
    fun diaperKindStatus(kind: DiaperKind): String = strings.string(Labels.diaperKindStatus(kind))

    fun appearance(appearance: Appearance): String = strings.string(Labels.appearance(appearance))

    fun language(language: AppLanguage): String = strings.string(Labels.language(language))

    /** Today-grid captions: "feed"/"feeds", "pee", "poop", "sleep" (Danish "måltid"/"måltider", "tis", "bæ", "søvn"). */
    fun feedsCaption(count: Int): String = strings.plural(R.plurals.count_feeds, count)
    fun peeCaption(): String = strings.string(R.string.count_pee)
    fun poopCaption(): String = strings.string(R.string.count_poop)
    fun sleepCaption(): String = strings.string(R.string.count_sleep)

    /** History supporting line of a diaper: "13:44", plus " · Yellow" and " · Seedy" when set. */
    fun diaperDetail(entry: Entry, clock: ClockFormatter, zone: ZoneId): String = buildString {
        append(clock.format(entry.startMillis, zone))
        entry.poopColor?.takeIf { entry.poop }?.let { append(DOT_SEPARATOR).append(poopColor(it)) }
        entry.poopTexture?.takeIf { entry.poop }?.let { append(DOT_SEPARATOR).append(poopTexture(it)) }
    }

    /**
     * History row title: "Sleep · 47m" / "Sleep · running", "Breast" / "Breast · Left → Right",
     * "Formula", "Food", "Feed" (no type), "Pee", "Poop", "Pee + poop", "Diaper" (a plain change).
     */
    fun historyTitle(entry: Entry, duration: DurationFormatter, now: Long): String = when (entry.kind) {
        EntryKind.SLEEP -> kind(EntryKind.SLEEP) + DOT_SEPARATOR +
            if (entry.isRunning) strings.string(R.string.status_running) else duration.format(entry.clockDurationMillis(now))
        EntryKind.FEED -> feedType(entry.feedType) +
            (entry.breastSide?.takeIf { entry.isBreastfeed }?.let { DOT_SEPARATOR + side(it) } ?: "")
        EntryKind.DIAPER -> entry.diaperKind?.let(::diaperKind) ?: kind(EntryKind.DIAPER)
    }

    /**
     * History row supporting line: sleeps "12:40 – 13:27" (a day prefix when they start on another day), a running
     * sleep "since 14:32", feeds (breastfeeds included) "12:22", diapers [diaperDetail].
     */
    fun historySupporting(
        entry: Entry,
        day: LocalDate,
        zone: ZoneId,
        now: Long,
        clock: ClockFormatter,
        duration: DurationFormatter,
        relative: RelativeFormatter,
        spacedRange: Boolean = true,
    ): String = when {
        entry.isRunning -> relative.since(entry.startMillis, zone)
        entry.kind == EntryKind.SLEEP -> clock.rangeOnDay(entry.startMillis, entry.endMillis ?: now, day, zone, spacedRange)
        entry.kind == EntryKind.DIAPER -> diaperDetail(entry, clock, zone)
        else -> clock.format(entry.startMillis, zone)
    }

    /**
     * The sync line (DESIGN_SPEC §4.5), naming the devices. [peer] is the other device, so the phone passes
     * [SyncPeer.WATCH] and the watch [SyncPeer.PHONE]:
     *
     * | Status | Phone | Watch |
     * |---|---|---|
     * | Synced | "Synced with watch · 14:31" | "Synced with phone · 14:31" |
     * | Pending | "Syncing with watch…" | "Syncing with phone…" (the spec shows no line here; the app may hide it) |
     * | Offline | "Saved on phone · will sync" | "Saved on watch · will sync" |
     * | Error | "Couldn't save · Tap to retry" (the retry button) | "Saved on watch · will sync" (no error line on the watch) |
     *
     * Null for [SyncStatus.Unavailable] (hide the line).
     */
    fun syncLine(status: SyncStatus, peer: SyncPeer, clock: ClockFormatter, zone: ZoneId): String? {
        val onPhone = peer == SyncPeer.WATCH
        return when (status) {
            is SyncStatus.Synced -> {
                val format = strings.string(if (onPhone) R.string.sync_synced_with_watch else R.string.sync_synced_with_phone)
                String.format(clock.locale, format, clock.format(status.atMillis, zone))
            }
            SyncStatus.Pending -> strings.string(if (onPhone) R.string.sync_syncing_with_watch else R.string.sync_syncing_with_phone)
            SyncStatus.Offline -> savedLocally(onPhone)
            SyncStatus.Error ->
                if (onPhone) strings.string(R.string.sync_error) + DOT_SEPARATOR + strings.string(R.string.action_retry) else savedLocally(false)
            SyncStatus.Unavailable -> null
        }
    }

    private fun savedLocally(onPhone: Boolean): String =
        strings.string(if (onPhone) R.string.sync_saved_on_phone else R.string.sync_saved_on_watch)
}
