# Add past entry

History's Add entry logs something that was forgotten: a sleep, a feed or a
diaper, at any past day and time, with the same blocks as the edit sheet.

```ail
node AddPastEntry : flow   level: experience
  why   Parents forget to log at night; a missed feed or nap must be added later,
        on the right day, without being able to make the log impossible.   <- HistoryScreen
  what
    Scenario: Add entry opens a feed 15 minutes ago
      Given a new install, on History
      When the parent taps Add entry
      Then "Add past entry" opens on "Today ·" and today's short date, with Sleep, Feed and Diaper, Feed picked
      And "Type · Optional" offers Breast, Bottle and Food with Breast picked, and "Side · Optional" Left, Right and "No side"
      And the Time block reads "Today" and the time 15 minutes ago, with −5m, −1m, +1m and +5m, then Cancel and Save
    Scenario: a past sleep starts as the last 15 minutes
      Given a new install, on History
      When the parent taps Add entry, then Sleep
      Then Fell asleep reads 15 minutes ago and Woke up the time now, both "Today", with "Duration 15m"
    Scenario: adding a past sleep
      Given a new install, on History
      When the parent adds a sleep yesterday from 1:00 PM to 2:00 PM and taps Save
      Then the sheet closes on today, "Nothing logged on this day." still reads, and Previous day lists "Sleep · 1h 00m"
    Scenario: a past sleep can't overlap another
      Given a sleep yesterday from 1:00 PM to 2:00 PM
      When the parent sets a new sleep yesterday from 1:30 PM to 3:00 PM
      Then it reads "Overlaps the 1:00 PM sleep. Sleeps can't overlap." and Save can't be tapped
    Scenario: a time that hasn't happened yet offers yesterday
      Given a new install, on History
      When the parent sets a past feed's time later today
      Then it reads that time and "hasn't happened yet. Log it for yesterday?" with "Use yesterday"
      And tapping Use yesterday moves the day chip to "Yesterday", and Save lists the feed on yesterday
    Scenario: every kind of past entry is listed by its kind
      Given past entries yesterday: a Bottle of Milk at 8:00 AM, a Food at 9:00 AM, a Breast on the Left at 10:00 AM, a diaper with nothing ticked at 11:00 AM and one with Pee and Poop at 12:00 PM
      Then yesterday lists "Pee + poop", "Diaper", "Breast · Left", "Food" and "Bottle · Milk", newest first, each with its time
  how
    decide Add entry opens the edit sheet in its add mode, titled "Add past entry", with the connected group Sleep | Feed | Diaper, Feed picked, and the time now − 15 minutes; a sleep from then to now
      because DESIGN_SPEC §3.3.4 "Add past entry mode (from the FAB)"
    decide a new feed starts with the last feed's type (none for a feed with just the time), Breast before the first feed
      because "unknown, confirm with the owner": seen in the app's code (the edit view model's last feed type), not in the design
    decide the day chip offers Today, Yesterday and "Earlier date…" (a calendar); the time field opens the clock dial
      because of the edit sheet's day chip and time picker (DESIGN_SPEC §3.3.4)
    decide Save is refused for a sleep on top of another, a wake-up before falling asleep, or a time in the future; a future time offers "Use yesterday"
      because sleeps never overlap and no time is in the future (V1_ADDITIONS §1 invariants; DESIGN_SPEC §3.3.4 validation; commit c043766)
    decide Save closes the sheet and History stays on the day it showed
      because "unknown, confirm with the owner": seen driving the app; should History move to the day of the entry just added?
  look
    The edit sheet's look (EditSheet), titled "Add past entry" with no Delete; under the day line the connected group
      "Sleep" | "Feed" | "Diaper", the picked one filled color.primary.container with a check
  copy
    add.title           "Add past entry"
    add.kinds           "Sleep" "Feed" "Diaper"
    add.type            "Type · Optional"   "Breast" "Bottle" "Food"
    add.side            "Side · Optional"   "Left" "Right" "No side"
    add.bottle          "Contents · Optional"  "Milk" "Formula"   "Amount · Optional"
    add.diaper          "Pee" "Poop"   "Colour" "Texture"
    add.day             "Today" "Yesterday" "Earlier date…"
    add.duration        "Duration {duration}"
    error.overlap       "Overlaps the {time} sleep. Sleeps can't overlap."
    error.future        "{time} hasn't happened yet. Log it for yesterday?"
    error.useYesterday  "Use yesterday"
  uses      EditSheet
  sources
    docs/design/DESIGN_SPEC.md §3.3.3 (the FAB), §3.3.4 (Add past entry mode, validation); V1_ADDITIONS §1, §4
    commit c043766 (never let a sleep be saved on top of another)
    driven on Robolectric, 2026-11-27: Add entry (Feed, Breast picked on a new install), Sleep ("Duration 15m"), Diaper, a
      sleep yesterday 1:00–2:00 PM, an overlapping 1:30–3:00 PM, a feed at 6:15 PM at 5:05 PM ("hasn't happened yet")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/edit/EditViewModel.kt (add mode, the last feed type), EditRules.kt,
      EditSheet.kt, EditTimeBlock.kt
```
