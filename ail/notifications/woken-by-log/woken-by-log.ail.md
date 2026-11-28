# A log that ends a sleep

A feed or diaper logged during a sleep ends it at its time. The phone then
says so in the woke-up notification, wherever the log was made.

```ail
node WokenByLog : flow   level: experience
  why   A sleep ended by a feed or diaper log was not ended by the parent's own tap,
        so the phone says why the sleep stopped, and lets its end be moved.   <- Notifications
  what
    Scenario: a diaper ends the sleep
      Given notifications allowed, and a sleep started 20 minutes ago
      When the parent logs a pee on Home's Diaper sheet now
      Then the shade shows "Woke up at" the time now, over "The diaper change ended the sleep · Slept" the sleep's length ("Slept 20m")
      And expanded it reads "The diaper change ended the sleep", then "Slept" its length "·" its start "–" its end, then "Pee just now ·" the time, then the Today line
      And it has the buttons "−5m", "−10m" and "−15m"
    Scenario: a feed ends the sleep
      Given notifications allowed, and a sleep started 20 minutes ago
      When the parent logs Food on Home's Feed sheet now
      Then the shade shows "Woke up at" the time now, over "The feed ended the sleep · Slept" the sleep's length
    Scenario: a log that ended the sleep over half an hour ago says nothing
      Given notifications allowed, and a sleep started 50 minutes ago
      When the parent logs Food nudged 35 minutes back
      Then the sleep ends at the feed's time, and the shade shows no notification
  how
    decide a sleep counts once it was seen running and then ends at the very time of a feed or diaper of the same baby, logged on this phone, the watch or another family phone; the two may arrive in either order
      because a watch's sync batch can bring the feed before the sleep's end (app/…/notify/WokenByLog.kt)
    decide once per sleep, and only within 30 minutes of its end
      because a sync that catches up hours later must not ring an old wake-up (DESIGN_SPEC §4.4)
    decide the reason leads the text ("The feed ended the sleep · Slept 20m") and the expanded text; the title and the Earlier offsets are those of the woke-up notification
      because the parent sees at once why the sleep stopped (DESIGN_SPEC §4.4; commit c2297ad)
  copy
    reason.feed         "The feed ended the sleep"
    reason.diaper       "The diaper change ended the sleep"
    text                "{reason} · Slept {duration}"
  uses      WokeUpNotification
  sources
    docs/design/DESIGN_SPEC.md §4.4 (Awake on a log), §10.5; commit c2297ad
    driven on Robolectric, 2026-11-27: a pee 20 minutes into a sleep ("Woke up at 7:27 PM" / "The diaper change ended the sleep
      · Slept 20m", expanded "Slept 20m · 7:07 PM – 7:27 PM", "Pee just now · 7:27 PM", Today), Food 35 minutes back on a
      sleep started 50 minutes before (nothing)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/notify/WokenByLog.kt, app/…/system/SystemSurfaces.kt
```
