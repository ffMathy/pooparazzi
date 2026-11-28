# History

One day of the log at a time: a decorative ribbon of the day, its counts, and
its entries newest first. A row opens its entry in the edit sheet; Add entry
logs something that was forgotten.

Written from the running app (Robolectric, a fresh install, 2026-11-27), the
design documents and the phone app's code. The several days of history its
scenarios need are made with Add entry, as a parent would.

```
history-screen.ail.md                 HistoryScreen: the day, its ribbon, counts and list; day prefixes; to Statistics and back
add-past-entry/add-past-entry.ail.md  AddPastEntry: Add entry, every kind, its checks
edit-sheet/edit-sheet.ail.md          EditSheet: the edit sheet of a sleep, its nudges, "was", validation, Cancel and Save
```

```ail
node HistoryScreen : flow   level: experience
  why   Home shows only the latest of each kind; a parent needs to see and fix a
        whole day, and look back at the days before.   <- Pooparazzi
  what
    Scenario: History of a new install
      Given a new install
      When the parent taps History on Home
      Then "History" opens with Back and a Statistics button, on "Today ·" and today's short date
      And Previous day and Next day can't be tapped
      And the counts read 0 feeds, 0 pee, 0 poop and "<1m" sleep, spoken "Today: 0 feeds, 0 pee, 0 poop, less than a minute sleep"
      And the list reads "Nothing logged on this day.", with "Add entry"
    Scenario: today's entries, newest first
      Given a feed logged now with Just the time, then a pee diaper logged now on Home
      When the parent opens History
      Then the list reads "Pee" over "Feed", each with its time, spoken "Pee," and "Feed," then the time
      And the counts read "1" "feed" and "1" "pee"
    Scenario: stepping back to yesterday
      Given a sleep yesterday from 1:00 PM to 2:00 PM, and nothing before it
      When the parent taps Previous day
      Then the day reads "Yesterday ·" and yesterday's short date, and the counts "1h 00m" sleep
      And the list reads "Sleep · 1h 00m" over "1:00 PM – 2:00 PM", spoken "Sleep, 1 hour, 1:00 PM to 2:00 PM"
      And Previous day can't be tapped
      When the parent taps Next day
      Then the day reads "Today ·" and today's short date again, its list "Nothing logged on this day.", and Previous day can be tapped
    Scenario: a sleep across midnight is listed on both days
      Given a sleep from 10:00 PM two days ago to 2:00 AM yesterday
      Then yesterday lists "Sleep · 4h 00m" over the start's short weekday, "10:00 PM – 2:00 AM", and counts "2h 00m" sleep
      And two days ago, headed by its short date alone, lists "10:00 PM – 2:00 AM" and counts "2h 00m" sleep
    Scenario: History opens Statistics, and Back returns
      Given a new install, on History
      When the parent taps Statistics, then Back, then Back
      Then Statistics opens, Back returns to History, and Back again to Home
  how
    decide History shows one day, today first; Previous day stops at the first day with an entry, Next day at today
      because History stays "one day" and Statistics is "many days" (V1_ADDITIONS §8); there is nothing to see before the first entry
    decide the day reads "Today · Fri 27 Nov", "Yesterday · Thu 26 Nov", or the short date alone further back
      because of the day switcher in DESIGN_SPEC §3.3.3
    decide the counts are Today's (feeds, pee, poop, the day's minutes of sleep), for the day shown; "<1m" with no sleep, as on Home
      because the stats row stays the same: feeds · pee · poop · sleep (V1_ADDITIONS §4)
    decide a sleep is listed on each day it touches, as one row with its whole length and range; the day it ended on writes the start's short weekday first ("Wed 10:00 PM – 2:00 AM"); each day counts only its own minutes
      because a day prefix appears only when an entry starts on another day, and a sleep crossing midnight is one bar flush at 00:00 (DESIGN_SPEC §3.3.3, §4.4, §7)
    decide row titles: "Sleep · 47m", "Feed", "Breast · Left → Right", "Bottle · Milk", "Food", "Pee", "Poop", "Pee + poop" (spoken "Pee and poop"), "Diaper"; every row has a second line under its title: an instant its time, a sleep its range
      because receipts and History write Both as "Pee + poop" (DESIGN_SPEC §4.2; V1_ADDITIONS §4)
    decide the ribbon is decorative, with one spoken summary of the day's counts
      because it is not tappable, and the counts say it in words (DESIGN_SPEC §3.3.3, §9.4)
    decide Statistics in the top bar opens Statistics; Back there returns to History with its day kept
      because Statistics is many days and History one (V1_ADDITIONS §8)
  look
    Top bar: Back (24dp), "History" in type.titleLarge, and at the end the "Statistics" pill (48dp tall, a 20dp chart icon and
      type.labelLarge, color.text.dim). The day switcher: Previous day, the day in type.titleMedium centred, Next day (its
      icon color.outline.variant while it can't be tapped)
    The ribbon card: 350 × 100, radius 20dp, fill color.surface.container; night bands in color.ribbon.night 00–07 and 19–24;
      sleep bars (sleep.strong) at the top, feed ticks (feed.strong) under them, diaper marks at the bottom; a 2dp color.text
      now-line on today; axis "00 06 12 18 24" in type.labelSmall color.text.dim
    The counts: four columns, numbers in type.titleLarge color.text over captions in type.labelSmall color.text.dim; then the
      heading ("Today", "Yesterday" or the date) in type.labelMedium color.text.dim
    Rows: 56dp, 2dp apart, fill color.surface.container, the group's outer corners 20dp and inner 6dp; a 40dp shape-coded
      avatar, then the title in type.titleMedium over the time (an instant) or the range (a sleep) in type.dataMedium
      color.text.dim, both start-aligned. The extended FAB "Add entry"
      at the bottom end, fill color.primary.container, 16dp corners
  copy
    history.title       "History"
    history.statistics  "Statistics"
    history.day         "Today · {date}" "Yesterday · {date}" "{date}"     ; "Today · Fri 27 Nov"
    history.empty       "Nothing logged on this day."
    history.addEntry    "Add entry"
    history.previous    "Previous day"
    history.next        "Next day"
    history.sleepRow    "Sleep · {duration}"   "{start} – {end}"   "{weekday} {start} – {end}"
    history.spoken      "{day}: {feeds}, {pee} pee, {poop} poop, {sleep} sleep"   "Sleep, {duration}, {start} to {end}"
  children  AddPastEntry, EditSheet
  sources
    docs/design/DESIGN_SPEC.md §3.3.3 (P3 History: bands, ribbon, rows), §4.4 (day boundary), §6.3, §6.4, §7, §9.4
    docs/design/V1_ADDITIONS.md §4 (History rows), §8 (Statistics placement); commits 0bfeeb5, cc08b47, 22761bd
    driven on Robolectric, 2026-11-27: a new install, a feed and a pee from Home, past sleeps made with Add entry (yesterday
      1:00–2:00 PM; 10:00 PM Wed to 2:00 AM Thu), Previous day and Next day, Statistics and Back
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/history/HistoryScreen.kt, HistoryStateBuilder.kt, HistoryRow.kt,
      HistoryRibbon.kt, HistoryText.kt, HistoryViewModel.kt
```
