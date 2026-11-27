# The watch

The Wear OS watch app: Main, which looks like the Tile, and the screens a log
goes through (Feed type, Side, Bottle, Diaper, Logged, Time), plus the Tile, the
complications and the Ongoing Activity on the watch face. The watch logs on its
own; the phone is never needed to log.

Written from the running watch app (Robolectric, a 227 dp round watch on Wear
OS 6, a fresh install with no phone in reach, 2026-11-27), the design documents
and the watch app's code. Times in the scenarios are the watch's own clock,
written as the watch writes them (12- or 24-hour, as it is set).

```
watch.ail.md                        Watch: Main, idle and asleep, mirroring the Tile
sleep/watch-sleep.ail.md            WatchSleep: the sleep edge button, its guard, Start sleep and Woke up
feed/watch-feed.ail.md              WatchFeed: Feed type, Food, Just the time, Already logged
feed/side/watch-side.ail.md         WatchSide: Side, Left and Right in tick order
feed/bottle/watch-bottle.ail.md     WatchBottle: Bottle, contents, the amount with − / + and its chips
diaper/watch-diaper.ail.md          WatchDiaper: Pee and Poop, colour and texture, the cooldowns
logged/watch-logged.ail.md          WatchLogged: Logged, Undo, its window and inert start, Woke up too, Undone
time/watch-time.ail.md              WatchTime: Time, −5m and +5m, the crown, Done
tile/watch-tile.ail.md              WatchTile: the Tile
complications/watch-complications.ail.md  WatchComplications: the four complications
ongoing/watch-ongoing.ail.md        WatchOngoing: the Ongoing Activity
```

```ail
node Watch : flow   level: experience
  why   A parent with a baby on one arm logs from the wrist, without the phone: the
        watch says at a glance how long since the last feed, diaper and sleep, and
        logs each with one or two taps.   <- Pooparazzi
  what
    Main is one fixed screen, as the Tile: a title line, the Feed row, the Diaper row and the sleep edge button.
    Scenario: a fresh watch has nothing logged
      Given a new install of the watch app, with nothing logged
      When the parent opens the app
      Then Main shows the Feed row reading "No feeds yet" and the Diaper row reading "No diapers yet"
      And the sleep edge button reads "Start sleep", with no time under it
      And the title line says nothing
    Scenario: main shows how long since the last feed and diaper
      Given a food feed and then a pee logged on the watch a moment ago
      When the parent is back on Main
      Then the Feed row reads "just now" after the history icon, spoken "Feed. Last feed, food, just now, at" the feed's time
      And the Diaper row reads "just now", spoken "Diaper. Last diaper, pee, just now, at" the pee's time
      And the title line is the last diaper, spoken "Last diaper, pee, just now"
    Scenario: main while the baby sleeps
      Given a sleep that started 10 minutes ago
      When the parent is on Main
      Then the title line reads "Asleep 10m", spoken "Asleep, 10 minutes, since" the sleep's start
      And the sleep edge button reads "Woke up" over "10m" after the history icon, spoken "Woke up, end sleep", in the state "Asleep since" the sleep's start
    Scenario: main after waking up
      Given a sleep that started 10 minutes ago and has just ended with Woke up
      When the parent is back on Main
      Then the sleep edge button reads "Start sleep" over "just now", in the state "Awake since" the wake-up's time
      And the title line says nothing
  how
    decide Main and the Tile are built from one model and say the same: the title, the time since the last feed and diaper, the expected next ones, and the sleep button
      because they must always agree (DESIGN_SPEC §3.5.1; commit 874a042 "one glance model for the Tile and Main")
    decide the title line is "Asleep" and how long while a sleep runs, else the baby's name, else the last diaper ("Pee 1m ago"), else nothing
      because the baby's name comes from the phone; without it the last diaper is the most useful line that fits (DESIGN_SPEC §3.5.1, §3.6)
    decide the rows show their icon and no word; each is spoken as its name and what it shows
      because the icon already says it, and the content description names it for TalkBack (commit bd5b809)
    decide the times read whole clock minutes since the last one ("just now" within the minute), and after them the expected next one when the past week allows one ("55m", "soon", "now")
      because the estimates follow the phone's rules (V1_ADDITIONS §9), shown as the time alone after the upcoming icon (DESIGN_SPEC §3.5.1)
    decide Main speaks a row with the type and the clock time ("Feed. Last feed, food, 1 minute ago, at 5:14 PM"), the Tile the same row without them ("Feed. Last feed 1 minute ago")
      because "unknown, confirm with the owner"
    decide no history, editing or sync state on Main; Main never scrolls
      because they are on the phone, and Main mirrors the Tile (DESIGN_SPEC §3.5.1, §3.5.8, §3.5.9)
    decide with two or more babies the title line is a baby button that opens the baby picker, and every screen's time text names the baby ("Noa · 14:32")
      because the parent must always see which baby a tap logs for (BABIES_AND_BACKUP §2.5)
    decide the babies come only from the phone; the watch cannot add one, so with no phone the watch has one unnamed baby
      because the phone owns the babies and the watch picks them up over the Data Layer (README "Install"; BABIES_AND_BACKUP §2.5)
  look
    Main on a watch.background screen with the system time text at the top; the title line in wear.titleSmall
      color.text (asleep: the sleep icon in sleep.strong and the words in sleep.text)
    The Feed and Diaper rows: full width less 7 % at each side, sharing the height between the title and the edge button,
      4dp apart, radius.watch.button; Feed in feed.container with the feed icon in feed.strong, Diaper in diaper.container
      with the diaper icon in diaper.strong; their lines in wear.labelMedium feed.on.container or diaper.on.container
    The sleep edge button: size.edge.medium, pinned to the bottom edge, fill sleep.container, "Start sleep" in
      wear.labelMedium sleep.on.container over the time after the history icon in wear.labelSmall; asleep:
      fill sleep.lit with a 1.5dp sleep.strong border, "Woke up"
  copy
    row.noFeed        "No feeds yet"
    row.noDiaper      "No diapers yet"
    row.justNow       "just now"
    title.asleep      "Asleep {duration}"            ; "Asleep 10m", "Asleep <1m"
    title.lastDiaper  "{kind} {ago}"                 ; "Pee 1m ago", spoken "Last diaper, pee, 1 minute ago"
    toggle.start      "Start sleep"
    toggle.end        "Woke up"                      ; spoken "Woke up, end sleep"
    state.asleep      "Asleep since {time}"
    state.awake       "Awake since {time}"
  children  WatchSleep, WatchFeed, WatchDiaper, WatchLogged, WatchTime, WatchTile, WatchComplications, WatchOngoing
  sources
    docs/design/DESIGN_SPEC.md §3.5 (the Wear app), §3.5.1 (W1 Main idle), §3.5.2 (W2 Main asleep), §3.5.10, §5.4
    docs/design/BABIES_AND_BACKUP.md §2.5 (the watch with several babies)
    README.md, "Watch" and "Install"
    driven on Robolectric (the real watch MainActivity, 227 dp round, API 36), 2026-11-27: a fresh Main, Main after Food
      and Pee, Main asleep 10 and 12 minutes, Main after Woke up and after a diaper that ended a sleep
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/main/MainScreen.kt, the title, rows and sleep edge button
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/glance/WatchGlance.kt, the model Main and the Tile share
    wear/src/main/res/values/strings.xml, core/src/main/res/values/strings.xml
```
