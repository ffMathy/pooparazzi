# A feed while the baby sleeps

A baby that feeds is awake. A feed logged at a time after the running sleep's
start ends that sleep there first, and the Feed sheet says so before it is
saved.

```ail
node FeedWhileAsleep : flow   level: experience
  why   A parent who feeds a waking baby should not need a second tap for Woke up,
        and must see that the feed will end the sleep before saving it.   <- Feeds
  what
    Scenario: the sheet says the sleep will end
      Given a sleep that started 20 minutes ago
      When the parent taps Feed
      Then the Feed sheet reads "Woke up will be logged too" under the nudges
    Scenario: a nudge to before the sleep began hides the note
      Given the Feed sheet opened during a sleep that started 20 minutes ago
      When the parent taps −15m and then −10m
      Then the time reads 25 minutes ago, before the sleep began, and "Woke up will be logged too" is gone
      And a tap on +5m brings it back
    Scenario: a feed before the sleep began leaves it running
      Given the Feed sheet of a sleep that started 20 minutes ago, nudged back 25 minutes
      When the parent taps Just the time
      Then the sleep keeps running: the Lamp reads "Asleep" since its start, and the sleep button "Woke up"
    Scenario: saving the feed ends the sleep
      Given the Feed sheet of a sleep that started 20 minutes ago, nudged back 10 minutes
      When the parent taps Food
      Then the sleep has ended at the feed's time: Home reads "Awake" since then, and the sleep button "Start sleep"
      And Today counts the sleep from its start to the feed, 10 minutes in clock minutes
    Scenario: a feed seconds after the sleep began cancels it
      Given a sleep started 20 minutes ago, and the Feed sheet opened seconds later and nudged back 20 minutes
      When the parent taps Food
      Then the sleep is gone: the sleep button reads "Start sleep", Home shows no "Awake" row, and Today counts no sleep
  how
    decide a feed whose time is after the running sleep's start first ends the sleep at that time, as Woke up would; one at or before the start leaves it running
      because a feeding baby is awake, and a feed logged late must not end a sleep it came before (DESIGN_SPEC §4.4 "Awake on a log")
    decide the note "Woke up will be logged too" shows under the nudges while the sheet's time is after the sleep's start, and follows every nudge
      because the parent must see before saving that the sleep will end (DESIGN_SPEC §4.4, commit 4633353)
    decide a sleep so ended less than 60 s after its start is discarded, as a mis-tap is
      because the 60 s rule is the one rule for every end of a sleep (V1_ADDITIONS §1 "Mis-taps")
    decide a feed nudged back to the sleep's own minute cancels the sleep instead of ending it: its time keeps the seconds of the Feed tap, so it lands seconds after the start
      because "unknown, confirm with the owner": is cancelling the sleep wanted here, or should such a feed leave the sleep running?
    decide the phone then posts the woke-up notification for that sleep
      because a parent elsewhere should learn the baby is awake (DESIGN_SPEC §4.4, §10.5); the notifications are their own slice
  look
    The note: a row under the nudges, 8dp below them, the wake icon 18dp in sleep.strong and the text in type.bodyMedium
      color.text.dim; it is announced when it appears or goes
  copy
    sheet.wakes         "Woke up will be logged too"
    home.awake          "Awake"
  uses      Feeds, Sleep
  sources
    docs/design/DESIGN_SPEC.md §4.4 (Awake on a log), §3.3.2 ("Woke up will be logged too")
    commit 4633353 "Log Woke up when a feed or diaper is saved during a sleep (#83)"
    driven on Robolectric, 2026-11-27: during a sleep since 4:42 PM, Feed (the note), −15m (4:47 PM, the note), −10m
      (4:37 PM, gone), +5m (4:42 PM, back), Food at 4:42 PM (the sleep gone, Today "less than a minute" of sleep);
      Just the time at 4:45 PM during a sleep since 4:50 PM (still asleep); Food at 5:00 PM ("Awake 10m · 5:00 PM")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeStateBuilder.kt, the note
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, waking for a log
```
