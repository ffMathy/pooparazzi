# Settings › Notifications

A link to the system's notification settings, and one row per prediction
notification, each saying whether it is on.

```ail
node NotificationSettings : flow   level: experience
  why   A running sleep and what comes next can be seen outside the app, but only
        as the parent allows in Android's own settings; Settings says what is on
        and takes them there.   <- Settings
  what
    Scenario: notifications not allowed yet
      Given a new install that has not been allowed to notify, its first run skipped
      When the parent opens Settings
      Then Notifications reads "Notifications are off" over "Turn them on to see a running sleep outside the app"
      And under "What comes next" the rows "Next feed", "Next poop" and "Next sleep and wake-up" each read "Off"
      And under them "What Home expects next, as a silent notification while the app is closed. Tap one to turn it on or off in Android's notification settings."
  how
    decide the first row is one of three: "Notifications are off" (the app may not notify: opens its notification settings), "Live Updates" with "Show a running sleep in the status bar and on the lock screen" (Android 16 QPR2 and later: opens the system's Live Update setting), or "Notification settings" with "A running sleep shows a notification with Woke up" (older Android)
      because a running sleep belongs in the status bar and on the lock screen, as far as the system allows (DESIGN_SPEC §10.5)
    decide each prediction row says "On" or "Off" as its channel is set in the system; a tap opens that channel's page (the app's page while notifications are off); all are read again whenever Settings comes back to the front
      because the parent turns them on or off in Android's settings, and Settings must show what is true (DESIGN_SPEC §3.3.5)
    decide while the app may not notify, every prediction reads "Off"
      because nothing can show then, whatever its channel says (app/…/notify/ForecastNotifications.kt)
  look
    The first row as a link row in color.row with radius.row corners: its title in type.titleMedium color.text and its
      summary in type.bodySmall color.text.dim, a chevron at the end. "What comes next" in type.titleSmall color.text,
      then three link rows of size.row, each with its label and "On" or "Off" in type.bodyMedium color.text.dim, the hint
      under them in type.bodySmall color.text.dim
  copy
    off          "Notifications are off"                              ; da "Notifikationer er slået fra"
    off.body     "Turn them on to see a running sleep outside the app"
    live         "Live Updates"
    live.body    "Show a running sleep in the status bar and on the lock screen"
    plain        "Notification settings"
    plain.body   "A running sleep shows a notification with Woke up"
    next         "What comes next"                                    ; da "Det næste"
    next.feed    "Next feed"
    next.poop    "Next poop"
    next.sleep   "Next sleep and wake-up"
    on           "On"
    offState     "Off"                                                ; da "Fra"
    hint         "What Home expects next, as a silent notification while the app is closed. Tap one to turn it on or off in Android's notification settings."
  sources
    docs/design/DESIGN_SPEC.md §3.3.5 (the notifications link, What comes next), §10.5
    driven on Robolectric (notifications not allowed on a new install), 2026-11-27: "Notifications are off", the three
      rows reading "Off", the hint
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/notify/NotificationSettings.kt, which row shows
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt, the rows
```
