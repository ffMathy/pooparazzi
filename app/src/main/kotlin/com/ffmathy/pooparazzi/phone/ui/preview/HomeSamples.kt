package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.phone.ui.home.CardModel
import com.ffmathy.pooparazzi.phone.ui.home.CardSpec
import com.ffmathy.pooparazzi.phone.ui.home.GuardKind
import com.ffmathy.pooparazzi.phone.ui.home.HomeStateBuilder
import com.ffmathy.pooparazzi.phone.ui.home.HomeUiState
import com.ffmathy.pooparazzi.phone.ui.home.ReceiptSpec
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

    /**
     * 14:32, idle: "Last fed 2h 10m ago · 12:22 · Formula", Awake 1h 05m, Pee 48m ago, Today 5 · 4 · 2 · 7h 57m. The
     * weeks of [SampleData.history] before it give the buttons their forecast: sleep in 50m, feed in 1h (poop varies too
     * much to predict).
     */
    fun idle(): HomeUiState = build(SampleData.history(zone = zone), SampleData.NOW_1432)

    /**
     * [idle] at 15:55 with nothing logged since: both estimates have passed, so the buttons say how long ago they were
     * due: feed "24m ago" (expected ~15:30), sleep "36m ago" (expected ~15:19).
     */
    fun forecastPassed(): HomeUiState = build(SampleData.history(zone = zone), SampleData.at(15, 55))

    /** 03:12, asleep 1h 07m since 02:05; last fed 1h 32m ago · 01:40 · L → R; expected to wake in 2h 15m. */
    fun asleep(): HomeUiState =
        build(SampleData.historyAt0312(zone), SampleData.NOW_0312, sync = SyncStatus.Synced(SampleData.SYNCED_0311))

    /**
     * A first day without a paired watch (no status icons): only a sleep running since 12:48, at 14:32 (no feeds or
     * diapers yet): Today 0 · 0 · 0 · 1h 44m.
     */
    fun firstSleep(): HomeUiState = build(
        listOf(Entry.sleep(SampleData.NOW_1432 - 104 * 60_000L, null, id = "sample-first-sleep")),
        SampleData.NOW_1432,
        sync = SyncStatus.Unavailable,
    )

    /**
     * 14:50, a breast feed (Left → Right) just logged for 14:32 with the −15m chip from the Feed sheet opened at 14:47.
     * No receipt follows a logging sheet: Home shows "Last fed 18m ago · 14:32 · Left → Right".
     */
    fun breastLogged(): HomeUiState = build(
        SampleData.scenarioWithRecentFeed(zone),
        SampleData.NOW_1450,
        sync = SyncStatus.Synced(SampleData.at(14, 49)),
    )

    /**
     * 14:32, a receipt for a Formula logged at 14:32 (as a deep link to a feed's receipt shows it): "Formula logged ·
     * 14:32" with Undo, the offset chips (+5m disabled: it would be in the future), Edit and Done. The log is on its way to the watch ("Syncing with
     * watch…").
     */
    fun justLogged(): HomeUiState = build(
        SampleData.scenario(zone) + formula1432,
        SampleData.NOW_1432,
        sync = SyncStatus.Pending,
        sheet = SheetModel.Receipt(
            stamp = SampleData.NOW_1432,
            key = 1,
            spec = ReceiptSpec.FeedLogged(formula1432.id, FeedType.FORMULA),
            undo = Undo.Delete(formula1432.id),
        ),
    )

    /**
     * 03:12, asleep since 02:05 with "Adjust start" tapped and −15m chosen: the sheet "Adjust start · 01:50" with
     * −15m · −10m · −5m · +5m, Edit and Save.
     */
    fun adjustingStart(): HomeUiState = build(
        SampleData.scenarioAt0312(zone),
        SampleData.NOW_0312,
        sync = SyncStatus.Synced(SampleData.SYNCED_0311),
        sheet = SheetModel.Adjust(stamp = SampleData.at(2, 5), offsetMinutes = -15, entryId = SLEEP_0205),
    )

    /**
     * 14:32, Start sleep just tapped: the receipt "Sleep started · 14:32" with Undo, the offset chips (+5m disabled: it
     * would be in the future), Edit and Done. The sleep is on its way to the watch ("Syncing with watch…").
     */
    fun sleepStarted(): HomeUiState {
        val sleep = Entry.sleep(SampleData.NOW_1432, null, id = "sample-sleep-1432")
        return build(
            SampleData.scenario(zone) + sleep,
            SampleData.NOW_1432,
            sync = SyncStatus.Pending,
            sheet = SheetModel.Receipt(
                stamp = SampleData.NOW_1432,
                key = 1,
                spec = ReceiptSpec.SleepStarted(sleep.id),
                undo = Undo.Delete(sleep.id),
            ),
        )
    }

    /** 14:32, a Formula logged again within 5 s of [formula1432]: the notice "Formula" + "Already logged at 14:32". */
    fun duplicateNotice(): HomeUiState = build(
        SampleData.scenario(zone) + formula1432,
        SampleData.NOW_1432,
        card = CardModel(key = 1, spec = CardSpec.Duplicate(formula1432)),
    )

    /** 14:32, a toggle drawn before the sleep started elsewhere: the notice "Asleep" + "Already asleep since 14:30". */
    fun guardNotice(): HomeUiState = build(
        SampleData.scenario(zone) + Entry.sleep(SampleData.at(14, 30), null, id = "sample-sleep-1430"),
        SampleData.NOW_1432,
        card = CardModel(key = 1, spec = CardSpec.Guard(GuardKind.ASLEEP, SampleData.at(14, 30))),
    )

    /** 03:12, asleep since 02:05, a wet diaper just logged on the widget: its receipt also offers "End sleep". */
    fun loggedDuringSleep(): HomeUiState {
        val pee = Entry.diaper(SampleData.NOW_0312, pee = true, poop = false, id = "sample-pee-0312")
        return build(
            SampleData.scenarioAt0312(zone) + pee,
            SampleData.NOW_0312,
            sync = SyncStatus.Synced(SampleData.SYNCED_0311),
            sheet = SheetModel.Receipt(
                stamp = SampleData.NOW_0312,
                key = 1,
                spec = ReceiptSpec.DiaperLogged(pee.id, DiaperKind.PEE),
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

    /** The Feed sheet opened at 14:32 after tapping Formula: the amount slider at [amountMl] (null: no amount). */
    fun formulaSheet(amountMl: Int? = 120): HomeUiState = build(
        SampleData.scenario(zone),
        SampleData.NOW_1432,
        sheet = SheetModel.Feed(SampleData.NOW_1432, formula = true, amountMl = amountMl),
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

    /**
     * The Diaper sheet opened at 14:32 and nudged −5m twice: "Diaper · 14:22", "was 14:32" beside it, +5m enabled (it
     * no longer reaches the future).
     */
    fun diaperSheetNudged(): HomeUiState = build(
        SampleData.scenario(zone),
        SampleData.NOW_1432,
        sheet = SheetModel.Diaper(stamp = SampleData.NOW_1432, offsetMinutes = -10, pee = true),
    )

    /** The Diaper sheet with Poop on and Pale selected: the health hint shows. */
    fun diaperSheetHint(): HomeUiState = build(
        SampleData.scenario(zone),
        SampleData.NOW_1432,
        sheet = SheetModel.Diaper(stamp = SampleData.NOW_1432, poop = true, color = PoopColor.PALE),
    )

    /**
     * Twins (Noa and Ida), Noa current, at 14:32: the name is the switcher (BABIES_AND_BACKUP §2.4). Signed in and backed
     * up at 14:31, so the watch and the cloud icon both show under the name.
     */
    fun twins(sync: SyncStatus = synced1431, cloud: CloudStatus = CloudStatus.Synced(SampleData.SYNCED_1431)): HomeUiState =
        build(SampleData.scenario(zone), SampleData.NOW_1432, sync = sync, settings = SampleData.twinSettings, cloud = cloud)

    /** [idle] with the watch sync and the backup in the given states (the status icons, DESIGN_SPEC §4.5). */
    fun syncStates(sync: SyncStatus, cloud: CloudStatus): HomeUiState =
        build(SampleData.scenario(zone), SampleData.NOW_1432, sync = sync, cloud = cloud)

    /** [twins] with the Switch baby sheet open. */
    fun twinsSwitching(): HomeUiState =
        build(SampleData.scenario(zone), SampleData.NOW_1432, settings = SampleData.twinSettings, switchingBaby = true)

    private fun build(
        entries: List<Entry>,
        now: Long,
        sync: SyncStatus = synced1431,
        card: CardModel? = null,
        sheet: SheetModel? = null,
        settings: Settings = SampleData.settings,
        switchingBaby: Boolean = false,
        cloud: CloudStatus = CloudStatus.Off,
    ): HomeUiState = HomeStateBuilder.build(
        entries = entries,
        babyName = settings.babyName,
        sync = sync,
        now = now,
        zone = zone,
        card = card,
        sheet = sheet,
        babies = settings.babies,
        babyId = settings.selectedBaby.id,
        switchingBaby = switchingBaby,
        cloud = cloud,
    )
}
