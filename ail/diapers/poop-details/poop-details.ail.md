# Poop details

With Poop ticked, the Diaper sheet offers the poop's colour and texture, both
optional. Pale, red or black poop shows a gentle hint to call a doctor or
health visitor; the app never diagnoses.

```ail
node PoopDetails : flow   level: experience
  why   Colour and texture are what a doctor or health visitor asks about, and a
        worried parent at 3am should be pointed to them, not frightened.   <- Diapers
  what
    Scenario: poop reveals its colour and texture
      Given the Diaper sheet opened now
      When the parent ticks Poop
      Then "Colour" offers Yellow, Green, Brown, Black, Red and Pale, and "Texture" offers Watery, Seedy, Mushy, Formed and Hard
      And none of them is picked, and no hint shows
    Scenario: red poop shows the doctor hint
      Given the Diaper sheet with Poop ticked
      When the parent picks Red
      Then the sheet reads "Pale, red or black poop can be a reason to call your doctor or health visitor."
      And a second tap on Red clears it, and the hint is gone
    Scenario: black and pale poop show the hint, other colours don't
      Given the Diaper sheet with Poop ticked
      When the parent picks Black, then Pale, then Yellow, then Green, then Brown
      Then the hint shows for Black and for Pale, and not for Yellow, Green or Brown
    Scenario: unticking poop clears its details
      Given the Diaper sheet with Poop ticked, Pale and Seedy picked
      When the parent unticks Poop
      Then Colour and Texture are gone
      And when Poop is ticked again, neither Pale nor Seedy is picked
    Scenario: saving a poop with its details
      Given the Diaper sheet with Pee and Poop ticked, Pale and Seedy picked
      When the parent taps Save
      Then Home's diaper row reads "Both", without colour or texture
      And History lists it as "Pee + poop" over the change's time, "Pale" and "Seedy"
  how
    decide Colour and Texture appear only while Poop is ticked; each is one optional choice, and tapping the picked one clears it
      because only poop has a colour or texture, and every detail is optional (V1_ADDITIONS §1, §3.1)
    decide unticking Poop clears the colour and texture, so ticking it again starts with none
      because colour and texture belong to poop only (app/…/ui/home/HomeViewModel.kt); the watch keeps them hidden instead (V1_ADDITIONS §3.2)
    decide the hint shows while Pale, Red or Black is picked, and the app says nothing more
      because those colours can be a reason to call a doctor, but the app never diagnoses (V1_ADDITIONS §3.1)
    decide Home's diaper row never shows the colour or texture; History does
      because the row says what the change held, and the details belong to the log (V1_ADDITIONS §3.3)
    decide every colour chip carries its name as well as its swatch
      because colour is never the only cue (V1_ADDITIONS §3.1, DESIGN_SPEC §9.2)
  look
    Under the tiles, expanding into view: "Colour" in type.labelMedium color.text.dim over six chips in a wrapping row,
      each 48dp tall with a 20dp swatch dot (swatch.yellow, swatch.green, swatch.brown, swatch.black, swatch.red,
      swatch.pale, each ringed 1dp color.outline) and its name; a picked chip is filled color.primary.container with a
      check on its swatch. Then "Texture" over five chips that keep room for a check, the check showing when picked
    The hint: the info icon 20dp in color.text.dim and the text in type.bodySmall color.text.dim, 12dp under the chips
  copy
    heading.colour      "Colour"
    heading.texture     "Texture"
    colour              "Yellow" "Green" "Brown" "Black" "Red" "Pale"
    texture             "Watery" "Seedy" "Mushy" "Formed" "Hard"
    hint                "Pale, red or black poop can be a reason to call your doctor or health visitor."
    history.title       "Pee + poop"
    history.line        "{time} · Pale · Seedy"
  uses      Diapers
  sources
    README.md, "Diapers"; docs/design/V1_ADDITIONS.md §1 (poop colour and texture), §3.1 (the chips and the hint), §3.3
    driven on Robolectric, 2026-11-27: Poop (Colour, Texture), Red (the hint), Red again (cleared), Pale and Seedy, Poop
      off and on (neither picked), Black (the hint), Save with Pee (Home "Both"; History "Pee + poop", "17:10 · Pale ·
      Seedy")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/DiaperSheet.kt, the details and the hint
```
