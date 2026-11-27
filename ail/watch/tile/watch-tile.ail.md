# The Tile

The Tile in the watch's tile carousel says what Main says (the title, the Feed
and Diaper rows, the sleep button) and starts or ends a sleep without opening
the app. The rows open the app's Feed type and Diaper screens.

```ail
node WatchTile : flow   level: experience
  why   A swipe from the watch face is quicker than opening an app: the Tile shows
        how long since each, and a sleep starts or ends from it in one tap.   <- Watch
  what
    Scenario: a fresh tile
      Given a new install, with nothing logged
      When the parent swipes to the Tile
      Then it reads "No feeds yet" in the Feed row, "No diapers yet" in the Diaper row, and "Start sleep", with no title
    Scenario: the tile shows the last feed and diaper
      Given a food feed and then a pee logged on the watch a moment ago
      When the parent swipes to the Tile
      Then its title reads "Pee" and how long ago ("Pee just now", or "Pee 1m ago" once the minute has turned), spoken "Last diaper, pee," and that
      And the rows read the same time since, spoken "Feed. Last feed" and "Diaper. Last diaper" and how long ago
    Scenario: starting a sleep from the tile
      Given the Tile, with the baby awake
      When the parent taps Start sleep on it
      Then the Tile's title reads "Asleep" and the time now, then "Undo", spoken "Asleep at" the time now ". Tap the tile to undo"
      And its sleep button reads "Woke up" over "just now", spoken "Woke up, end sleep, Asleep since" the time now
      And ten seconds later the title is "Asleep" and how long, and the confirmation is gone
    Scenario: the tile's background opens logged with undo at once
      Given a sleep just started from the Tile
      When the parent taps the Tile's background
      Then the app opens on Logged "Asleep at" the sleep's start, where Undo can be tapped at once
    Scenario: the tile's rows open the app
      Given the Tile
      When the parent taps its Feed row, or its Diaper row
      Then the app opens on Feed type, or on Diaper, stamped with the time of the tap
  how
    decide the Tile is built from the model Main uses, so both say the same
      because they must always agree (DESIGN_SPEC §3.5.1, §3.6; commit 874a042)
    decide only the sleep button acts on the Tile itself; the rows open the app's screens; diapers are never logged from the Tile
      because the rows need the screens' details, and no button ever relabels or moves (DESIGN_SPEC §3.6)
    decide a sleep started or ended from the Tile is confirmed in the title for ten seconds ("Asleep 5:16 PM · Undo", "Woke up 5:16 PM · Undo", "Sleep cancelled · Undo"); the background then opens Logged with Undo live at once
      because Undo is reached through the background, never in a button's place (DESIGN_SPEC §3.6, §10.7)
    decide a Tile tap within two seconds of any sleep toggle (the Tile's, the app's, or the phone's through sync) shows "Already asleep" or "Already awake" in the title and changes nothing
      because the double-toggle guard covers every surface (DESIGN_SPEC §3.6; wear/…/tile/TileActions.kt)
    decide the title keeps "Asleep 5:16 PM" without "at", and is hidden below 225 dp, where the confirmation takes the sleep button's first line
      because with "at" it would not fit the ≈168 dp title chord (DESIGN_SPEC §3.6, §3.5.10)
    decide the Tile's times tick by themselves (dynamic expressions); a renderer without them gets a static Tile refreshed every minute
      because the Tile must never be stale (DESIGN_SPEC §10.9)
  look
    The Tile: fixed neutral chrome on watch.background, no time text; the title in wear.titleSmall color.text; the Feed
      row in feed.container with the feed icon in feed.strong and the Diaper row in diaper.container with the diaper icon in
      diaper.strong, radius.watch.button; the sleep button 70dp tall on the bottom edge, sleep.container (asleep: sleep.lit
      with a 1.5dp sleep.strong border), "Start sleep" in wear.labelMedium sleep.on.container
  copy
    tile.asleepTitle  "Asleep {duration}"
    tile.lastDiaper   "{kind} {ago}"             ; "Pee 1m ago"
    tile.started      "Asleep {time} · Undo"     ; spoken "Asleep at {time}. Tap the tile to undo"
    tile.cancelled    "Sleep cancelled · Undo"
    tile.guardAsleep  "Already asleep"
    tile.guardAwake   "Already awake"
    tile.feedRow      "Feed. Last feed {ago}"    ; spoken; "Feed. No feeds yet"
    tile.diaperRow    "Diaper. Last diaper {ago}"
  sources
    docs/design/DESIGN_SPEC.md §3.6 (W9 Tile), §10.9; V1_ADDITIONS.md §2.2 (Tile, later changed: Row B opens Diaper)
    commits 874a042 (one model for the Tile and Main), be99c60 (the Tile's "Asleep 14:32 · Undo")
    driven on Robolectric through the tile service and the real ProtoLayout renderer, 2026-11-27: a fresh Tile, after Food
      and Pee ("Pee 1m ago"), Start sleep ("Asleep 5:16 PM · Undo"), the background (Logged with Undo live), the rows
      (Feed type, Diaper), the confirmation gone after 10 s
    not driven: the Tile's guard. Here every Tile tap takes over two seconds (the Tile waits up to two seconds for its
      write to reach the phone, and there is none), so a second tap always comes after the guard
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/tile/PooparazziTileService.kt, TileActions.kt, TileLayout.kt
```
