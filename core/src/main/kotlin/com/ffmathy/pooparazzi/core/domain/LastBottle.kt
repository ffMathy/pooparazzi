package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType

/**
 * The amount of the newest bottle feed in [entries] that recorded one, within the [days] up to [now] (inclusive at
 * both ends); null when there is none. Deleted entries, other feeds and amount-less bottles are skipped. The watch's
 * amount chips centre on it (V1_ADDITIONS §2.2), so a bottle from long ago never sets them.
 */
fun lastBottleAmountMl(entries: List<Entry>, now: Long, days: Int = 7): Int? {
    val since = now - days * DAY_MILLIS
    return entries
        .filter { !it.deleted && it.kind == EntryKind.FEED && it.feedType == FeedType.BOTTLE && it.startMillis in since..now }
        .filter { (it.amountMl ?: 0) > 0 }
        .maxByOrNull { it.startMillis }
        ?.amountMl
}

private const val DAY_MILLIS = 24 * 60 * 60_000L
