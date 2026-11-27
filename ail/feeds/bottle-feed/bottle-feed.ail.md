# A bottle feed

Bottle moves the Feed sheet to its Bottle step: what the bottle held, Milk or
Formula, and how much, both optional, then Log feed. The amount is picked in
millilitres, or in fluid ounces where the region measures a bottle in them.

```ail
node BottleFeed : flow   level: experience
  why   A bottle's contents and amount help to see how much the baby takes, but at
        night the time must be enough.   <- Feeds
  what
    Scenario: the Bottle step
      Given a phone in the United Kingdom, and the Feed sheet opened now
      When the parent taps Bottle
      Then the sheet's title reads "Bottle" over the time, and the nudges stay
      And it reads "Contents · Optional" over Milk and Formula, neither picked
      And "Amount · Optional" over the amount slider, which reads "No amount", then Log feed
    Scenario: picking the contents and the amount
      Given a phone in the United Kingdom, and the Bottle step
      When the parent picks Formula and slides the amount 12 steps
      Then Formula is picked and the amount reads "120 ml"
      And at the slider's end the amount reads "300 ml"
    Scenario: tapping the picked contents again clears it
      Given the Bottle step with Formula picked
      When the parent taps Formula again
      Then neither Milk nor Formula is picked
    Scenario: logging a bottle with its contents and amount
      Given a phone in the United Kingdom, and the Bottle step with Formula and 120 ml
      When the parent taps Log feed
      Then the sheet closes and no receipt follows
      And the Lamp reads "Last fed" "just now" with the feed's time and "Bottle", spoken with "bottle, formula"
      And History lists it as "Bottle · Formula" over the feed's time and "120 ml"
    Scenario: logging a bottle with nothing picked
      Given a phone in the United Kingdom, and the Bottle step
      When the parent taps Log feed
      Then the Lamp reads "Last fed" with the feed's time and "Bottle", spoken without contents
    Scenario: a phone in the United States picks ounces
      Given a phone in the United States, and the Bottle step
      When the parent slides the amount 8 steps
      Then the amount reads "4 fl oz", and at the slider's end "10 fl oz"
  how
    decide Bottle logs nothing yet: the same sheet moves to its Bottle step, keeping the stamp and the nudges
      because contents and amount are optional detail on top of the time already picked (V1_ADDITIONS §2.1)
    decide Milk and Formula are one choice, neither picked at first; tapping the picked one clears it
      because the contents are optional, and a choice must be undoable without a third button (V1_ADDITIONS §2.1)
    decide the slider starts at "No amount"; each step adds 10 ml up to 300 ml, or ½ fl oz up to 10 fl oz
      because the amount is optional, and these steps cover a baby's bottle (V1_ADDITIONS §2.1)
    decide fluid ounces where the app's region, else the phone's, is the United States, Liberia or Myanmar; millilitres everywhere else; ounces show to the nearest half and are kept as millilitres
      because those regions measure a bottle in ounces, and the in-app languages carry no region (V1_ADDITIONS §2.1)
    decide Log feed can always be tapped; a bottle with nothing picked records only its time
      because both details are optional (V1_ADDITIONS §1)
    decide the Lamp's line shows "Bottle" and not the contents or amount; they are spoken, and History shows them
      because the Lamp's line must fit its chord; the type word goes first when it would not fit (V1_ADDITIONS §2.1)
  look
    The Bottle step: under the nudges the heading in type.labelMedium color.text.dim, then Milk and Formula as one
      connected pair, Formula wider (3 to 2) so a long Danish word breaks at its hyphen; the picked one filled
      color.primary.container with a check. Then the second heading, the amount in type.titleLarge feed.text ("No
      amount" in color.text.dim), and a full-width slider at least 48dp tall: thumb and active track feed.strong,
      inactive track feed.container, no tick marks. Log feed at the end, as in the Breast step
  copy
    step.title          "Bottle"
    contents.heading    "Contents · Optional"
    contents.milk       "Milk"
    contents.formula    "Formula"
    amount.heading      "Amount · Optional"
    amount.none         "No amount"
    amount.ml           "{n} ml"                  ; "120 ml"
    amount.oz           "{n} fl oz"               ; "4 fl oz", "4.5 fl oz"
    amount.spoken       "Amount"                  ; the slider, with its value: "Amount, 120 ml"
    lamp.line           "{time} · Bottle"         ; spoken "Last feed, bottle, formula, just now, at 17:10"
    history.title       "Bottle · Formula"
    history.line        "{time} · 120 ml"
  uses      BreastFeed
  sources
    docs/design/V1_ADDITIONS.md §1 (bottle contents and amount), §2.1 (the Bottle step, ounces)
    commit ebd76a1 "Call formula feeds Bottle, and let a bottle record milk or formula"
    driven on Robolectric, 2026-11-27: on a US phone (the checks' default) "No amount", 8 steps "4 fl oz", the end
      "10 fl oz"; on a UK phone (en-GB) 12 steps "120 ml", the end "300 ml", Formula picked, Log feed (Lamp "17:10 ·
      Bottle", spoken "bottle, formula"; History "Bottle · Formula", "17:10 · 120 ml"); Formula tapped twice (cleared)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/FeedSheets.kt, the Bottle step, the slider
    core/src/main/kotlin/com/ffmathy/pooparazzi/core/format/VolumeFormatter.kt, the unit and the steps
```
