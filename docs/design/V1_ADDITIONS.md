# v1 additions after the mockup review

> **Several babies and the backup:** [`BABIES_AND_BACKUP.md`](BABIES_AND_BACKUP.md) adds several babies (a current baby per device) and an optional account that backs the log up; it wins where it differs from this document (one baby, "phone ↔ watch only").

This document extends [`DESIGN_SPEC.md`](DESIGN_SPEC.md), which stays the source of truth for everything it covers: the Lamp, tokens, typography, icons, formatting grammar, night behaviour and accessibility. This file covers what the user asked for after approving the mockups:

1. **Feed types**: Breast, Formula or Food. The type is **optional**: a feed can be logged with just its time.
2. **A breast feed is one time, not a timer.** Like Formula and Food it records only its time (Now or −5m / −10m / −15m), plus an optional **side order**, ticked with two checkboxes, Left and Right, before it is logged. The **order of the ticks is the order recorded** (Left then Right = Left → Right). Nothing asks for a duration. (An earlier version timed breastfeeds with a start and an end; see "Compatibility" in §1.)
3. **Diaper Pee and Poop are independent toggles** (checkboxes). When Poop is on, the parent can also pick a **colour** and a **texture**. All of these are **optional**: a diaper with nothing ticked is a plain diaper change.
4. **The whole app is in English and Danish**, through Android string resources, with an in-app language choice.
5. **Statistics** (§8): averages per day of feeds, pee, poop and sleep over a chosen range, with a bar chart. It opens from History.

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
    val amountMl: Int? = null,      // FEED+FORMULA only; optional amount in millilitres (Room v3)
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
- **Formula amount:** optional. A formula feed logged at "No amount" has `amountMl == null`; a set amount is positive and always stored in millilitres, whatever unit it was picked in. Other feed types (and non-feeds) never carry an amount.
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
    - **Formula**: icon `feed` (the bottle), supporting line "Then the amount, if you like" ("Derefter mængden, hvis du vil").
    - **Food**: icon `food` (bowl and spoon).
  - A fourth row, **"Just the time"** ("Kun tidspunktet"): same size, but *outlined* (1 dp `outline`, no fill), icon `clock`, label in `on-surface`. It logs a feed with no type.
  - Tapping **Food** logs an instant at `stamp + offset`, closes the sheet and shows the receipt sheet "Food logged" with Undo and offset chips. **Just the time** does the same with "Feed logged" ("Måltid registreret").
  - Tapping **Breast** logs nothing yet: the same sheet moves to its **Breast step** (below). Tapping **Formula** likewise moves to its **Formula step**. The stamp and the selected chip are kept.
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
  - **"Log feed"** ("Registrér"): the primary button bottom-right (as Save in the Diaper sheet). It is always enabled, because the side is optional. It logs a breast feed instant at `stamp + offset` with the ticked order (none ticked: just the time), closes the sheet and shows the receipt sheet "Breast logged", "14:32 · Left → Right" (just the time with no side), with offset chips that move its time.
  - "Cancel" (or a scrim tap) closes the sheet and logs nothing.
- **Formula step** (the Feed sheet after Formula):
  - Title "Formula · 14:32" ("Modermælkserstatning · 14:32"), following the chips, which stay.
  - Section heading "Amount · Optional" ("Mængde · Valgfri"), then the amount in titleLarge FEED text ("120 ml"), or "No amount" ("Ingen mængde") in `on-surface-variant`.
  - A full-width **slider** (min 48 dp tall) in FEED colours: thumb and active track `feed-strong`, inactive track `feed-container`. Its start is **No amount**; each step adds **10 ml** up to **300 ml**, or **½ fl oz** up to **10 fl oz** where the region measures in fluid ounces (the US, Liberia, Myanmar: the app's region, else the device's, since the in-app languages carry none). Ounces are shown to the nearest half ("4 fl oz", "4.5 fl oz", Danish "4,5 fl oz") and stored as millilitres. TalkBack reads "Amount, 120 ml, slider".
  - **"Log feed"** ("Registrér") bottom-right, always enabled (the amount is optional). It logs a formula feed at `stamp + offset` with the amount (at No amount: just the time) and shows the receipt sheet "Formula logged", "14:32 · 120 ml" (just the time without an amount).
  - The amount shows in History's supporting line ("12:22 · 120 ml"; TalkBack "Formula, 12:22, 120 ml") and on the watch's Today list. The watch logs formula without an amount.
- **Running sleep and a feed:** logging any feed (a breast feed included) while a sleep runs shows the receipt with the "End sleep" chip. The sleep keeps the Lamp.
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
5. **Save:** a primary button at the bottom right of the sheet, in the thumb zone, labelled "Save". It is **always enabled**: Pee, Poop, colour and texture are all optional. Saving logs the entry at `stamp + offset`, closes the sheet and shows the receipt sheet ("Pee logged" / "Poop logged" / "Pee + poop logged", or "Diaper logged" for a plain change).

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
  - Every feed shows one time block. Breast adds "Side · Optional": the **same Left / Right checkboxes** as the Feed sheet's Breast step (order badges, tick order = side) and the summary line. Formula adds "Amount · Optional": the **same amount slider** as the Feed sheet's Formula step. Switching to another type drops the side or amount; switching back restores the original's. There is no end time and no duration.
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
| Amount (formula) | Amount | Mængde |
| No amount | No amount | Ingen mængde |
| Then the amount, if you like | Then the amount, if you like | Derefter mængden, hvis du vil |
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
| Statistics (the screen, History's chart button) | Statistics | Statistik |
| Last 7 days / Last 30 days / Last 90 days | Last 7 days / Last 30 days / Last 90 days | Seneste 7 dage / Seneste 30 dage / Seneste 90 dage |
| Average per day (Statistics subtitle) | Average per day | Gennemsnit pr. dag |

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

**Package root:** `com.ffmathy.pooparazzi`. Both apps use `applicationId "com.ffmathy.pooparazzi"`, which the Wearable Data Layer requires.

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
- Room `PooparazziDatabase` and `EntryDao`. Storage is LWW-friendly: `upsert` only replaces a row when the incoming `updatedAtMillis` is ≥ the stored one.
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
- `fun dailyCounts(entries, from, to, now, zone): List<DayCounts>`: History's counts for every day of a range, in one pass.
- Statistics (§8): `fun statsReport(entries, now, zone, range): StatsReport?` (the chosen range's window; null only when nothing is logged), `StatsReport.chart(metric): StatsChart`, `binDays(dayCount)`, and `StatsRange.clamped` / `normalised` for chosen dates.

**`format/`**
- `DurationFormatter` (units injected; resource-backed factory `DurationFormatter.from(context)`).
- `ClockFormatter`.
- `LabelFormatter`, which maps enums to string resources: feed type, side, poop colour and texture, and diaper kind labels.
- Composable `rememberFormatters()`.
- `formatTenths(tenths, locale)`: an average with one decimal and the language's separator ("6.7" / "6,7").

**`ui/`** (Compose; no Material dependency)
- `tokens`: Night, Day and Watch colour sets plus event colours, including `feed-container-lit` and `feed-track`; poop swatches.
- `icons`: `XIcons.*` `ImageVector`s from the DESIGN_SPEC §6 path data, plus `breast`, `food`, `sideLeft`, `sideRight`, `sideLeftRight`, `sideRightLeft`, `checkbox`, `checkboxChecked`, `info`, `chart`.
- `type`: the `FontFamily`s with variable axes.
- `lamp`: `Lamp(state, sizeDp, theme)`, Canvas drawing shared by phone and watch.

**`res/`**
- Shared strings (EN + DA): all glossary words, units, relative phrases, spoken plurals, labels for poop colours and textures.
- The fonts.

**`sample/`**
- `SampleData.scenario(zone)` builds §6.
- `SampleData.NOW_1432`, `NOW_0312` and `NOW_1450`, plus the 14:32 breast feed variant (`scenarioWithRecentFeed`).
- `SampleData.history(days)`: weeks of deterministic logging that end in §6, for Statistics; `historyWithGaps()` leaves out the days in `GAP_DAYS` (Tue 13 and Wed 14 Oct), days when nothing was logged.

### `:app` and `:wear` rules

- **Screen structure:** each screen is a **stateless composable** (`HomeScreen(state: HomeUiState, actions: HomeActions)`, and so on) plus a ViewModel. Screenshot tests render the stateless screens with `SampleData` and a fixed clock, in EN and DA.
- **Strings:** app-specific strings live in the app module's `values/` + `values-da/`. Shared words come from core.
- **Graceful degradation:** without Play Services or a paired device, everything works locally. Sync is optional.

---

## 8. Statistics (P8)

**Placement.** Statistics is a destination over History. The `chart` icon button at the end of History's top bar opens it (TalkBack: "Statistics"); Back, in the top bar or from the system, returns to History with its day kept, and a second tap during the transition is dropped. There is no deep link, widget or Home entry point; a widget or notification link still returns to Home. History's list, its single scrollable node and its single "Back" are unchanged: History stays "one day", Statistics is "many days".

**What it shows.** Averages per day of feeds · pee · poop · sleep (§4's order) over **one range**, chosen with the range chip: the **last 30 days** by default. Only that range is shown; there is no second column to compare with. The rows are a single-select accordion: the open row shows a bar chart of the range. Poop is open on every new visit.

### 8.1 Ranges

Only whole days count: every window ends **yesterday**, and the **first logged day** is left out as probably partial, so the first countable day (`firstDay`) is the day after it. The numbers therefore change only at midnight or when an entry changes, never as today goes on. Until there is a whole day (the first entry is from today or yesterday), the report counts **today so far** instead, from the first logged day through today (`soFar`), so even a single entry shows; the footnote then says "No whole day has been logged yet, so these averages count today so far. …". With nothing logged at all: "Statistics start as soon as something is logged."

| Range | Days | Reference (`history()` at Mon 26 Oct 2026) |
|---|---|---|
| Last 7 days | yesterday − 6 … yesterday | Mon 19 – Sun 25 Oct |
| Last 30 days (default) | yesterday − 29 … yesterday | Sat 26 Sep – Sun 25 Oct |
| Last 90 days | yesterday − 89 … yesterday | Tue 28 Jul – Sun 25 Oct ("33 of 90 days logged") |
| All days | `firstDay` … yesterday | Wed 23 Sep – Sun 25 Oct |
| Chosen dates | picked in the date range dialog | e.g. 5 – 18 Oct |

- **Presets are nominal.** "Last 30 days" is always 30 days, even for a new user: days before `firstDay` are drawn empty and counted in "n of N days logged". Presets are relative (they move at midnight); chosen dates are absolute.
- **The range chip** (like the edit sheet's day chip) shows "Last 7 days", "Last 30 days", "Last 90 days", "All days" or "Chosen dates". Its menu offers the presets and "Choose dates…", which opens the M3 `DateRangePicker` in a `DatePickerDialog`: selectable days are `firstDay` … yesterday, a start alone is a one-day range, and Cancel changes nothing. Its day, weekday and year labels use dataMedium (Google Sans Flex, tabular figures) instead of the default bodyLarge, so the dialog keeps the digit rule (DESIGN_SPEC §5.7).
- **Chosen dates are cut** to `firstDay` … yesterday on every build (entirely outside: the last 30 days). On OK, dates equal to a preset's become that preset.
- **Saved state:** the open row and the range survive rotation (view model) and process death (`statistics_metric`, `statistics_range`, and `statistics_from` / `statistics_to` as epoch days for chosen dates). Anything unknown restores as the last 30 days with Poop open.

### 8.2 Which days count

| Day status | Rule |
|---|---|
| Before logging | before `firstDay`: not counted, not a gap, drawn empty |
| Nothing logged | no entry of any kind starts on the day: left out of every average, drawn as a gap |
| Counted (a logged day) | otherwise |

- **Per kind,** a counted day enters an average only if an entry of that kind **starts** on it: a feed for Feeds; any diaper, plain changes included, for Pee and Poop; a sleep for Sleep. A forgotten kind is left out of that kind only; a forgotten running sleep, or the tail of last night's sleep, never enters an average.
- **"Forgot to log" is never 0.** A real zero is a counted day with diapers logged and no poop: averaged in, and drawn as a 2 dp mark.
- **Daily values** are History's counts for the day (`dailyCounts`): sleep is the clock minutes inside the day, including the part of a sleep that began the evening before. DST days count as one day with their real minutes.
- **Averages** use integers and round half up: counts to tenths, `(20·S + n) / (2·n)` ("6.7" / "6,7" through `formatTenths`), sleep to whole minutes, `(2·S + n) / (2·n)` ("14h 01m" / "14t 01m"). No counted day shows "–".
- **Coverage:** a range with days with nothing logged (or before logging) adds "3 of 30 days logged" under its dates.

### 8.3 Layout (390 × 844, Night and Day alike)

| Band | Content |
|---|---|
| Top bar | Back; "Statistics" titleLarge (a heading); under it "Average per day" labelMedium onSurfaceVariant (not in the empty state). No action at the end |
| Period header | the range chip, inset 16 like the rows' content, with its dates under it ("26 Sep – 25 Oct", dataSmall onSurfaceVariant) and a coverage line when it applies |
| Rows | 4 grouped cards (gap 2, r 20 / r 6, surfaceContainer, padding 16 / 12 / 8): the kind's glyph 20 in its strong colour + name labelLarge + `chevronDown` 20 when closed; then the range's average (displaySmall), shrinking to 12 sp rather than clip |
| Open row | adds its chart; the card grows with `animateContentSize` (left out with reduced motion) |
| Footnote | "Averages count whole days up to yesterday. …" bodySmall onSurfaceVariant |

Everything scrolls in one column (the menu and the dialog are popups). At large font sizes labels and dates wrap; the numbers and the range chip's label stay on one line and shrink.

### 8.4 Chart

- **Block:** the scale label (labelSmall: the top count "5", or "16h 00m" for sleep) over a decorative 1 dp outlineVariant hairline; the plot (full width × 112 dp, a `Canvas`); the first and last dates; the legend; "Days with no diapers logged are left out." (per kind) when the window has gaps. With nothing counted the block is "Nothing logged on these days."
- **Bars:** one per day up to 62 days; longer windows use 7-day bars cut from the end (14-, 21-day … beyond 434 days), so there are never more than 62 bars and the newest bar is exactly the last 7 days. A bar's value is the average per counted day. Bars are 60 % of their slot (2–12 dp wide, at least 3 tall), r 3, in the kind's strong colour; a gap draws nothing.
- **The range's average** is a dashed 2 dp onSurfaceVariant line across the plot, on a 4 dp halo in the card colour so it stays visible over the bars. Nothing else is marked: no second range is drawn.
- **Scale:** counts up to the next whole number (at least 1), sleep up to the next whole hour.
- **Legend:** the range's name (dashed swatch), "Each bar is 7 days" for binned bars; in a row, or stacked from 130 % font.
- **Not by colour alone:** the dashed line, legend words, a zero mark versus no mark, one series per chart named by the row. Bars reach 3 : 1 on the card in both themes.

### 8.5 TalkBack

- **Range chip:** a button, click label "Change range": "Last 30 days, 26 September to 25 October" or "Chosen dates, 5 October to 18 October", with ", 3 of 30 days logged" when it applies.
- **Rows** are radio buttons in a selectable group: "Poop. Last 30 days: 1.8 a day."; under another range "Feeds. Chosen dates: 7.1 a day."; durations in words ("14 hours 1 minute a day"); "not logged" instead of "–".
- **Chart:** one node: "Poop per day, 26 September to 25 October. Highest 5 on Wednesday 7 October. Lowest 0 on Friday 23 October.", for bars of several days "…, each bar 7 days. Highest 3.3 from 11 May to 17 May. …" (the oldest bar can be a single day, and is then said like a day: "Lowest 1.0 on Sunday 23 August."), then "2 days with no diapers logged are left out." and "Days before 23 October are left out." when they apply.
- Spoken dates use full month names; the year is added when a range spans two years or is not in the current year.

### 8.6 Known limit

A partly logged day (the afternoon forgotten) cannot be told apart from a quiet day: it counts, and shows as a low bar and as the lowest value in the TalkBack summary.

## 9. Forecast: what comes next

Home's buttons say what the last week expects next, as a small second line under each label (labelLarge, the
button's onContainer at 78 %, one line that shrinks to fit rather than wrap):

| Button | Second line | TalkBack |
|---|---|---|
| Feed | "in 1h 05m" / "om 1t 05m" | "next feed in about 1 hour 5 minutes, around 15:35" |
| Diaper | "poop in 3h 10m" / "bæ om 3t 10m" (poop only; pee is not predicted) | "next poop in about …, around …" |
| Start sleep | "in 55m" / "om 55m" | "sleeps again in about 55 minutes, around 15:23" |
| Woke up (a sleep runs) | "in 2h 15m" / "om 2t 15m": the expected wake-up | "wakes up in about 2 hours 15 minutes, around 05:27" |

Times are rounded up to 5 minutes (an estimate, not a promise); under 5 minutes, or past every comparable stretch, the
line reads "soon" / "snart" ("poop soon" / "bæ snart"). A button the week cannot predict has no second line.

**The other surfaces** (one more line each where there is room):

| Surface | Feed | Diaper | Sleep, awake | Sleep, asleep |
|---|---|---|---|---|
| 4×2 widget (and the cover screen), 11–13 sp; with the line, the face tightens to icon 20 + gap 2 | under the Feed label: "Next ~15:35" / "Næste ~15:35" | under Diaper, when poop is predictable: "Poop ~18:00" / "Bæ ~18:00" | under the status: "Next sleep ~15:25" / "Sover igen ~15:25" | "Wakes ~05:25" / "Vågner ~05:25" |
| Watch Main, scrolled | idle: a "Next feed" card first, "in 1h 05m · ~15:35" (opens the last feed); asleep: the Last fed card's third line "Next feed in 2h 45m" | none | Awake card, third line: "Next sleep in 55m" / "Sover igen om 55m" | Adjust start's secondary line: "Wakes ~05:25" (small watches: under "Asleep since 02:05") |

The widget shows clock times only (it does not tick), rounded to 5 minutes, and refreshes once an estimate has passed.
Feed and Diaper keep the same face: when either has a line, both reserve it, so the icons stay aligned; the compact
(two-line label) face has no room and shows none. The watch's Adjust start line is a clock time too, so the Danish fits
on one line. TalkBack: "Log feed, next feed expected around 15:35", "expected to wake around 05:25", "Next sleep in 55
minutes". The 3×1 widget, the tile, the complications and the watch's first screen (its 60 dp Feed and Diaper buttons
are full) have no room and show none.

**The estimate** (`forecast` in `:core`, `domain/Forecast.kt`). There is no validated model for predicting one baby's
sleep or feeds (wake-window charts are rules of thumb, and babies differ widely), so the app only looks at this baby's
own last 7 days, which is recent enough to follow a growing baby:

1. Stretches of the same kind: sleep lengths (asleep), wake windows from a sleep's end to the next sleep (awake), gaps
   between feeds, gaps between poops. Gaps under 10 minutes (one feed logged per side) and feed or wake gaps over
   12 hours (a forgotten log) are dropped.
2. Same time of day: night sleeps run longer than naps and evening feeds cluster, so only stretches that began within
   2 hours of the current one's start count, widened to 4 hours, then the whole day, until there are 5 from at least
   3 different days. Poop follows no clock, so all poop gaps count.
3. Too few (under 5, or from fewer than 3 days), too spread out (interquartile range over 1.5 × the median), or
   the current stretch already twice the longest of them (logging has stopped): no forecast.
4. The median of the stretches that lasted **longer than the current one has so far**, added to its start. So a long
   nap pushes the expected wake-up later instead of leaving it in the past; when none lasted longer, it is "soon".

In the sample week (§6 history) poop comes about once a day at random hours, so Diaper shows no forecast.
