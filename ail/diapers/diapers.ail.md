# Diapers on the phone

Diaper on Home opens the Diaper sheet, stamped with the time of the tap. Pee
and Poop are two checkboxes, both optional; Save logs the change at the sheet's
time, moved back with the nudges if it was a while ago.

```
diapers.ail.md                                  Diapers: the Diaper sheet, Pee and Poop, Save, nudges, closing it
poop-details/poop-details.ail.md                PoopDetails: the colour, the texture and the doctor hint
diaper-while-asleep/diaper-while-asleep.ail.md  DiaperWhileAsleep: "Woke up will be logged too", and the sleep it ends
repeat-diaper/repeat-diaper.ail.md              RepeatDiaper: the same diaper twice within five seconds
```

```ail
node Diapers : flow   level: experience
  why   A diaper change is logged with one hand while the other holds the baby; what
        it held is worth a tap, but only the time is required.   <- Pooparazzi
  what
    Scenario: opening the Diaper sheet
      Given the baby is awake on Home with nothing logged
      When the parent taps Diaper
      Then the sheet "Diaper" opens over the time now, with Cancel
      And it offers the nudges −15m, −10m, −5m and +5m, of which +5m can't be tapped
      And Pee and Poop, both unticked, and Save
    Scenario: saving a pee
      Given the Diaper sheet opened now
      When the parent ticks Pee and taps Save
      Then the sheet closes and no receipt follows
      And Home's diaper row reads "Pee" over the clock minutes since the change ("just now" within its minute) and the change's time
      And Today counts 1 pee and 0 poop
    Scenario: saving pee and poop, nudged earlier
      Given the Diaper sheet opened now
      When the parent ticks Pee and Poop, taps −5m twice and taps Save
      Then while the sheet is open its time reads 10 minutes ago, followed by "was" and the time now
      And Home's diaper row reads "Both" over 10 minutes and the change's time
      And Today counts 1 pee and 1 poop
    Scenario: saving with nothing ticked logs a plain change
      Given the Diaper sheet opened now
      When the parent taps Save
      Then Home's diaper row reads "Diaper" over the clock minutes since the change ("just now" within its minute) and the change's time
      And Today counts 0 pee and 0 poop
    Scenario: cancel logs nothing
      Given the Diaper sheet opened now, Pee ticked
      When the parent taps Cancel
      Then the sheet closes, Home shows no diaper row, and Diaper opens the sheet again at once with nothing ticked
    Scenario: going back logs nothing
      Given the Diaper sheet opened now, Pee ticked
      When the parent goes back
      Then the sheet closes and Home shows no diaper row
  how
    decide the time is stamped when Diaper is tapped and moved with the nudges, as on the Feed sheet
      because "Diaper stamps the time on the first tap" (DESIGN_SPEC §11); backdating is the fix for a late log
    decide Pee and Poop are two checkboxes, both off when the sheet opens; Save can always be tapped
      because a diaper may hold either, both or neither, and nothing but the time is required (V1_ADDITIONS §3.1)
    decide nothing ticked logs a plain change, "Diaper"; pee and poop are counted apart in Today, a plain change in neither
      because a dry change is still a change, and the Today row has no diaper total (V1_ADDITIONS §1 "Today counts")
    decide Home's diaper row is named for the kind: Pee, Poop, Both or Diaper, over the time since and the change's time
      because the row tells what the last change held (DESIGN_SPEC §3.3.1 since-rows, V1_ADDITIONS §3.3)
    decide a save gets no receipt, and is followed by Home's 600 ms cooldown; Cancel and going back log nothing and are not
      because the sheet already picked the time (DESIGN_SPEC §3.3.1, §3.3.2, §5.9)
    decide the sheet's chips, tiles and Save ignore taps for their first 500 ms, fading in from 60 %
      because the Diaper button lies under the tiles, and a double tap must not log a change (DESIGN_SPEC §3.3.2, §5.9);
      the checks' clock finishes the fade before a tap, so no scenario shows it
  look
    The sheet: the Feed sheet's frame, title "Diaper"; under the nudges two tiles side by side, gap 12dp, each at least
      112dp tall with radius.button: the kind's icon 40dp over its label in type.headlineSmall, a checkbox at the top end.
      Pee: fill pee.container, the icon pee.strong, the label pee.on.container. Poop: fill poop.container, poop.strong,
      poop.on.container. Unticked: a 1dp color.outline border and an empty box; ticked: fill diaper.lit, a 2dp
      diaper.strong border, the box checked in diaper.strong. Save at the bottom end, as Log feed is on the Feed sheet
    The diaper row on Home: the kind's icon 40dp, the kind in type.titleMedium color.text over the time since and the time
      in type.dataMedium color.text.dim
  copy
    sheet.title         "Diaper"
    sheet.pane          "Diaper · {time}"
    sheet.cancel        "Cancel"
    tile.pee            "Pee"
    tile.poop           "Poop"
    action.save         "Save"
    row.kind            "Pee" "Poop" "Both" "Diaper"
    row.line            "just now · {time}"       ; "10m · 4:52 PM"; spoken "Last diaper, pee and poop, 10 minutes ago, at 4:52 PM"
  children  PoopDetails, DiaperWhileAsleep, RepeatDiaper
  uses      Feeds
  sources
    README.md, "Diapers"; docs/design/V1_ADDITIONS.md §1, §3.1 (the Diaper sheet), §3.3 (display strings)
    docs/design/DESIGN_SPEC.md §3.3.1 (since-rows, the notice card), §3.3.2 (P2 Diaper sheet), §4.4, §5.9, §11
    driven on Robolectric, 2026-11-27: Diaper, Save with nothing ("Diaper", "just now · 5:02 PM"), Pee, Pee and Poop
      after −5m twice ("4:52 PM · was 5:02 PM", then "Both", "10m · 4:52 PM"), Cancel and back with Pee ticked
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/DiaperSheet.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, saving a diaper
```
