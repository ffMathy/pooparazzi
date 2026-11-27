# Starting a sleep

Start sleep logs a sleep from now and brings up its receipt, where a late tap
is fixed with the nudges and a wrong one taken back with Undo.

```ail
node StartSleep : flow   level: experience
  why   The parent often taps Start sleep a while after the baby fell asleep, or by
        mistake; both must be fixed right there, with one more tap.   <- Sleep
  what
    Scenario: starting a sleep
      Given the baby is awake on Home
      When the parent taps Start sleep
      Then the receipt reads "Sleep started" over the time now, with Undo
      And it offers the nudges −15m, −10m, −5m and +5m, then Edit and Done
      And +5m can't be tapped, because the start would be in the future
      And the sleep button now reads "Woke up"
      And the Lamp reads "Asleep" and "just now", since the time now
    Scenario: nudging the start earlier
      Given the receipt of a sleep started now
      When the parent taps −15m and then −5m
      Then the receipt's time reads 20 minutes ago, followed by "was" and the time now
      And the Lamp reads "Asleep" for 20 minutes, since 20 minutes ago
    Scenario: done keeps the sleep running
      Given the receipt of a sleep started now
      When the parent taps Done
      Then the receipt closes
      And the sleep button still reads "Woke up"
    Scenario: undoing a sleep start
      Given the receipt of a sleep started now
      When the parent taps Undo
      Then the receipt closes and the notice reads "Undone"
      And the sleep button reads "Start sleep" again
      And two seconds later the notice is gone
    Scenario: edit opens the sleep
      Given the receipt of a sleep started now
      When the parent taps Edit
      Then the receipt closes and "Edit sleep" opens, with Fell asleep at the time now and Woke up reading "Still asleep"
  how
    decide the receipt has no timeout: it stays until Done, Undo, Edit or going back, and the log stays as shown
      because "unknown, confirm with the owner" (DESIGN_SPEC §3.3.1 says "no timeout" but not why)
    decide the receipt's title has no time; the effective time is the line under it and follows every nudge
      because the time must follow every chip tap, so it gets a line of its own (DESIGN_SPEC §11)
    decide nudges apply at once and add up from the logged time, shown as "was" once moved; one that would put the start in the future can't be tapped
      because a late tap is the common mistake at night, and backdating at log time is the cheapest fix (DESIGN_SPEC §1, §4.4)
    decide the receipt ignores taps for its first 500 ms
      because a double tap on Start sleep must not land on Undo (DESIGN_SPEC §3.3.1, §5.9)
    decide Undo deletes the sleep; the notice "Undone" shows for two seconds, and the guard is reset so the next tap counts
      because every log can be taken back at once (DESIGN_SPEC §1 "Undo after every log")
    decide Edit closes the receipt and opens the sleep's edit sheet, which writes nothing until Save
      because a stray tap must not change the log (DESIGN_SPEC §11)
  look
    The receipt: a bottom sheet over Home and a color.scrim, fill color.sheet, top corners radius.sheet, padding gutter.sheet.
      Its first row: the moon icon in sleep.strong, then "Sleep started" in type.titleLarge color.text over the time line
      in type.dataMedium color.text.dim, and Undo at the end in type.labelLarge color.primary with its icon
    Under it one row of the four nudges, equal widths, each a size.chip pill with radius.chip and a 1dp color.outline
      border, label type.labelLarge color.text; a nudge that can't be tapped is at 38 % opacity
    The last row: an outlined Edit with its icon at the start, and Done, filled color.primary.container with a check, at the end
    The notice "Undone": in place of Today, a card of fill color.notice with radius.notice, the undo icon and "Undone" in
      type.titleMedium color.text
  copy
    receipt.started     "Sleep started"
    receipt.was         "was {time}"               ; after the time: "3:17 PM · was 3:37 PM"
    receipt.undo        "Undo"
    receipt.edit        "Edit"
    receipt.done        "Done"
    nudge               "−15m" "−10m" "−5m" "+5m"  ; spoken "15 minutes earlier" … "5 minutes later"
    notice.undone       "Undone"
    lamp.asleep         "Asleep"
    lamp.justNow        "just now"
    edit.title          "Edit sleep"
    edit.stillAsleep    "Still asleep"
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (the receipt sheet and its rules), §4.4 (offsets, time limits), §11
    README.md, "Sleep" (Undo receipt, Adjust the start)
    driven on Robolectric, 2026-11-27: Start sleep, −15m then −5m ("3:17 PM · was 3:37 PM"), +5m disabled, Done, Undo
      (the notice "Undone", gone after two seconds), Edit ("Edit sleep", Fell asleep, Still asleep), Back
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, starting a sleep, the receipt's offsets, Undo
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/ReceiptSheet.kt
    core/src/main/res/values/strings.xml, the receipt and action strings
```
