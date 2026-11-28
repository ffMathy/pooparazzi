# The phone's notifications

While a sleep runs, a silent notification shows it, with Woke up; after Woke
up there, a second one moves the wake-up time. A feed or diaper that ends a
sleep says so in one. Prediction notifications say what comes next. The app
asks for the notification permission the first time a sleep starts.

Written from the running app (Robolectric, a fresh install, the notification
shade read as it shows each notification: title, text, expanded text and
buttons, 2026-11-27), the design documents and the phone app's code.

```
notifications.ail.md                                Notifications: the permission, a tap on a notification
sleep-notification/sleep-notification.ail.md        SleepNotification: a running sleep, its lines and Woke up
woke-up-notification/woke-up-notification.ail.md    WokeUpNotification: "Woke up at …" and the Earlier offsets
woken-by-log/woken-by-log.ail.md                    WokenByLog: "The feed ended the sleep"
forecast-notifications/forecast-notifications.ail.md  ForecastNotifications: "Next feed in …", and which are on
```

```ail
node Notifications : flow   level: experience
  why   The parent should see a running sleep, end it and fix its end from the lock
        screen, and know what comes next, without opening the app.   <- Pooparazzi
  what
    Scenario: the first sleep asks for notifications
      Given a new install, where notifications have never been allowed
      When the parent taps Start sleep
      Then the system asks whether to allow the app's notifications
      And when the parent allows them, the shade shows "Asleep" over how long in clock minutes ("<1m" within the start's minute) "· since" the sleep's start, with Woke up
    Scenario: notifications not allowed show nothing
      Given a new install, and a sleep started on Home
      When the parent answers the system's question with Don't allow
      Then the shade shows no notification
      And the next sleep started does not ask again
    Scenario: notifications allowed later in the system's settings
      Given a sleep running after notifications were not allowed
      When the parent allows them in the system's settings and comes back to the app
      Then the shade shows "Asleep" for the running sleep
    Scenario: a tap on the notification opens Home
      Given a running sleep, and its notification in the shade
      When the parent taps the notification
      Then the app opens on Home, reading "Asleep"
  how
    decide the permission is asked in context, once, the first time a sleep starts; a denial is respected, and the timers run without notifications
      because the running sleep is what the notification is for, and a parent asked out of context says no (DESIGN_SPEC §3.3.1, §10.5)
    decide notifications allowed later (in the system's settings, or the dialog) post the running sleep's at once, as the app comes back
      because the parent should not have to start the sleep again (app/…/notify/NotificationPermission.kt)
    decide every notification is silent (low importance, no sound, vibration or badge), shows on the lock screen, and its buttons work there without unlocking
      because it is read at 3am beside a sleeping baby (DESIGN_SPEC §10.5)
    decide a tap on a notification opens Home
      because Home shows the same status and the way to fix it (DESIGN_SPEC §10.5)
    decide every baby who is asleep has a notification of their own, titled with the baby's name ("Noa is asleep"), each with its own Woke up
      because a parent must not lose sight of a sleeping twin when switching babies (BABIES_AND_BACKUP §2.3)
  copy
    sleep.title         "Asleep"
    sleep.text          "{duration} · since {time}"
    action.woke         "Woke up"
  children  SleepNotification, WokeUpNotification, WokenByLog, ForecastNotifications
  sources
    docs/design/DESIGN_SPEC.md §3.3.7 (P6), §10.5 (the phone's Live Update and prediction notifications)
    docs/design/BABIES_AND_BACKUP.md §2.3 (one notification per sleeping baby)
    driven on Robolectric, 2026-11-27: Start sleep on a new install (the system's question), Allow ("Asleep" / "<1m · since
      6:27 PM", Woke up), Don't allow (nothing; the next Start sleep did not ask), allowed later in the system's settings
      and the app resumed ("Asleep"), a tap on it (Home); a baby named Noa in Settings ("Noa is asleep")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/notify/ (NotificationPermission.kt, TimerNotifications.kt),
      app/…/system/SystemSurfaces.kt
```
