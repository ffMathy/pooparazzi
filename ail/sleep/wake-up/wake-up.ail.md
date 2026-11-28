# Waking up

Woke up ends the running sleep now and brings up its receipt. A sleep that
lasted less than a minute was a mis-tap, and is cancelled instead.

```ail
node WakeUp : flow   level: experience
  why   The end of a sleep is logged as late as its start, and a sleep started by
        mistake must not stay in the log as a sleep.   <- Sleep
  what
    Scenario: waking up
      Given a sleep that started 20 minutes ago
      When the parent taps Woke up
      Then the receipt reads "Woke up" over the time now and how long the baby slept in clock minutes ("Slept 20m" from 4:20 PM to 4:40 PM), with Undo
      And it offers the nudges −15m, −10m, −5m and +5m, of which +5m can't be tapped
      And the sleep button reads "Start sleep"
      And Home reads "Awake" since the time now
    Scenario: undoing a wake-up
      Given a sleep that started 20 minutes ago has just been ended with Woke up
      When the parent taps Undo on its receipt
      Then the notice reads "Undone"
      And the sleep runs again: the Lamp reads "Asleep" since its start 20 minutes ago, and the sleep button "Woke up"
    Scenario: going back from the receipt keeps the log
      Given a sleep that started 20 minutes ago has just been ended with Woke up
      When the parent goes back
      Then the receipt closes and Home reads "Awake"
    Scenario: a sleep ended within a minute is cancelled
      Given a sleep started a few seconds ago, its receipt closed with Done
      When the parent taps Woke up
      Then the receipt reads "Sleep cancelled" over "Slept <1m", with Undo and Done
      And it offers no nudges and no Edit
      And the sleep button reads "Start sleep"
    Scenario: undoing a cancelled sleep
      Given a sleep that was just cancelled
      When the parent taps Undo on its receipt
      Then the sleep runs again from its start, and the sleep button reads "Woke up"
  how
    decide Woke up ends the sleep now; its receipt's nudges move the end, never before the start nor into the future
      because the end is logged late as often as the start (DESIGN_SPEC §3.3.1, §4.4)
    decide a sleep ended less than 60 seconds after it started is discarded, and the receipt says "Sleep cancelled", with Undo but no nudges or Edit
      because such sleeps are mis-taps; this removes junk entries without an extra control (DESIGN_SPEC §11)
    decide Undo after Woke up or a cancel makes the sleep run again from its start, and the notice "Undone" shows for two seconds
      because every log can be taken back at once (DESIGN_SPEC §1 "Undo after every log")
    decide after a cancel, Today's sleep reads "<1m", as Today does whenever nothing was slept (TodayGrid)
      because "unknown, confirm with the owner": the cancelled sleep is gone and counts nothing, yet "<1m" can read as a short sleep that was kept; keep it, or show "0m"?
    decide going back, like Done, closes the receipt and keeps what it shows
      because the receipt has no timeout and closing it is never a choice to undo (DESIGN_SPEC §3.3.1)
  look
    The receipt is the one StartSleep shows, with the wake icon in sleep.strong and the title "Woke up" (or "Sleep
      cancelled"); the time line reads the end and how long the baby slept ("3:37 PM · Slept 20m"); a cancel's reads
      "Slept <1m" alone, and it has no nudges and no Edit, so Done stands alone in the last row
    Home after waking: the Lamp is back to "Last fed", and the first row under it reads "Awake" over how long since the
      sleep ended
    Behind a cancel's receipt, Today's sleep reads "<1m", as it does with no sleep at all: the cancelled sleep counts no
      minutes, but nothing slept is written "<1m", not "0m"
  copy
    receipt.woke        "Woke up"
    receipt.cancelled   "Sleep cancelled"
    receipt.slept       "Slept {duration}"         ; "Slept 20m", "Slept <1m"
    notice.undone       "Undone"
    home.awake          "Awake"
  uses      StartSleep
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (the receipt table: Woke up, Sleep cancelled), §4.4 (mis-tap sleep), §11
    docs/design/V1_ADDITIONS.md §1 (mis-taps: a sleep that ends less than 60 s after its start is discarded)
    driven on Robolectric, 2026-11-27: Woke up after a start nudged 20 minutes back ("3:37 PM · Slept 20m"), Undo (the
      sleep runs again since 3:17 PM), Woke up within a minute ("Sleep cancelled", "Slept <1m", Undo and Done only),
      Undo of the cancel, Back on the receipt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, ending a sleep and Undo
    core/src/main/kotlin/com/ffmathy/pooparazzi/core/data, the repository's end of a sleep (ended, discarded, not running)
```
