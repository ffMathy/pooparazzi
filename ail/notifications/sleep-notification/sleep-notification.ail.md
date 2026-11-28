# The running-sleep notification

While a sleep runs, the shade shows how long and since when, the baby's last
feed and diaper and today's counts, and Woke up.

```ail
node SleepNotification : flow   level: experience
  why   At night the parent checks the lock screen, not the app: how long the baby
        has slept, when they last ate, and one tap to log the wake-up.   <- Notifications
  what
    Scenario: the notification of a running sleep
      Given notifications allowed, and a sleep started on Home and nudged 20 minutes back
      Then the shade shows "Asleep" over how long it has run in clock minutes and since when ("21m · since 6:07 PM")
      And expanded it adds "Today · 0 feeds · 0 pee · 0 poop ·" and that duration, then "sleep"
      And it has one button, Woke up, and cannot be swiped away
    Scenario: the last feed shows in the expanded notification
      Given notifications allowed, a sleep running, and Food logged 15 minutes ago, before the sleep began
      Then the expanded notification adds "Fed", the clock minutes since the feed, "ago ·" the feed's time "· Food" ("Fed 15m ago · 4:27 AM · Food" right then), and Today counts 1 feed
    Scenario: Woke up on the notification ends the sleep
      Given notifications allowed, and a sleep started 20 minutes ago
      When the parent taps Woke up on its notification
      Then the "Asleep" notification goes, and the shade shows "Woke up at" the time now
      And Home reads "Awake"
    Scenario: Woke up within a minute cancels the sleep
      Given notifications allowed, and a sleep started a few seconds ago
      When the parent taps Woke up on its notification
      Then the shade shows no notification, and Home's sleep button reads "Start sleep"
  how
    decide title "Asleep" for one unnamed baby, else "<name> is asleep"; text the duration in whole clock minutes and the start; re-rendered at each minute by a non-waking alarm, never a ticking chronometer
      because a ticking value is noise at 3am, and a phone that wakes up shows the current value (DESIGN_SPEC §3.3.7, §10.5)
    decide expanded, the last feed ("Fed 1h 32m ago · 01:40 · L → R", the type for other feeds), the last diaper ("Pee 20m ago · 02:52") and today's counts, leaving out lines without data
      because the parent should not have to subtract times at 3am (DESIGN_SPEC §3.3.7)
    decide ongoing: it stays while the sleep runs, asks to be promoted to a Live Update where the system allows, and its status chip reads "Asleep"; on Android 17 it shows "Asleep for", "Since" and "Last fed" as system metrics
      because a running sleep is the one thing worth a Live Update (DESIGN_SPEC §3.3.7, §10.5)
    decide one the parent dismisses is not posted again for that sleep
      because a dismissal is the parent's choice (DESIGN_SPEC §10.5)
    decide Woke up ends the sleep now without opening the app, like the app's Woke up: under a minute it is discarded as a mis-tap and nothing follows; otherwise the woke-up notification follows
      because the end is logged from the lock screen as often as from the app (DESIGN_SPEC §3.3.7, §4.4)
    decide feeds are never running notifications
      because feeds are instants (V1_ADDITIONS §2.1)
  look
    The system's notification, as its own template draws it in the shade: the small sleepRunning icon in a filled circle
      and the app's name, the title, the text, and expanded the lines in order, one a line; under them the Woke up button,
      drawn by the system as its label alone in the accent colour (capitals on this system, "WOKE UP"; the wake icon the app
      gives it is not shown). After a wake-up the shade holds the woke-up notification instead, and
      after a cancel it is empty; the app stays visible under the shade
  copy
    title               "Asleep"
    title.named         "{name} is asleep"
    text                "{duration} · since {time}"
    line.fed            "Fed {since} ago · {time} · {detail}"
    line.diaper         "{kind} {since} ago · {time}"
    line.today          "Today · {feeds} feeds · {pee} pee · {poop} poop · {duration} sleep"
    button.woke         "Woke up"
  sources
    docs/design/DESIGN_SPEC.md §3.3.7 (P6), §10.5; BABIES_AND_BACKUP.md §2.3
    driven on Robolectric, 2026-11-27: a sleep nudged 20 minutes back ("Asleep" / "21m · since 6:07 PM", "Today · 0 feeds · 0
      pee · 0 poop · 21m sleep"), Food 15 minutes back ("Fed 15m ago · 7:05 PM · Food", "Today · 1 feed …"), Woke up on it
      ("Woke up at 6:28 PM"), Woke up on a sleep a few seconds old (nothing)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/notify/TimerNotifications.kt, TimerActionReceiver.kt
```
