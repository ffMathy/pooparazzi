# Editing a diaper

A diaper's edit sheet changes Pee and Poop, a poop's colour and texture, and
its time.

```ail
node EditDiaper : flow   level: experience
  why   Pee, Poop, colour and texture are all optional and often added or fixed
        later, once the parent has had a proper look.   <- EditSheet
  what
    Scenario: a pee diaper's sheet
      Given a pee diaper yesterday at 9:00 AM
      When the parent taps its row on yesterday
      Then "Edit diaper" opens with one Time block reading "Yesterday" and 9:00 AM, and Pee and Poop
    Scenario: ticking Poop shows its colour and texture
      Given the edit sheet of a pee diaper yesterday at 9:00 AM
      When the parent ticks Poop
      Then "Colour" offers Yellow, Green, Brown, Black, Red and Pale, and "Texture" Watery, Seedy, Mushy, Formed and Hard
      And Save lists "Pee + poop" at 9:00 AM
    Scenario: a diaper with nothing ticked is a plain change
      Given the edit sheet of a pee diaper yesterday at 9:00 AM
      When the parent unticks Pee and taps Save
      Then yesterday lists "Diaper" at 9:00 AM
  how
    decide Pee and Poop are independent checkboxes; Save is enabled with both off, a plain change
      because Pee, Poop, colour and texture are all optional (V1_ADDITIONS §3.1, §4)
    decide Colour and Texture show only while Poop is ticked, single-select and optional (a tap on the picked one clears it)
      because a colour or texture belongs to a poop only (V1_ADDITIONS §1, §3.1)
    decide the row reads "Pee", "Poop", "Pee + poop" or "Diaper", with the colour and texture after its time when set
      because History writes Both as "Pee + poop" (DESIGN_SPEC §4.2; V1_ADDITIONS §4)
  look
    The edit sheet's look (EditSheet); the Pee and Poop tiles of the Diaper sheet (diaper colours, a check in diaper.strong
      when ticked); the colour chips with their swatches and the texture chips in wrapping rows
  copy
    edit.diaper         "Pee" "Poop"
    edit.colour         "Colour"   "Yellow" "Green" "Brown" "Black" "Red" "Pale"
    edit.texture        "Texture"   "Watery" "Seedy" "Mushy" "Formed" "Hard"
    row.diaper          "Pee" "Poop" "Pee + poop" "Diaper"
  sources
    docs/design/V1_ADDITIONS.md §3.1 (the Diaper sheet), §4 (Edit sheet: Diaper)
    driven on Robolectric, 2026-11-27: Edit diaper from since-row 2 (Poop: Colour and Texture shown) and from History
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/edit/EditSheet.kt (the diaper form)
```
