# Bottle

After Bottle, one list says what the bottle held and how much: Milk or Formula,
an amount stepper and three amount chips, all optional, then Save.

```ail
node WatchBottle : flow   level: experience
  why   A bottle's contents and amount help the parent see how much the baby
        drinks, but at 3am only the time is required, and the usual amount is one
        tap away.   <- WatchFeed
  what
    The amounts are in the region's unit: fluid ounces on a watch set to US English, as here; millilitres elsewhere (10 ml steps up to 300 ml, chips 60 · 90 · 120 ml).
    Scenario: the bottle screen
      Given Feed type on a watch set to US English, with no bottle logged
      When the parent taps Bottle
      Then Bottle reads "Bottle · " and the time of the Feed tap, over Milk and Formula, neither chosen
      And the amount reads "No amount", its "0.5 fl oz less" button can't be tapped, and the chips read 2, 3 and 4 fl oz, none chosen
    Scenario: a second tap within half a second keeps a choice
      Given Bottle with nothing chosen
      When the parent taps Milk twice within half a second
      Then Milk stays chosen
      And a tap on Milk a second later clears it
    Scenario: stepping the amount
      Given Bottle with no amount
      When the parent taps "0.5 fl oz more"
      Then the amount reads "0.5 fl oz", spoken "Amount, 0.5 fl oz"
      And at "10 fl oz" "0.5 fl oz more" can't be tapped
    Scenario: an amount chip sets the amount
      Given Bottle with no amount
      When the parent taps the 3 fl oz chip
      Then the chip is chosen and the amount reads "3 fl oz"
      And a step up makes it "3.5 fl oz", with no chip chosen
    Scenario: saving a bottle
      Given Bottle with Formula and 4.5 fl oz chosen
      When the parent taps Save
      Then Logged reads "Bottle at" the time of the Feed tap over "Formula · 4.5 fl oz"
      And with nothing chosen, Logged reads "Bottle at" that time with no second line
    Scenario: the chips follow the last bottle
      Given a bottle of 4.5 fl oz logged a minute ago
      When the parent opens Bottle again
      Then the chips read 3.5, 4.5 and 5.5 fl oz, the middle one spoken "4.5 fl oz, as last time", none chosen
  how
    decide contents, amount and chips are all optional; Save is always enabled and logs what was picked, or just the time
      because only the time is ever required (README; V1_ADDITIONS §2.2)
    decide the amount steps as the phone's slider: 10 ml up to 300 ml, or half a fluid ounce up to 10 fl oz where the region uses ounces
      because both apps must record the same amounts (V1_ADDITIONS §2.2)
    decide three chips in ascending order, a step of 30 ml (or 1 fl oz) apart, centred on this baby's last bottle with an amount within 7 days, else on 90 ml or 3 fl oz; taken once when Bottle opens
      because a position always means a size, and the chips never change under a finger (V1_ADDITIONS §2.2)
    decide tapping the chosen option clears it, unless it was chosen less than 500 ms ago; a step never clears anything
      because a double tap on an option must leave it chosen (DESIGN_SPEC §3.5.11, §5.9)
    decide everything ignores taps for 500 ms when Bottle appears
      because a double tap on Bottle must not land on Milk (DESIGN_SPEC §5.9)
  look
    Bottle: "Bottle · 5:09 PM" in wear.titleMedium color.text; Milk and Formula as full-width radio buttons in
      feed.container (chosen: feed.lit with the dot in feed.strong), radius.watch.button; the stepper's − and + as
      48dp compact buttons around the amount in wear.titleMedium feed.strong ("No amount" in wear.labelSmall);
      three chips, the number over the unit, radius.watch.chip, chosen in feed.lit with a 2dp feed.strong border and a
      check; Save as WatchSide's
  copy
    bottle.title      "Bottle · {time}"
    bottle.milk       "Milk"
    bottle.formula    "Formula"
    bottle.noAmount   "No amount"                ; spoken "Amount, No amount"
    bottle.amount     "{amount}"                 ; "4.5 fl oz", spoken "Amount, 4.5 fl oz"
    bottle.less       "{step} less"              ; spoken name of −: "0.5 fl oz less"
    bottle.more       "{step} more"              ; spoken name of +: "0.5 fl oz more"
    bottle.lastTime   "{amount}, as last time"
    logged.bottle     "Bottle at {time}"
    logged.details    "{contents} · {amount}"    ; "Formula · 4.5 fl oz", "Milk", "3 fl oz"
  uses      WatchLogged, WatchSide
  sources
    docs/design/V1_ADDITIONS.md §2.2 (the Bottle screen), DESIGN_SPEC.md §3.5.3 (Bottle, chip grids), §5.9
    commit 9da03a4 "Watch: fill in every detail on one screen, no sub-screens"
    driven on Robolectric (US English: fluid ounces), 2026-11-27: Bottle ("Bottle · 5:06 PM", "No amount", 2 · 3 · 4 fl
      oz), Milk twice within 500 ms (kept) and again later (cleared), + to 10 fl oz (+ disabled), the 3 fl oz chip, Save
      ("Formula · 4.5 fl oz"; "Milk"; nothing), Bottle again (3.5 · 4.5 "as last time" · 5.5 fl oz)
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/feed/BottleScreen.kt
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/MainViewModel.kt, the bottle draft and the clear guard
```
