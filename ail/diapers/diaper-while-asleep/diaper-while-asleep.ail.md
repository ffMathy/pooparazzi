# A diaper while the baby sleeps

A baby being changed is awake. A diaper saved at a time after the running
sleep's start ends that sleep there first, and the Diaper sheet says so before
it is saved, as the Feed sheet does.

```ail
node DiaperWhileAsleep : flow   level: experience
  why   A parent who changes a waking baby should not need a second tap for Woke up,
        and must see that the change will end the sleep before saving it.   <- Diapers
  what
    Scenario: the Diaper sheet says the sleep will end
      Given a sleep that started 20 minutes ago
      When the parent taps Diaper
      Then the Diaper sheet reads "Woke up will be logged too" under the nudges
    Scenario: saving the diaper ends the sleep
      Given the Diaper sheet of a sleep that started 20 minutes ago, Pee ticked
      When the parent taps Save
      Then the sleep has ended at the change's time: Home reads "Awake" since then, and the sleep button "Start sleep"
      And Home's diaper row reads "Pee", and Today counts the sleep from its start to the change, 20 minutes in clock minutes
    Scenario: a diaper before the sleep began leaves it running
      Given the Diaper sheet of a sleep that started 20 minutes ago
      When the parent taps −15m and then −10m
      Then "Woke up will be logged too" is gone
      And after Save the sleep keeps running: the Lamp reads "Asleep" since its start, and the sleep button "Woke up"
  how
    decide a diaper whose time is after the running sleep's start ends the sleep at that time; one at or before it leaves the sleep running
      because a baby being changed is awake (DESIGN_SPEC §4.4 "Awake on a log"), the same rule as for a feed
    decide the note and its rules are the Feed sheet's
      because both sheets log an instant that may wake the baby (DESIGN_SPEC §3.3.2: "the Feed sheet shows it too")
  look
    The note as on the Feed sheet, under the Diaper sheet's nudges
  copy
    sheet.wakes         "Woke up will be logged too"
    home.awake          "Awake"
  uses      Diapers, FeedWhileAsleep, Sleep
  sources
    docs/design/DESIGN_SPEC.md §4.4 (Awake on a log), §3.3.2; README.md "Diapers", While asleep
    commit 4633353 "Log Woke up when a feed or diaper is saved during a sleep (#83)"
    driven on Robolectric, 2026-11-27: during a sleep since 4:42 PM, Diaper (the note), Pee, Save ("Awake <1m · 5:02
      PM", "Pee", Today 20 minutes of sleep)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/DiaperSheet.kt, the note
```
