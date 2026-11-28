# The 4×2 widget

"Log and status": a status row over three equal buttons, Feed, Diaper and the
sleep button, each with how long since the last one and what the week expects
next.

```ail
node LogWidget : flow   level: experience
  why   A glance at the home screen should say how long since the last feed and
        diaper, and how long the baby has slept, without opening the app.   <- Widgets
  what
    Scenario: a new widget has nothing to show yet
      Given a new install, and a 4×2 widget placed at 358 × 208
      Then it shows Feed, Diaper and Start sleep, with no line under any of them
      And its status row is empty, as the only baby has no name
    Scenario: the sleep button says how long the baby has slept
      Given a 4×2 widget, and a sleep started on Home and nudged 15 minutes back
      Then the widget's sleep button reads "Woke up" over how long the baby has slept in clock minutes ("16m" from 7:05 PM to 7:21 PM)
      And it is spoken "Woke up, end sleep, Asleep" and that duration
    Scenario: after waking the sleep button says how long the baby is awake
      Given a 4×2 widget, and a sleep that has just ended on the widget
      Then the sleep button reads "Start sleep" over "<1m", spoken "Start sleep, Awake <1m"
    Scenario: Feed says when the next feed is expected
      Given feeds at 8:00 AM and 11:00 AM four days ago, and at 8:00 AM, 11:00 AM and 2:00 PM three and two days ago
      And a feed logged now with Just the time, and a 4×2 widget
      Then the widget's Feed reads "just now" over "3h 00m"
      And it is spoken "Log feed, Last feed just now, next feed expected around" the time 3 hours from now, rounded to 5 minutes
    Scenario: a small log widget keeps only the icons
      Given a 4×2 widget resized to 120 × 100
      Then its buttons show their icons only, spoken "Log feed", "Log diaper" and "Start sleep"
    Scenario: a log widget that is not tall enough has no status row
      Given a 4×2 widget resized to 240 × 130
      When the parent taps its Start sleep
      Then the buttons read Feed, Diaper and Woke up, and there is no receipt row
  how
    decide the status row is the baby's name (nothing for one unnamed baby), else the receipt with Undo after a widget log, else a two-second notice; the times sit in their buttons
      because the row names the baby, as the watch's title does; the times belong to the buttons they describe (commit 83681b0)
    decide under each label one line after the history icon (the time since the last one; on the sleep button how long asleep or awake) and one after the upcoming icon (what the week expects next), the three buttons keeping the same number of lines
      because short lines with icons fit a third of the widget in Danish too, and level icons and labels read as one row (commits a18ac59, 9f33986, 802d112)
    decide a last breast feed adds its order ("1h 32m · L → R"), on a line of its own when it does not fit
      because the next feed starts from it (V1_ADDITIONS §2.1; commit 802d112)
    decide the estimate line follows Home's rules: rounded up to 5 minutes, "soon", then "now"; spoken with the clock time
      because every surface states the estimates by the same rules (V1_ADDITIONS §9)
    decide from 180dp of height the status row shows; from 240 × 130dp the labels; below that the buttons are icons only; a label that needs two lines turns all three to the compact face
      because those are the breakpoints of DESIGN_SPEC §10.11
    decide a 2×2 or smaller is the same widget without the status row, so its taps log without a receipt
      because "a 2×2 falls back to the 4×2 content without the status row" (DESIGN_SPEC §10.11)
  look
    The widget is pictured alone on the home screen's wallpaper: a panel in color.surface.container with the system's widget
      corner. The 4×2 at 358 × 208: padding 12dp; the 48dp status row (receipt or notice text 15sp color.text after a 16dp check icon in
      color.text.dim, Undo in color.primary at the end); under it the three buttons side by side, 8dp apart, radius 20dp, each
      a 24dp icon over its label in 15sp, then the history and upcoming lines in 12sp after 12dp icons, in the button's
      on-container colour (feed.on.container, diaper.on.container, sleep.on.container); the lines and the icons centred in their
      buttons; a button with fewer lines gets blank ones, so the three keep the same number of lines and their icons and labels
      stay level; asleep, the sleep
      button is sleep.lit with a 2dp sleep.strong border and the wake icon
    Lower than 180dp (240 × 130) the same buttons fill the panel with no status row, a label that needs two lines ("Woke up")
      wrapping; at 120 × 100 the three buttons are narrow, each its 24dp icon alone
  copy
    label.feed          "Feed"
    label.diaper        "Diaper"
    label.start         "Start sleep"
    label.woke          "Woke up"
    line.justNow        "just now"
    line.underMinute    "<1m"
    spoken.asleep       "Woke up, end sleep, Asleep {duration}"
    spoken.awake        "Start sleep, Awake {duration}"
    spoken.forecast     "Log feed, Last feed {since}, next feed expected around {time}"
  children  WidgetReceipt
  sources
    docs/design/DESIGN_SPEC.md §3.3.6 (the 4×2 table), §10.11 (breakpoints); V1_ADDITIONS.md §9 (the widget's estimates)
    commits 83681b0, a18ac59, 9f33986, 802d112, 8fcf6ec
    driven on Robolectric, 2026-11-27: a new widget (three labels, no lines), a sleep nudged 15 minutes back ("16m", "Woke up,
      end sleep, Asleep 16m"), feeds three hours apart over three days then a feed now ("just now", "3h 00m"), 120 × 100 (icons
      only), 240 × 130 and 276 × 190 (labels, no status row)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/widget/LogWidget.kt, WidgetUi.kt, WidgetSnapshot.kt
```
