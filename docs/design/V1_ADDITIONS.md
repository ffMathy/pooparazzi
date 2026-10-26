# v1 additions after the mockup review

This document extends [`DESIGN_SPEC.md`](DESIGN_SPEC.md), which stays the source of truth for everything it covers: the Lamp, tokens, typography, icons, formatting grammar, night behaviour and accessibility. This file covers what the user asked for after approving the mockups:

1. **Feed types**: Breast, Formula or Food.
2. **Breastfeeding is a timed interval.** The side is picked **when the feed ends**: Left, Right, Left → Right or Right → Left.
3. **Diaper Pee and Poop are independent toggles** (checkboxes). When Poop is on, the parent can also pick a **colour** and a **texture**.
4. **The whole app is in English and Danish**, through Android string resources, with an in-app language choice.

Where this file and `DESIGN_SPEC.md` disagree, this file wins.

---

## 1. Data model (replaces DESIGN_SPEC §4.1 where they differ)

```kotlin
enum class EntryKind { SLEEP, FEED, DIAPER }
enum class FeedType { BREAST, FORMULA, FOOD }
enum class BreastSide { LEFT, RIGHT, LEFT_THEN_RIGHT, RIGHT_THEN_LEFT }
enum class PoopColor { YELLOW, GREEN, BROWN, BLACK, RED, PALE }
enum class PoopTexture { WATERY, SEEDY, MUSHY, FORMED, HARD }

data class Entry(
    val id: String,                 // UUID
    val kind: EntryKind,
    val startMillis: Long,          // epoch ms (UTC)
    val endMillis: Long? = null,    // SLEEP, and FEED with feedType == BREAST; null while running
    val feedType: FeedType? = null, // FEED only
    val breastSide: BreastSide? = null, // FEED+BREAST only, set when the feed ends
    val pee: Boolean = false,       // DIAPER only
    val poop: Boolean = false,      // DIAPER only
    val poopColor: PoopColor? = null,     // DIAPER with poop only (optional)
    val poopTexture: PoopTexture? = null, // DIAPER with poop only (optional)
    val updatedAtMillis: Long,      // last-writer-wins clock for sync
    val deleted: Boolean = false,   // soft delete (purged after 7 days)
)
```

**Invariants.** All of these are enforced in the repository and covered by unit tests:
- **Intervals:** an entry is an *interval* when `kind == SLEEP`, or when `kind == FEED && feedType == BREAST`. Every other entry is an *instant*, with `endMillis == null` always.
- **Running entries:** an interval is *running* when `endMillis == null && !deleted`. At most one sleep and at most one breastfeed run at a time. A second start is rejected and the UI shows the guard message.
- **Diapers:** a DIAPER needs `pee || poop`. `poopColor` and `poopTexture` must be null when `!poop`.
- **Breast side:** `breastSide` is null while a breastfeed runs. Ending a breastfeed always sets it; the end flow cannot finish without a side.
- **Mis-taps:** an interval that ends **less than 60 s** after its start is discarded as a mis-tap. The UI then shows "Sleep cancelled" or "Feed cancelled", with Undo.

**Time since last feed** is measured from the feed's **start**, whatever its type. This is the convention midwives use.

**Today counts:** **feeds** count every FEED of any type. **pee** counts diapers with `pee`, and **poop** counts diapers with `poop` (so a diaper with both counts once in each). **sleep** is the sleep minutes inside today, including a running sleep.

---

## 2. Feed flows

### 2.1 Phone

- **Home Feed button, idle:** the label stays "Feed". A tap opens the **Feed sheet**, which follows the same pattern as the diaper sheet (DESIGN_SPEC §3.3.2):
  - Title "Feed · 14:32". The time is stamped on the first tap.
  - Offset chips: Now · −5m · −10m · −15m (single-select). The title follows the selected chip.
  - Three **full-width option rows**, each 72 dp tall with radius 28, filled `feed-container`. Each row has a 32 dp icon in `feed-strong`, a label in headlineSmall `feed-on-container`, and an optional supporting line in bodyMedium:
    - **Breast**: icon `breast`, supporting line "Starts a timer".
    - **Formula**: icon `feed` (the bottle).
    - **Food**: icon `food` (bowl and spoon).
  - Tapping **Formula** or **Food** logs an instant at `stamp + offset`, closes the sheet and shows the undo card "Formula logged" / "Food logged", with Earlier chips.
  - Tapping **Breast** starts a breastfeed at `stamp + offset`, closes the sheet and shows the undo card "Feed started", with Earlier chips that move the start.
  - The rows are full width because the Danish labels are long ("Modermælkserstatning").
- **Breastfeed running:**
  - **Lamp:** a running feed takes priority over a running sleep. The Lamp shows label "Breastfeeding" (icon `breast`, FEED text colour), the elapsed time as the numeral, and the sub-line "since 14:32". It has the orbit ring and ember, with track `feed-track` and ember `feed-strong`, plus an "Adjust start" chip (same behaviour as for sleep).
  - **Feed button:** becomes its *checked* form, the same treatment as the Sleep toggle. Radius 28, `feed-container-lit` fill, 2 dp `feed-strong` border, icon `breast`, label "End feed".
  - **Ending:** tapping "End feed" opens the **Side sheet**.
- **Side sheet:**
  - Title "Which side?". Sub-line "Breastfeeding · 14:32–14:50 · 18m", which updates with the chips.
  - End-time chips: Now · −5m · −10m. A chip that would put the end before the start is disabled.
  - A **2 × 2 grid** of option tiles, each 165 × 96 dp with radius 28 and `feed-container` fill:

    | Tile | Glyph |
    |---|---|
    | Left | `sideLeft` |
    | Right | `sideRight` |
    | Left → Right | `sideLeftRight` |
    | Right → Left | `sideRightLeft` |

    The glyphs are two small circles labelled with the letters L/R (V/H in Danish come from text, not the icon). Each tile also shows its text label.
  - **One tap ends the feed** at `stamp + offset` with that side. The undo card reads "Feed ended", sub "Left → Right · 18m".
  - "Cancel" (or a scrim tap) closes the sheet and the feed keeps running.
- **Running sleep and a feed:** logging Formula or Food, or *starting* a breastfeed, while a sleep runs shows the running-sleep card variant with the "End sleep" chip (unchanged).
- **Since-row 1** at 14:32 idle still reads "Awake …". The feed type appears in the Lamp label icon (`breast` / `feed` / `food`) and in the Lamp sub-line: "ago · 12:22 · Formula". The type word is omitted when the line would exceed the Lamp chord; the icon always carries it, and TalkBack reads "Last feed, formula, 2 hours 10 minutes ago, at 12:22".

### 2.2 Watch

- **Main:** Feed opens the **Feed type** screen.
  - Three stacked buttons (full width, 52 dp tall, `feed-container`): Breast, Formula, Food.
  - A CompactButton "Earlier" cycles the stamp through −5m / −10m / −15m, as on the diaper picker.
  - Formula and Food lead to **Logged** ("Formula at 14:32" + Undo).
  - Breast starts the timer and returns to **Main** in its running-feed state.
- **Main while a breastfeed runs:**
  - The Lamp shows the feed: label "Breastfeeding", elapsed numeral, "since 14:32", FEED ring and ember.
  - The pinned EdgeButton becomes **"End feed"**: Medium, `feed-container-lit`, `feed-strong` border.
  - The sleep toggle moves into the scroll list as a full-width button ("Start sleep" / "Woke up").
  - If a sleep also runs, the list shows a status card for it.
- **"End feed" leads to the Side screen:**
  - Title "Which side?".
  - Four full-width buttons, 52 dp each: Left, Right, Left → Right, Right → Left.
  - Tapping one leads to **Logged** ("Feed ended", second line "Left → Right · 18m") + Undo.
- **Tile:**
  - **Row A, idle:** "Feed", which opens the app on the Feed type screen.
  - **Row A, while a breastfeed runs:** "End feed", which opens the app on the Side screen.
  - **Row B:** Pee | Poop | Pee + poop. Each is one tap and logs with no colour or texture; both can be added later in the app.
  - **Edge:** the sleep toggle.

---

## 3. Diaper flow (replaces the Pee / Poop / Both one-tap picker)

### 3.1 Phone: the Diaper sheet

The sheet scrolls. It opens with `skipPartiallyExpanded`.

1. **Header:** title "Diaper · 14:32" with a Cancel button.
2. **Offset chips:** Now · −5m · −10m · −15m.
3. **Two toggle tiles**, side by side, each 165 × 112 dp with radius 28. They are **checkboxes**, **both off when the sheet opens**:
   - **Pee:** icon `pee`.
   - **Poop:** icon `poop`.
   - **Off:** container fill and a 1 dp `outline` border, plus an empty checkbox glyph in the tile's top-right corner.
   - **On:** `*-container-lit` fill (pee/poop use the diaper lit colour), a 2 dp border in its strong colour, and a checked checkbox glyph in that colour.
   - **Semantics:** `Modifier.toggleable(role = Role.Checkbox)`. TalkBack announces "Pee, checkbox, not checked".
4. **When Poop is on**, two sections expand below it (animated height; reduced motion makes it instant):
   - **Colour** (labelMedium heading): six chips in a wrapping row. Each is 48 dp tall with a 20 dp swatch dot and a label: Yellow, Green, Brown, Black, Red, Pale. They are single-select and **optional**: tapping the selected chip clears it.
   - **Texture**: five chips: Watery, Seedy, Mushy, Formed, Hard. Also single-select and optional.
   - **Health hint:** when Pale, Red or Black is selected, a bodySmall line in `on-surface-variant` with an `info` icon appears: "Pale, red or black poop can be a reason to call your doctor or health visitor." The app never diagnoses.
5. **Save:** a primary button at the bottom right of the sheet, in the thumb zone, labelled "Save". It is **disabled until Pee or Poop is on**. Saving logs the entry at `stamp + offset`, closes the sheet and shows the undo card ("Pee logged" / "Poop logged" / "Pee + poop logged").

**Swatch colours.** These are content colours, not theme colours, so they look the same in Night and Day. Every chip has a text label, so colour is never the only cue. Each swatch gets a 1 dp `outline` ring so Black and Pale stay visible on any surface.

| Colour | Hex |
|---|---|
| Yellow | `#E2B33C` |
| Green | `#6F8F3A` |
| Brown | `#8A5A2B` |
| Black | `#2B2622` |
| Red | `#B23A2E` |
| Pale | `#ECE6D6` |

### 3.2 Watch: the Diaper screen

- Title "Diaper · 14:32", plus the Earlier CompactButton.
- **Two `CheckboxButton`s:** Pee and Poop.
- **When Poop is on**, two more buttons appear:
  - "Colour" shows the current value or "Optional", and opens a list of 6 options (each with its swatch dot) plus "None".
  - "Texture" works the same way, with 5 options plus "None".
- **Pinned Medium EdgeButton "Save"**, disabled until one checkbox is on, which leads to **Logged** ("Pee at 14:32" / "Poop at 14:32" / "Pee + poop at 14:32").

### 3.3 Display strings

| Where | Format | Example |
|---|---|---|
| History list | title + supporting line | "Pee + poop" / "13:44 · Yellow · Seedy" |
| Edit sheet | | the same toggles and chips as the Diaper sheet |
| Since-row 2 | kind-first, as before | "Poop 48m ago · 13:44" |

The since-row never shows colour or texture.

---

## 4. History and Edit changes

**List rows:**
- **Breastfeed:** title "Breast · Left → Right", supporting "12:00–12:18 · 18m". While running: "Breast · running", supporting "since 14:32".
- **Formula / Food:** title "Formula" / "Food", supporting "12:22".
- **Diaper:** title "Pee" / "Poop" / "Pee + poop", supporting "13:44" plus " · {colour}" and " · {texture}" when set.

**Ribbon feed lane:**
- **Breastfeeds** are bars (min 6 dp wide), like sleep but in `feed-strong`.
- **Formula** keeps the tick mark.
- **Food** is a tick with a small square cap. Shape stays the redundant cue.

**Edit sheet:**
- **Feed:**
  - Type selector: a connected button group, Breast | Formula | Food.
  - Breast shows a start time block, an end time block (or "Still feeding" + End now) and a side selector: a connected group of the 4 sides, single-select.
  - Formula / Food show one time block.
- **Diaper:** the toggles, colour chips and texture chips from §3.1, plus one time block. Save stays disabled while both toggles are off.

**Stats row** stays the same: feeds · pee · poop · sleep.

---

## 5. Internationalisation (English + Danish)

- **Resources:** every user-facing string lives in Android string resources:
  - English is the default, in `values/`.
  - Danish is in `values-da/`.
  - Counts use `<plurals>`.
  - **No user-facing text is hard-coded in Kotlin**, including content descriptions, notification text, tile text and complication text.
- **Language setting:**
  - **Phone:** Settings › Language offers System default · English · Dansk, through `AppCompatDelegate.setApplicationLocales`. Both apps declare `android:localeConfig` (`res/xml/locales_config.xml`: `en`, `da`).
  - **Sync:** the phone syncs the chosen language to the watch through the `/settings` data item. The watch applies it through `LocaleManager.setApplicationLocales` on API 33+, and on older watches it follows the system.
- **Formatting:**
  - **Duration units** come from resources:
    - English `h`, `m`, `d`, e.g. "2h 10m", "48m", "1d 3h".
    - Danish `t`, `m`, `d`, e.g. "2t 10m", "48m", "1d 3t".
  - **Clock times:** 24 h (`HH:mm`) when `DateFormat.is24HourFormat(context)` says so, which is always for Danish. Otherwise use the locale's short time with AM/PM.
  - **Relative phrases** are separate resources:

    | English | Danish |
    |---|---|
    | "ago" | "siden" |
    | "since %s" | "siden kl. %s" |
    | "just now" | "lige nu" |
    | "%s ago" | "%s siden" |

  - **Spoken forms** (TalkBack) use plurals. English "2 hours 10 minutes"; Danish "2 timer 10 minutter".
- **Long words:**
  - "Modermælkserstatning" contains a soft hyphen in compact places (`Modermælks­erstatning`), and those `Text`s use `hyphens = Hyphens.Auto`.
  - Layouts must survive Danish at 200 % font scale: labels wrap, nothing clips.

### 5.1 Glossary (use these exact words)

| Key concept | English | Danish |
|---|---|---|
| Feed (category / button) | Feed | Måltid |
| Breast (type) | Breast | Amning |
| Breastfeeding (running Lamp label) | Breastfeeding | Ammer |
| Formula | Formula | Modermælkserstatning |
| Food (solids) | Food | Mad |
| End feed | End feed | Afslut amning |
| Which side? | Which side? | Hvilken side? |
| Left / Right | Left / Right | Venstre / Højre |
| Left → Right | Left → Right | Venstre → Højre |
| Diaper | Diaper | Ble |
| Pee / Poop / Pee + poop | Pee / Poop / Pee + poop | Tis / Bæ / Tis + bæ |
| Colour / Texture | Colour / Texture | Farve / Konsistens |
| Yellow, Green, Brown, Black, Red, Pale | — | Gul, Grøn, Brun, Sort, Rød, Bleg |
| Watery, Seedy, Mushy, Formed, Hard | — | Vandig, Kornet, Grødet, Formet, Hård |
| Sleep / Start sleep / Woke up | — | Søvn / Start søvn / Vågnede |
| Asleep (Lamp label) / Awake | — | Sover / Vågen |
| Last fed | Last fed | Sidste måltid |
| History / Today | — | Historik / I dag |
| Undo / Done / Save / Cancel / Delete / Edit | — | Fortryd / Færdig / Gem / Annuller / Slet / Rediger |
| Earlier? | Earlier? | Tidligere? |
| Adjust start | Adjust start | Ret start |
| Settings / Appearance / Language | — | Indstillinger / Udseende / Sprog |
| Auto / Night / Day | — | Auto / Nat / Dag |
| Synced with watch · 14:31 | — | Synkroniseret med ur · 14:31 |
| Synced with phone · 14:31 | — | Synkroniseret med telefon · 14:31 |
| feeds / pee / poop / sleep (Today grid) | — | måltider / tis / bæ / søvn |

---

## 6. Reference scenario v2 (previews, screenshots, tests)

Baby **Noa**, 7 months. Today is **Mon 26 Oct 2026** and now is **14:32**, in time zone `Europe/Copenhagen`. Times in the log are local.

| Time | Entry |
|---|---|
| Sun 22:40 – Mon 01:35 | Sleep |
| 01:40 – 01:58 | Breast, Left → Right |
| 01:52 | Pee |
| 02:05 – 05:20 | Sleep |
| 05:25 | Formula |
| 05:41 | Pee + poop, Yellow, Seedy |
| 06:10 – 07:45 | Sleep |
| 08:00 – 08:14 | Breast, Right → Left |
| 08:20 | Poop, Brown, Mushy |
| 09:30 – 10:15 | Sleep |
| 10:30 | Food |
| 11:05 | Pee |
| 12:22 | Formula |
| 12:40 – 13:27 | Sleep |
| 13:44 | Pee |

The 01:52 pee falls *during* the 01:40 breastfeed. That is realistic and intended (it tests overlap handling).

**Derived values at 14:32:**
- Last fed 2h 10m ago · 12:22 · Formula.
- Awake 1h 05m · since 13:27.
- Pee 48m ago · 13:44.
- Today: 5 feeds · 4 pee · 2 poop · 7h 57m sleep.

**Night snapshot at 03:12:** asleep 1h 07m since 02:05. Last fed 1h 32m ago · 01:40 · Breast.

**Breastfeeding snapshot at 14:50:** breastfeed running since 14:32, so the Lamp shows "Breastfeeding 18m".

---

## 7. Architecture contract

**Package root:** `com.ffmathy.xaternity`. Both apps use `applicationId "com.ffmathy.xaternity"`, which the Wearable Data Layer requires.

| Module | Namespace | Contents |
|---|---|---|
| `:core` | `.core` | Android library shared by both apps: model, storage, sync, domain, formatting, shared strings, design tokens, icons, fonts, Lamp drawing |
| `:app` | `.phone` (R class); applicationId above | Phone app |
| `:wear` | `.wear` | Wear OS app |

### `:core` public API

These names are what the apps code against.

**`model/`**
- `Entry` and the enums from §1, `@Serializable`.
- Extensions: `isInterval`, `isRunning`, `durationMillis(now)`.

**`data/`**
- Room `XaternityDatabase` and `EntryDao`. Storage is LWW-friendly: `upsert` only replaces a row when the incoming `updatedAtMillis` is ≥ the stored one.
- `EntryRepository` (interface) plus `RoomEntryRepository`:
  - `fun observeEntries(): Flow<List<Entry>>`: non-deleted entries.
  - `suspend fun logFeed(type: FeedType, at: Long): Entry`: FORMULA/FOOD instants.
  - `suspend fun startBreastfeed(at: Long): Result<Entry>`: fails if one is running.
  - `suspend fun endBreastfeed(id: String, at: Long, side: BreastSide): EndResult`: `Ended(entry)` or `Discarded(entry)` (under 60 s).
  - `suspend fun startSleep(at: Long): Result<Entry>`.
  - `suspend fun endSleep(id: String, at: Long): EndResult`.
  - `suspend fun logDiaper(at: Long, pee: Boolean, poop: Boolean, color: PoopColor?, texture: PoopTexture?): Entry`.
  - `suspend fun update(entry: Entry): Entry`: bumps `updatedAtMillis` and validates the invariants.
  - `suspend fun delete(id: String)`: soft delete.
  - `suspend fun restore(previous: Entry)`: undo; writes the old snapshot with a new `updatedAtMillis`.
  - `suspend fun applyRemote(entry: Entry)`: LWW merge, with no echo back to sync.
- `Clock`: `fun interface Clock { fun now(): Long }`, with `SystemClock` and a fixed clock for tests and screenshots.

**`sync/`**
- `EntrySync`: pushes every local write to the Wearable `DataClient` as a data item at `/entries/<id>` holding the JSON-encoded entry. `setUrgent()` is used.
- `EntrySyncListenerService`: a `WearableListenerService` declared in **core's manifest**, so it merges into both apps. It applies remote `/entries/*` and `/settings` items through `applyRemote` / `SettingsRepository`.
- `SyncStatus` flow: `Synced(atMillis)` · `Pending` · `Offline` · `Error`.
- A `NoopEntrySync` exists for tests.

**`settings/`**
- `SettingsRepository`, backed by DataStore: `babyName` (default "Noa" only in sample data; empty in production with the hint "Baby"), `appearance` (AUTO/NIGHT/DAY), `language` (SYSTEM/EN/DA).
- Changes are mirrored to `/settings`.

**`domain/`**
- Pure Kotlin with no Android dependencies, unit-tested against §6.
- `fun summarize(entries: List<Entry>, now: Long, zone: ZoneId): Summary`, where `Summary` contains:
  - `lastFeed`, `lastDiaper`, `runningSleep`, `runningFeed`, `lastSleepEnd` (awake since);
  - `today: DayCounts(feeds, pee, poop, sleepMinutes)`;
  - `dayEntries(day)` for History.
- `fun lampState(summary): LampState`, one of `FeedIdle`, `SleepRunning`, `FeedRunning`.

**`format/`**
- `DurationFormatter` (units injected; resource-backed factory `DurationFormatter.from(context)`).
- `ClockFormatter`.
- `LabelFormatter`, which maps enums to string resources: feed type, side, poop colour and texture, and diaper kind labels.
- Composable `rememberFormatters()`.

**`ui/`** (Compose; no Material dependency)
- `tokens`: Night, Day and Watch colour sets plus event colours, including `feed-container-lit` and `feed-track`; poop swatches.
- `icons`: `XIcons.*` `ImageVector`s from the DESIGN_SPEC §6 path data, plus `breast`, `food`, `sideLeft`, `sideRight`, `sideLeftRight`, `sideRightLeft`, `checkbox`, `checkboxChecked`, `info`.
- `type`: the `FontFamily`s with variable axes.
- `lamp`: `Lamp(state, sizeDp, theme)`, Canvas drawing shared by phone and watch.

**`res/`**
- Shared strings (EN + DA): all glossary words, units, relative phrases, spoken plurals, labels for poop colours and textures.
- The fonts.

**`sample/`**
- `SampleData.scenario(zone)` builds §6.
- `SampleData.NOW_1432`, `NOW_0312` and `NOW_1450`, plus the running-feed variant.

### `:app` and `:wear` rules

- **Screen structure:** each screen is a **stateless composable** (`HomeScreen(state: HomeUiState, actions: HomeActions)`, and so on) plus a ViewModel. Screenshot tests render the stateless screens with `SampleData` and a fixed clock, in EN and DA.
- **Strings:** app-specific strings live in the app module's `values/` + `values-da/`. Shared words come from core.
- **Graceful degradation:** without Play Services or a paired device, everything works locally. Sync is optional.
