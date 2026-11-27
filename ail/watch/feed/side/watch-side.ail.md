# Side

After Breast, Side asks which side, in the order the baby fed: the order of the
ticks is the order recorded. The side is optional.

```ail
node WatchSide : flow   level: experience
  why   Which breast came first decides the next feed's first side, and a parent
        ticks it in the order it happened, with one finger.   <- WatchFeed
  what
    Scenario: the side screen
      Given Feed type, opened just now
      When the parent taps Breast
      Then Side reads "Which side?" over the time of the Feed tap, spoken "Breast," and that time
      And Left and Right are unticked over "No side", with Save
    Scenario: ticking both sides records their order
      Given Side, opened just now
      When the parent ticks Left and then Right
      Then Left is ticked first, spoken "Left, first", and Right second, spoken "Right, second"
      And the order line reads "Left → Right", spoken "Left then right"
      And Save shows Logged "Breast at" the time of the Feed tap over "Left → Right", spoken "Left then right"
    Scenario: unticking a side moves the other one first
      Given Side with Left and then Right ticked
      When the parent unticks Left
      Then Right is ticked first, spoken "Right, first", and the order line reads "Right"
    Scenario: saving a breast feed with no side
      Given Side, opened just now
      When the parent taps Save
      Then Logged reads "Breast at" the time of the Feed tap, with no second line
    Scenario: swiping back from side returns to feed type
      Given Side, opened just now
      When the parent swipes right
      Then Feed type is back, and nothing is logged
  how
    decide Left and Right are checkboxes whose tick order is the order recorded; re-ticking a side puts it last; a ticked box shows its place, 1 or 2, where its icon was
      because the order is what the next feed needs (V1_ADDITIONS §2.2)
    decide Save is always enabled; nothing ticked logs a breast feed with no side
      because only the time is ever required (V1_ADDITIONS §2.2)
    decide the arrow is shown, never spoken: TalkBack reads "Left then right"; on Logged no-break spaces hold it to both sides
      because TalkBack never reads arrows, a "+" or dashes (DESIGN_SPEC §9.4), and the order must never break onto two lines (wear/…/ui/WatchText.kt)
    decide every target ignores taps for 500 ms when Side appears
      because a double tap on Breast must not land on Left (DESIGN_SPEC §5.9)
  look
    Side: "Which side?" in wear.titleMedium color.text over "Breast · 5:04 PM"; Left and Right as full-width checkbox
      buttons in feed.container, lit feed.lit with the box in feed.strong when ticked, the place in a feed.strong badge;
      the order line in wear.bodyMedium color.text.dim; Save as a size.edge.medium edge button in color.primary.container
      with the check icon and "Save" in color.on.primary.container
  copy
    side.title        "Which side?"
    side.left         "Left"
    side.right        "Right"
    side.none         "No side"
    side.order        "Left → Right"          ; spoken "Left then right"; "Right → Left" spoken "Right then left"
    side.first        "first"                  ; spoken after the side: "Left, first"
    side.second       "second"
    save              "Save"
    logged.breast     "Breast at {time}"
  uses      WatchLogged
  sources
    docs/design/V1_ADDITIONS.md §2.2 (the Side screen), DESIGN_SPEC.md §3.5.3, §9.4
    driven on Robolectric, 2026-11-27: Breast ("Which side?", "Breast, 5:02 PM"), Left then Right ("Left, first",
      "Right, second", "Left → Right"), Left unticked ("Right, first", "Right"), Save ("Breast at 5:04 PM", "Right"),
      swipe back to Feed type
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/feed/SideScreen.kt
    core/src/main/kotlin/com/ffmathy/pooparazzi/core/model, the breast side from the tick order
```
