package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.phone.ui.home.CardModel
import com.ffmathy.pooparazzi.phone.ui.home.CardSpec
import com.ffmathy.pooparazzi.phone.ui.home.HomeStateBuilder
import com.ffmathy.pooparazzi.phone.ui.home.HomeUiState
import com.ffmathy.pooparazzi.phone.ui.home.MoveField
import com.ffmathy.pooparazzi.phone.ui.home.SheetModel
import com.ffmathy.pooparazzi.phone.ui.home.Undo

/**
 * Home states from the reference scenario (V1_ADDITIONS §6), built through [HomeStateBuilder] like the app's own
 * states. Used by previews and the screenshot tests.
 */
object HomeSamples {
    private val zone = SampleData.ZONE
    private const val SLEEP_0205 = "sample-sleep-0205"
    private val synced1431 = SyncStatus.Synced(SampleData.SYNCED_1431)

    /** The Formula logged at 14:32 in [justLogged]. */
    val formula1432: Entry = Entry.feed(FeedType.FORMULA, SampleData.NOW_1432, id = "sample-formula-1432")

    /** 14:32, idle: "Last fed 2h 10m ago · 12:22 · Formula", Awake 1h 05m, Pee 48m ago, Today 5 · 4 · 2 · 7h 57m. */
    fun idle(): HomeUiState = build(SampleData.scenario(zone), SampleData.NOW_1432)

    /** 03:12, asleep 1h 07m since 02:05; last fed 1h 32m ago · 01:40 · L → R. */
    fun asleep(): HomeUiState =
        build(SampleData.scenarioAt0312(zone), SampleData.NOW_0312, sync = SyncStatus.Synced(SampleData.SYNCED_0311))

    /**
     * A first day without a paired watch (no sync line): only a sleep running since 12:48, at 14:32 (no feeds or
     * diapers yet): Today 0 · 0 · 0 · 1h 44m.
     */
    fun firstSleep(): HomeUiState = build(
        listOf(Entry.sleep(SampleData.NOW_1432 - 104 * 60_000L, null, id = "sample-first-sleep")),
        SampleData.NOW_1432,
        sync = SyncStatus.Unavailable,
    )

    /**
     * 14:50, a breast feed (Left → Right) just logged for 14:32 with the −15m chip from the Feed sheet opened at 14:47:
     * "Last fed 18m ago · 14:32 · Left → Right" and the undo card "Breast logged · Left → Right".
     */
    fun breastLogged(): HomeUiState {
        val feed = SampleData.recentBreastfeed(zone)
        val stamp = SampleData.at(14, 47)
        return build(
            SampleData.scenarioWithRecentFeed(zone),
            SampleData.NOW_1450,
            sync = SyncStatus.Synced(SampleData.at(14, 49)),
            card = CardModel(
                key = 1,
                spec = CardSpec.FeedLogged(feed.id, FeedType.BREAST, feed.breastSide),
                baseTime = stamp,
                offsetMinutes = -15,
                undo = Undo.Delete(feed.id),
            ),
        )
    }

    /**
     * 14:32, Formula just logged from the Feed sheet: the undo card with Undo and "Adjust time". The log is on its way to the
     * watch ("Syncing with watch…"), as in the approved mockup.
     */
    fun justLogged(): HomeUiState = build(
        SampleData.scenario(zone) + formula1432,
        SampleData.NOW_1432,
        sync = SyncStatus.Pending,
        card = justLoggedCard(),
    )

    private fun justLoggedCard() = CardModel(
        key = 1,
        spec = CardSpec.FeedLogged(formula1432.id, FeedType.FORMULA),
        baseTime = SampleData.NOW_1432,
        undo = Undo.Delete(formula1432.id),
    )

    /**
     * 03:12, asleep since 02:05 with "Adjust start" tapped and −15m chosen: the Adjust time sheet "Adjust start · 01:50"
     * with −15m · −10m · −5m · +5m, Edit and Save.
     */
    fun adjustingStart(): HomeUiState = build(
        SampleData.scenarioAt0312(zone),
        SampleData.NOW_0312,
        sync = SyncStatus.Synced(SampleData.SYNCED_0311),
        sheet = SheetModel.Adjust(stamp = SampleData.at(2, 5), offsetMinutes = -15, entryId = SLEEP_0205, field = MoveField.START),
    )

    /**
     * 14:32, Formula just logged and "Adjust time" tapped on its card: the sheet "Adjust time · 14:32" (+5m disabled,
     * it would be in the future) over the paused card.
     */
    fun adjustingFeed(): HomeUiState {
        val card = justLoggedCard()
        return build(
            SampleData.scenario(zone) + formula1432,
            SampleData.NOW_1432,
            sync = SyncStatus.Pending,
            card = card.copy(paused = true),
            sheet = SheetModel.Adjust(stamp = card.baseTime, entryId = formula1432.id, field = MoveField.START, cardKey = card.key),
        )
    }

    /** 03:12, asleep since 02:05, a wet diaper just logged: the card offers "End sleep" and "Adjust time". */
    fun loggedDuringSleep(): HomeUiState {
        val pee = Entry.diaper(SampleData.NOW_0312, pee = true, poop = false, id = "sample-pee-0312")
        return build(
            SampleData.scenarioAt0312(zone) + pee,
            SampleData.NOW_0312,
            sync = SyncStatus.Synced(SampleData.SYNCED_0311),
            card = CardModel(
                key = 1,
                spec = CardSpec.DiaperLogged(pee.id, DiaperKind.PEE),
                baseTime = SampleData.NOW_0312,
                duringSleep = true,
                undo = Undo.Delete(pee.id),
            ),
        )
    }

    /** The Feed sheet opened at 14:32. */
    fun feedSheet(): HomeUiState = build(SampleData.scenario(zone), SampleData.NOW_1432, sheet = SheetModel.Feed(SampleData.NOW_1432))

    /** The Feed sheet opened at 14:32 after tapping Breast: the side checkboxes, with [ticks] ticked in that order. */
    fun breastSheet(ticks: List<Breast> = emptyList()): HomeUiState = build(
        SampleData.scenario(zone),
        SampleData.NOW_1432,
        sheet = SheetModel.Feed(SampleData.NOW_1432, breast = true, ticks = ticks),
    )

    /** The Diaper sheet with Pee and Poop on, Yellow and Seedy selected. */
    fun diaperSheet(): HomeUiState = build(
        SampleData.scenario(zone),
        SampleData.NOW_1432,
        sheet = SheetModel.Diaper(
            stamp = SampleData.NOW_1432,
            pee = true,
            poop = true,
            color = PoopColor.YELLOW,
            texture = PoopTexture.SEEDY,
        ),
    )

    /** The Diaper sheet with Poop on and Pale selected: the health hint shows. */
    fun diaperSheetHint(): HomeUiState = build(
        SampleData.scenario(zone),
        SampleData.NOW_1432,
        sheet = SheetModel.Diaper(stamp = SampleData.NOW_1432, poop = true, color = PoopColor.PALE),
    )

    private fun build(
        entries: List<Entry>,
        now: Long,
        sync: SyncStatus = synced1431,
        card: CardModel? = null,
        sheet: SheetModel? = null,
    ): HomeUiState = HomeStateBuilder.build(
        entries = entries,
        babyName = SampleData.BABY_NAME,
        sync = sync,
        now = now,
        zone = zone,
        card = card,
        sheet = sheet,
    )
}
