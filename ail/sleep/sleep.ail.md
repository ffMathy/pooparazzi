# Sleep on the phone

One button on Home starts a sleep, the same button ends it. Each tap brings up
a receipt that says what was logged and when, moves that time back if the
parent was late, and takes the tap back with Undo.

Written from the running app (Robolectric, a fresh install, 2026-11-27), the
design documents and the phone app's code. Times in the scenarios are the
phone's own clock, written as the phone writes them (12- or 24-hour, as it is
set).

```
sleep.ail.md                    Sleep: the sleep button, the guard and the cooldown
start-sleep/start-sleep.ail.md  StartSleep: Start sleep, its receipt, nudges, Undo and Edit
wake-up/wake-up.ail.md          WakeUp: Woke up, its receipt, a cancelled sleep, Undo
```

```ail
node Sleep : flow   level: experience
  why   A parent should log when the baby fell asleep and woke up with one tap of
        one hand, in the dark, and be able to fix a late or wrong tap at once.   <- Pooparazzi
  what
    The sleep button starts a sleep while the baby is awake and ends it while a sleep runs; each tap brings up a receipt.
    Scenario: a second tap within two seconds changes nothing
      Given a sleep started a second ago, its receipt closed with Done
      When the parent taps Woke up
      Then the sleep keeps running and the sleep button still reads "Woke up"
      And the notice reads "Asleep" over "Already asleep since" the sleep's start
      And two seconds later the notice is gone
  how
    decide one button toggles: Start sleep while awake, Woke up while a sleep runs; at most one sleep runs
      because the core requirement is to "register start and stop actions for sleeping" with one tap (DESIGN_SPEC §1)
    decide for two seconds after a tap on the sleep button, another tap changes nothing and the notice says what is already true ("Already asleep since …", "Already awake since …")
      because a double tap must never toggle a sleep twice (DESIGN_SPEC §11); the notice makes the refusal visible, as no rule is silent (§4.4)
    decide right after a receipt closes, Home ignores taps on the sleep button for 600 ms, without a notice
      because a double tap must never land on a different target (DESIGN_SPEC §5.9, §11)
    decide the sleep button is one control whose label changes: "Start sleep", or "Woke up" while asleep, spoken "Woke up, end sleep"; it exposes no on/off state
      because the label already says what a tap does (DESIGN_SPEC §9.4)
  look
    Home's sleep button: full width inside gutter.phone, size.toggle, radius.button, fill sleep.container with the moon icon
      and "Start sleep" in type.headlineSmall sleep.on.container; while asleep fill sleep.lit with a 2dp sleep.strong border,
      the wake icon and "Woke up"; its shape never changes with its state
    The notice: in place of Today, a card of fill color.notice with radius.notice, the moon icon in sleep.strong and "Asleep"
      in type.titleMedium color.text, and under it the guard line in type.dataMedium sleep.text
  copy
    toggle.start        "Start sleep"
    toggle.end          "Woke up"
    toggle.spoken.end   "Woke up, end sleep"
    notice.asleep       "Asleep"
    guard.asleep        "Already asleep since {time}"
    guard.awake         "Already awake since {time}"
  children  StartSleep, WakeUp
  sources
    docs/design/DESIGN_SPEC.md §1 (the core requirement), §3.3.1 (the Sleep toggle, the notice card, Home states and
      interactions), §4.4 (rules), §5.9 (cooldowns), §5.12 (timing), §9.4, §11 (decisions)
    driven on Robolectric (the real MainActivity, a fresh install with the first run skipped), 2026-11-27: Woke up 1.5 s
      after Start sleep (the guard), and Woke up 0.7 s after Done (ignored: the cooldown)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, the sleep toggle and the Home cooldown
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeModels.kt, HomeTimings (2 s guard, 600 ms cooldown)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeButtons.kt, the toggle
```
