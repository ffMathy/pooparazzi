# A breast feed

Breast moves the Feed sheet to its Breast step: tick the sides in the order the
baby fed, or none, and Log feed. The next feed starts from the side the last
one ended on, so the order is what Home shows.

```ail
node BreastFeed : flow   level: experience
  why   The side the baby fed on last decides where the next feed starts, so the
        order is worth one or two more taps, but never required.   <- Feeds
  what
    Scenario: the Breast step
      Given the Feed sheet opened now and nudged back 20 minutes
      When the parent taps Breast
      Then the sheet's title reads "Breast" over the same time, 20 minutes ago with "was" the time now, and the nudges stay
      And it reads "Tick the sides in the order you fed. Optional." over Left and Right, both unticked
      And the order reads "No side", with Log feed
    Scenario: ticking the sides in order
      Given the Breast step
      When the parent ticks Right and then Left
      Then Right is ticked first and Left second, and the order reads "Right → Left"
    Scenario: unticking a side takes it out of the order
      Given the Breast step with Right and then Left ticked
      When the parent unticks Right and ticks it again
      Then Left is first and Right second, and the order reads "Left → Right"
    Scenario: logging a breast feed with its order
      Given the Breast step of a sheet nudged back 20 minutes, with Left and then Right ticked
      When the parent taps Log feed
      Then the sheet closes and no receipt follows
      And the Lamp reads "Last fed" 20 minutes ago, with the feed's time and "Left → Right"
    Scenario: logging a breast feed with no side
      Given the Breast step with nothing ticked
      When the parent taps Log feed
      Then the Lamp reads "Last fed" "just now" with the feed's time and "Breast"
  how
    decide Breast logs nothing yet: the same sheet moves to its Breast step, keeping the stamp and the nudges
      because the side order is optional detail on top of the time already picked (V1_ADDITIONS §2.1)
    decide the tick order is the order recorded: the first side ticked is first; unticking takes a side out, ticking it again puts it last
      because the parent ticks the sides as they fed, so the order needs no control of its own (V1_ADDITIONS §1, "Tick order → side")
    decide Log feed can always be tapped; with nothing ticked the feed records only its time and reads "Breast"
      because the side is optional (V1_ADDITIONS §1, §2.1)
    decide a feed with a side order shows the order in place of the word "Breast" wherever the last feed is shown
      because it is what the next feed starts from (V1_ADDITIONS §2.1, "Breast order on every last-feed line")
    decide each side is spoken with its place: "Left, first", "Right, second"; the order "Left then right"
      because a blind parent must hear the order a sighted one sees in the badges (V1_ADDITIONS §2.1, DESIGN_SPEC §9.4)
  look
    The Breast step: under the nudges the hint in type.bodyMedium color.text.dim, then two tiles side by side, gap 12dp,
      each at least 112dp tall with radius.button: the side glyph with its letters and the label in type.headlineSmall
      feed.on.container, a checkbox at the top end. Unticked: fill feed.container, a 1dp color.outline border, an empty
      box. Ticked: fill feed.lit, a 2dp feed.strong border, the box checked in feed.strong, and at the top start a 28dp
      feed.strong circle with "1" or "2" in type.labelLarge feed.lit
    Under the tiles the order in type.titleMedium, feed.text ("No side" in color.text.dim), and at the end Log feed:
      at least 160dp by 56dp, radius.button, fill color.primary.container, a check and the label in type.labelLarge
      color.on.primary.container
  copy
    step.title          "Breast"
    step.hint           "Tick the sides in the order you fed. Optional."
    side.left           "Left"
    side.right          "Right"
    order.none          "No side"
    order.leftRight     "Left → Right"            ; spoken "Left then right"
    order.rightLeft     "Right → Left"            ; spoken "Right then left"
    side.spoken         "Left, first"  "Right, second"
    action.log          "Log feed"
    lamp.line           "{time} · Left → Right"   ; with no side "{time} · Breast"
  sources
    docs/design/V1_ADDITIONS.md §1 (breast side, tick order), §2.1 (the Breast step, the order on every last-feed line)
    driven on Robolectric, 2026-11-27: Breast after −15m −5m ("Breast · 4:39 PM"), Right then Left ("Right → Left",
      "Right, first", "Left, second"), Right unticked and ticked again ("Left → Right"), Log feed (Lamp "4:39 PM · Left →
      Right", spoken "Last feed, breast, left then right, 20 minutes ago, at 4:39 PM"), Log feed with no side ("· Breast")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/FeedSheets.kt, the Breast step and its tiles
    core/src/main/kotlin/com/ffmathy/pooparazzi/core/model, the breast side and its order
```
