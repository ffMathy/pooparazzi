# Time

Opened from Logged by its time (a tap, or a turn of the crown), Time moves the
time just logged: −5m and +5m, or a minute per step of the crown, then Done. It
is the only place a watch log's time moves, the same for a feed, a diaper, a
sleep's start and a wake-up.

```ail
node WatchTime : flow   level: experience
  why   The parent often logs on the watch a while after it happened; the time is
        fixed right after logging, with the crown, never on the way in.   <- Watch
  what
    Scenario: moving a feed's time
      Given Logged for a food feed logged just now, after its first second
      When the parent taps its time
      Then Time reads "Feed" over the feed's time, with −5m, +5m and Done
      And +5m can't be tapped, as the time is now, and for half a second nothing can be tapped
    Scenario: moving a feed's time back ten minutes
      Given Time for a food feed logged just now
      When the parent taps −5m twice and Done
      Then Time read "Feed · −10m" over the time 10 minutes ago, spoken "Feed," that time ", 10 minutes earlier"
      And Logged is back reading "Food at" the time 10 minutes ago
      And once Logged has closed, the Feed row reads "10m"
    Scenario: the crown moves the time a minute a step
      Given Time for a food feed logged just now
      When the parent turns the crown three steps anticlockwise, then one step clockwise
      Then Time reads "Feed · −3m", then "Feed · −2m", over the time 2 minutes ago
    Scenario: turning the crown on logged opens time
      Given Logged for a plain diaper change logged just now
      When the parent turns the crown
      Then Time reads "Diaper" over the diaper's time
    Scenario: swiping back from time keeps the time
      Given Time for a food feed, moved to "−5m"
      When the parent swipes right
      Then Logged is back reading "Food at" the feed's own time, and nothing moved
    Scenario: moving a sleep's start
      Given Logged for a sleep started just now, after its first second
      When the parent taps its time, taps −5m twice and Done
      Then Logged reads "Asleep at" the time 10 minutes ago
      And once Logged has closed, Main's title reads "Asleep 10m"
    Scenario: a wake-up stays a minute after the sleep's start
      Given a sleep that started 10 minutes ago, just ended with Woke up
      When the parent opens Time from its Logged and taps −5m twice
      Then Time reads "Woke up · −9m", and −5m can't be tapped any more
      And Done shows Logged "Woke up at" that time over "Slept 1m"
  how
    decide the time moves up to now and back up to 12 hours; a wake-up no earlier than a minute after its sleep's start; a sleep's start or wake-up never onto another sleep (a start back to the previous sleep's end at most, a wake-up up to the next sleep's start)
      because a log is never in the future, and sleeps never overlap (DESIGN_SPEC §3.5.4; commits be99c60, c043766)
    decide −5m and +5m move 5 minutes; the crown a minute per step (about 18 dp of rotation, one detent of a bezel), clockwise later; at a limit nothing moves and the button can't be tapped
      because the crown is the watch's precise control (DESIGN_SPEC §3.5.4)
    decide the heading shows the offset from the time Time opened with ("· −7m", from an hour "· −1h 05m"); heading and time are read as one
      because the parent sees how far it moved (DESIGN_SPEC §3.5.4)
    decide nothing changes before Done; Done moves the entry, restarts Logged's five seconds and goes back to it; swiping back leaves the time as it was
      because a stray turn must not change the log (DESIGN_SPEC §3.5.4)
    decide moving a feed's or diaper's time also moves the wake-up it caused
      because the sleep ended when the baby was fed (DESIGN_SPEC §3.5.6; commit 4633353)
    decide a cancelled sleep has no time to move
      because nothing was logged (DESIGN_SPEC §3.5.4)
    decide the buttons and Done ignore taps for 500 ms when Time appears
      because a double tap on the time must not land on −5m (DESIGN_SPEC §3.5.4, §5.9)
  look
    Time: the heading 12 % from the top in wear.titleMedium color.text, over the time in the Wear system's display small
      type, in the event's colour (feed.strong, diaper.text, sleep.text); under it −5m and +5m as two tonal compact
      buttons 64dp wide, radius.watch.pill, 8dp apart; Done as a size.edge.small edge button in color.primary.container
      with the check icon and "Done" in color.on.primary.container
  copy
    time.feed         "Feed"
    time.diaper       "Diaper"
    time.asleep       "Asleep"
    time.woke         "Woke up"
    time.offset       "{kind} · {offset}"        ; "Feed · −10m", spoken "Feed, 4:56 PM, 10 minutes earlier"
    time.earlier      "−5m"                       ; spoken "5 minutes earlier"
    time.later        "+5m"                       ; spoken "5 minutes later"
    time.done         "Done"
  uses      WatchLogged
  sources
    docs/design/DESIGN_SPEC.md §3.5.4 (W4 Time), §3.5.11; V1_ADDITIONS.md §2.2 ("every watch log's time is moved afterwards")
    commits bc0ac14, be99c60 "Move a sleep's start on the watch", c043766 "Never let a sleep be saved on top of another"
    driven on Robolectric, 2026-11-27: Time from a sleep's start ("Asleep · −5m", "−10m", the crown −3 to "−13m" and +1
      to "−12m", Done), from a diaper by the crown, +5m disabled at now, swipe back, a wake-up moved to "−9m" with −5m
      disabled ("Slept 1m")
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/time/TimeScreen.kt, wear/…/ui/Rotary.kt (18 dp a step)
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/MainViewModel.kt, the Time screen's limits and Done
```
