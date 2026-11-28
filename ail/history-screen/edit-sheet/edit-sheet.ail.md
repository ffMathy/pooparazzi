# The edit sheet

One sheet fixes any entry after the fact: its day and time with a day chip, the
clock dial and the nudges, what it was before each change, and the rules that
keep the log possible. It opens from a History row, the Lamp, a since-row, and
Edit in a receipt; it writes nothing until Save.

```
edit-sheet.ail.md                 EditSheet: a sleep's blocks, nudges, "was", validation, Cancel and Save
edit-feed/edit-feed.ail.md        EditFeed: a feed's type, side, contents and time
edit-diaper/edit-diaper.ail.md    EditDiaper: a diaper's Pee, Poop, colour, texture and time
delete-entry/delete-entry.ail.md  DeleteEntry: Delete, the "… deleted" receipt and Undo
```

```ail
node EditSheet : flow   level: experience
  why   A late or wrong log must be fixable with a few taps, by the minute, without
        ever saving a log that can't be true.   <- HistoryScreen
  what
    Scenario: a History row opens its entry
      Given a sleep yesterday from 1:00 PM to 2:00 PM
      When the parent taps its row on yesterday
      Then "Edit sleep" opens with Delete, on "Yesterday ·" and yesterday's short date
      And Fell asleep reads "Yesterday" and 1:00 PM, Woke up "Yesterday" and 2:00 PM, each with −5m, −1m, +1m and +5m
      And it reads "Duration 1h 00m", then Cancel and Save
    Scenario: a nudge shows what the time was
      Given the edit sheet of a sleep yesterday from 1:00 PM to 2:00 PM
      When the parent taps −5m under Fell asleep
      Then Fell asleep reads 12:55 PM and "was 1:00 PM", and "Duration 1h 05m · was 1h 00m"
      And Save lists "Sleep · 1h 05m" over "12:55 PM – 2:00 PM"
    Scenario: nudges add up and back
      Given the edit sheet of a sleep yesterday from 1:00 PM to 2:00 PM
      When the parent taps −5m and then +1m five times under Fell asleep
      Then Fell asleep reads 1:00 PM again, with no "was", and "Duration 1h 00m"
    Scenario: waking up before falling asleep can't be saved
      Given the edit sheet of a sleep yesterday from 1:00 PM to 2:00 PM
      When the parent sets Woke up to 12:00 PM
      Then it reads "Waking up must come after falling asleep." and "Duration <1m · was 1h 00m"
      And Save can't be tapped
    Scenario: Cancel keeps the entry as it was
      Given the edit sheet of a sleep yesterday from 1:00 PM to 2:00 PM, Fell asleep nudged −5m
      When the parent taps Cancel
      Then the sheet closes and the row still reads "Sleep · 1h 00m" over "1:00 PM – 2:00 PM"
  how
    decide the title says the kind ("Edit sleep", "Edit feed", "Edit diaper"), with Close at the start and Delete at the end; under it the entry's day
      because the sheet is a dialog labelled by its title (DESIGN_SPEC §3.3.4, §9.4)
    decide each time is a block: its label, a day chip (Today, Yesterday, Earlier date…), the time field (the clock dial on a tap), and the nudges −5m −1m +1m +5m, which add up from the saved time; "was" and the old duration show while it differs
      because fine corrections by the minute happen after the fact, unlike the receipt's −15m … +5m (DESIGN_SPEC §3.3.4)
    decide a running sleep's Woke up reads "Still asleep" with "End now"; changing its start keeps it running
      because the running Lamp opens it (DESIGN_SPEC §3.3.4; commit bbb61c3)
    decide the validation slot is empty unless a rule fires; blocking rules disable Save: a wake-up before falling asleep, a sleep on top of another ("Overlaps the 1:00 PM sleep. Sleeps can't overlap."), a time in the future (with "Use yesterday"), the entry deleted on the watch; "Changed on the watch meanwhile. Check and save again." does not block
      because a sleep is never saved on top of another, and nothing is in the future (DESIGN_SPEC §3.3.4; V1_ADDITIONS §1, §4; commit c043766)
    decide Cancel, Close, back or the scrim close the sheet and change nothing; Save writes the changes and closes it
      because a stray tap must not change the log (DESIGN_SPEC §11)
  look
    A full-height bottom sheet over a color.scrim, fill color.sheet, top corners radius.sheet, padding gutter.sheet. The header:
      Close (24dp), the title in type.titleLarge, Delete in type.labelLarge color.error with its icon; the day in type.dataMedium
      color.text.dim
    A block: its label in type.labelMedium color.text.dim with the day chip at the end (48/40dp, 1dp color.outline border); the
      time field 342 × 80, radius 20dp, fill color.time.field, a clock icon, the time in type.displaySmall color.text and "was …"
      in type.dataMedium color.text.dim; the four nudges in equal size.chip pills
    The duration row: the moon in sleep.strong, "Duration 1h 05m" in type.titleMedium and " · was 1h 00m" in type.dataMedium
      color.text.dim; the validation slot (48dp) in color.error while a rule blocks Save
    The footer: Cancel in type.labelLarge color.text.dim; Save 160 × 56, radius 28dp, fill color.primary.container (Night)
  copy
    edit.titles         "Edit sleep" "Edit feed" "Edit diaper"
    edit.delete         "Delete"
    edit.blocks         "Fell asleep" "Woke up" "Time"
    edit.day            "Today" "Yesterday" "Earlier date…"
    edit.nudges         "−5m" "−1m" "+1m" "+5m"      ; spoken "Fell asleep, 5 minutes earlier", "Woke up, 1 minute later"
    edit.was            "was {time}"
    edit.duration       "Duration {duration}"  " · was {duration}"
    edit.stillAsleep    "Still asleep"   "End now"
    error.order         "Waking up must come after falling asleep."
    error.watch         "Changed on the watch meanwhile. Check and save again."  "Deleted on the watch meanwhile."
    edit.cancel         "Cancel"
    edit.save           "Save"
  children  EditFeed, EditDiaper, DeleteEntry
  sources
    docs/design/DESIGN_SPEC.md §3.3.4 (P4 Edit sheet), §9.4 (edit sheet semantics), §10.4, §11; V1_ADDITIONS §4
    commit c043766 (never let a sleep be saved on top of another)
    driven on Robolectric, 2026-11-27: a past sleep yesterday 1:00–2:00 PM, −5m ("12:55 PM", "was 1:00 PM", "Duration 1h 05m
      · was 1h 00m"), five +1m back, Woke up set to 12:00 PM (the order message, Save disabled), Save and Cancel
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/edit/EditSheet.kt, EditTimeBlock.kt, EditRules.kt, EditViewModel.kt
```
