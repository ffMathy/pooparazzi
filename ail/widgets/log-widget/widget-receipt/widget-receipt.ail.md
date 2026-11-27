# The 4×2 widget's receipt

A tap on the widget's sleep button logs at once; the status row then says what
was logged and when, with Undo, until the next widget log or for ten seconds.

```ail
node WidgetReceipt : flow   level: experience
  why   A tap on a widget logs without opening the app, so the widget itself must
        say what it logged and take a wrong tap back.   <- LogWidget
  what
    Scenario: Start sleep on the widget logs at once
      Given a 4×2 widget, with the baby awake
      When the parent taps its Start sleep
      Then its status row reads "Sleep started at" the time now, with Undo, spoken "Undo sleep start"
      And its sleep button reads "Woke up", and the app's Home reads "Asleep" since that time, without the app having opened a sheet
    Scenario: Undo on the widget takes the sleep back
      Given a sleep just started on the widget
      When the parent taps Undo on the widget
      Then the status row reads "Undone" and the sleep button "Start sleep"
      And two seconds later the status row is empty again
    Scenario: a second tap within two seconds changes nothing
      Given a sleep just started on the widget
      When the parent taps its Woke up within two seconds
      Then the status row reads "Already asleep since" the sleep's start, and the sleep keeps running
      And two seconds later the status row reads "Sleep started at" that time again
    Scenario: the receipt goes after ten seconds
      Given a sleep just started on the widget
      When ten seconds pass
      Then the status row is empty, and the sleep button still reads "Woke up"
    Scenario: Woke up on the widget
      Given a 4×2 widget, and a sleep started 15 minutes ago on Home
      When the parent taps the widget's Woke up
      Then its status row reads "Woke up at" the time now, with Undo, spoken "Undo woke up"
    Scenario: the background opens the receipt in the app
      Given a sleep just ended with the widget's Woke up, 15 minutes after it started
      When the parent taps the widget outside its buttons
      Then the app opens on Home with the receipt titled "Woke up ·" the time now, reading "Slept" the sleep's length, with Undo, the nudges, Edit and Done
    Scenario: a sleep ended within a minute is cancelled on the widget
      Given a sleep started on the widget a few seconds ago, its guard over
      When the parent taps its Woke up
      Then the status row reads "Sleep cancelled" with Undo, spoken "Undo sleep cancelled", and the sleep button "Start sleep"
      And Undo makes the sleep run again: the sleep button reads "Woke up"
    Scenario: a feed logged from the widget's sheet leaves the receipt
      Given a sleep started 15 minutes ago, just ended with the widget's Woke up
      When the parent logs Food from the widget's Feed button
      Then the widget's Feed reads "just now", and the status row still reads "Woke up at" the wake-up's time, with Undo
  how
    decide the receipt keeps the log's clock time ("Sleep started at 7:21 PM"), stays until the next widget log or for 10 seconds, and Undo never sits where a log button is
      because a widget cannot hold a sheet; the row is the receipt, and Undo must not catch a tap meant for a button (DESIGN_SPEC §3.3.6, §10.11)
    decide Undo deletes a started sleep, or makes an ended or cancelled one run again, then "Undone" shows for two seconds and the guard is reset
      because every log can be taken back at once, as in the app (DESIGN_SPEC §1, §10.7)
    decide a second tap on the sleep button within two seconds changes nothing and the row says what is true ("Already asleep since …", "Already awake since …") for two seconds; the guard is each baby's own
      because a double tap must never toggle a sleep twice, and twins' widgets can be tapped one after the other (DESIGN_SPEC §4.4, §10.7; BABIES_AND_BACKUP §2.4)
    decide a sleep ended less than 60 seconds after it started is discarded, as in the app: "Sleep cancelled", with Undo
      because such sleeps are mis-taps (DESIGN_SPEC §4.4)
    decide the background opens the app on the receipt of the last widget log, where it can be nudged, edited or undone; with none, on Home
      because the widget offers no nudges (DESIGN_SPEC §3.3.6)
    decide a feed or diaper logged on the app's sheet, though opened from the widget, is not a widget log: the widget's receipt stays
      because "unknown, confirm with the owner"
  look
    The status row: a 16dp check icon in color.text.dim, 6dp, the receipt in 15sp color.text, and at the end Undo: the undo
      icon and "Undo" in 15sp color.primary, a 48dp target; the notice: the moon (or undo) icon and its line in sleep.text
  copy
    receipt.started     "Sleep started at {time}"
    receipt.woke        "Woke up at {time}"
    receipt.cancelled   "Sleep cancelled"
    receipt.undo        "Undo"
    spoken.undo         "Undo sleep start" "Undo woke up" "Undo sleep cancelled" "Undo pee" "Undo poop" "Undo pee and poop"
    notice.undone       "Undone"
    guard.asleep        "Already asleep since {time}"
    guard.awake         "Already awake since {time}"
  sources
    docs/design/DESIGN_SPEC.md §3.3.6 (Just logged), §4.4 (guard, mis-tap), §10.7, §10.11 (Receipt and Undo, background tap)
    driven on Robolectric, 2026-11-27: Start sleep on the widget ("Sleep started at 6:56 PM", Undo), a second tap 0.6 s later
      ("Already asleep since 6:56 PM", then the receipt again), 11 s later (no row), Undo ("Undone", gone 2.5 s later), Woke
      up after a sleep nudged 15 minutes back ("Woke up at 7:21 PM"; the background opened "Woke up · 7:21 PM"), a Woke up
      2 s after a start ("Sleep cancelled"), Food from the widget's Feed (the receipt stayed)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/widget/WidgetCommands.kt, WidgetActions.kt, WidgetMemo.kt, LogWidget.kt
```
