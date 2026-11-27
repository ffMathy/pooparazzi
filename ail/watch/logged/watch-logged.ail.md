# Logged and Undone

Every watch log ends on Logged: what was logged and when, Undo, and the time to
tap (or the crown to turn) to move it. It closes by itself after five seconds;
Undo takes the log back and shows Undone.

```ail
node WatchLogged : flow   level: experience
  why   The parent sees at once that the tap counted, and takes a wrong one back
        with one more tap, without a dialog to read in the dark.   <- Watch
  what
    Scenario: logged closes after five seconds
      Given a food feed logged on the watch just now
      When the parent does nothing
      Then Logged still reads "Food at" its time four seconds later
      And after five seconds Main is back, the Feed row reading "just now"
    Scenario: logged ignores taps for its first second
      Given a food feed logged on the watch just now
      When the parent taps the background and Undo at once
      Then Logged stays, Undo and the time can't be tapped, and the feed is kept
      And a second after Logged appeared, Undo and the time can be tapped
    Scenario: tapping the background closes logged
      Given Logged for a food feed, after its first second
      When the parent taps the background
      Then Main is back, the Feed row reading "just now"
    Scenario: undo takes a watch log back
      Given Logged for a food feed, after its first second
      When the parent taps Undo
      Then Undone reads "Undone"
      And a second and a half later Main is back, the Feed row reading "No feeds yet"
    Scenario: undoing a wake-up resumes the sleep
      Given a sleep that started 10 minutes ago, just ended with Woke up
      When the parent taps Undo on its Logged
      Then Main shows the sleep running again: the title reads "Asleep 10m" and the sleep edge button "Woke up"
    Scenario: a feed during a sleep wakes the baby too
      Given a sleep that started 5 minutes ago
      When the parent logs a food feed
      Then Logged reads "Food at" the time now over "Woke up too"
      And Undo takes both back: Main's title reads "Asleep 5m" again and the Feed row "No feeds yet"
    Scenario: a log within a minute of falling asleep cancels the sleep
      Given a sleep started a few seconds ago, its Logged closed
      When the parent logs a plain diaper change
      Then Logged reads "Diaper at" the time now over "Sleep cancelled too"
      And once Logged has closed, the sleep edge button reads "Start sleep" with no state
    Scenario: logged says the log is kept on the watch
      Given a food feed logged on the watch ten seconds ago, with no phone in reach
      When the parent logs a plain diaper change
      Then Logged's sync icon is spoken "Saved on watch · will sync"
  how
    decide Logged closes after five seconds (stretched by the parent's accessibility timeout), its bezel arc draining over them; ambient keeps the window running
      because the confirmation must not need a tap (DESIGN_SPEC §3.5.6, §5.12)
    decide Undo, the time and the dismiss target ignore taps for the first 1000 ms, and again for a second on returning from Time; opened from the Tile's background they are live at once
      because a double tap on Save or Start sleep must not land on Undo; a tap on the Tile is already deliberate (DESIGN_SPEC §3.5.6, §5.9, §10.7)
    decide the whole screen but Undo and the time dismisses to Main
      because closing must be the easiest tap (DESIGN_SPEC §3.5.6)
    decide Undo deletes a new feed, diaper or sleep, or makes an ended or cancelled sleep run again; then Undone shows for 1.5 s, and the double-toggle guard is reset
      because every log can be taken back at once (DESIGN_SPEC §1, §3.5.7)
    decide a feed or diaper during a sleep first ends the sleep at the log's time ("Woke up too"; under a minute old, "Sleep cancelled too"), and Undo resumes that sleep too
      because a baby being fed or changed is awake (DESIGN_SPEC §4.4; commit 4633353)
    decide the sync icon says where the log is: "Synced with phone · 14:32", "Saved on watch · will sync" while no phone is in reach; nothing before the watch has tried to sync
      because logging never needs the phone, and the watch never shows a dead-end "check connection" (DESIGN_SPEC §3.5.11)
    decide if Undo would resume a sleep another sleep now overlaps, nothing changes and Main is back
      because sleeps never overlap (wear/…/MainViewModel.kt; commit c043766)
  look
    Logged: no time text; a 270° bezel arc in color.outline, open at the bottom, draining clockwise; the check circle in
      the event's container with the check in its strong colour; the title in wear.titleLarge color.text with the time
      in the event's colour (feed.strong, diaper.text, sleep.text) on a pill of watch.surface.container, radius.watch.pill,
      after the clock icon; a second line in wear.bodyMedium color.text.dim; the sync icon in color.text.dim; Undo as a
      size.edge.small edge button in watch.surface.container with the undo icon and "Undo" in wear.labelMedium color.text
    Undone: the undo icon in color.text.dim over "Undone" in wear.titleLarge color.text, centred, no time text
  copy
    logged.undo       "Undo"
    logged.dismiss    "Dismiss"                  ; spoken name of the background
    logged.change     "Change time"              ; the time's click label
    logged.wokeToo    "Woke up too"
    logged.cancelToo  "Sleep cancelled too"
    sync.offline      "Saved on watch · will sync"
    sync.synced       "Synced with phone · {time}"
    undone            "Undone"
  sources
    docs/design/DESIGN_SPEC.md §3.5.6 (W6 Logged), §3.5.7 (W7 Undone), §3.5.11, §4.4, §5.9, §5.12
    commit 4633353 "Log Woke up when a feed or diaper is saved during a sleep (#83)"
    driven on Robolectric, 2026-11-27: Logged disabled for its first second, closing after 5 s, the background, Undo
      ("Undone", Main 1.5 s later), Woke up undone (the sleep runs again), Food 5 minutes into a sleep ("Woke up too"),
      a diaper within a minute of Start sleep ("Sleep cancelled too"), "Saved on watch · will sync" from the second log on
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/logged/LoggedScreen.kt (the 5 s window, the 1 s inert start, Undone)
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/MainViewModel.kt, Undo and the wake-up a log ends
```
