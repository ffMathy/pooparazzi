# Sleep on the watch

Main's sleep edge button starts a sleep and ends it, as the phone's sleep
button does. Each tap shows Logged, where a late tap is fixed (on Time) or taken
back with Undo. A second tap within two seconds changes nothing.

```ail
node WatchSleep : flow   level: experience
  why   A parent logs when the baby fell asleep and woke up with one tap on the
        wrist, in the dark, and a double tap must never toggle a sleep twice.   <- Watch
  what
    Scenario: starting a sleep on the watch
      Given the baby is awake on Main
      When the parent taps Start sleep
      Then Logged reads "Asleep at" the time now, with Undo
      And once Logged has closed, Main's title reads "Asleep" and how long in clock minutes ("Asleep <1m" within the start's minute) and the sleep edge button "Woke up", in the state "Asleep since" the time now
    Scenario: a second tap within two seconds changes nothing on the watch
      Given a sleep started a second ago, its Logged closed with a tap on the background
      When the parent taps Woke up
      Then the sleep edge button reads "Already" over "asleep", spoken "Already asleep", and the sleep keeps running
      And two seconds later it reads "Woke up" again
    Scenario: waking up on the watch
      Given a sleep that started 10 minutes ago
      When the parent taps Woke up
      Then Logged reads "Woke up at" the time now over "Slept 10m", spoken "Slept 10 minutes", with Undo
      And once Logged has closed, the sleep edge button reads "Start sleep", in the state "Awake since" the time now
    Scenario: waking up within a minute cancels the sleep on the watch
      Given a sleep started a few seconds ago, past the two seconds, its Logged closed
      When the parent taps Woke up
      Then Logged reads "Sleep cancelled" over how long it lasted in clock minutes: "Slept <1m", spoken "Slept less than a minute", or "Slept 1m" when it crossed a minute's turn (4:20:50 to 4:21:10), with Undo and no time to change
      And turning the crown there opens nothing
      And once Logged has closed, the sleep edge button reads "Start sleep" with no state, as no sleep is left
  how
    decide one edge button toggles: Start sleep while awake, Woke up while a sleep runs, spoken "Woke up, end sleep"; it is always the sleep toggle, never a feed's
      because only a sleep runs on the watch; feeds are instants (V1_ADDITIONS §2.2 "Main never has a breastfeeding state")
    decide Start sleep shows Logged "Asleep at" its time, like a feed or a diaper, where the time can be moved and Undo removes the sleep
      because a sleep's start is fixed the same way as every watch log (commit be99c60)
    decide a tap within two seconds of a toggle shows "Already" over "asleep" (or "awake") on the button for two seconds and changes nothing
      because a double tap must never toggle a sleep twice; one line would be cut by the button's width (DESIGN_SPEC §3.5.2, §11)
    decide a tap that crosses a change made elsewhere (the phone or the Tile ended or started the sleep) shows the same guard instead of flipping it back
      because the first toggle wins (DESIGN_SPEC §4.4; wear/…/MainViewModel.kt)
    decide a sleep ended less than 60 seconds after its start is discarded: "Sleep cancelled", "Slept <1m", Undo resumes it; it has no time to move
      because such sleeps are mis-taps (DESIGN_SPEC §3.5.6, §4.4; V1_ADDITIONS §1)
    decide a cancelled sleep still says how long it lasted in clock minutes, so one that crossed a minute's turn reads "Sleep cancelled" over "Slept 1m"
      because "unknown, confirm with the owner"
    decide the button's state is "Asleep since" the start or "Awake since" the last sleep's end, and none before any sleep
      because TalkBack reads the clock time, which never goes stale (DESIGN_SPEC §9.4; commit 36467d5)
    decide the first Start sleep asks for the notification permission in context
      because the Ongoing Activity needs it on API 33 and later (wear/…/ongoing/NotificationPermissionRequest.kt)
  look
    The guard: the icon goes and two centred lines "Already" / "asleep" in wear.labelMedium sleep.on.container, at
      full opacity, in the button's own fill; nothing else on Main moves
    Logged for a sleep: the check circle in sleep.container with the check in sleep.strong (a cancelled sleep: the wake
      icon), the time in sleep.text
  copy
    logged.asleep       "Asleep at {time}"
    logged.woke         "Woke up at {time}"
    logged.cancelled    "Sleep cancelled"
    logged.slept        "Slept {duration}"         ; "Slept 10m", "Slept <1m"; spoken "Slept 10 minutes", "Slept less than a minute"
    guard.asleep        "Already\nasleep"          ; spoken "Already asleep"
    guard.awake         "Already\nawake"           ; spoken "Already awake"
  uses      WatchLogged
  sources
    docs/design/DESIGN_SPEC.md §3.5.2 (W2 and the guard state), §3.5.6 (Logged's kinds), §3.5.11, §4.4, §5.12 (2 s)
    commit be99c60 "Move a sleep's start on the watch, as a feed's or diaper's time (#97)"
    driven on Robolectric, 2026-11-27: Start sleep (Logged "Asleep at 5:06 PM"), Woke up 1.3 s after it ("Already" /
      "asleep", gone 2 s later), Woke up after 10 minutes ("Woke up at 5:17 PM", "Slept 10m"), Woke up within a minute
      ("Sleep cancelled", "Slept <1m", the crown opening nothing)
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/MainViewModel.kt, the sleep toggle and its guard (2 s)
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/main/MainScreen.kt, the sleep edge button
```
