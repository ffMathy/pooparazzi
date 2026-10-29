# v1 additions after the mockup review

> **Renamed:** the product is now called **Pooparazzi**. This design document predates the rename and keeps the working name "Xaternity"; in code, read `com.ffmathy.xaternity` as `com.ffmathy.pooparazzi`, `xaternity://` as `pooparazzi://` and `Xaternity*` classes as `Pooparazzi*`.

> **Several babies and the backup:** [`BABIES_AND_BACKUP.md`](BABIES_AND_BACKUP.md) adds several babies (a current baby per device) and an optional account that backs the log up; it wins where it differs from this document (one baby, "phone ↔ watch only").

This document extends [`DESIGN_SPEC.md`](DESIGN_SPEC.md), which stays the source of truth for everything it covers: the Lamp, tokens, typography, icons, formatting grammar, night behaviour and accessibility. This file covers what the user asked for after approving the mockups:

1. **Feed types**: Breast, Formula or Food. The type is **optional**: a feed can be logged with just its time.
2. **A breast feed is one time, not a timer.** Like Formula and Food it records only its time (Now or −5m / −10m / −15m), plus an optional **side order**, ticked with two checkboxes, Left and Right, before it is logged. The **order of the ticks is the order recorded** (Left then Right = Left → Right). Nothing asks for a duration. (An earlier version timed breastfeeds with a start and an end; see "Compatibility" in §1.)
3. **Diaper Pee and Poop are independent toggles** (checkboxes). When Poop is on, the parent can also pick a **colour** and a **texture**. All of these are **optional**: a diaper with nothing ticked is a plain diaper change.
4. **The whole app is in English and Danish**, through Android string resources, with an in-app language choice.

**Minimal data.** The user asked that the data required be minimal: only the times are ever required. Feed type, breast side, pee, poop, colour and texture are all optional extras.

Where this file and `DESIGN_SPEC.md` disagree, this file wins.

---

## 1. Data model (replaces DESIGN_SPEC §4.1 where they differ)

```kotlin
enum class EntryKind { SLEEP, FEED, DIAPER }
enum class FeedType { BREAST, FORMULA, FOOD }
enum class Breast { LEFT, RIGHT }  // one ticked checkbox (UI only, not stored)
enum class BreastSide { LEFT, RIGHT, LEFT_THEN_RIGHT, RIGHT_THEN_LEFT } // stored; = the tick order
enum class PoopColor { YELLOW, GREEN, BROWN, BLACK, RED, PALE }
enum class PoopTexture { WATERY, SEEDY, MUSHY, FORMED, HARD }

data class Entry(
    val id: String,                 // UUID
    val kind: EntryKind,
    val startMillis: Long,          // epoch ms (UTC): a sleep's start, or the time of any other entry
    val endMillis: Long? = null,    // SLEEP only; null while running
    val feedType: FeedType? = null, // FEED only; optional (null = just the time)
    val breastSide: BreastSide? = null, // FEED+BREAST only; optional side order
    val pee: Boolean = false,       // DIAPER only
    val poop: Boolean = false,      // DIAPER only
    val poopColor: PoopColor? = null,     // DIAPER with poop only (optional)
    val poopTexture: PoopTexture? = null, // DIAPER with poop only (optional)
    val updatedAtMillis: Long,      // last-writer-wins clock for sync
    val deleted: Boolean = false,   // soft delete (purged after 7 days)
)
```

**Invariants.** All of these are enforced in the repository and covered by unit tests:
- **Intervals:** only a SLEEP is an *interval*. Every other entry is an *instant*, with `endMillis == null` always: every FEED (breastfeeds included, and a FEED with no type) and every DIAPER.
- **Running entries:** a sleep is *running* when `endMillis == null && !deleted`. At most one sleep runs at a time. A second start is rejected and the UI shows the guard message. Nothing else ever runs.
- **Feed type:** optional. A FEED with `feedType == null` is an instant that records only its time; it can never carry a side.
- **Diapers:** `pee` and `poop` are optional. A DIAPER with neither is a *plain change* (display kind `PLAIN`, label "Diaper"). `poopColor` and `poopTexture` must be null when `!poop`.
- **Breast side:** optional. A breastfeed logged with nothing ticked has `breastSide == null` (only its time was recorded). Other feed types never carry a side.
- **Tick order → side:** `BreastSide.fromOrder(ticks)` maps the ticked `Breast`es, in tick order, to the stored value: `[]` → null, `[L]` → LEFT, `[R]` → RIGHT, `[L, R]` → LEFT_THEN_RIGHT, `[R, L]` → RIGHT_THEN_LEFT. `BreastSide.toggle(ticks, b)` unticks `b` when ticked, otherwise appends it (so re-ticking puts it last). `side.order` maps back for editing. The stored enum is unchanged, so storage and sync stay compatible.
- **Mis-taps:** a sleep that ends **less than 60 s** after its start is discarded as a mis-tap. The UI then shows "Sleep cancelled", with Undo.
- **Dedupe:** two feeds of the same type (breastfeeds included, whatever their side) less than 5 s apart are one entry ("Already logged at …"), as for every instant.

**Time since last feed** is measured from the feed's time, whatever its type.

**Today counts:** **feeds** count every FEED of any type, including feeds with no type. **pee** counts diapers with `pee`, and **poop** counts diapers with `poop` (so a diaper with both counts once in each; a plain change counts in neither, and the stats row deliberately has no diaper total). **sleep** is the sleep minutes inside today, including a running sleep.

**Compatibility.** Every entry an older version wrote still decodes. The removed violations are `FEED_TYPE_MISSING`, `SIDE_MISSING`, `DIAPER_EMPTY` and `SIDE_WHILE_RUNNING`. Older versions stored a breastfeed as a timed interval: one with an `endMillis`, or one still "running" (no end). Every read path (Room rows and the sync JSON) passes entries through `Entry.normalized()`, which drops a FEED's end, so such an entry reads, displays and syncs as an instant at its start, keeps its side, never runs and never blocks a new feed or a sleep; editing it writes it back without the end. New data never has a breastfeed end (`INSTANT_HAS_END`). Old notifications of a running breastfeed (phone id 1002, watch Ongoing Activity 1002) are cancelled, and the old deep links (`pooparazzi://home/side`, the tile's `end_feed`) open Home / Main. Both apps ship together.

---

## 2. Feed flows

### 2.1 Phone

- **Home Feed button:** the label is always "Feed" (it has no running form). A tap opens the **Feed sheet**, which follows the same pattern as the diaper sheet (DESIGN_SPEC §3.3.2):
  - Title "Feed · 14:32". The time is stamped on the first tap.
  - Offset chips: Now · −5m · −10m · −15m (single-select). The title follows the selected chip.
  - Three **full-width option rows**, each 72 dp tall with radius 28, filled `feed-container`. Each row has a 32 dp icon in `feed-strong`, a label in headlineSmall `feed-on-container`, and an optional supporting line in bodyMedium:
    - **Breast**: icon `breast`, supporting line "Then the sides, if you like" ("Derefter siderne, hvis du vil").
    - **Formula**: icon `feed` (the bottle).
    - **Food**: icon `food` (bowl and spoon).
  - A fourth row, **"Just the time"** ("Kun tidspunktet"): same size, but *outlined* (1 dp `outline`, no fill), icon `clock`, label in `on-surface`. It logs a feed with no type.
  - Tapping **Formula** or **Food** logs an instant at `stamp + offset`, closes the sheet and shows the undo card "Formula logged" / "Food logged", with Earlier chips. **Just the time** does the same with "Feed logged" ("Måltid registreret").
  - Tapping **Breast** logs nothing yet: the same sheet moves to its **Breast step** (below). The stamp and the selected chip are kept.
  - The rows are full width because the Danish labels are long ("Modermælkserstatning").
- **Breast step** (the Feed sheet after Breast):
  - Title "Breast · 14:32" ("Amning · 14:32"), following the chips, which stay: Now · −5m · −10m · −15m.
  - Hint (bodyMedium, `on-surface-variant`): "Tick the sides in the order you fed. Optional." / "Sæt flueben i den rækkefølge, du ammede. Valgfrit."
  - **Two checkbox tiles side by side**, Left and Right, styled like the Diaper sheet's tiles but in FEED colours (min 112 dp tall, radius 28). Each shows the `sideLeft` / `sideRight` glyph with the L/R letters (V/H in Danish come from text), its label, and a checkbox glyph top-right.
    - **Off:** `feed-container` fill, 1 dp `outline` border, empty checkbox.
    - **On:** `feed-container-lit` fill, 2 dp `feed-strong` border, checked checkbox in `feed-strong`, and an **order badge** top-left: a 28 dp `feed-strong` circle with "1" or "2".
    - **Order:** the first box ticked is 1, the second 2. Unticking removes a side from the order; ticking it again appends it.
    - **Semantics:** `Role.Checkbox`; TalkBack reads "Left, first, checkbox, checked".
  - A **summary line** under the tiles (titleMedium, live region): "Left → Right" in FEED text, or "No side" in `on-surface-variant` while nothing is ticked. TalkBack reads "Left then right".
  - **"Log feed"** ("Registrér"): the primary button bottom-right (as Save in the Diaper sheet). It is always enabled, because the side is optional. It logs a breast feed instant at `stamp + offset` with the ticked order (none ticked: just the time), closes the sheet and shows the undo card "Breast logged", sub "Left → Right" (no sub with no side), with Earlier chips that move its time.
  - "Cancel" (or a scrim tap) closes the sheet and logs nothing.
- **Running sleep and a feed:** logging any feed (a breast feed included) while a sleep runs shows the running-sleep card variant with the "End sleep" chip (unchanged). The sleep keeps the Lamp.
- **Since-row 1** at 14:32 idle still reads "Awake …". The feed type appears in the Lamp label icon (`breast` / `feed` / `food`) and in the Lamp sub-line: "ago · 12:22 · Formula". The type word is omitted when the line would exceed the Lamp chord; the icon always carries it, and TalkBack reads "Last feed, formula, 2 hours 10 minutes ago, at 12:22". A feed with no type shows the generic `feed` icon and no type word ("ago · 12:22"; TalkBack "Last feed, 2 hours 10 minutes ago, at 12:22").
- **Breast order on every last-feed line.** A breastfeed with a recorded side shows the order instead of the word "Breast", because it is what the next feed starts from:
  - Lamp sub-line "ago · 14:32 · Left → Right". When that would exceed the chord it shortens to "ago · 14:32 · L → R", then "14:32 · L → R".
  - Since-row 1 while asleep "Last fed 1h 32m ago · 01:40 · L → R" (short, so the row stays on one line).
  - Widget "Fed at 01:40 · L → R".
  - Watch status block "Fed at 12:22 · L → R" and the asleep status card "1h 32m ago · 01:40 · L → R".
  - TalkBack reads "Last feed, breast, left then right, …". Danish uses "Venstre → Højre" and "V → H".
- **No running feed anywhere:** there is no "End feed", no Side-at-end sheet, no Adjust start for feeds and no ongoing notification or Live Update for feeds (the sleep keeps its own). The widgets' Feed button always opens the Feed sheet.

### 2.2 Watch

- **Main:** Feed opens the **Feed type** screen.
  - Three stacked buttons (full width, 52 dp tall, `feed-container`): Breast, Formula, Food.
  - A fourth, outlined button: **"Just the time"** (icon `clock`), which logs a feed with no type.
  - A CompactButton "Earlier" cycles the stamp through −5m / −10m / −15m, as on the diaper picker.
  - Formula, Food and Just the time lead to **Logged** ("Formula at 14:32" / "Feed at 14:32" + Undo).
  - Breast leads to the **Side screen**; nothing is logged yet, and swiping back returns to Feed type with its time.
- **Side screen** (after Breast):
  - Title "Which side?", with "Breast · 14:32" (the Feed type screen's time, with its Earlier offset) under it.
  - Two full-width **`CheckboxButton`s**, Left and Right, in FEED colours (`feed-container`, lit `feed-container-lit` with the box in `feed-strong`). A ticked button's icon slot shows its **order badge** ("1" / "2" in a `feed-strong` circle) instead of the side glyph. TalkBack: "Left, first, checkbox, checked".
  - Under them, the order in words: "Left → Right", or "No side".
  - **Pinned Medium EdgeButton "Save"** (as on the Diaper screen), **enabled with nothing ticked**.
  - It logs the breast feed instant and leads to **Logged** ("Breast at 14:32", second line "Left → Right", or none with no side) + Undo, which deletes it.
- **Main** never has a breastfeeding state: the Lamp only runs for a sleep, the pinned EdgeButton is always the sleep toggle, and no Ongoing Activity or complication text exists for feeds.
- **Tile:**
  - **Row A:** "Feed", with the time since the last feed, which opens the app on the Feed type screen.
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
   - **On:** `*-container-lit` fill (pee/poop use the diaper lit colour), a 2 dp border in DIAPER strong, and a checked checkbox glyph in that colour (both tiles alike, as on the watch and the avatars: POOP strong is only 2.4:1 on the lit fill at Night). The pee/poop icon keeps its kind colour.
   - **Semantics:** `Modifier.toggleable(role = Role.Checkbox)`. TalkBack announces "Pee, checkbox, not checked".
4. **When Poop is on**, two sections expand below it (animated height; reduced motion makes it instant):
   - **Colour** (labelMedium heading): six chips in a wrapping row. Each is 48 dp tall with a 20 dp swatch dot and a label: Yellow, Green, Brown, Black, Red, Pale. They are single-select and **optional**: tapping the selected chip clears it.
   - **Texture**: five chips: Watery, Seedy, Mushy, Formed, Hard. Also single-select and optional.
   - **Health hint:** when Pale, Red or Black is selected, a bodySmall line in `on-surface-variant` with an `info` icon appears: "Pale, red or black poop can be a reason to call your doctor or health visitor." The app never diagnoses.
5. **Save:** a primary button at the bottom right of the sheet, in the thumb zone, labelled "Save". It is **always enabled**: Pee, Poop, colour and texture are all optional. Saving logs the entry at `stamp + offset`, closes the sheet and shows the undo card ("Pee logged" / "Poop logged" / "Pee + poop logged", or "Diaper logged" for a plain change).

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
- **Pinned Medium EdgeButton "Save"**, always enabled, which leads to **Logged** ("Pee at 14:32" / "Poop at 14:32" / "Pee + poop at 14:32", or "Diaper at 14:32" for a plain change).
- The tile's one-tap Pee | Poop | Pee + poop buttons are unchanged.

### 3.3 Display strings

| Where | Format | Example |
|---|---|---|
| History list | title + supporting line | "Pee + poop" / "13:44 · Yellow · Seedy"; a plain change "Diaper" / "13:44" |
| Edit sheet | | the same toggles and chips as the Diaper sheet |
| Since-row 2 | kind-first, as before | "Poop 48m ago · 13:44"; a plain change "Diaper 48m ago · 13:44" with the `diaper` icon |

A plain change is drawn in the History ribbon as a small square in DIAPER strong.

The since-row never shows colour or texture.

---

## 4. History and Edit changes

**List rows:**
- **Breastfeed:** title "Breast · Left → Right" (just "Breast" with no side), supporting "12:00": its time, like every feed (no end, no duration).
- **Formula / Food:** title "Formula" / "Food", supporting "12:22". A feed with no type: title "Feed" ("Måltid"), generic `feed` icon, and a formula-style tick in the ribbon.
- **Diaper:** title "Pee" / "Poop" / "Pee + poop", or "Diaper" for a plain change, supporting "13:44" plus " · {colour}" and " · {texture}" when set.

**Ribbon feed lane:**
- **Formula** keeps the tick mark.
- **Food** is a tick with a small square cap.
- **Breastfeeds** are ticks too (they are instants), with a round foot (an 8 dp disc at the bottom). Shape stays the redundant cue.

**Edit sheet:**
- **Feed:**
  - Type selector: a connected button group, Breast | Formula | Food, labelled "Type · Optional". Tapping the selected type again **clears it** (a feed with just its time).
  - Every feed shows one time block. Breast adds "Side · Optional": the **same Left / Right checkboxes** as the Feed sheet's Breast step (order badges, tick order = side) and the summary line. There is no end time and no duration.
- **Diaper:** the toggles, colour chips and texture chips from §3.1, plus one time block. Save is enabled with both toggles off (a plain change).
- **Save** is disabled only by a blocking issue (end before start, a time in the future, the entry deleted elsewhere). Nothing beyond the times is required.

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
| Formula | Formula | Modermælkserstatning |
| Food (solids) | Food | Mad |
| Log feed (Breast step button) | Log feed | Registrér |
| Which side? | Which side? | Hvilken side? |
| Tick the sides in the order you fed. Optional. | — | Sæt flueben i den rækkefølge, du ammede. Valgfrit. |
| No side | No side | Ingen side |
| Just the time (untyped feed) | Just the time | Kun tidspunktet |
| Feed (untyped feed, history title) | Feed | Måltid |
| Diaper (plain change) | Diaper | Ble |
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
| 01:40 | Breast, Left → Right |
| 01:52 | Pee |
| 02:05 – 05:20 | Sleep |
| 05:25 | Formula |
| 05:41 | Pee + poop, Yellow, Seedy |
| 06:10 – 07:45 | Sleep |
| 08:00 | Breast, Right → Left |
| 08:20 | Poop, Brown, Mushy |
| 09:30 – 10:15 | Sleep |
| 10:30 | Food |
| 11:05 | Pee |
| 12:22 | Formula |
| 12:40 – 13:27 | Sleep |
| 13:44 | Pee |

**Derived values at 14:32:**
- Last fed 2h 10m ago · 12:22 · Formula.
- Awake 1h 05m · since 13:27.
- Pee 48m ago · 13:44.
- Today: 5 feeds · 4 pee · 2 poop · 7h 57m sleep.

**Night snapshot at 03:12:** asleep 1h 07m since 02:05. Last fed 1h 32m ago · 01:40 · L → R.

**Afternoon snapshot at 14:50:** a breast feed (Left → Right) logged at 14:32, so the Lamp shows "Last fed 18m ago · 14:32 · Left → Right". Nothing runs.

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
- `Entry` and the enums from §1, `@Serializable` (`Breast` is UI-only), plus `DiaperKind` (PEE, POOP, BOTH, PLAIN) derived from the flags, and `BreastSide.fromOrder` / `toggle` / `order` for the tick order.
- Extensions: `isInterval`, `isRunning`, `durationMillis(now)`.

**`data/`**
- Room `XaternityDatabase` and `EntryDao`. Storage is LWW-friendly: `upsert` only replaces a row when the incoming `updatedAtMillis` is ≥ the stored one.
- `EntryRepository` (interface) plus `RoomEntryRepository`:
  - `fun observeEntries(): Flow<List<Entry>>`: non-deleted entries.
  - `suspend fun logFeed(type: FeedType?, at: Long, side: BreastSide? = null): Entry`: a feed instant of any type, or an untyped feed (`null`); `side` is the optional side order of a BREAST feed. Deduplicated within 5 s (same type).
  - `suspend fun startSleep(at: Long): Result<Entry>`.
  - `suspend fun endSleep(id: String, at: Long): EndResult`.
  - `suspend fun logDiaper(at: Long, pee: Boolean, poop: Boolean, color: PoopColor?, texture: PoopTexture?): Entry`. Neither flag logs a plain change.
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
  - `lastFeed`, `lastDiaper`, `runningSleep`, `lastSleepEnd` (awake since);
  - `today: DayCounts(feeds, pee, poop, sleepMinutes)`;
  - `dayEntries(day)` for History.
- `fun lampState(summary): LampState`, one of `FeedIdle`, `SleepRunning`.

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
- `SampleData.NOW_1432`, `NOW_0312` and `NOW_1450`, plus the 14:32 breast feed variant (`scenarioWithRecentFeed`).

### `:app` and `:wear` rules

- **Screen structure:** each screen is a **stateless composable** (`HomeScreen(state: HomeUiState, actions: HomeActions)`, and so on) plus a ViewModel. Screenshot tests render the stateless screens with `SampleData` and a fixed clock, in EN and DA.
- **Strings:** app-specific strings live in the app module's `values/` + `values-da/`. Shared words come from core.
- **Graceful degradation:** without Play Services or a paired device, everything works locally. Sync is optional.
