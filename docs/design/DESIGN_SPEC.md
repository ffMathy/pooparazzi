# Pooparazzi — Design Specification

Design: Night and Day themes (final) · Spec date: Mon 26 Oct 2026 · Status: **approved**; later changes are in [`V1_ADDITIONS.md`](V1_ADDITIONS.md)

> **Several babies and the backup:** [`BABIES_AND_BACKUP.md`](BABIES_AND_BACKUP.md) adds several babies (a current baby per device) and an optional account that backs the log up; it wins where it differs from this document (one baby, "phone ↔ watch only").

This document defines the product as designed in the approved mockups. It is written for the developers who will build:

- the **Android phone app** (Jetpack Compose, Material 3 Expressive), its **Glance home-screen widgets** and its **Live Update** notification;
- the **Wear OS app** (Compose for Wear OS, Material 3), its **Tile**, its **complications** and its **Ongoing Activity / Live Update**.

Conventions

- All sizes are **dp**; type sizes are **sp**. Phone reference device: 390 × 844 dp. Watch reference device: 228 dp round. Small-watch rules (< 225 dp, e.g. 192 dp) are in §3.5.10.
- Reference coordinates such as "y 96–360" are for the reference device only. Lay out from window insets (§10.3), not from fixed bands.
- Quoted strings are final UI copy. Copy them exactly, including the middle dot `·` (U+00B7), the en dash `–` (U+2013) in time ranges, the minus sign `−` (U+2212) in offset chips such as "−5m", and the ellipsis `…` (U+2026).
- Sample values (Noa, 14:32, "2h 10m"…) come from the reference scenario in Appendix A. Use it for previews and screenshot tests.
- The mockup set also contains two alternative visual directions ("Big Buttons", "Soft Paper") shown for comparison only. They were not chosen and are not specified here.

---

## Contents

1. Product scope and core requirements
2. Concept: the Night theme and the Lamp
3. Navigation model and screen inventory
4. Event and data model
5. Design tokens
6. Icons
7. Formatting grammar
8. Night behaviour
9. Accessibility
10. Implementation notes
11. Key design decisions
12. Open questions

Appendix A: Reference scenario · Appendix B: Not designed or deferred

---

## 1. Product scope and core requirements

**Core requirement (the user's words):** *"register start and stop actions for sleeping to track start and stop times, and trigger time for feeding and diapers (pooping/peeing)"*.

| Event | Shape | Captured |
|---|---|---|
| Sleep | interval | start, stop (stop is empty while the sleep runs) |
| Feed | instant | the time of the feed (its start) |
| Diaper | instant | the time, plus a **pee** flag and a **poop** flag ("Both" = both flags) |

**In scope (v1)**

| Feature | Surfaces |
|---|---|
| One-tap logging of Feed and Sleep start/stop; Diaper in 2 taps (Diaper → Pee / Poop / Both) | Phone Home, watch Main |
| Feed and Diaper open the app's own sheets/screens (offset, type, side order, pee/poop, colour, texture); sleep toggles in one tap | Phone widgets (4×2, 3×1, Samsung cover screen), watch Tile |
| Status at a glance: time since last feed, awake/asleep duration, last diaper | Phone Home, watch Main, Tile, complications, widget, Live Update |
| Undo after every log | Phone receipt sheet (logs with no sheet of their own; a Feed or Diaper sheet log is undone from its since-row's edit sheet), watch Logged screen, widget receipt, Tile title |
| Backdating at log time and later editing | Feed and Diaper sheet offsets, phone receipt-sheet offsets, phone edit sheet |
| History: day ribbon, counts, list | Phone History (the watch has none) |
| Phone-to-watch sync status | Phone top bar, watch Logged and Main list footer |
| Automatic Night appearance (dark, low-blue, "lights-down" while asleep) | Phone, widget; the watch is always dark |

**Out of scope (v1)**

- **No caregiver or partner features**: no "logged by", no provenance, no merge chips, no partner notifications. This is open question 1 (§12).
- No feeding details (side, amount, duration). Open question 2.
- No "Recently deleted" screen (deletes are soft, with Undo).
- No sounds, ever.
- QS sleep tile (optional, v1.1). Optional accelerators ("Log earlier" on long-press, "Hold to log") only if testing asks (§10.13).
- No dynamic (wallpaper / watch-face) colour anywhere.

---

## 2. Concept: the Night theme and the Lamp

**One line:** a calm, dim app for the night that dims further while the baby sleeps.

### 2.1 Principles

1. **One signature element, the Lamp,** holds the one number that matters right now: time since the last feed when idle, sleep duration while a sleep runs.
2. **Colour means an event and nothing else.** FEED is amber, SLEEP moon-blue, DIAPER dew-teal (PEE the light end of the teal ramp, POOP the deep end, BOTH two-tone). All chrome (buttons such as Save, the FAB, selected chips, Undo, the History now-line) uses warm neutral roles.
3. **Warm, low-blue neutrals.** Night text is parchment `#E8D8C3`, never pure white. No large bright fill at night (every Night fill has L* ≤ 25).
4. **Lights-down.** While a sleep runs at night, the top bar and the glow dim. Every control stays where it is and keeps its fill. Nothing bright appears.
5. **Each fact appears once per screen.** Time-since lives in the since-rows, never on the action buttons. Receipt titles are time-free; the phone receipt sheet shows the effective time on its own line.
6. **Nothing ticks.** No seconds anywhere. The ember steps once per minute. No system chronometer is used.
7. **Fixed palette.** No dynamic colour on phone, watch, tile or widget, so a wallpaper hue cannot pass for an event colour.

### 2.2 Lamp anatomy

| Part | Meaning | Phone Night | Phone Day | Watch |
|---|---|---|---|---|
| **Body** | the event of the hero fact, in that event's colour | static radial **glow** | solid **disc** (the "shade") | glow **only while a sleep runs**; no Lamp when idle |
| **Orbit** (track + ember + tail) | **a running sleep, nothing else** | yes | yes | yes |

- Orbit: one lap per hour. The ember starts at 12 o'clock at the sleep's start and moves clockwise. **Ember angle = (elapsed minutes mod 60) × 6°** (e.g. 1h 07m → 42°).
- The ring is **never** used for progress, countdowns or idle states. The watch Undo countdown is a separate neutral bezel arc (§3.5.6).
- No breathing. The glow is static. The ember steps once per minute with a 600 ms ease and is otherwise still.
- The phone keeps a subtle static idle glow at night. It gives the idle Lamp a form without a ring. Its core is L* 10 and every text pair on it passes contrast. Day uses a disc instead, because a glow on paper reads as a stain.
- Lamp text is real text laid out over the canvas, never drawn into it. **The Lamp text (label, hero and sub-line) is one tap target**, like a since-row: idle it opens the edit sheet of the last feed (a 16 dp `chevronRight` in outline after the label marks it); while a sleep runs it opens the edit sheet of the running sleep (the same chevron marks it). Neither writes anything until Save. Before the first feed it is not a target.

### 2.3 Lamp states

| State | Body | Orbit | Label (icon + text) | Hero | Sub-line |
|---|---|---|---|---|---|
| Phone Night idle | FEED glow, core alpha 0.55 | none | `feed` FEED strong + "Last fed" FEED text | "2h 10m" | "ago · 12:22" |
| Phone Night running (lights-down) | SLEEP glow, core alpha 0.45 | track `#2E3660`, ember + tail `#7C88C4` | `sleep` SLEEP strong + "Asleep" SLEEP text | "1h 07m" | "since 02:05" |
| Phone Day idle | FEED disc `#FFE1C4` | none | as Night idle (Day tokens) | as above | as above |
| Phone Day running | SLEEP disc `#E1E4F8` | track `#6A73B3`, ember + tail `#37418A`, ember cut out by a 2 dp ring of paper `#FBF6F0` | as Night running (Day tokens) | as above | as above |
| Watch | **no Lamp**: Main mirrors the Tile (§3.5.1) | — | | | |

Under one minute: the phone hero shows "just now" (heroWord) with the sub-line "at 14:32" when idle. While running it keeps "since …". The watch shows "just now" (§3.5.1, §3.5.2).

### 2.4 Phone Lamp geometry

| Item | Value |
|---|---|
| Lamp | Ø264 at the reference size (centre x 195, y 228 on the reference device; spans y 96–360). Size is continuous: §10.3 |
| Glow | radial gradient centred on the Lamp, radius 180 (it extends past the Lamp to Ø360). Stops: 0 → alpha *a*, 0.6 → 0.6·*a*, 1 → 0. Colour = event **container** |
| Day disc | radius 132, event **container** |
| Ring | centreline r 129, stroke 6 (126–132) |
| Ember | Ø10 on the centreline (Day: plus a Ø14 paper-coloured cut-out under it) |
| Tail | 8 arcs of 6° (48° in all) behind the ember, stroke 6, butt caps, opacities from the farthest: 0.06, 0.12, 0.20, 0.30, 0.42, 0.56, 0.72, 0.88 |
| Tail truncation | for elapsed < 8 min show only the newest *n* = elapsed segments; under 1 min, no tail |
| Reduced motion | the ember jumps (no transition), no tail |

**Content stack** (centred horizontally, and vertically on the Lamp centre). Offsets are from the top of the Ø264 Lamp:

| Element | Idle | Running | Style |
|---|---|---|---|
| Label row: icon 20 + 8 + text (+ 8 + `chevronRight` 16 outline when it opens an entry) | 65–85 | 65–85 | labelLarge; colours per §2.3 |
| Hero | 93–173 | 93–173 | heroNumeral + heroUnit in onSurface; "just now" in heroWord, centred in the 80 box |
| Sub-line | 177–199 | 177–199 | dataMedium, onSurfaceVariant |

**Hero composition**: number spans (heroNumeral 76/80 sp, weight 600, letter-spacing −0.005em) and unit spans (heroUnit 34/40 sp, weight 500), baseline-aligned. 4 dp after each number, 12 dp between groups, and no space characters. Measured: "2h 10m" = 213.8 dp, which fits the 223 dp usable chord. "10h 45m" needs autosize to 60/27 (209.7 dp). Autosize range 56–76 sp.

### 2.5 Watch Lamp geometry

_Not used on Main any more (it mirrors the Tile, §3.5.1); kept for reference._

| Item | Value |
|---|---|
| Centre | x 114, y 88 (reference 228 dp screen) |
| Ring | centreline r 60.5, stroke 5 (58–63), outer Ø126 |
| Ember | Ø8 on the centreline |
| Tail | as phone: 8 × 6° arcs, stroke 5, same opacity ramp |
| Glow | radius 95, SLEEP container, core alpha 0.60, same stops |
| Vertical budget | Lamp extent including the ember: y 23.5–152.5. The Medium EdgeButton starts at y 155, so the ember stays ≥ 2.5 dp above it at every minute (worst case minute 30) |
| Ambient | ring stroke 2 solid `#7C88C4`; static dot Ø6 at the current angle; no glow, no tail, no transition |

**Content stack**: label row y 51–67 (`sleep` 14 dp `#7C88C4` + 4 + "Asleep" titleSmall `#AEB6E6`); numeral y 71–105 ("1h 07m" lampNumeral `#E8D8C3`); sub-line y 108–124 ("since 02:05" bodyMedium `#B8A48E`). Ambient: all `#B8A48E`.

**Fit**: numeral units are 17 sp with 2 dp after each number and 6 dp between groups. "1h 07m" at 34 sp is at most 102.3 dp wide, inside the 107.8 dp ember-clear chord. Autosize down to 28 sp ("10h 45m" = 101.4 dp against a 109.5 dp chord). On screens < 225 dp the floor is 24 sp (§3.5.10). The Lamp cannot grow larger: the Medium EdgeButton (70 dp) and TimeText leave about 130 dp of height.

---

## 3. Navigation model and screen inventory

### 3.1 Navigation

**Phone**

```
Home ──Diaper / Feed───────▶ Diaper / Feed sheet ──Save / a type──▶ Home (no receipt: the sheet set the time)
 │ ├─Start sleep / Woke up──▶ Receipt sheet ──Done──▶ Home
 │ ├─since-row 1 / 2──────▶ Edit sheet (latest entry of that row)
 │ ├─Lamp text──────────▶ Edit sheet (last feed, or the running sleep)
 │ ├─Receipt sheet ──Edit──▶ Edit sheet (that entry) · ──Undo──▶ Home + "Undone" notice
 │ ├─History pill / Today grid ──▶ History ──row──▶ Edit sheet
 │ │                                        ├─Statistics icon──▶ Statistics (P8) ──range chip──▶ menu / date range dialog
 │ │                                        └─"Add entry" FAB──▶ Edit sheet ("Add past entry" mode)
 │ └─Settings ──────────────▶ Settings (not designed; Appendix B)
Widget buttons ──▶ log in place (no app launch) · Widget background ──▶ Home with the receipt for the last widget log
Live Update ──"Woke up" action──▶ ends the sleep
```

**Watch**

```
Main (idle | asleep) ──Diaper──▶ Diaper picker ──Earlier──▶ Earlier ──choice──▶ Diaper picker
 │                                   └─Pee/Poop/Both──▶ Logged ──Undo──▶ Undone ──▶ Main
 ├─Feed──▶ Logged                         └─timeout / tap outside Undo──▶ Main
 ├─Start sleep / Woke up (EdgeButton) ──▶ Main (asleep) / Logged
 └─baby button (two or more babies) ──▶ Baby picker ──▶ Main
Tile buttons ──▶ log in place · Tile background during the 10 s feedback ──▶ Logged (Undo active at once)
Complication tap ──▶ the matching app screen (never logs)
Ongoing Activity tap ──▶ the sleep screen (Main, asleep)
```

Swipe right (system swipe-to-dismiss) cancels the Diaper picker and Earlier.

### 3.2 Inventory

| ID | Surface | Screen | Purpose |
|---|---|---|---|
| P1 | Phone | Home | Log Feed, Diaper and Sleep. Show the top fact (Lamp), two since-rows, today's counts, receipts with Undo |
| P2 | Phone | Diaper sheet | Pee / Poop / Both, with the time stamped on the first tap and an optional backdate |
| P3 | Phone | History | Day ribbon, counts, a tappable list of the day's entries, Add entry |
| P4 | Phone | Edit sheet | Edit or delete an entry, or add a past one |
| P5 | Phone | Home-screen widgets 4×2, 3×1 | Feed and Diaper open the app's sheets; the sleep toggle logs in one tap |
| P6 | Phone | Live Update (running sleep) | Status chip and an ongoing notification with "Woke up" |
| P7 | Phone | Splash | System splash, dark at night and light by day |
| P8 | Phone | Statistics | Average feeds, pee, poop and sleep per day over a chosen range (the last 7, 30 or 90 days, all days or chosen dates; default the last 30 days), with a bar chart of one kind |
| W1 | Watch | Main, idle | As the Tile: name, Feed and Diaper rows with time since, "Start sleep"; History below |
| W2 | Watch | Main, asleep | As the Tile: "Asleep 1h 07m", Feed and Diaper rows, "Woke up"; History below |
| W3 | Watch | Diaper picker | Pee / Poop / Both; Earlier |
| W4 | Watch | Earlier | Backdate a diaper (−5m / −10m / −15m) |
| W6 | Watch | Logged | Confirmation plus a 5 s Undo |
| W7 | Watch | Undone | Undo confirmation (1.5 s) |
| W9 | Watch | Tile | One-tap logging and status from the tile carousel |
| W10 | Watch | Complications | Time since feed, diaper, sleep on any watch face |
| W11 | Watch | Ongoing Activity / Live Update | Running-sleep indicator |

### 3.3 Phone app

#### 3.3.1 P1 Home

**Layout bands** (reference 390 × 844, gutter 20). Bands and positions are identical for idle and running, Night and Day.

| Band | Height | Reference y | Content |
|---|---|---|---|
| Status-bar inset | 24 | 0–24 | background only |
| Top bar | 64 | 24–88 | "Noa", status icons, History pill, Settings |
| gap | 8 | | |
| Lamp | 248 | 96–344 | §2.4 (it gives up the room the two-line since-rows take) |
| gap | 4 | | |
| Since-row 1 | 56 | 348–404 | |
| Since-row 2 | 56 | 404–460 | |
| gap | 4 | | |
| Whisper slot | 128 (fixed min) | 464–592 | Today grid, or a 2 s notice card, or a one-off prompt |
| gap | 12 | | |
| Feed · Diaper | 96 | 604–700 | two buttons 169 wide, gap 12 |
| gap | 12 | | |
| Sleep toggle | 96 | 712–808 | 350 wide |
| gap | 12 | | |
| Navigation inset | 24 | 820–844 | nothing interactive |

**Top bar**
- "Noa" (the baby's name): titleLarge, onSurface, vertically centred on the same line as the History pill and Settings (32 dp from the top of the bar), whether or not status icons follow; the icons hang below it and the bar grows to fit them.
- Status icons under the name (§4.5), directly below its 40 dp line: 20 dp glyphs 12 apart in onSurfaceVariant, no visible text — the watch (hidden without one), then the cloud while an account is signed in. TalkBack reads each icon's sentence (§4.5). An icon in its **error** state is in error colour and a 48 dp button (retry) that starts at its glyph and reaches up over the name; it may overlap only non-interactive content. When the first icon is a button the next glyph starts after it (28 dp gap), so two buttons never overlap. With two or more babies (and in History, under the title) the icons are their own row, 48 dp tall in an error state.
- History pill: 48 target / 40 visual, r 20, fill surfaceContainerHigh, padding start 12 / end 14, `history` 18 onSurfaceVariant + 6 + "History" labelLarge onSurface. Opens History.
- Settings: 48 × 48 icon button, `settings` 24 onSurfaceVariant, content description "Settings".

**Since-rows**: each is a full-width button 350 × 56 (min), r 12, padding 4 16, gap 12. Content: icon 40 (spanning both lines), then two lines — the title (titleMedium onSurface) over the detail (dataMedium onSurfaceVariant: elapsed time, then " · "-separated facts) — and `chevronRight` 16 in outline at the end. Each line is one line, ellipsized; from 130 % font scale each may take two. Text slot = **238 dp**.

| State | Row 1 | Row 2 (kind-first) |
|---|---|---|
| Idle | `awake` (SLEEP strong): "Awake" / "1h 05m · since 13:27" | kind icon: "Pee" / "48m ago · 13:44" |
| Sleep running | `feed` (FEED strong): "Last fed" / "1h 32m ago · 01:40 · L → R" | "Pee" / "1h 20m ago · 01:52" |
| Diaper just logged | (idle row 1) | "Pee" / "just now · 14:32" |

- Row 2 uses the **kind icon** (`pee` PEE strong, `poop` POOP strong, two-tone `both`), never the generic `diaper`. Its title is the kind word: Pee / Poop / Both.
- Tap → the edit sheet for that row's latest entry (row 1: the last sleep, or the last feed while asleep; row 2: the last diaper). This is the one-tap correction path.

**Whisper slot: Today grid** (default). The whole block is a link to History (r 20, padding 14 16). Header "Today" labelMedium onSurfaceVariant + `chevronRight` 16 outline. Below it, a 4-column equal grid (gap 8): number titleMedium onSurface over caption labelMedium onSurfaceVariant; the numbers share one baseline across the row, and so do the captions. 14:32: "5" feeds, "4" pee, "2" poop, "7h 57m" sleep. 03:12: "1" feed, "1" pee, "0" poop, "2h 42m" sleep (the caption is singular "feed" for 1). In lights-down the numbers are onSurfaceVariant.

**Whisper slot: notice card** (replaces the grid, same slot, for 2 s). Card 350 × ≥120, r 28, surfaceContainerHighest, no border or shadow. Polite live region. Fades in over 150 ms with no slide. Row 1 (48): icon 20 + 8 + title titleMedium onSurface; for a guard, row 2 is the guard line in dataMedium SLEEP text; for a repeat log, "Already logged at 14:32". No buttons and no drain line.

| Notice | Icon | Title | Row 2 |
|---|---|---|---|
| Guard, asleep (§4.4) | `sleep` SLEEP strong | "Asleep" | "Already asleep since 14:32" |
| Guard, awake | `awake` SLEEP strong | "Awake" | "Already awake since 14:40" |
| Undone (after Undo in a receipt) | `undo` onSurfaceVariant | "Undone" | — |
| Repeat log (§4.4) | the entry's icon | "Formula" / "Pee" (the logged kind) | "Already logged at 14:32" dataMedium onSurfaceVariant |

**Receipt sheet** (after a log that had no sheet of its own: Start sleep, Woke up, End sleep, a widget log opened in the app, and a delete in the edit sheet). A modal sheet like the logging sheets (§3.3.2). A Feed or Diaper sheet already picks the time, so saving it just closes it: no receipt follows, and the since-row (tap → edit sheet, with Delete) is the correction path. **No timeout**: it stays until Done, Undo, Edit or a dismiss (scrim, back, swipe), and the log stays as shown.
- Header (≥56): event icon 24 (event strong) + 12 · title titleLarge onSurface (time-free, as below) over the **effective time** in dataMedium onSurfaceVariant, joined with the sub-line ("14:27", "14:32 · Left → Right", "14:32 · Slept 1h 07m"); after a nudge "was 14:32" follows the time ("14:22 · was 14:32 · Left → Right"); the time line follows every chip tap and is a polite live region · Undo text button at the end (48 tall, r 24, `undo` 18 + 6 + "Undo" labelLarge, primary). The pane title is "Formula logged · 14:27".
- Nudge chips "−15m" "−10m" "−5m" "+5m" (one row, equal widths, never selected) when the receipt moves a time.
- "End sleep" chip (`wake` 18, hugs its content) for a feed or diaper logged while a sleep still runs.
- Bottom row: outlined "Edit" (56 tall, r 28, `edit` 20 + 8 + labelLarge) at the start when there is an entry to edit, and "Done" (the Save button style, `check`) at the end.
- Everything ignores taps for 500 ms when the sheet appears (the picker cooldown, §5.9), so a double tap on a Feed type cannot hit Undo.

| Receipt | Icon | Title | Time line | Chips move |
|---|---|---|---|---|
| Feed | `feed` (or the type's icon) | "Formula logged" / "Breast logged" / "Feed logged" | "14:27", breast adds " · Left → Right" | the time |
| Pee / Poop / Both / plain | `pee` / `poop` / `both` / `diaper` | "Pee logged" / "Poop logged" / "Pee + poop logged" / "Diaper logged" | "14:27" | the time |
| Sleep start | `sleep` | "Sleep started" | "14:32" | the start |
| Woke up | `wake` | "Woke up" | "14:32 · Slept 1h 07m" | the end |
| Sleep cancelled (stopped < 60 s after start) | `wake` | "Sleep cancelled" | "Slept <1m" | — |
| Deleted (from the edit sheet) | the entry's icon | "Sleep deleted" / "Pee + poop deleted" | — | — (no Edit) |

Receipt rules:
- Nudges add up, like the edit sheet's: each tap moves the time by its minutes more (−5m twice is 10 minutes earlier; +5m steps back). The receipt's base time is the logged time; "was 14:32" shows it while the time differs. Each tap applies at once (event time, sleep start or sleep end) and plays `SegmentTick`.
- A chip is disabled if one more tap would put a sleep end before its start, or a start or end in the future ("+5m" while the time + 5 min > now).
- "End sleep" ends the running sleep at the logged event's time. The receipt then becomes Woke up for that sleep.
- Undo: feed/diaper → remove the entry. Sleep start → no running sleep. Woke up → the sleep is running again. Sleep cancelled → the sleep is running again. Deleted → the entry is back. The sheet closes and the "Undone" notice shows for 2 s. Haptic `Reject`.
- Edit closes the sheet and opens the edit sheet for that entry. Done closes it (Home's 600 ms cooldown follows, as after a committed sheet).
- Feed, Diaper and the Sleep toggle sit under the scrim while a receipt is open: the next log starts after Done or a dismiss.

**Chip** (used in cards and sheets): a 48 dp tall button holding a 40 dp pill, r 20, labelLarge onSurface, 1 dp outline border, transparent fill.
- Nudge chips (−15m, −10m, −5m, +5m) share one row in equal widths (content centred, padding 0 4). They are buttons (`Role.Button`), never selected. Other chips hug or fill their content, padding 0 12.
- Selected: fill primaryContainer, label onPrimaryContainer, **outline border kept**, leading `check` 16 + 4 inside the same width.
- Disabled: label and border at 0.38 opacity.
- Single-select chips expose a selected state.

**Feed and Diaper buttons**: 169 × 96, full pill r 48 (pressed or not: the ripple shows the press). Fill FEED / DIAPER container. Content row: icon 32 (event strong) + 12 + headlineSmall "Feed" / "Diaper" (event onContainer). **Unchanged in lights-down.**

**Forecast line**: under the label of Feed, Diaper and the Sleep toggle, what the last 7 days expect next ("in 1h 05m"; Diaper "poop in 3h", or, when poop keeps to the feeds rather than a gap, the poop expected after the last feed; Woke up the expected wake-up), and once that has passed, how long ago it was due ("24m ago"). The label and the forecast line are start-aligned 12 dp after the icon; icon and text are centred in the button as one group. See V1_ADDITIONS §9, which also puts the estimates on the 4×2 widget and the watch.

**Sleep toggle**: 350 × 96, a button whose name changes with its state (it exposes no separate on/off state; §9.4).
- Unchecked: r 48, SLEEP container, `sleep` 32 SLEEP strong + 12 + "Start sleep" headlineSmall SLEEP onContainer.
- Checked: r 48 (the shape stays; fill, border and icon tell the state), SLEEP containerLit, 2 dp SLEEP strong border, `wake` 32 + "Woke up". Day has no containerLit: it uses SLEEP container + the border.


**Home states**

| State | Trigger | Visible change |
|---|---|---|
| Idle | no running sleep | FEED Lamp (Night glow / Day disc); rows Awake + last diaper; Today grid |
| Just logged | Feed or Diaper sheet saved | the sheet closes; the Lamp / since-rows show the log, `Confirm` |
| Toggled | Start sleep, Woke up | the receipt sheet (no timeout) |
| Running, Night = **lights-down** | Night active and a sleep running | "Noa", status icons and Settings in onSurfaceVariant; History pill unfilled with onSurfaceVariant label; glow 0.45 SLEEP + orbit; Today numbers onSurfaceVariant; toggle checked. Nothing else changes |
| Running, Day | Day active and a sleep running | no dimming; SLEEP disc + track + cut-out ember |
| Diaper sheet open | Diaper | scrim + sheet (P2) |
| Guard | repeat toggle within 2 s | card row 2 guard line, `Reject` |
| Dedupe | Feed again within 5 s | the notice card "Already logged at 14:32" for the existing entry (2 s), `Reject`; no new entry |
| Undone | Undo | "Undone" for 2 s |
| Under a minute | feed < 1 min ago | hero "just now", sub-line "at 14:32" |
| Sync states | §4.5 | status icon glyph and spoken sentence; error = retry button |
| Dimmer offer | first sleep start while Night is active | whisper slot: "Too bright? Dim this screen while Noa sleeps" · Dim · No thanks (§8.4) |
| Dimmer active (first time) | first activation | card "Screen dimmed for night" with Undo |
| Long sleep | running > 12 h | whisper slot: "Asleep 12h 10m · still asleep?" with "Fix end time" and "Yes" |

**Home interactions**

| Input | Result | Haptic |
|---|---|---|
| Feed | log a feed now (dedupe: §4.4); Feed card | `Confirm` |
| Diaper | open the Diaper sheet, stamping now | — |
| Start sleep | sleep running from now; "Sleep started" card; 2 s guard; first time: ask for notification permission in context; Live Update | `ToggleOn` |
| Woke up | end the sleep now; "Woke up" card (stopped < 60 s → "Sleep cancelled", entry discarded); 2 s guard | `ToggleOff` |
| Toggle within the 2 s guard | no change; guard line | `Reject` |
| Card offset / Adjust chip | shift the time (see card rules) | `SegmentTick` |
| Undo | revert; "Undone" | `Reject` |
| Since-row | edit sheet of that latest entry | — |
| History pill, Today grid | History | — |
| Lamp text | edit sheet of the last feed (idle) or of the running sleep | — |

Input cooldowns apply (§5.9): after a sheet tile commits, Feed, Diaper and the Sleep toggle ignore taps for 600 ms after the sheet has finished closing.

#### 3.3.2 P2 Diaper sheet

Modal bottom sheet over Home with the scrim. It is a dialog labelled by its title.

| Part | Spec |
|---|---|
| Sheet | full width, 368 tall at reference (top at y 476), surfaceContainerLow, top corners r 28. Handle 32 × 4, r 2, onSurfaceVariant at 0.4, 12 from the top |
| Header (48) | "Diaper · 14:32" titleLarge onSurface at the 24 gutter. **The time is stamped at the moment Diaper was tapped.** After a nudge, "was 14:32" dataMedium onSurfaceVariant before Cancel. "Cancel" text button (labelLarge onSurfaceVariant) at the end |
| When chips | four nudge chips, gap 8, equal widths: "−15m", "−10m", "−5m", "+5m". Taps add up, like the edit sheet's nudges: −5m twice changes the title to "Diaper · 14:22" with "was 14:32". "+5m" is disabled while it would reach the future |
| Tiles | three buttons 106 × 128, r 28, gap 12. Content column: icon 40 at +24, label headlineSmall at +76. Pee: PEE container, `pee` PEE strong, "Pee" PEE onContainer. Poop: POOP container, `poop`, "Poop". Both: hard 50/50 split (start PEE container, end POOP container), two-tone `both`, "Both" in POOP onContainer |
| Bottom | empty sheet area over the gesture inset |

- No "Last: …" line (the fact is already on Home).
- Tile → log at stamp + offset and close the sheet; no receipt follows (the sheet already picked the time). Home shows the log (e.g. since-row 2 "Pee 10m ago · 14:22"). Haptic `Confirm`.
- Cancel or a scrim tap closes without logging.
- **Cooldown**: chips and tiles ignore taps for 500 ms each time the sheet appears. They fade 0.6 → 1 over that time and are exposed as disabled until ready. Reason: the Diaper button lies 47 % under the Both tile and 21 % under Poop, so a double tap would log Both.

#### 3.3.3 P3 History

| Band (reference y) | Content |
|---|---|
| Top bar 24–88 | Back icon button (`back` 24, "Back"); "History" titleLarge; at the end the `chart` icon button (24, "Statistics"), its glyph 20 from the screen edge, mirroring Back. It opens Statistics (P8, V1_ADDITIONS §8) |
| Day switcher 96–144 | `chevronLeft` icon button ("Previous day"); "Today · Mon 26 Oct" titleMedium centred; `chevronRight` icon button ("Next day"), disabled on today (icon outlineVariant) |
| Ribbon card 152–252 | 350 × 100, r 20, surfaceContainer. Decorative, not tappable |
| Stats 264–312 | 4 columns: numbers titleLarge onSurface ("5", "4", "2", "7h 57m"), captions labelSmall onSurfaceVariant ("feeds", "pee", "poop", "sleep") |
| Heading 332–350 | "Today" labelMedium onSurfaceVariant (a heading) |
| List from 360 | one row per entry, newest first |
| FAB | extended FAB "Add entry", bottom end: 138 × 56, r 16, primaryContainer, `plus` 24 + 8 + labelLarge onPrimaryContainer. Flat (§5.9), so while rows scroll under it, it collapses to the 56 × 56 icon FAB (still named "Add entry"); at the end of the list, where the list's bottom padding keeps it clear of the rows, it shows its label |

**Ribbon** (coordinates inside the card). Plot x = 12 + 326 × hours / 24 (00 to 24).
- Night bands: ribbon-night-band colour, rx 6, y 8–74. In the reference they span 00:00–07:00 and 19:00–24:00.
- Lanes: sleep bars at y 13 (14 tall); feed ticks at y 34 (14 tall); diaper marks centred at y 60. Marks follow §6.4.
- A sleep that began the previous day starts flush at 00:00.
- Now-line: 2 dp wide, y 8–74, onSurface. Nothing is drawn after now.
- Axis labels y 78–94, labelSmall onSurfaceVariant: "00" (start-aligned), "06", "12", "18" (centred), "24" (end-aligned).
- Text alternative: "Today: 5 feeds, 4 pee, 2 poop, 7 hours 57 minutes sleep".

**List rows**: buttons 350 × 56, gap 2, fill surfaceContainer. The group's outer corners are r 20 and the inner corners r 6. Shape-coded avatar 40 (§6.3) at 16 from the start, then text at 72 from the start.
- Instant rows: one-line title titleMedium ("Pee", "Fed", "Poop", "Pee + poop"), vertically centred; trailing time dataMedium onSurfaceVariant, end-aligned with 16 inset.
- Sleep rows: two lines and **no trailing text**: title titleMedium 18/22 ("Sleep · 47m"), then the range dataMedium 16/20 onSurfaceVariant ("12:40 – 13:27"; previous day: "Sun 22:40 – 01:35").
- Tap → edit sheet. Custom accessibility actions "Edit time" and "Delete". Swipe-to-reveal Delete (§10.8).

#### 3.3.4 P4 Edit sheet

Only the **Edit sleep** form is drawn. It opens from History rows, since-row 1, the running Lamp and Edit in a receipt.

| Part (top → bottom) | Spec |
|---|---|
| Sheet | full-height modal sheet starting 24 below the status bar, surfaceContainerLow, top r 28, handle; a dialog labelled "Edit sleep"; scrim over History |
| Header (48) | Close icon button (`close` 24, "Close"); "Edit sleep" titleLarge; "Delete" text button (`delete` 18 + 6 + labelLarge) in error colour |
| Sub | "Today · Mon 26 Oct" dataMedium onSurfaceVariant |
| Block "Fell asleep" | label labelMedium onSurfaceVariant; day chip at the end ("Today" + `chevronDown` 16, 48/40, outline border, padding 0 12); time field 342 × 80, r 20, surfaceContainerHigh: `clock` 24 onSurfaceVariant, time displaySmall onSurface ("12:35"), and after a change "was 12:40" dataMedium onSurfaceVariant at the end; nudge row: 4 equal chips "−5m" "−1m" "+1m" "+5m", gap 8 |
| Block "Woke up" | same ("13:27") |
| Duration row | `sleep` 20 SLEEP strong + "Duration 52m" titleMedium + " · was 47m" dataMedium onSurfaceVariant |
| Validation slot (48) | empty unless a rule fires |
| Footer | "Cancel" text button (labelLarge onSurfaceVariant); Save button 160 × 56, r 28, `check` 20 + 8 + "Save" labelLarge. Night: primaryContainer / onPrimaryContainer. Day: primary / onPrimary |

Behaviour:
- Nudges ±1m / ±5m with `SegmentTick`.
- Time field → `TimePickerDialog` in keyboard `TimeInput` mode (dial toggle available).
- Day chip menu: Today / Yesterday / "Earlier date…" (DatePicker).
- Running sleep: the Woke up block reads "Still asleep" with "End now". Changing the start keeps the timer running.
- Validation copy. The slot uses the error colour only when the rule blocks Save.
  - "Waking up must come after falling asleep." (blocks Save)
  - "13:50 hasn't happened yet. Log it for yesterday?" with "Use yesterday"
  - "Overlaps the 12:10 sleep. Save anyway?" (non-blocking)
  - "Changed on the watch meanwhile. Check and save again." (non-blocking): Save found the entry changed on the watch since the sheet opened (e.g. Woke up on a sleep whose start is being corrected). Nothing is overwritten; the sheet now shows the watch's version with the sheet's own changes on top. "Deleted on the watch meanwhile." blocks Save.
- Delete closes the sheet and shows the card "Sleep deleted" with Undo (soft delete, §4.4).
- **Add past entry** mode (from the FAB): title "Add past entry"; connected button group Sleep | Feed | Diaper with Feed preselected; defaults to now − 15m.

#### 3.3.5 Settings (referenced, not designed)

Settings referenced by the design (screen layout not drawn; Appendix B):
- **Appearance**: Auto (default) · Night · Day. Includes "Dim for naps too" (default on) and the editable night window used without location (default 19:00–07:00). See §8.
- **Night dimmer**: re-offer and level (default 10 %, adjustable 5–30 %).
- Link to the system promoted-notifications setting (§10.5).
- "Day starts at" (default 00:00; pending open question 4).
- With the optional account (BABIES_AND_BACKUP §3): Account first, then **Family** while signed in (the members, "Invite someone", "Leave family", "Remove" for the family's creator; §3.4), and a **Privacy policy** row and a **Download my data** row (the log and the babies' names as a ZIP file of JSON files, BABIES_AND_BACKUP §3.2) and an **Import data** row (merges such a file into the log) in About.
- At the very bottom, under About, a centred footer: "Made with ❤️ by a father in Aarhus, Denmark" (Danish: "Lavet med ❤️ af en far i Aarhus"), "Pooparazzi is open source", and a row of two logo buttons (48 dp targets, tinted onSurfaceVariant): GitHub opens the repository and LinkedIn the maker's profile, in the browser.
- **Babies** (BABIES_AND_BACKUP §2.4): a name field per baby, "Add a baby", and with two or more babies a remove button (`delete` icon) at the end of each field that asks "Remove <label>?" before it removes the baby and deletes its log.
- The first-run **Welcome** ("No account needed", BABIES_AND_BACKUP §3.1) shows two equal cards: "Only on this phone" first, with "You can switch to cloud backup at any time in Settings › Account" and a filled "Continue without an account", then "Back up to the cloud" with the sign-in choices.
- After signing in, a full-screen **consent** ("Back up to Google Firebase?": what is uploaded, who sees it, where, how to change your mind; "I agree" filled, "Not now, sign out" as text; BABIES_AND_BACKUP §3.6) comes before anything else until the account has agreed on the phone; it is laid out like the Welcome.

#### 3.3.6 P5 Home-screen widgets (Glance)

Both widgets use the fixed NIGHT/DAY tokens chosen by the **app's** Appearance (§8.5) and the device font (no tnum). They show **absolute times only, always with "at"** ("Fed at 12:22"). The sleep toggle logs in place without launching the app; Feed and Diaper open the app on the same Feed and Diaper sheets as Home's buttons, so the optional details (feed type and breast order, pee/poop, colour, texture) are asked for exactly as in the app.

**4×2** (reference 358 × 208, system corner radius (reference r 24), surfaceContainer, padding 12)

| Row | Idle | Asleep | Just logged |
|---|---|---|---|
| 1 (24, not a separate target) | `feed` 16 + 6 + "Fed at 12:22" labelLarge 15 sp onSurface; 16 gap; `pee` 16 + "Pee at 13:44" | "Fed at 01:40", "Pee at 01:52" | `check` 16 onSurfaceVariant + 6 + "Sleep started at 14:32"; at the end a text button "Undo" (`undo` 16 + 6 + labelLarge 15 sp, primary, 48 dp target, description "Undo sleep start") |
| 2 (68) | 2 equal buttons, gap 8, r 20; icon 24 at +10, label 15 sp at +40: Feed (FEED colours, opens the Feed sheet), Diaper (DIAPER colours, `diaper`, opens the Diaper sheet) | unchanged | unchanged |
| 3 (68) | Sleep toggle, full width, r 20 (as every widget button), SLEEP container, padding 0 20: `sleep` 24 + 10 + "Start sleep" 16 sp SLEEP onContainer; end-aligned "Awake since 13:27" dataSmall | checked: r 20, SLEEP containerLit, 2 dp SLEEP strong border, `wake` + "Woke up" (name "Woke up, end sleep"); end-aligned `sleepRunning` 16 + 6 + "Asleep since 02:05" | unchanged |

- Just logged: nothing else changes. No button moves, relabels or changes colour, and Undo never sits where a log button is. The receipt reverts on the next log or after 10 s.
- The widget background (outside the buttons) opens the app with the receipt for the last widget log.

**3×1** (reference 260 × 100, padding 12, minimum 160 wide): three equal buttons 76 tall, gap 8, r 20, content column (icon 24, label 14 sp, max 2 lines, never broken inside a word): Feed (`feed`, opens the Feed sheet); Diaper (`diaper`, DIAPER colours, opens the Diaper sheet); Sleep ("Start sleep", the toggle). When asleep, Sleep shows `wake` + "Woke up" with the checked style. Every widget offers all three: Feed, Diaper and Sleep. (It was a 2×1 with Feed and Sleep; the provider keeps its receiver so placed widgets survive.)

**Cover screen (Samsung flip phones)**: the same 4×2 widget is offered on the Flex Window (Settings › Cover screen › Widgets) by a third provider with the `keyguard` category and Samsung's `sub_screen` metadata (352 × 339 dp minimum). Launchers only list `home_screen` widgets, so it never appears anywhere else. It is drawn at that one fixed size (`SizeMode.Single`): with the per-size RemoteViews of `SizeMode.Responsive` the widget stayed on its loading layout on a Flip's cover screen. Refreshes go to every placed widget by its provider's ids, so a widget the host never asked to update is still drawn. Feed and Diaper open the app's sheet on the cover screen; when that sheet closes (logged, or dismissed with Back) the app finishes and the cover screen shows the widget again, since Home has no room there. On the main screen Home stays.

Breakpoints: §10.11.

#### 3.3.7 P6 Live Update (running sleep only)

| Element | Content |
|---|---|
| Status chip | small icon `sleepRunning` + "Asleep" (no ticking value) |
| Title / text | "Noa is asleep" / "1h 07m · since 02:05" (whole minutes, re-rendered each minute) |
| Expanded | the text, then "Fed 1h 32m ago · 01:40 · L → R", "Pee 20m ago · 02:52", "Today · 1 feed · 1 pee · 0 poop · 2h 42m sleep" (lines without data are left out) |
| Action | "Woke up" (works from the lock screen) |
| Metrics (Android 17 `MetricStyle`) | "Asleep for" 1h 07m (system TimeDifference), "Since" 02:05 (fixed time), "Last fed · L → R" 1h 32m (system TimeDifference) |

**Woke-up notification.** "Woke up" on the notification ends the sleep and posts a silent, dismissable notification on the same channel, so the wake-up time can be moved without opening the app, as the receipt sheet's chips do:

| Element | Content |
|---|---|
| Title / text | "Noa woke up at 03:12" (no name: "Woke up at 03:12") / "Slept 1h 07m · 02:05 – 03:12" |
| Expanded | the text, then the feed, diaper and Today lines above |
| Actions | "−5m" "−10m" "−15m" (lock screen too). Relative to the tap, never cumulative; the selected one reads "✓ −10m" and a second tap returns to the tap time. An offset that would end the sleep before its start is left out |
| Lifetime | 30 minutes (`setTimeoutAfter`); cancelled when a sleep runs again (a new sleep, or the wake undone in the app) |

Feed and diaper are never Live Updates. API details: §10.5.

#### 3.3.8 P7 Splash and app icon

- System splash via a `values-night` colour. Night: `#0C0A08` with the `diaper` glyph in `#E8D8C3`. Day: `#FBF6F0` with `#3A332C`. Never an always-black splash.
- The app icon's diaper glyph is parchment `#E8D8C3`, not amber (amber means FEED).

### 3.4 Shared phone rules

- Facts appear once per screen. Time-since lives in the since-rows, never on buttons. Button labels never change length (stable at 200 % font scale, identical day and night).
- Undo is never the only recovery path: since-rows and History rows open the edit sheet.

### 3.5 Wear app

Every app screen except Logged and Undone shows the system **TimeText** (15 sp, weight 600, tnum, onSurface; ambient onSurfaceVariant). The watch is **dark only**.

#### 3.5.1 W1 Main, idle

Main looks like the Tile (§3.6): a title line, a Feed row and a Diaper row with the time since the last one, and the sleep toggle. Nothing else on the first screen; details (forecasts, the last diaper's kind, sync state) are on the phone.

| Element | Spec (reference 228 dp) |
|---|---|
| Title line | the baby's name, titleSmall onSurface (nothing when unnamed). With two or more babies: a compact button (`profile` + the baby's label) that opens the baby picker |
| Feed row | full-width button, r 26, FEED container `#3B2715`: `feed` 24 `#F2B577`, "Feed" labelMedium + "2h 10m ago" labelSmall `#FFDCBB` ("No feeds yet" before the first). The two rows share the height between the title and the EdgeButton, 4 apart, side margins 7 % of the width |
| Diaper row | the same in DIAPER: `#13312C`, `diaper` 26 `#62B3A5`, "Diaper" + "48m ago" `#BFE9E0` ("No diapers yet") |
| EdgeButton | **Small** (ExtraSmall below 225 dp), pinned at the bottom; fill SLEEP container `#222844`, "Start sleep" labelMedium `#D6DBF6` on one line |
| Scrolling | none: Main is one fixed screen, as the Tile |
| Font scale | Main's text follows the font scale up to 1.3 (the EdgeButton label to 1.15) and steps down to fit its button where a row or the EdgeButton has less room; a row too short for two lines reads "Feed · 2h 10m ago" on one |

No Lamp and no glow.

#### 3.5.2 W2 Main, asleep

| Element | Spec |
|---|---|
| Title line | `sleep` 14 `#7C88C4` + 4 + "Asleep 1h 07m" titleSmall SLEEP text, as the Tile's title (spoken "Asleep, 1 hour 7 minutes, since 02:05"). With two or more babies it is the baby button's label |
| Rows | Feed and Diaper as when idle |
| EdgeButton | fill SLEEP containerLit `#2E3660` with a 1.5 dp `#7C88C4` border; "Woke up" `#D6DBF6`. Full opacity immediately after the toggle (no fade-in) |
| Ambient | TimeText `#B8A48E`; rows and EdgeButton no fill, 2 dp outline in their strong colour, icon and label `#B8A48E`; ≥ 85 % black pixels |

**Guard state of the EdgeButton**: a tap within 2 s of a toggle hides the icon and shows two centred lines in labelMedium `#D6DBF6` at full opacity: "Already" / "asleep" (after Woke up: "Already" / "awake"). It shows for 2000 ms with a `Reject` haptic and no state change. One line ("Already asleep") would be clipped by the ≈99 dp content width.

#### 3.5.3 W3 Diaper picker

| Element | Spec |
|---|---|
| Title | y 30–48: "Diaper · 14:32" titleMedium onSurface, centred. **Stamped on the Diaper tap**; updates after Earlier ("Diaper · {stamp + offset}") |
| Tiles | three buttons 62 × 88 (vertical pills r 31), gap 6, y 58–146. Content column: icon 32 at +18, label labelMedium at +54. Pee `#13312C` / `#8FD0C3` / "Pee" `#BFE9E0`; Poop `#0E2622` / `#358276` / "Poop" `#A8DCD1`; Both split `#13312C` \| `#0E2622`, two-tone icon, "Both" `#A8DCD1` |
| "Earlier" | CompactButton: 32 visual / 48 target, 94 wide, r 16, `#1E1914`, `clock` 20 + 4 + "Earlier" labelSmall 13 sp weight 600 `#E8D8C3`; y 160–192 |
| No EdgeButton | swipe right cancels |
| Cooldown | tiles and Earlier ignore taps for 500 ms each time the picker appears (including on return from Earlier), fading 0.6 → 1. The Diaper button lies 61 % under Both |

Tile → log at stamp + offset → Logged. Haptic `Confirm`.

#### 3.5.4 W4 Earlier

Three tonal buttons 150 × 52 (Wear button minimum height), r 26, `#1E1914`, labelMedium `#E8D8C3`, centred at y 32–84, 88–140, 144–196: "−5m · 14:27", "−10m · 14:22", "−15m · 14:17" (times = stamp − offset). A choice sets the offset and returns to the picker. 500 ms cooldown. Spoken: "5 minutes earlier, 14:27".

#### 3.5.5 W5 Adjust (removed)

The watch no longer adjusts times: history and edits are on the phone.

#### 3.5.6 W6 Logged

| Element | Spec |
|---|---|
| TimeText | none |
| Bezel drain arc | radius 111, stroke 2, outline `#8A7864`, round caps. 270° **open at the bottom** (gap centred on 6 o'clock, clearing the Undo button). It starts at the lower left, runs clockwise over the top and ends at the lower right. As time passes its tip retreats from the lower right back over the top. 5 s window |
| Check circle | Ø64 centred at (114, 66), event container fill; `check` 32 in event strong (Sleep cancelled: `wake`) |
| Title | y 106–130, titleLarge, centred |
| Line 2 / sync icon | y 132–152: the `phoneSynced` icon 20 in `#B8A48E`, centred, spoken "Synced with phone · 14:32" (offline: `phoneOffline`, "Saved on watch · will sync"). When a second line is needed it takes y 132–146 in bodyMedium `#B8A48E`, and the icon moves to y 148–168 |
| Undo | EdgeButton **Small** (56), fill `#1E1914`, `undo` 20 + 6 + "Undo" labelMedium `#E8D8C3` |
| Taps | the whole screen except Undo dismisses (→ Main). The confirmation content sits in a non-interactive status layer above the dismiss target and is announced |
| Inert period | Undo and dismiss ignore taps for the first 1000 ms (Undo fades 0.4 → 1 and is exposed as disabled). Exception: opened from the Tile's undo path, both are active at once |

| Kind | Circle / glyph | Title | Line 2 |
|---|---|---|---|
| feed | `#3B2715` / `#F2B577` | "Fed at " `#E8D8C3` + "14:32" `#F2B577` | (sync only) |
| pee | `#13312C` / `#8FD0C3` | "Pee at 14:32" (time `#8FD0C3`) | (sync only) |
| poop | `#0E2622` / `#358276` | "Poop at 14:32" (time `#8FD0C3`) | (sync only) |
| both | split `#13312C` \| `#0E2622` / `#8FD0C3` | "Pee + poop at 14:32" (time `#8FD0C3`; autosize to 15 sp if it does not fit) | (sync only) |
| wake | `#222844` / `#7C88C4` | "Woke up at 14:40" (time `#AEB6E6`) | "Slept 8m" |
| cancelled (stopped < 1 min) | `#222844` / `wake` `#7C88C4` | "Sleep cancelled" | "Slept <1m" |
| feed, deduped (< 5 s) | as feed | "Fed at 14:32" (the existing entry) | "Already logged" (+ `Reject`; the 5 s drain restarts) |

Undo removes the entry (wake and cancelled: the sleep is running again) → Undone. Ambient: the bezel arc is hidden, Undo is drawn as an outline, the 5 s window keeps running and expiry returns to Main.

#### 3.5.7 W7 Undone

No TimeText. `undo` 32 `#B8A48E` centred at y 66; "Undone" titleLarge y 106–130. Shows 1.5 s, then Main.

#### 3.5.8 W8 Today (removed)

The watch has no history list; History is on the phone.

#### 3.5.9 Watch status card (removed)

Main no longer shows status cards: it mirrors the Tile (§3.5.1).

#### 3.5.10 Small watches (< 225 dp, e.g. 192 dp)

| Screen | Rule |
|---|---|
| Main | the sleep toggle uses EdgeButton **ExtraSmall**; the title is hidden (as on the Tile) unless two or more babies need the baby button |
| Tile | the title is hidden (§3.6) |

Layouts use percentage padding.

#### 3.5.11 Watch interactions summary

| Input | Result | Haptic |
|---|---|---|
| Feed | log now → Logged (dedupe within 5 s → Logged "Already logged") | `Confirm` (dedupe `Reject`) |
| Diaper | picker (stamp now) | — |
| Pee / Poop / Both | log at stamp + offset → Logged | `Confirm` |
| Start sleep | running; Main (asleep); 2 s guard | `ToggleOn` |
| Woke up | < 1 min: Logged "Sleep cancelled"; else Logged wake | `ToggleOff` |
| Toggle within the guard | two-line "Already / asleep" for 2 s | `Reject` |
| Earlier choice | shift the time | `SegmentTick` |
| Undo | revert → Undone | `Reject` |
| Double-pinch (Wear OS 7, Pixel Watch 3+) | bound only to the Start sleep / Woke up EdgeButton; never on the picker or Logged | as the button |

**Standalone**: logging never needs the phone. Offline sync copy: "Saved on watch · will sync". Phone app missing: "Phone app not installed" with "Install on phone" (`RemoteActivityHelper`). Never a dead-end "check connection" screen.

### 3.6 W9 Tile

Fixed neutral chrome (`allowDynamicTheme = false`), container fills only (night-safe), no TimeText.

| Element | Spec (reference 228) |
|---|---|
| Header | `diaper` 24 `#E8D8C3`, y 11–35 (system app icon: the tile's icon, the app's launcher glyph) |
| Title (titleSlot) | y 37–53, titleSmall `#E8D8C3`, centred: **"Pee 48m ago"** (last diaper) |
| Row A | Feed button 184 × 52, r 26, `#3B2715`: `feed` 24 `#F2B577` at 16 from the start; "Feed" labelMedium 15 sp `#FFDCBB` over "2h 10m ago" labelSmall `#FFDCBB`. Opens the app's Feed type screen |
| Row B | Diaper button 184 × 52, r 26, DIAPER container `#13312C`: `diaper` 24 DIAPER strong at 16 from the start; "Diaper" labelMedium 15 sp `#BFE9E0` over "48m ago" (or "No diapers yet") labelSmall. Opens the app's Diaper screen, like Main's Diaper button; diapers are never logged from the tile |
| EdgeButton | drawn 46 tall (y 179–225), 48 dp hit box, 9 dp below Row B; fill `#222844`, "Start sleep" labelMedium 15 sp `#D6DBF6` |

| State | Title | Other changes |
|---|---|---|
| Idle | "Pee 48m ago" | — |
| Just logged (10 s, a Woke up or Sleep cancelled from the tile) | `check` 14 `#7C88C4` + 4 + "Woke up 14:32 · " `#E8D8C3` + `undo` 14 + 3 + "Undo" (weight 700) | The tile background opens Logged for that entry with Undo active at once |
| Asleep | `sleepRunning` 14 `#7C88C4` + 4 + "Asleep 1h 07m" titleSmall `#AEB6E6` | Row A secondary "1h 32m ago"; EdgeButton fill `#2E3660` + 1.5 dp `#7C88C4` border, "Woke up" |
| Guard (2 s) | `sleep` + "Already asleep" (or `awake` + "Already awake") in `#AEB6E6` | `Reject`; nothing moves |

- With both "just logged" and "asleep" active, the just-logged title wins.
- **No button ever relabels or moves.** Undo is reached through the background tap, never in a log button's place.
- The tile title keeps "Fed 14:32" without "at": with "at" it would exceed the ≈168 dp title chord.
- Below 225 dp the title is hidden. Feedback is then the secondary-label change plus the background-tap undo path.

### 3.7 W10 Complications

| Complication | Type | Content (14:32) | While asleep |
|---|---|---|---|
| Since feed | SHORT_TEXT | `feed` icon + "2h 10m" | same |
| Diaper | SHORT_TEXT | kind glyph (`pee` / `poop` / `both`) as MONOCHROMATIC_IMAGE + SMALL_IMAGE; text "48m"; title "Pee" (drawn only by faces that show no image) | same |
| Sleep | SHORT_TEXT | `awake` + time since wake | `sleepRunning` + time asleep |
| Sleep (long) | LONG_TEXT | `awake` + "Awake 1h 05m", title "since 13:27" | "Asleep 1h 07m", title "Fed 1h 32m ago" |

- Monochrome and small images, white service icon. The colours come from the watch face.
- Taps open the matching app screen and **never log**.
- No RANGED_VALUE "orbit" complication (progress semantics would be misused). Never `STOPWATCH`.

### 3.8 W11 Ongoing Activity / Live Update (watch)

Static white `sleepRunning` icon. Status "Asleep since 02:05" (no ticking duration). Tap → the sleep screen. Dismissed on both devices when the sleep ends on either one. API details: §10.6.

---

## 4. Event and data model

Design-level model implied by the screens. Not a schema.

### 4.1 Entities

| Entity | Fields | Notes |
|---|---|---|
| **Sleep** | id, start, end (empty = running) | interval. At most one running sleep |
| **Feed** | id, time | instant = the start of the feed. Time-since is counted start to start |
| **Diaper** | id, time, pee (bool), poop (bool) | instant. At least one flag is set. Both = both flags |
| **Baby** | name | one baby ("Noa"), shown in the top bar and the notification and dimmer copy |
| **Settings** | appearance (Auto / Night / Day), dim for naps (on), night window (19:00–07:00), dimmer level (10 %, 5–30 %), day starts at (00:00) | §3.3.5, §8 |

Fields common to every entry (implied by sync, dedupe and undo):

| Field | Needed for |
|---|---|
| **id (UUID)** | cross-device dedupe; matching the phone-bridged and watch Live Updates (sleep-session UUID); the widget's "Undo" targets a UUID |
| **times stored in UTC** | sync between devices; displayed in local time |
| **sync status** (pending → synced) | the status icon's mark (slash vs tick) and its spoken sentence ("Saved on phone · will sync" vs "Synced with watch · 14:31") |
| **soft-delete marker** | deletes are soft for 7 days (sync-safe) and can be undone; no "Recently deleted" screen in v1 |

### 4.2 Diaper kind labels

| pee | poop | Status surfaces (since-row 2, widget row 1, tile title, complication, watch cards) | Receipts and History rows | Icon | Avatar / mark |
|---|---|---|---|---|---|
| yes | no | "Pee" | "Pee" | `pee` | rounded square, PEE container / ring |
| no | yes | "Poop" | "Poop" | `poop` | rounded square, POOP container / dot |
| yes | yes | "Both" | "Pee + poop" | two-tone `both` | split square / ring + right half solid |

Counts: Both adds 1 to pee **and** 1 to poop.

### 4.3 Derived values

| Value | Rule |
|---|---|
| Last fed | the most recent feed's time; Lamp hero when idle |
| Awake since | the end of the most recent sleep (when none is running) |
| Asleep for | now − running start |
| Last diaper | the most recent diaper's time and kind |
| Today counts | from the day start (00:00 default, local): feeds, pee, poop, sleep minutes. Only the part of a sleep after the day start counts, and a running sleep counts up to now |
| Ember angle | (elapsed minutes mod 60) × 6° |
| Lamp mode | sleep running → SLEEP; otherwise → FEED |
| Lights-down | Night is active and a sleep is running |

### 4.4 Rules

| Rule | Behaviour |
|---|---|
| Dedupe | the same kind within 5 s from **any** surface (phone, watch, tile, widget) = one entry (event UUID + a (kind, 5 s) window). Never silent: show "Already logged …" + `Reject` |
| Idempotent sleep toggles | Start while running → "Already asleep since 02:05" + `Reject`. Near-simultaneous Woke up from phone and watch: the first wins, the second shows "Already awake since 03:12" |
| Double-toggle guard | 2 s after any toggle (phone toggle, watch EdgeButton, Tile EdgeButton, widget toggle); visible feedback + `Reject` |
| Mis-tap sleep | a sleep stopped < 60 s after its start is discarded ("Sleep cancelled", with Undo, on phone and watch) |
| Long sleep | past 12 h: "Asleep 12h 10m · still asleep?" with "Fix end time" / "Yes" |
| No auto-ending | a feed or diaper during a sleep offers "End sleep" in the card; a sleep is never ended automatically |
| Offsets | nudges add up from the base time, shown as "was 14:32" once moved |
| Time limits | no start or end in the future; no end before start (chips disabled; edit sheet validation) |
| Day boundary | "Today" starts at 00:00 by default (configurable; open question 4). A sleep crossing the boundary is drawn as one bar (flush at 00:00 on the later day) and listed as one row with a day prefix |
| Deletes | soft for 7 days, with an Undo receipt |
| Writes | local-first on each device; watch → phone via `DataClient` with `setUrgent()` |

### 4.5 Sync state (device-level, shown in UI)

The state shows as a small icon, never as visible text: the **other device** with a mark on its face or screen (the phone's top bars show a watch, the watch shows a phone), and on the phone also a **cloud** for the backup while an account is signed in (BABIES_AND_BACKUP §3.2). The sentence is the icon's TalkBack description; Settings › Watch sync still shows it as text, with "Sync now".

| State | Phone (top bars) · spoken | Watch (Logged, list footer) · spoken | Mark |
|---|---|---|---|
| Synced | `watchSynced` · "Synced with watch · 14:31" | `phoneSynced` · "Synced with phone · 14:31" | tick |
| Syncing | `watchPending` · "Syncing with watch…" | `phonePending` in the footer (Logged hides it) · "Syncing with phone…" | "…" |
| Saved locally | `watchOffline` · "Saved on phone · will sync" | `phoneOffline` · "Saved on watch · will sync" | slash |
| Error | `watchError` in error colour, a 48 dp retry button · "Couldn't save · Tap to retry" | as saved locally (no error on the watch) | "!" |
| No watch / phone | no icon | no icon ("Phone app not installed" + "Install on phone" where relevant) | — |

The backup's cloud (phone only, hidden when nobody is signed in or the build has no backend):

| Backup | Icon · spoken |
|---|---|
| Backing up | `pending` · "Backing up…" |
| Backed up | `synced` · "Backed up · 14:31" |
| Offline | `offline` · "Saved on phone · will back up" |
| Error | `cloudError` in error colour, a 48 dp retry button (backs up again) · "Couldn't back up · Tap to retry" |
| Backup deleted | `cloudError` in error colour, not a button · "Backup deleted · Back up now to start again" |

Phone ↔ watch sync never goes through the backend; the backup is the phone's alone (BABIES_AND_BACKUP §1).

---

## 5. Design tokens

Event colours are **not** M3 roles. They form a separate fixed palette (container, onContainer, strong, containerLit, text, track per event). No M3 role (including the watch `secondary*` / `tertiary*`) carries an event hue.

### 5.1 Colour principles

1. Warm, low-blue neutrals. Night text `#E8D8C3` (≥ 10.23:1), never pure white.
2. No large bright fills at night (Night fills L* ≤ 25). Strong event colours appear only in icons, rings, outlines and marks. Notice cards use surfaceContainerHighest, never inverseSurface.
3. Colour = event only. FEED amber, SLEEP moon-blue, DIAPER dew-teal; PEE = light teal, POOP = deep teal, BOTH = two-tone.
4. Lightness ladder (strong L*): Night FEED 78 > DIAPER 68 > SLEEP 58 (PEE 79, POOP 50). Day FEED 47 > DIAPER 39 > SLEEP 30. SLEEP vs POOP is also separated by shape (bar vs dot, circle vs rounded square).
5. Fixed tokens, no dynamic colour anywhere.

### 5.2 Phone Night (default)

| Role | Hex | Use |
|---|---|---|
| background / surface | `#0C0A08` | |
| surfaceContainerLowest | `#080706` | ribbon night bands |
| surfaceContainerLow | `#14110E` | bottom sheets |
| surfaceContainer | `#1C1713` | ribbon card, list rows, widget body |
| surfaceContainerHigh | `#26201A` | History pill, time fields |
| surfaceContainerHighest | `#312922` | notice card |
| onSurface | `#E8D8C3` | |
| onSurfaceVariant | `#B8A48E` | |
| outline | `#8A7864` | chip outlines, chevrons, drain lines |
| outlineVariant | `#3F352C` | decorative only |
| primary | `#E8D8C3` | Undo text, links |
| onPrimary | `#1F1914` | |
| primaryContainer | `#3F3A35` | Save, FAB, selected chip |
| onPrimaryContainer | `#F2E6D6` | |
| error / onError | `#F2A497` / `#4C110A` | |
| errorContainer / onErrorContainer | `#5E1E15` / `#FFDAD4` | |
| scrim | `#000000` at 60 % | |
| *ribbon-night-band* | `#080706` | = surfaceContainerLowest |
| *lamp-cut* | `#0C0A08` | = background |

### 5.3 Phone Day

| Role | Hex | Use |
|---|---|---|
| background / surface | `#FBF6F0` | warm paper |
| surfaceContainerLowest | `#FFFCF8` | |
| surfaceContainerLow | `#F6EEE5` | bottom sheets |
| surfaceContainer | `#F0E6DA` | ribbon card, list rows, widget body |
| surfaceContainerHigh | `#EADDCF` | History pill, time fields, ribbon night bands |
| surfaceContainerHighest | `#E3D5C5` | notice card |
| onSurface | `#221A12` | |
| onSurfaceVariant | `#54473A` | |
| outline | `#7A6B5E` | |
| outlineVariant | `#D5C6B6` | decorative only |
| primary | `#3A332C` | ink: Undo text, Day Save fill |
| onPrimary | `#FFFFFF` | |
| primaryContainer | `#E2DDD6` | FAB, selected chip |
| onPrimaryContainer | `#221A12` | |
| error / onError | `#9C3328` / `#FFFFFF` | |
| errorContainer / onErrorContainer | `#FFDAD4` / `#410100` | |
| scrim | `#000000` at 32 % | |
| *ribbon-night-band* | `#EADDCF` | = surfaceContainerHigh |
| *lamp-cut* | `#FBF6F0` | = background |

### 5.4 Watch (dark only)

| Role | Hex |
|---|---|
| background | `#000000` |
| surfaceContainerLow / surfaceContainer / surfaceContainerHigh | `#15110E` / `#1E1914` / `#2A231C` |
| onSurface / onSurfaceVariant | `#E8D8C3` / `#B8A48E` (all ambient text uses onSurfaceVariant) |
| outline / outlineVariant | `#8A7864` / `#3F352C` |
| primary / primaryDim | `#E8D8C3` / `#CDBCA6` |
| primaryContainer / onPrimaryContainer | `#3F3A35` / `#F2E6D6` |
| onPrimary | `#1F1914` |
| secondary\*, tertiary\* | same values as the primary family |
| error / errorDim / errorContainer / onError / onErrorContainer | `#F2A497` / `#D98B7E` / `#5E1E15` / `#4C110A` / `#FFDAD4` |

Watch event colours = the Night event colours.

### 5.5 Event colours

- **strong**: icons, rings, outlines, marks. Never text.
- **text**: event-coloured label text.
- **containerLit**: the lit fill of a running-sleep toggle.
- **track**: the orbit track of a running Lamp.

**Night (phone Night and watch)**

| Event | container | onContainer | strong | containerLit | text | track |
|---|---|---|---|---|---|---|
| SLEEP | `#222844` | `#D6DBF6` | `#7C88C4` | `#2E3660` | `#AEB6E6` | `#2E3660` |
| FEED | `#3B2715` | `#FFDCBB` | `#F2B577` | `#4F351C` | `#F2B577` | — |
| DIAPER (category) | `#13312C` | `#BFE9E0` | `#62B3A5` | `#1A423B` | `#8FD0C3` | — |
| PEE | `#13312C` | `#BFE9E0` | `#8FD0C3` | — | — | — |
| POOP | `#0E2622` | `#A8DCD1` | `#358276` | — | — | — |
| BOTH | fill split 50/50, hard stop: start PEE container, end POOP container; label POOP onContainer `#A8DCD1`; icon two-tone (droplet PEE strong, mound POOP strong); mark = PEE ring with the right half POOP solid | | | | | |

**Day (phone Day)**

| Event | container | onContainer | strong | text | track |
|---|---|---|---|---|---|
| SLEEP | `#E1E4F8` | `#1B2152` | `#37418A` | `#37418A` | `#6A73B3` |
| FEED | `#FFE1C4` | `#452300` | `#A35F1A` | `#84490E` | — |
| DIAPER | `#CFEDE6` | `#0F3D36` | `#24665C` | `#24665C` | — |
| PEE | `#CFEDE6` | `#0F3D36` | `#2C7468` | — | — |
| POOP | `#B9E0D7` | `#0B332D` | `#143F38` | — | — |
| BOTH | split: start `#CFEDE6`, end `#B9E0D7`; label `#0B332D`; icon two-tone `#2C7468` + `#143F38` | | | | |

Day has no containerLit. The checked Sleep toggle uses SLEEP container + a 2 dp SLEEP strong border (containerLit resolves to `#E1E4F8`).

**Lamp glow** (Night and watch only): radial gradient of the event container. Stops: 0 → *a*, 0.6 → 0.6·*a*, 1 → 0.

| Glow | Colour | Core alpha *a* | Composited core |
|---|---|---|---|
| Phone idle (FEED) | `#3B2715` | 0.55 | `#261A0F` |
| Phone lights-down (SLEEP) | `#222844` | 0.45 | `#161823` |
| Watch running (SLEEP) | `#222844` | 0.60 | `#141829` |

### 5.6 Other fixed colours

| Use | Value |
|---|---|
| Splash Night | background `#0C0A08`, `diaper` glyph `#E8D8C3` |
| Splash Day | background `#FBF6F0`, glyph `#3A332C` |
| App icon glyph | `#E8D8C3` |
| Watch ambient | text, icons and TimeText `#B8A48E`; ring, dot and EdgeButton outline `#7C88C4` |
| Complications, Ongoing Activity icon | white monochrome (the face supplies the colour) |

### 5.7 Typography

**Families**

| Family | Axes / features | Used for |
|---|---|---|
| **Google Sans Flex** (GSF, OFL) | `opsz` 1–144, `wght` 1–1000, `ROND` 0–100; `tnum`, `lnum`, `zero` | numerals, display, titles, buttons, labels, and **every line that contains a digit** |
| **Atkinson Hyperlegible Next** (OFL) | `wght` 200–800, `tnum` | **phone only**, prose lines **without digits** (e.g. "Earlier?", validation messages without times, help text) |

- **Digit rule**: if a line shows any digit, it uses a GSF style with `tnum` on. Two digit designs never sit side by side.
- Fallbacks: GSF → Roboto Flex → Roboto → system sans. Atkinson → Roboto → system sans.
- Set weight with the font weight, not a `wght` variation.
- ROND: phone text 40 (headlineSmall 60), numerals and display 100 (with opsz 144 for numerals); watch text 0 (matches the Wear system font); watch numerals 100.
- Bundling: §10.12.

**Phone scale** (sp)

| Token | Family | Size / line | Weight | ROND | tnum | Used for |
|---|---|---|---|---|---|---|
| heroNumeral | GSF | 76 / 80 | 600 | 100, opsz 144 | yes | Lamp digits; letter-spacing −0.005em |
| heroUnit | GSF | 34 / 40 | 500 | 100, opsz 144 | — | "h", "m" in the hero; 4 dp after each number |
| heroWord | GSF | 48 / 56 | 500 | 100 | — | "just now" in the Lamp |
| displaySmall | GSF | 48 / 56 | 500 | 100 | yes | edit-sheet time fields "12:35" |
| headlineSmall | GSF | 24 / 32 | 600 | 60 | — | big button labels "Feed", "Diaper", "Start sleep", "Woke up", "Pee" |
| titleLarge | GSF | 22 / 28 | 600 | 40 | yes | "Noa", "History", "Diaper · 14:32", "Edit sleep", stats numbers |
| titleMedium | GSF | 18 / 24 | 600 | 40 | yes | since-row lead, undo-card title, list row titles, Today numbers |
| dataMedium | GSF | 16 / 22 | 450 | 40 | yes | supporting lines with digits: "ago · 12:22", "since 02:05", " · 13:44", list trailing times, sleep ranges |
| dataSmall | GSF | 14 / 18 | 500 | 40 | yes | "Slept 1h 07m", widget sub-labels |
| labelLarge | GSF | 16 / 20 | 600 | 40 | yes | chips, text buttons, Lamp label "Last fed", "History" |
| labelMedium | GSF | 14 / 18 | 600 | 40 | yes | section labels "Today", "Fell asleep", Today captions |
| labelSmall | GSF | 13 / 16 | 600 | 40 | yes | ribbon axis "00 06 12 18 24", stats captions |
| bodyLarge | Atkinson | 18 / 26 | 400 | — | — | prose (reserved) |
| bodyMedium | Atkinson | 16 / 22 | 400 | — | — | "Earlier?" |
| bodySmall | Atkinson | 14 / 20 | 400 | — | — | help text (reserved) |

Minimum phone text: 13 sp.

**Watch scale** (sp)

| Token | Size / line | Weight | ROND | tnum | Used for |
|---|---|---|---|---|---|
| lampNumeral | 34 / 34; units 17 | 600 / 500 | 100 | yes | "1h 07m" in the Lamp; 2 dp after each number, 6 dp between groups; autosize 28–34 (24 on < 225 dp) |
| statusNumeral | 36 / 36; units 18 | 600 / 500 | 100 | yes | idle "2h 10m" (30 on < 225 dp) |
| titleLarge | 20 / 24 | 600 | 0 | yes | Logged "Fed at 14:32", "Undone" |
| titleMedium | 16 / 18 | 600 | 0 | yes | "Diaper · 14:32", ListHeader "Today" |
| titleSmall | 14 / 16 | 600 | 0 | yes | status label "Fed at 12:22", "Asleep", tile title |
| labelMedium | 16 / 19 (tile 15) | 600 | 0 | — | button labels |
| labelSmall | 13 / 16 | 500 (CompactButton 600) | 0 | yes | secondary labels "48m ago · 13:44", "13:44"; CompactButton "Earlier" |
| bodyMedium | 14 / 16 | 450 | 0 | yes | "since 02:05", " ago", Logged line 2 |
| bodySmall | 12 / 14 | 500 | 0 | yes | sync lines |
| timeText | 15 | 600 | 0 | yes | TimeText (system) |

The watch, tile and complications use GSF ROND 0 only as the mockup stand-in for the Wear system font (§10.12). The phone widget uses the device font with no tnum.

### 5.8 Shape

| Element | Phone | Watch |
|---|---|---|
| Lamp | Ø264; ring r 129, track 6; ember Ø10; tail 48° | outer Ø126; ring r 60.5, track 5; ember Ø8; tail 48° |
| Feed / Diaper buttons | 169 × 96, full pill r 48 | 98 × 60, r 26 |
| Sleep toggle | 350 × 96; r 48, checked or not | EdgeButton Medium 70 (system shape, 143 wide D-shape; content area ≈ 99 × 56, so the icon is stacked over the label) |
| EdgeButton Small | — | 56 (content ≈ 92 × 42; icon + label in a row; Undo only) |
| Tile EdgeButton | — | 46 drawn, 48 hit box |
| Diaper tiles | 106 × 128, r 28 | 62 × 88, r 31 |
| Notice card | 350 × ≥120, r 28 | — |
| Bottom sheets | top corners r 28; handle 32 × 4 | — |
| Chips | 40 visual / 48 target, r 20; offset chips fixed 76 wide | CompactButton 32 visual / 48 target, r 16 |
| Tonal list buttons | — | ≥ 52 tall, r 26 |
| Status cards | — | 156 × 52, r 26 |
| History rows | 56 tall, gap 2, group corners 20, inner corners 6 | 60 tall, r 26, gap 4 |
| Avatars | 40, shape-coded, outlined | 32, shape-coded, outlined |
| Ribbon | card r 20; sleep bars 14 tall, rx 3, min width 6; feed ticks 4 × 14, rx 2; diaper marks Ø10–12 | — |
| Widget | system radius (reference r 24); every button r 20 (the 4×2 sleep toggle too), checked or not | — |
| Save button / FAB | 160 × 56 r 28 / extended FAB r 16 | — |

**A shape never changes with its state.** Pressed, checked, selected and active elements (the big buttons, the sleep toggle, connected button groups, checkbox tiles, chips, widget buttons, watch buttons) keep their corners; fill, border, icon, check and ripple tell the state.

### 5.9 Spacing, targets, elevation

- Spacing scale: 4 · 8 · 12 · 16 · 20 · 24 · 32 · 48.
- Phone gutter 20 (sheets 24). Button row gap 12. Watch ButtonGroup gap 6.
- **40 visual / 48 target pattern** (chips, History pill, CompactButton): the clickable element is 48 tall and transparent; the visible pill inside it is 40 (watch CompactButton 32) and carries the fill, border and radius.
- Minimum targets: 48 dp on phone, widget and watch; Wear tonal buttons ≥ 52 dp tall.
- **Elevation: 0 everywhere. No shadows.** Modal layers use the scrim.

**Input cooldowns** (a double tap must never land on a different target)

| Where | Rule |
|---|---|
| Pickers: phone Diaper sheet chips and tiles; watch Pee / Poop / Both and Earlier; watch Earlier buttons | ignore taps for **500 ms** each time the picker appears (including re-appearing after Earlier); fade 0.6 → 1; exposed as disabled until ready |
| Phone Home after a sheet tile commits | Feed, Diaper and Sleep toggle ignore taps for **600 ms** after the sheet has finished closing (no haptic, no state change) |
| Watch Logged | Undo **and** dismiss inert for **1000 ms** (not when opened from the Tile's undo path) |

### 5.10 Motion

| Item | Spec |
|---|---|
| Scheme | Night: `MotionScheme.standard()`, no overshoot. Day: `expressive()` |
| Ember | one 6° step per minute, 600 ms, easing cubic-bezier(0.2, 0, 0, 1). **Nothing else moves continuously** |
| Notice card | fade in 150 ms, no slide |
| Sheets | standard default-spatial spring |
| Cooldown fades | 0.6 → 1 over 500 ms (pickers); watch Undo 0.4 → 1 over 1000 ms |
| Reduced motion | the ember jumps, no tail, no drain-line transition |
| Never | breathing, pulsing, infinite transitions, ticking seconds |

### 5.11 Haptics (Compose `HapticFeedbackType`)

| Action | Haptic |
|---|---|
| Log Feed / Pee / Poop / Both | `Confirm` |
| Start sleep | `ToggleOn` |
| Woke up | `ToggleOff` |
| Undo, blocked double-toggle, deduped repeat log, validation error | `Reject` |
| Backdate chips, nudges, Adjust choices | `SegmentTick` |

**No sound, ever.** Nothing vibrates that the parent did not trigger.

### 5.12 Timing constants

| Constant | Value |
|---|---|
| Phone receipt sheet | no timeout |
| Phone notices (Undone, guards) | 2 s |
| Watch Logged window | 5 s (`getRecommendedTimeoutMillis`) |
| Tile feedback | 10 s timeline entry |
| Widget receipt | until the next log or 10 s |
| Double-toggle guard / guard message | 2 s / 2 s |
| Dedupe window / dedupe message | 5 s / 2 s |
| "Undone" | phone 2 s, watch 1.5 s |
| Mis-tap sleep | stopped < 60 s → discarded |
| Long-sleep prompt | running > 12 h |
| Soft delete | 7 days |
| Cooldowns | 500 ms picker · 600 ms Home after a sheet · 1000 ms watch Logged |

---

## 6. Icons

### 6.1 Style

- 24 × 24 viewport, no fill, stroke = tint, stroke width 2 (in viewport units, so it scales with the icon), round caps and joins. Convert to vector drawables as given.
- Colour: the event **strong** colour on a container; otherwise onSurfaceVariant (or as stated).
- Action icons always sit next to a text label. Icon-only buttons get a content description.

| Use | Phone | Watch |
|---|---|---|
| Big buttons | 32 | ButtonGroup 26 |
| EdgeButton | — | 24 (Small: 20) |
| Rows, notice card, Lamp label | 20 | Lamp label 14; status line 16 |
| Chips | 18 (selected check 16) | CompactButton 20 |
| Lists / avatars | 22 inside a 40 avatar | 18 inside a 32 avatar; status cards 18 |
| Diaper tiles | 40 | 32 |
| Logged check / Undone | — | 32 |
| Status icons (sync, backup) | 20 | 20 |
| Widget | row 1: 16; buttons: 24 | — |
| Tile | — | header 24, Row A 24, Row B 20, title glyphs 14 |

`both` is two-tone. Its two paths use explicit colours instead of the tint: path 1 PEE strong, path 2 POOP strong (Night `#8FD0C3` + `#358276`; Day `#2C7468` + `#143F38`).

### 6.2 Path data

Each quoted string is one path. All are stroked unless noted.

| Name | Geometry / use | Paths |
|---|---|---|
| `sleep` | crescent moon | `M20 14.6A8.5 8.5 0 1 1 9.4 4a7 7 0 0 0 10.6 10.6z` |
| `wake` | half sun on the horizon (Woke up) | `M3 19.5h18` `M7 19.5a5 5 0 0 1 10 0` `M12 5v3` `M5.3 10.3l2.1 2.1` `M18.7 10.3l-2.1 2.1` |
| `feed` | baby bottle | `M10 5v-.5a2 2 0 0 1 4 0V5` `M8.5 5h7v3h-7z` `M9 8 8 10.5v8.5a2.5 2.5 0 0 0 2.5 2.5h3a2.5 2.5 0 0 0 2.5-2.5v-8.5L15 8` `M10.5 13.5h3` `M10.5 17h3` |
| `diaper` | diaper, front view | `M3 6.5h18v4a3.5 3.5 0 0 0-3.5 3.5v1.5a3 3 0 0 1-3 3h-5a3 3 0 0 1-3-3V14A3.5 3.5 0 0 0 3 10.5z` `M3 9h18` |
| `pee` | droplet | `M12 3.5C9 7.5 6.5 10.5 6.5 14a5.5 5.5 0 0 0 11 0c0-3.5-2.5-6.5-5.5-10.5z` |
| `poop` | three soft tiers with curl | `M6.5 20.5h11a2.5 2.5 0 0 0 0-5h-2.25a2.25 2.25 0 0 0 0-4.5H13a2 2 0 0 0 0-4c0-1.2-.6-2.3-1.8-3L11 7a2 2 0 0 0 0 4H8.75a2.25 2.25 0 0 0 0 4.5H6.5a2.5 2.5 0 0 0 0 5z` |
| `both` | droplet (path 1, PEE strong) + small mound (path 2, POOP strong) | `M7.5 3C5.6 5.6 4 7.6 4 9.9a3.5 3.5 0 0 0 7 0C11 7.6 9.4 5.6 7.5 3z` `M13 20.5h6a2 2 0 0 0 0-4h-.2A2.8 2.8 0 0 0 16.6 13c0-.9-.4-1.7-1.2-2.3l-.4 2.3a2.8 2.8 0 0 0-1.8 3.5H13a2 2 0 0 0 0 4z` |
| `history` | clock with back arrow | `M3.5 12a8.5 8.5 0 1 0 2.5-6` `M3.5 3.5V8H8` `M12 7.5V12l3 2` |
| `edit` | pencil | `M5 19l.9-3.9L16.1 4.9a2.05 2.05 0 0 1 2.9 2.9L8.8 18.1z` `M14.5 6.5l3 3` |
| `undo` | hooked arrow | `M9 14 4 9l5-5` `M4 9h10.5a5.5 5.5 0 0 1 0 11H11` |
| `delete` | bin | `M4 6.5h16` `M9.5 6.5v-2h5v2` `M6.5 6.5l.9 12.6a2 2 0 0 0 2 1.9h5.2a2 2 0 0 0 2-1.9l.9-12.6` `M10 10.5v6` `M14 10.5v6` |
| `profile` | head and shoulders | `M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z` `M4.5 20.5a7.5 7.5 0 0 1 15 0` |
| `settings` | two sliders | `M4 7h8.5` `M16.5 7H20` `M4 17h3.5` `M11.5 17H20` `M14.5 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z` `M9.5 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z` |
| `phone` | phone outline | `M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5z` `M10.5 18.5h3` |
| `synced` | cloud with check | `M7 19h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6 10.1 4.5 4.5 0 0 0 7 19z` `M9.5 14.5l2 2 3.5-3.5` |
| `pending` | cloud with up arrow | `M7 19h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6 10.1 4.5 4.5 0 0 0 7 19z` `M12 17v-5` `M9.75 14.25 12 12l2.25 2.25` |
| `offline` | cloud with slash | `M8.6 6.5A6 6 0 0 1 17.6 11a4 4 0 0 1 2.1 6.9` `M16 19H7a4.5 4.5 0 0 1-1-8.9` `M3 3l18 18` |
| `check` | tick | `M5 12.5l4.5 4.5L19 7.5` |
| `plus` | plus | `M12 5v14` `M5 12h14` |
| `back` | left arrow | `M19 12H5` `M11 18l-6-6 6-6` |
| `chevronLeft` | | `M14.5 6l-6 6 6 6` |
| `chevronRight` | | `M9.5 6l6 6-6 6` |
| `chevronDown` | | `M6 9.5l6 6 6-6` |
| `close` | X | `M6 6l12 12` `M18 6 6 18` |
| `clock` | clock face (backdate, Adjust start, Earlier, time fields) | `M12 20.5a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17z` `M12 7.5V12l3 2` |
| `awake` | sun with 8 rays | `M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z` `M12 2.5v2` `M12 19.5v2` `M2.5 12h2` `M19.5 12h2` `M5.3 5.3l1.4 1.4` `M17.3 17.3l1.4 1.4` `M5.3 18.7l1.4-1.4` `M17.3 6.7l1.4-1.4` |
| `sleepRunning` | ring + filled crescent = "a sleep is running" on glance surfaces (widget, tile, complication, notification small icon). Static, no ember, so it cannot go stale | ring (stroked): `M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 0 0 0-19z` + crescent (**filled**, no stroke): `M16 13.3A4.25 4.25 0 1 1 10.7 8a3.5 3.5 0 0 0 5.3 5.3z` |
| `chart` | a baseline and three uneven bars (History's button to Statistics, P8) | `M3.5 20.5h17` `M7 17v-5` `M12 17V6` `M17 17v-8` |
| `watchSynced` | watch with a tick on the face (§4.5) | `M8.5 5.5h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z` `M9.5 5.5 10 3h4l.5 2.5` `M9.5 18.5l.5 2.5h4l.5-2.5` `M9.25 12.25 11 14l3.75-3.75` |
| `watchPending` | watch with "…" | `M8.5 5.5h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z` `M9.5 5.5 10 3h4l.5 2.5` `M9.5 18.5l.5 2.5h4l.5-2.5` `M9 12h.01` `M12 12h.01` `M15 12h.01` |
| `watchOffline` | watch with slash (body broken at the slash) | `M10 5.5h5.5a2 2 0 0 1 2 2v6` `M14 18.5H8.5a2 2 0 0 1-2-2v-6` `M9.5 5.5 10 3h4l.5 2.5` `M9.5 18.5l.5 2.5h4l.5-2.5` `M4 4l16 16` |
| `watchError` | watch with "!" | `M8.5 5.5h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z` `M9.5 5.5 10 3h4l.5 2.5` `M9.5 18.5l.5 2.5h4l.5-2.5` `M12 9v3` `M12 15.5h.01` |
| `phoneSynced` | `phone` with a tick on the screen | `M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5z` `M10.5 18.5h3` `M9.25 10.75 11 12.5l3.75-3.75` |
| `phonePending` | `phone` with "…" | `M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5z` `M10.5 18.5h3` `M9 10.5h.01` `M12 10.5h.01` `M15 10.5h.01` |
| `phoneOffline` | `phone` with slash (body broken at the slash) | `M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v9.5` `M16 21.5H8A1.5 1.5 0 0 1 6.5 20v-9.5` `M10.5 18.5h3` `M3 3l18 18` |
| `cloudError` | cloud with "!" (the backup failed) | `M7 19h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6 10.1 4.5 4.5 0 0 0 7 19z` `M12 10v3` `M12 16h.01` |

29 icons (`chart` was added with Statistics). All were render-checked legible at 18 dp and up; `sleepRunning` at 16, 24 and 56 dp on black.

### 6.3 Shape-coded avatars (lists)

A 48 × 48 viewport drawn at 40 dp (phone) or 32 dp (watch). The shape is **filled with the event container** and **outlined in the event strong colour** (stroke 2 in viewport units). The event icon is centred on top at 55 % of the avatar size (22 dp / 18 dp) in the event strong colour. Diaper outlines (Pee / Poop / Both) all use **DIAPER strong** (Night and watch `#62B3A5`, Day `#24665C`). Do not clip: the scallop lobes and the stroke reach the viewport edge.

| Event | Shape (viewport 48 × 48) |
|---|---|
| Sleep | circle, centre 24,24, r 22 |
| Feed (soft 9-lobe scallop) | `M31.01 4.74A8.6 8.6 0 0 1 41.75 13.75A8.6 8.6 0 0 1 44.19 27.56A8.6 8.6 0 0 1 37.18 39.70A8.6 8.6 0 0 1 24.00 44.50A8.6 8.6 0 0 1 10.82 39.70A8.6 8.6 0 0 1 3.81 27.56A8.6 8.6 0 0 1 6.25 13.75A8.6 8.6 0 0 1 16.99 4.74A8.6 8.6 0 0 1 31.01 4.74Z` |
| Pee / Poop / Both | rounded square x 2, y 2, 44 × 44, corner radius 14 |
| Both fill | the rounded square in PEE container, plus its right half (x ≥ 24) in POOP container clipped to the rounded square; then the outline on top |

Why outlined: the container fill alone is only 1.01–1.27:1 against the row. The strong outline is 4.05–9.86:1, so the silhouette (circle / scallop / square) carries the event for colour-blind users.

### 6.4 Ribbon marks (phone History)

| Mark | Drawing |
|---|---|
| Sleep | bar, height 14, corner rx 3, SLEEP strong. Minimum width 6 (widen around its centre); never a pill wider than tall |
| Feed | tick 4 × 14, rx 2, FEED strong, centred on the time |
| Pee | ring r 5, stroke 2, PEE strong, filled with the card colour |
| Poop | dot r 6, POOP strong |
| Both | the Pee ring + its right half-disc (r 6) filled POOP strong |

---

## 7. Formatting grammar

One grammar everywhere the app renders text, including the tile and complication dynamic text.

| Kind | Rule | Examples | Never |
|---|---|---|---|
| Duration | unit letters; whole minutes, floored; minutes zero-padded when hours are shown; under 1 minute = `<1m`; ≥ 24 h = `1d 3h` | `48m`, `2h 10m`, `1h 07m`, `10h 45m`, `<1m` | `1:07`, `1:07:12`, `1h 7m`, `67 min` |
| Time since | duration + " ago"; under 1 minute = `just now` | `48m ago`, `just now` | `0m ago` |
| Clock time | follows the system 12/24 h setting; 12 h always shows AM/PM; 24 h always has a colon | `13:44`, `1:44 PM` | `13.44`, `1344` |
| Range | phone: en dash with spaces; watch: en dash without spaces | `12:40 – 13:27` (phone), `12:40–13:27` (watch) | |
| Relative + absolute | shown together wherever a time-since appears, **except** complications, the tile and the widget | `48m ago · 13:44` | |
| Day prefix | only when an entry starts on another day | `Sun 22:40 – 01:35` | |
| Diaper kind | status surfaces lead with `Pee` / `Poop` / `Both`; receipts and History write Both as `Pee + poop` | `Pee 48m ago · 13:44` | `Diaper 48m ago · 13:44 · Pee` |
| Offsets | minus sign U+2212, unit letter | `−5m`, `+5m` | `-5m` (hyphen) |
| Widget | absolute times only, always with "at" | `Fed at 12:22` | `Fed 12:22` |
| Tile title | "Fed 14:32" (no "at"; width) | | |

**Hero split**: a duration renders as number/unit pairs. 130 min → `2` `h` `10` `m`; 48 min → `48` `m`.

**Counts**: "1 feed" / "5 feeds"; "pee", "poop" and "sleep" do not change.

**No ticking durations.** No seconds, no chronometer anywhere:
- Phone Live Update chip: "Asleep" (no chronometer). Its text shows whole minutes ("1h 07m · since 02:05"), re-rendered once a minute, and its Android 17 metrics use the system's TimeDifference.
- Wear Ongoing Activity / Live Update: "Asleep since 02:05".
- Tile and complications format their own dynamic text in this grammar.
- **The only exemption**: the pre-Wear OS 4 complication fallback `TimeDifferenceComplicationText` with `SHORT_DUAL_UNIT` (system format "1h 5m").

**Spoken forms** (TTS reads "48m" as metres; §9.5): "2h 10m" → "2 hours 10 minutes"; "48m ago" → "48 minutes ago"; "<1m" → "less than a minute"; "−5m" → "5 minutes earlier"; "+5m" → "5 minutes later".

---

## 8. Night behaviour

### 8.1 Appearance setting

**Auto (default) · Night · Day.**

Auto = Night when **any** of these holds:
- the system dark theme is on;
- the time is between local sunset and sunrise (coarse location if granted; otherwise an editable 19:00–07:00 window);
- **a sleep is running** ("Dim for naps too", default on).

Apply with `UiModeManager.setApplicationNightMode()` (API 31+).

Consequence: with Auto and naps on, a running sleep always gets the Night theme and therefore lights-down. "Day while a sleep runs" happens only when the user fixes Day (or turns naps off).

### 8.2 Lights-down (Night + sleep running)

| Changes | Stays |
|---|---|
| top-bar text and icons → onSurfaceVariant | every control's position |
| History pill loses its fill | Feed / Diaper / Sleep fills |
| glow → SLEEP at 0.45 (+ orbit) | layout bands |
| Today grid numbers → onSurfaceVariant (captions unchanged) | since-rows, notice card |

Day with a running sleep: no dimming, and the Lamp is the SLEEP disc with track.

### 8.3 Night tuning

- Motion: `standard()` scheme, no overshoot (Day: `expressive()`).
- Ripple (`RippleConfiguration`) alpha lowered at night.
- Splash follows the theme (§3.3.8).

### 8.4 Night dimmer (opt-in)

- Offered **once** in the whisper slot, the first time a sleep starts while Night is active: "Too bright? Dim this screen while Noa sleeps" · Dim · No thanks. Re-offered from Settings.
- Brightness = `min(current, target)` (target default 0.10, adjustable 5–30 %). Released when the app leaves the foreground. **Never in Day.**
- The first activation shows the card "Screen dimmed for night" with Undo.
- Never forced by default (open question 6).

### 8.5 Glance surfaces at night

- **Widget**: follows the **app's** resolved Appearance (Auto = Night between sunset and sunrise or while a sleep runs; otherwise the user's fixed choice), **never the launcher's uiMode**. Asleep → always Night under Auto. Without this rule, a phone kept in light mode shows the Day widget (fills at L* ≈ 91) at 3 am.
- **Tile**: fixed dark neutral chrome, container fills only (night-safe; asleep EdgeButton fill L* ≈ 24).
- **Watch**: dark only. Ambient on every screen (§10.6).

---

## 9. Accessibility

### 9.1 Contrast (WCAG 2.x)

Text needs 4.5:1 and graphics 3:1. 125 token pairs were verified with 0 failures. Tightest and key values:

| Pair | Ratio |
|---|---|
| Night onSurface / primary `#E8D8C3` on all phone surfaces | ≥ 10.23 |
| Night onSurfaceVariant (tightest) | 5.94 |
| Night outline / Day outline / watch outline | 3.37 / 3.57 / 3.65 |
| Night onPrimaryContainer on primaryContainer · Day | 9.14 · 12.70 |
| Day primary text on all Day surfaces | ≥ 8.63 |
| Every onContainer on its container: Night · Day | ≥ 8.45 · ≥ 9.64 |
| BOTH label on the split halves: Night · Day | 9.19 · 9.64 |
| Idle glow core: numeral / onSurfaceVariant / FEED text / chip outline | 12.16 / 7.06 / 9.42 / 4.00 |
| Lights-down glow core: onSurface / onSurfaceVariant / SLEEP text / chip outline | 12.65 / 7.35 / 8.94 / 4.16 |
| Watch glow core: onSurface / onSurfaceVariant / SLEEP text / ember | 12.61 / 7.33 / 8.92 / 5.18 |
| Night ember on background / on track | 5.81 / 3.41 |
| Watch ambient ring and dot on black | 6.18 |
| Day track on paper / on SLEEP disc; Day ember on its cut-out | 4.14 / 3.53; 8.55 |
| Day FEED label on FEED disc; Day SLEEP label on SLEEP disc | 5.72; 7.29 |
| SLEEP icon on container / containerLit; POOP icon on container | 3.41; 3.49 |
| POOP marks phone / watch; Day FEED marks | 3.53 / 3.40; 3.74 |
| Avatar outlines vs list row (Night / Day / watch, min) | 5.23 / 4.05 / 5.13 |
| Undo-card drain line (solid outline): Night / Day | 3.37 / 3.57 |
| Selected chip border on card / sheet: Night · Day | 3.37 / 4.44 · 3.57 / 4.47 |
| Watch guard "Already / asleep" `#D6DBF6` on `#2E3660` | 8.45 |
| Watch bezel drain arc on black | 4.95 |
| Widget Undo (primary) on the widget body: Night / Day | 12.73 / 10.08 |
| Distinctness (ΔE2000): primaryContainer vs FEED container Night / Day; now-line vs feed tick | 10.8 / 10.7; 15.4 |

**Usage rules**
- Strong event colours never set text.
- No alpha on text. A disabled control at 0.38 is exempt as an inactive component, and so are cooldown fades.
- Decorative tracks (the tail) are the only alpha strokes. Drain lines are solid outline.
- Any token change must be re-verified against these pairs.

### 9.2 Not by colour alone

- Diaper kinds always have a word (Pee / Poop / Both) and a distinct glyph.
- List avatars are shape-coded and outlined (circle = sleep, scallop = feed, rounded square = diaper).
- Ribbon marks differ in shape: bar = sleep, tick = feed, ring = pee, dot = poop, ring + half = both.
- BOTH is drawn two-tone, never as a third hue.

### 9.3 Targets and layout

- 48 dp minimum targets (phone, widget, watch). Wear tonal buttons ≥ 52 dp tall. Tile EdgeButton 48 dp hit box.
- 200 % (non-linear) font scale on phone: the content above the button block scrolls; the button block stays pinned above the navigation inset; the whisper slot grows (min 128); button labels wrap to two lines; the hero autosizes.
- Watch: percentage padding; ButtonGroup stacks above 1.15 font scale; Lamp numeral autosize.

### 9.4 Semantics

| Element | Semantics |
|---|---|
| Sleep toggle (phone, widget) | `ToggleButton` with the on/off announcement overridden: `stateDescription` "Asleep since 02:05" / "Awake since 13:27"; click label "End sleep" / "Start sleep". The name switches "Start sleep" / "Woke up, end sleep" and does not double-announce a pressed state |
| Lamp | canvas `clearAndSetSemantics {}`; the text carries `stateDescription` "Asleep, 1 hour 7 minutes, since 02:05". **Never a live region on the ticking value** |
| Since-rows | `mergeDescendants`, click label "Edit". Spoken name e.g. "Last diaper, pee, 48 minutes ago, at 13:44. Edit"; "Awake 1 hour 5 minutes, since 13:27. Edit" |
| Notice card; receipt time line | `liveRegion = Polite` |
| Watch Logged | title `liveRegion = Polite`; content in a status layer above the dismiss target |
| Single-select chips | expose the selected state |
| Disabled chips | disabled (0.38) |
| Cooldown targets, inert watch Undo | exposed as disabled until ready |
| Sheets | dialog, modal, labelled by their title |
| History rows | custom actions "Edit time", "Delete"; swipe-to-reveal Delete via `AnchoredDraggable` (not `SwipeToDismissBox`) |
| Ribbon | decorative, with a text summary |
| History next-day chevron | "Next day" (disabled on today); previous "Previous day" |
| Edit sheet | time fields named with their block: "Fell asleep, 12:35, was 12:40. Change time"; day chips "Fell asleep day, Today"; nudges "Fell asleep, 5 minutes earlier", "Woke up, 1 minute later" |
| Content descriptions | describe purpose: "Log pee diaper", "Start sleep", "Settings", "Back", "Close", "Edit start time", "Undo poop" |

### 9.5 Spoken durations

Every app-rendered duration carries a spoken form (`Modifier.semantics { contentDescription = … }` or `TtsSpan.MeasureBuilder().setNumber(n).setUnit("hour" | "minute")`):

| Shown | Spoken |
|---|---|
| "2h 10m" | "2 hours 10 minutes" |
| "48m ago" | "48 minutes ago" |
| "<1m" | "less than a minute" |
| "−5m" / "+5m" chips | "5 minutes earlier" / "5 minutes later" |
| watch "−5m · 14:27" | "5 minutes earlier, 14:27" |
| complication | `ShortTextComplicationData.Builder(text, PlainComplicationText("Last feed 2 hours 10 minutes ago"))` |
| tile button | `setContentDescription("Log feed. Last feed 2 hours 10 minutes ago")` |

### 9.6 Other

- Reduced motion: the ember jumps, no tail, no drain-line transition.
- High-contrast variant (`UiModeManager.getContrast()`, API 34+): maps onSurfaceVariant → onSurface and outline → onSurfaceVariant. It needs its own verified token set before shipping.
- No sound. Haptics only for parent-triggered actions.

---

## 10. Implementation notes

### 10.1 The Lamp in Compose

- `Canvas`: glow `drawCircle(Brush.radialGradient)` with a dithered `Paint`; track `drawArc`; tail as short arcs or a sweep gradient; ember `drawCircle`.
- The ember angle updates once per minute: a `LaunchedEffect` aligned to the minute boundary, then `animateFloatAsState` (600 ms, standard easing). No infinite transitions, no breathing.
- Animate the accumulated angle (elapsed × 6) rather than the value mod 360, so the step from 354° to 360° does not spin backwards.
- Semantics per §9.4. Hero `BasicText(autoSize = TextAutoSize.StepBased(56.sp, 76.sp))`.
- On the watch, the Lamp canvas inside `TransformingLazyColumn` must opt out of the default morph transform.

### 10.2 Watch Main: pinned EdgeButton

Use `ScreenScaffold(timeText, scrollIndicator)` **without** the `edgeButton` slot, containing:

```
Box {
  TransformingLazyColumn(contentPadding = PaddingValues(top = 23.dp, bottom = 84.dp))  // 70 + 3 + 3 + 8
  EdgeButton(Modifier.align(Alignment.BottomCenter), buttonSize = EdgeButtonSize.Medium)
}
```

ScreenScaffold's `edgeButton` slot keeps the button collapsed until the last list item is visible. Main's content is far taller than the screen, so "Woke up" would never show at rest. Watch History keeps the stock collapsing EdgeButton.

### 10.3 Phone layout and insets

- **Portrait only**: `MainActivity` is locked to `screenOrientation="portrait"`; the app does not rotate to landscape.
- Lay out from `WindowInsets.statusBars / navigationBars / displayCutout`, not fixed 24 dp bands. Support predictive back.
- **Continuous Lamp size**: H = window height − statusBars − navigationBars (dp). Lamp Ø = clamp(264 − (796 − H), 220, 264). The hero autosizes with it (56–76 sp). The whisper slot keeps its 128 dp minimum. Below Ø220, the area above the button block scrolls. Example: 3-button navigation (48 dp) on an 844 dp screen → H = 772 → a 240 dp Lamp.
- 200 % font scale: §9.3.

### 10.4 Edit sheet

§3.3.4: `TimePickerDialog` (keyboard `TimeInput` mode, dial toggle), DatePicker for "Earlier date…", nudges with `SegmentTick`, validation slot.

### 10.5 Phone Live Update (running sleep only)

- Manifest `POST_PROMOTED_NOTIFICATIONS`. Check `NotificationManager.canPostPromotedNotifications()` and fall back to a normal ongoing notification. Link to `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` from Settings.
- Builder settings:
  - `setOngoing(true)`, `setRequestPromotedOngoing(true)`.
  - Title "Noa is asleep", text "1h 07m · since 02:05" (whole minutes and the absolute start; no seconds). A non-waking `AlarmManager.RTC` alarm at each minute boundary re-renders it while the notification shows, so a phone that wakes up shows the current value; it is cancelled with the notification.
  - `BigTextStyle` (below Android 17) adds the last feed, the last diaper and Today's counts.
  - `setShortCriticalText("Asleep")` for the status chip (small icon `sleepRunning`), `setShowWhen(false)`, and **no** `setUsesChronometer`.
  - Action "Woke up" with `setAuthenticationRequired(false)`.
  - `VISIBILITY_PUBLIC`; channel `IMPORTANCE_LOW`; `setSilent(true)`; `setOnlyAlertOnce(true)`.
- Detect dismissal with `setDeleteIntent` and do not repost.
- Android 17 `MetricStyle`: "Asleep for" (`TimeDifference.forStopwatch(start, FORMAT_ADAPTIVE)`), "Since" 02:05 (FixedTime), "Last fed · L → R" (`TimeDifference.forStopwatch(feed, FORMAT_ADAPTIVE)`). The durations are the system's, so they stay current on the lock screen without a re-render; the parent should not have to subtract times at 3am.
- "Woke up" posts the woke-up notification (§3.3.7) with three Earlier actions (`setAuthenticationRequired(false)`); each action's broadcast carries the tap time and the offset applied when it was posted, and the receiver updates the sleep's end and re-posts.
- Ask for `POST_NOTIFICATIONS` in context on the first Start sleep.

### 10.6 Wear running sleep, ambient, gestures

- **Ongoing Activity** (Wear OS ≤ 6) / **Live Update** (Wear OS 7+):
  - static white `sleepRunning` vector icon, `CATEGORY_STOPWATCH`;
  - status template "Asleep since #start#" with `Status.TextPart("02:05")`. No `StopwatchPart`. The Wear OS 7 Live Update uses the same text;
  - touch intent to the sleep screen.
- Deduplicate the phone-bridged Live Update against the watch one by sleep-session UUID. When the sleep ends on either device, dismiss both.
- Double-pinch (`Modifier.oneHandedGesture`, Wear OS 7, Pixel Watch 3+) is bound only to the Woke up / Start sleep EdgeButton.
- **Ambient on every watch screen** (apps targeting SDK 36 are always-on). Minute precision via `AmbientTickEffect`; ≥ 85 % black; no gradients, no motion; positions unchanged; critical UI kept ≥ 10 px from the edge.

| Screen | Ambient rendering |
|---|---|
| Main idle | buttons become 2 dp outlines in their strong colour, no fills, text `#B8A48E` |
| Diaper picker | outline tiles |
| Logged | bezel arc hidden; Undo outlined; the 5 s window keeps running and expiry returns to Main |
| Main asleep | §3.5.2 ambient |

### 10.7 Dedupe, idempotency, guards, undo

- Dedupe and toggle rules per §4.4, enforced in the repository, in `onTileRequest` and in the widget callback.
- Local-first Room writes on each device. Watch → phone via `DataClient` with `setUrgent()`. Times stored in UTC.
- Guard feedback: phone card row 2; watch EdgeButton two-line state; tile titleSlot; widget toggle (2 s, `Reject`).
- Phone undo: 10 s via `calculateRecommendedTimeoutMillis(…, containsControls = true)`, card `liveRegion = Polite`.
- Watch undo: 5 s via `getRecommendedTimeoutMillis`, drawn as `CircularProgressIndicator(progress = { remaining }, startAngle = 135f, endAngle = 45f, strokeWidth = 2.dp)` in outline (the 270° arc open around the Undo EdgeButton). Any tap outside Undo dismisses.
- When Logged opens from the tile's undo path (intent extra `fromTileUndo`), Undo and dismiss are active at once and the 5 s drain starts at open. The 1 s inert period only absorbs a double tap on an in-app log button.
- Undo is never the only recovery path:
  - phone: the Lamp, since-rows and History rows open the edit sheet;
  - watch: Undo on Logged; anything older is edited on the phone.
- Deletes are soft for 7 days, with an Undo receipt.

### 10.8 History swipe

Swipe-to-reveal Delete uses `AnchoredDraggable` (not `SwipeToDismissBox`). Custom actions "Edit time" / "Delete" mirror it.

### 10.9 Tile (`Material3TileService`, `primaryLayout`)

- Clickables use `loadAction` with ids `log_feed`, `log_pee`, `log_poop`, `log_both`, `sleep_toggle`. Writes are idempotent.
- Feedback = a 10 s timeline entry that changes only the **titleSlot** ("Fed 14:32 · Undo") and the Feed secondary label ("just now"). During that window the tile background `onClick` opens the watch Logged screen for that entry with `fromTileUndo`.
- No button ever relabels or moves. The EdgeButton relies on the ProtoLayout 48 dp minimum clickable size.
- Time-since text is a `DynamicDuration` formatted to the app grammar (hours + zero-padded minutes via `DynamicInt32.format` with `minIntegerDigits(2)`). Static fallback "--".
- Asleep state and guard per §3.6. Below 225 dp the title is hidden.
- `allowDynamicTheme = false` (a deliberate deviation from Google's tile guidance, §10.13).
- Preview image 400 × 400 of the idle state.
- The mainSlot is designed to become a 2 × 2 Wear Widget later.

### 10.10 Complications

- Types and content per §3.7.
- Use `DynamicComplicationText` on Wear OS 4+, so the text follows the app grammar and LONG_TEXT can combine two time references. Fallback: `TimeDifferenceComplicationText` `SHORT_DUAL_UNIT`. Never `STOPWATCH`. No RANGED_VALUE.
- Diaper SHORT_TEXT: MONOCHROMATIC_IMAGE + SMALL_IMAGE = the kind glyph. The title "Pee" is used only by faces that draw no image: the stock `ShortTextLayoutHelper` gives the title empty bounds when an image is present.
- `UPDATE_PERIOD_SECONDS = 0` plus `requestUpdate()` on every log.
- Monochrome + small images, white service icon. Taps open the matching screen and never log.

### 10.11 Glance widget

- 4×2 and 3×1 with `SizeMode.Responsive`. A 2×2 falls back to the 4×2 content without the status row.
- `actionRunCallback` for the sleep toggle and Undo (no app launch), idempotent writes; Feed and Diaper start the app on their sheet (`pooparazzi://home/feed`, `pooparazzi://home/diaper`). 48 dp targets. System corner radius. Generated previews (Glance 1.2 `providePreview`).
- Absolute clock times only: RemoteViews cannot tick a relative "ago" in the app grammar, and the update budget would leave it stale.
- Update the widget on every log and every sleep change.
- **Palette**: build every colour as a single-value `ColorProvider` chosen at update time from the NIGHT / DAY token sets. No day/night pairs and no `GlanceTheme` dynamic colours. Call `updateAll()` at sunset, at sunrise, on an Appearance change and on every sleep start/stop.
- **Type**: device font (`FontFamily.SansSerif`, sizes in sp as drawn). No ROND, opsz or tnum.
- **Checked toggle stroke**: `GlanceModifier` has no border, so the 2 dp stroke is a background drawable (a shape with a 2 dp stroke and the button's own corners) set through `GlanceModifier.background(ImageProvider)`.
- **Receipt and Undo**: after a widget log, row 1 shows the receipt and an in-widget "Undo" (48 dp target, `actionRunCallback(UndoLast)` for that event UUID, no app launch) until the next log or for 10 s (one-time WorkManager work). Undo never sits where a log button is.
- **Background tap**: opens the app with the receipt for the last widget log. Row 1 is not a separate target (it is only 24 dp tall).
- **Breakpoints**:

| Condition | Change |
|---|---|
| 4×2 narrower than 320 dp, or font scale ≥ 1.3 | hide the end-aligned "Awake since 13:27" / "Asleep since 02:05" in row 3. While asleep, show "Asleep since 02:05" in row 1 instead. (The guideline 4×2 is 276 dp wide, 252 inside the padding, against the 294 dp row 3 needs) |
| 3×1 where a word of "Start sleep" does not fit its button | label "Sleep" (unchecked) / "Woke up" (checked) |
| 3×1 where any label still does not fit | icon-only buttons with content descriptions "Log feed" / "Log diaper" / "Start sleep" |
| 3×1 narrower than 180 dp (an old 2×1) | padding 8, gaps 4, so the three 24 dp icons still fit |

- QS sleep tile: optional, v1.1.

### 10.12 Fonts

- **Phone** bundles the Google Sans Flex variable font (Latin subset) and Atkinson Hyperlegible Next. Downloadable fonts cannot set ROND or opsz.
- **Watch** uses the Wear system Brand font through `Typography` size overrides. It bundles only a digits-and-units subset of Google Sans Flex (ROND 100) for the Lamp and status numerals.
- Tiles, complications and the Glance widget render in the system font.

### 10.13 Colour

- Fixed tokens everywhere. `allowDynamicTheme = false` on the tile. No `GlanceTheme` dynamic colours. Reason: a watch-face or wallpaper hue must not pass for an event colour. This is a deliberate deviation from Google's tile guidance.
- High-contrast variant: §9.6.

### 10.14 Watch standalone and cross-device

- Logging never needs the phone. Offline copy per §3.5.
- Never a dead-end "check connection" screen.

### 10.15 Optional accelerators (not v1 unless testing asks)

- Long-press Feed / Diaper / Sleep opens "Log earlier".
- A "Hold to log" (500 ms) setting for pocket taps.

---

## 11. Key design decisions

| Decision | Rationale |
|---|---|
| The orbit ring means only "a sleep is running" | One meaning per visual. Idle Lamps have no ring; the watch Undo countdown is a separate neutral bezel arc |
| Chrome is neutral; colour = event | Save, FAB, selected chips, Undo and the now-line never share an event hue (e.g. amber means FEED only) |
| Time-since lives in the since-rows, not on buttons | The rows double as the one-tap correction path. The Lamp already holds the top fact. Button labels never change length (stable at 200 % and identical day/night) |
| Receipt titles are time-free | The phone receipt sheet puts the effective time on its own line under the title, following every chip tap. Exception: the watch Logged title keeps the time, since nothing else on that screen shows it |
| Diaper stamps the time on the first tap | The picker title shows the stamp; backdate with chips (phone) or Earlier (watch) |
| Lights-down keeps fills and positions | Muscle memory at 3 am; only the chrome and the glow dim |
| The phone Lamp text opens the edit sheet of the entry it shows (the last feed, or the running sleep), like a since-row; the watch has no edit path (the phone has) | The top fact is the first thing a parent wants to correct. The edit sheet writes nothing until Save, so a stray tap edits nothing |
| Watch "Woke up" = Medium EdgeButton | Large and always visible: Main fits one screen, as the Tile |
| No "Earlier" on watch Logged | It would sit where Feed / Diaper just were (double-tap hazard). Backdating a feed or wake time is done on the phone |
| Input cooldowns and the 2 s guard | A double tap must never land on a different target or toggle a sleep twice |
| Sleeps < 60 s are discarded | They are mis-taps; this removes junk entries without an extra control |
| No ticking system durations | One grammar everywhere; no h:mm:ss beside the system clock |
| Tile / widget feedback never relabels or moves a button | Undo lives in the title / receipt row and the background tap |
| Widget shows absolute times only | Relative times would go stale within the update budget |
| Fixed palette, no dynamic colour | A wallpaper or face hue must not pass for an event colour |
| One numeral voice per line (digit rule) | Two digit designs never sit side by side |
| Shape-coded, outlined avatars | Redundancy for colour-blind users at no added clutter |

---

## 12. Open questions for the user

1. **Two caregivers.** Answered by families (BABIES_AND_BACKUP §3.4–3.5): members invited by a single-use link share the log and the babies. "Who logged it" and partner notifications are not part of it.
2. **Feeding details.** Is one "Fed" timestamp enough, or do you want breast side (left/right), bottle amount or feed duration? Each adds a step after the tap.
3. **Diaper on the phone Home.** Keep "Diaper → Pee / Poop / Both" (2 taps, big buttons; the widget and tile are 1 tap), or put Pee | Poop | Both directly on Home as three smaller buttons?
4. **Day boundary.** Should "Today" start at midnight (designed) or at a time like 07:00, so a night is not split across two days?
5. **Night appearance.** Is automatic Night by sunset/sunrise and during naps right for you, or do you prefer fixed hours or simply following the phone's dark mode?
6. **Screen dimmer.** Keep it opt-in (designed), or dim automatically at night with an Undo?
7. **Wording.** "Pee / Poop / Diaper" (designed) or "Wee / Poo / Nappy"?

---

## Appendix A: Reference scenario

**Baby Noa. Today is Mon 26 Oct 2026. Now is 14:32.** Night views show **03:12** the same morning.

| Time | Event |
|---|---|
| Sun 22:40 – Mon 01:35 | Sleep 2h 55m (crosses midnight) |
| 01:40 | Fed |
| 01:52 | Pee |
| 02:05 – 05:20 | Sleep 3h 15m (**running at the 03:12 snapshot**) |
| 05:25 | Fed |
| 05:41 | Pee + poop (Both) |
| 06:10 – 07:45 | Sleep 1h 35m |
| 08:00 | Fed |
| 08:20 | Poop |
| 09:30 – 10:15 | Sleep 45m |
| 10:30 | Fed |
| 11:05 | Pee |
| 12:22 | Fed |
| 12:40 – 13:27 | Sleep 47m |
| 13:44 | Pee |

| Derived | At 14:32 | At 03:12 |
|---|---|---|
| Lamp | Last fed **2h 10m** ago, at 12:22 | Asleep **1h 07m**, since 02:05; ember 42° |
| Since-row 1 | Awake **1h 05m**, since 13:27 | Last fed **1h 32m** ago, at 01:40 |
| Since-row 2 | Pee **48m** ago, at 13:44 | Pee **1h 20m** ago, at 01:52 |
| Today | 5 feeds · 4 pee · 2 poop · 7h 57m sleep | 1 feed · 1 pee · 0 poop · 2h 42m sleep (includes the running sleep) |
| Sync | "Synced with watch · 14:31" / "Synced with phone · 14:31" | "Synced with watch · 03:11" |

---

## Appendix B: Not designed or deferred

**Described but not drawn.** These need layouts before implementation. Follow the rules above.
- Settings screen (§3.3.5).
- Edit sheet for feed and diaper entries, and the "Add past entry" layout (behaviour in §3.3.4).
- Whisper-slot cards: dimmer offer, "Screen dimmed for night", long-sleep prompt, "Sleep deleted".
- Watch: "Phone app not installed" screen, ambient variants of Main idle, picker and Logged (rules in §10.6).
- Phone History for a previous day; a running sleep in the History list.

**Deferred.**
- QS sleep tile (v1.1).
- 2 × 2 Wear Widget (from the tile's mainSlot).
- High-contrast token set.
- "Log earlier" long-press and "Hold to log".
- Everything in open questions 1–2.
