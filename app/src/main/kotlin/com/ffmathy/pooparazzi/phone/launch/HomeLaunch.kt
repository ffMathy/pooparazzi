package com.ffmathy.pooparazzi.phone.launch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ffmathy.pooparazzi.phone.MainActivity

/**
 * What a system surface (widget, notification) asks Home to show when it opens the app (DESIGN_SPEC §3.1):
 * a logging sheet, or the undo card for the last widget log.
 */
sealed interface HomeLaunch {
    /** Home with nothing extra (the widget background, a notification body). */
    data object Home : HomeLaunch

    /** The Feed sheet (Breast / Formula / Food / Just the time). */
    data object Feed : HomeLaunch

    /** The Diaper sheet. */
    data object Diaper : HomeLaunch

    /**
     * History instead of Home: the watch's "More on phone" (`pooparazzi://history`, the only link other apps may open).
     * Handled by the navigation; Home itself shows nothing extra.
     */
    data object History : HomeLaunch

    /** The undo card for a log made on the widget ([entryId]), so it can be undone or backdated in the app. */
    data class Receipt(val kind: ReceiptKind, val entryId: String) : HomeLaunch

    /** True for the launches that open a logging sheet (which replaces an open edit sheet). */
    val opensSheet: Boolean get() = this == Feed || this == Diaper
}

/** Which widget log a [HomeLaunch.Receipt] card describes. */
enum class ReceiptKind(val path: String) {
    LOGGED("logged"),
    SLEEP_STARTED("sleep-started"),
    WOKE_UP("woke-up"),
    SLEEP_CANCELLED("sleep-cancelled"),
    ;

    companion object {
        fun fromPath(path: String?): ReceiptKind? = entries.firstOrNull { it.path == path }
    }
}

/**
 * The app's deep links, `pooparazzi://home[/<sheet>]`, `pooparazzi://home/receipt/<kind>/<entryId>` and
 * `pooparazzi://history` (the watch's "More on phone"), and the intents that carry them to [MainActivity]. The links
 * only open a screen; they never log anything.
 */
object DeepLinks {
    const val SCHEME = "pooparazzi"
    const val HOST = "home"

    /** `pooparazzi://history`: the one link the manifest exposes to other apps (VIEW + BROWSABLE). */
    const val HISTORY_HOST = "history"
    private const val FEED = "feed"
    /** The old "End feed" link of a timed breastfeed (no longer made); it now opens plain Home. */
    private const val LEGACY_SIDE = "side"
    private const val DIAPER = "diaper"
    private const val RECEIPT = "receipt"

    fun uri(launch: HomeLaunch): Uri {
        if (launch == HomeLaunch.History) return Uri.Builder().scheme(SCHEME).authority(HISTORY_HOST).build()
        val builder = Uri.Builder().scheme(SCHEME).authority(HOST)
        when (launch) {
            HomeLaunch.Home, HomeLaunch.History -> Unit
            HomeLaunch.Feed -> builder.appendPath(FEED)
            HomeLaunch.Diaper -> builder.appendPath(DIAPER)
            is HomeLaunch.Receipt -> builder.appendPath(RECEIPT).appendPath(launch.kind.path).appendPath(launch.entryId)
        }
        return builder.build()
    }

    /** The launch a link asks for; null for anything that is not one of the app's links. */
    fun parse(uri: Uri?): HomeLaunch? {
        if (uri == null || uri.scheme != SCHEME) return null
        if (uri.authority == HISTORY_HOST) return HomeLaunch.History
        if (uri.authority != HOST) return null
        val segments = uri.pathSegments.orEmpty()
        return when (segments.firstOrNull()) {
            null -> HomeLaunch.Home
            FEED -> HomeLaunch.Feed
            LEGACY_SIDE -> HomeLaunch.Home
            DIAPER -> HomeLaunch.Diaper
            RECEIPT -> {
                val kind = ReceiptKind.fromPath(segments.getOrNull(1)) ?: return HomeLaunch.Home
                val id = segments.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return HomeLaunch.Home
                HomeLaunch.Receipt(kind, id)
            }
            else -> HomeLaunch.Home
        }
    }

    /**
     * An explicit intent for [MainActivity] carrying [launch]. An existing Home is brought forward and receives it in
     * `onNewIntent` instead of a second activity being stacked on top.
     */
    fun intent(context: Context, launch: HomeLaunch): Intent =
        Intent(Intent.ACTION_VIEW, uri(launch), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** [intent] as an immutable PendingIntent (notifications). Links differ by URI, so each gets its own. */
    fun pendingIntent(context: Context, launch: HomeLaunch): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            intent(context, launch),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
