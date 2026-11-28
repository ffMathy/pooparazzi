# The prediction notifications

What the last week expects next, as Home's buttons show it, as silent
notifications while the app is closed: one per kind, each one the parent can
turn off.

```ail
node ForecastNotifications : flow   level: experience
  why   A parent with the app closed should still see when the next feed is due,
        and choose which of these reminders they want at all.   <- Notifications
  what
    Scenario: the next feed in a notification
      Given notifications allowed, feeds at 8:00 AM and 11:00 AM four days ago and at 8:00 AM, 11:00 AM and 2:00 PM three and two days ago
      And a feed logged now with Just the time
      Then the shade shows "Next feed in 3h 05m" over "Around" the time 3 hours from now rounded to 5 minutes, and it cannot be swiped away
    Scenario: the next feed soon
      Given the same feeds four, three and two days ago, and a feed 2 hours 58 minutes ago
      Then the shade shows "Next feed soon" over "Around" a clock time
    Scenario: the next feed due now
      Given the same feeds four, three and two days ago, and a feed 3 hours 10 minutes ago
      Then the shade shows "Feed due now" over "Around" a clock time
    Scenario: no week to go on, no prediction
      Given notifications allowed, and a feed logged now on a new install
      Then the shade shows no notification
    Scenario: a kind turned off in the system's settings
      Given the same feeds four, three and two days ago, and a feed logged now, its notification in the shade
      When the parent turns off "Next feed" in the system's notification settings, and a few seconds later logs another feed
      Then the shade shows no "Next feed" notification
      And Settings › Notifications › What comes next reads Next feed "Off", Next poop "On", Next sleep and wake-up "On"
    Scenario: Settings opens a kind's system page
      Given notifications allowed, on Settings
      When the parent taps Next feed under What comes next
      Then the system's notification settings open on the app's "Next feed"
  how
    decide one ongoing, silent notification per kind and baby, only while the week predicts that kind: "Next feed in 1h 05m", "Next poop soon", "Next sleep in 20m" (awake) or "Expected to wake in 40m" (asleep), each over "Around" the clock time, with the baby's label above when it is named or there are several
      because Home shows the same estimates; a kind the week cannot predict shows nothing (DESIGN_SPEC §10.5; V1_ADDITIONS §9)
    decide the words follow the estimate rules (rounded up to 5 minutes, "soon" under 5 minutes, "due now" once due), counted from the start of the current minute, so a feed logged now reads "in 3h 05m" where Home reads "3h 00m"
      because "unknown, confirm with the owner"
    decide each kind has its own channel ("Next feed", "Next poop", "Next sleep and wake-up", in the group "What comes next"), on until the parent turns it off in the system's settings; Settings › Notifications lists their state and opens each one's system page
      because the parent chooses which ones show, one switch each (commit 1b7624c; DESIGN_SPEC §10.5)
    decide re-rendered with the sleep notification, once a minute while any shows; one swiped away is not posted again for that estimate, and the next estimate of its kind shows again
      because an estimate once dismissed should stay dismissed (DESIGN_SPEC §10.5)
  copy
    feed                "Next feed in {duration}" "Next feed soon" "Feed due now"
    poop                "Next poop in {duration}" "Next poop soon" "Poop due now"
    sleep               "Next sleep in {duration}" "Next sleep soon" "Sleep due now"
    wake                "Expected to wake in {duration}" "Expected to wake soon" "Wake-up due now"
    around              "Around {time}"
    channels            "Next feed" "Next poop" "Next sleep and wake-up"
    group               "What comes next"
    settings.state      "On" "Off"
    settings.hint       "What Home expects next, as a silent notification while the app is closed. Tap one to turn it on or off in Android's notification settings."
  uses      SleepNotification
  sources
    docs/design/DESIGN_SPEC.md §10.5 (Prediction notifications); V1_ADDITIONS.md §9; commit 1b7624c
    driven on Robolectric, 2026-11-27: feeds three hours apart over three days made with History's Add entry, then a feed now
      ("Next feed in 3h 05m" / "Around 10:25 PM", while Home and the widget read "3h 00m"), 2h 58m ago ("Next feed soon"),
      3h 10m ago ("Feed due now"), "Next feed" off in the system's settings (gone; Settings read Off); a sleep with naps of
      90 minutes in the week showed no "Expected to wake" at 7:29 PM, so the sleep and poop kinds are not checked here
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/notify/ForecastNotifications.kt, app/…/ui/settings/SettingsScreen.kt
```
