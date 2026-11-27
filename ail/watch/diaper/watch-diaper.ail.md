# Diaper on the watch

Main's Diaper row opens one list, stamped with the time of the tap: Pee and Poop
tiles, and with Poop on its colour and texture, then Save. Nothing ticked is a
plain change.

```ail
node WatchDiaper : flow   level: experience
  why   A diaper is logged as it is changed, from the wrist; what was in it matters
        for the baby's health, but only the time is required.   <- Watch
  what
    Scenario: the diaper screen
      Given the watch's Main
      When the parent taps the Diaper row
      Then Diaper reads "Diaper · " and the time of the tap, over the Pee and Poop tiles, both unticked, and Save
      And there is no colour or texture yet, and for half a second nothing can be tapped
    Scenario: poop shows its colour and texture
      Given Diaper, opened just now
      When the parent ticks Poop
      Then "Colour" appears over Yellow, Green, Brown, Black, Red and Pale, and "Texture" over Watery, Seedy, Mushy, Formed and Hard, none chosen
      And for half a second after the tick the colours can't be tapped
    Scenario: logging pee and poop with a colour and texture
      Given Diaper with Pee and Poop ticked
      When the parent chooses Yellow and Seedy and taps Save
      Then Logged reads "Pee + poop at" the time of the Diaper tap, spoken "Pee and poop at" that time, with no second line
    Scenario: unticking poop keeps its choices hidden
      Given Diaper with Poop ticked and Yellow chosen
      When the parent unticks Poop and ticks it again
      Then the colours are back with Yellow still chosen
      And unticked again, Save shows Logged "Diaper at" the time of the Diaper tap: a plain change, without the colour
    Scenario: a plain change
      Given Diaper, opened just now
      When the parent taps Save with nothing ticked
      Then Logged reads "Diaper at" the time of the Diaper tap
      And once Logged has closed, the Diaper row reads "just now", spoken "Diaper. Last diaper, diaper, just now, at" that time
    Scenario: pee alone
      Given Diaper, opened just now
      When the parent ticks Pee and taps Save
      Then Logged reads "Pee at" the time of the Diaper tap
  how
    decide Pee and Poop start off and are optional; nothing ticked is a plain change, "Diaper"
      because the watch replaced the one-tap Pee, Poop and Both with one screen (V1_ADDITIONS §3, §3.2)
    decide colour and texture show only while Poop is on; turning Poop off keeps them hidden, and only a diaper with Poop saves them
      because an accidental untick is undone by ticking again (V1_ADDITIONS §3.2)
    decide everything under the tiles ignores taps for 500 ms after Poop changes, and the whole screen for 500 ms when it appears; the Pee tile is never held back by Poop
      because what Poop reveals and moves must not catch the second tap of a double tap (DESIGN_SPEC §3.5.3, §5.9)
    decide tapping the chosen colour or texture clears it, unless chosen less than 500 ms ago
      because a double tap on an option must leave it chosen (DESIGN_SPEC §3.5.11)
    decide Logged names only the kind; colour and texture are seen on the phone
      because the watch shows no health hint: that is the phone's (V1_ADDITIONS §3.2)
    decide the diaper is logged at the time of the Diaper tap; it is moved afterwards from Logged
      because every watch log's time moves the same way (commit bc0ac14)
  look
    Diaper: "Diaper · 5:07 PM" in wear.titleMedium color.text; Pee and Poop as two tiles side by side, 6dp apart,
      radius.watch.button in every state, the checkbox and the kind's icon (pee.strong, poop.strong) over the label;
      off in their containers (diaper.container, poop.container), on in diaper.lit with a 2dp diaper.strong border
    "Colour" and "Texture" headers in wear.labelMedium color.text.dim; six colour chips with their swatches and five
      texture chips with a ring, radius.watch.chip, chosen in diaper.lit with a 2dp diaper.strong border and a check;
      Save as WatchSide's
  copy
    diaper.title      "Diaper · {time}"
    diaper.pee        "Pee"
    diaper.poop       "Poop"
    diaper.colour     "Colour"
    diaper.colours    "Yellow" "Green" "Brown" "Black" "Red" "Pale"
    diaper.texture    "Texture"
    diaper.textures   "Watery" "Seedy" "Mushy" "Formed" "Hard"
    logged.pee        "Pee at {time}"
    logged.poop       "Poop at {time}"
    logged.both       "Pee + poop at {time}"     ; spoken "Pee and poop at {time}"
    logged.plain      "Diaper at {time}"
  uses      WatchLogged, WatchSide
  sources
    docs/design/V1_ADDITIONS.md §3.2 (the watch's Diaper screen), DESIGN_SPEC.md §3.5.3, §3.5.11, §5.9
    commit 9da03a4 "Watch: fill in every detail on one screen, no sub-screens"
    driven on Robolectric, 2026-11-27: Diaper ("Diaper · 5:07 PM"), Pee and Poop, the colours disabled for 500 ms
      after Poop, Yellow and Seedy, Poop off and on again (Yellow kept), Save ("Pee + poop at", "Poop at", "Diaper at")
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/diaper/DiaperScreen.kt, KindTiles.kt, PoopDetails.kt
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/MainViewModel.kt, the diaper draft and Save
```
