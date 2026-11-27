# The home-screen widgets

Three widgets put Feed, Diaper and the sleep button on the phone's home screen.
The sleep button logs in place, without opening the app; Feed and Diaper open
the app on the same sheets as Home's buttons. The 4×2 also shows what was just
logged, with Undo.

Written from the running app (Robolectric, a fresh install, the widgets placed
as a launcher places them and drawn from what the app sends, 2026-11-27), the
design documents and the phone app's code. Times are the phone's own clock.

```
widgets.ail.md                          Widgets: the picker, Feed and Diaper open the app's sheets
log-widget/log-widget.ail.md            LogWidget: the 4×2, its buttons and their lines
log-widget/widget-receipt/widget-receipt.ail.md  WidgetReceipt: the 4×2's receipt row, Undo, the guard, 10 s
quick-widget/quick-widget.ail.md        QuickWidget: the 3×1 at its sizes
cover-widget/cover-widget.ail.md        CoverWidget: the 4×2 on a Samsung flip's cover screen
```

```ail
node Widgets : flow   level: experience
  why   With the phone locked in one hand at 3am, opening the app is one step too
        many: the sleep button and the way into Feed and Diaper belong on the home
        screen, where the parent already is.   <- Pooparazzi
  what
    The widget picker lists "Log and status" (the 4×2), "Feed, diaper and sleep" (the 3×1) and, only on a Samsung flip's cover screen, "Cover screen".
    Every widget offers Feed, Diaper and the sleep button, for the phone's current baby.
    Scenario: Feed on a widget opens the Feed sheet
      Given a 4×2 widget on the home screen
      When the parent taps its Feed button, spoken "Log feed"
      Then the app opens on Home with the Feed sheet, titled "Feed ·" the time now, with Breast, Bottle, Food and Just the time
    Scenario: Diaper on a widget opens the Diaper sheet
      Given a 4×2 widget on the home screen
      When the parent taps its Diaper button, spoken "Log diaper"
      Then the app opens on Home with the Diaper sheet, titled "Diaper ·" the time now, with Pee, Poop and Save
    Scenario: what the sheets log shows on the widget
      Given a 4×2 widget on the home screen
      When the parent logs Food from its Feed button and a poop from its Diaper button
      Then the widget's Feed reads "just now", spoken "Log feed, Last feed just now"
      And its Diaper reads "just now", spoken "Log diaper, Last diaper just now"
    Scenario: the widget's background opens Home
      Given a 4×2 widget on the home screen, with nothing just logged on it
      When the parent taps the widget outside its buttons
      Then the app opens on Home, with no sheet
  how
    decide the sleep button logs in place; Feed and Diaper open the app's own Feed and Diaper sheets instead of logging
      because the details (feed type, breast order, pee or poop, colour, texture) are asked for exactly as in the app (DESIGN_SPEC §3.3.6; commit 80ac83c)
    decide the widgets use the Night or Day colours the app's own Appearance resolves to, never the launcher's, and the device font
      because a phone kept in light mode would otherwise show a bright widget at 3am (DESIGN_SPEC §8.5, §10.11)
    decide the widgets show relative times ("2h 10m", "just now") and are redrawn at the start of every minute, on every log and sleep change, and when a receipt expires
      because they match the watch, and a widget cannot tick by itself (DESIGN_SPEC §3.3.6, §10.11; commit bc37694)
    decide each widget keeps its own baby: a new one follows the phone's current baby; with two or more babies the 4×2's name is a switcher and the 3×1 gets a baby button
      because twins can each have a widget (BABIES_AND_BACKUP §2.4; commit 80ac83c)
    decide a widget's write reaches the watch's Data Layer before its tap returns
      because the phone may freeze the app right after a widget tap (commit 97abb7c)
  look
    Each widget is a rounded panel in the app's surface colour (color.background at night) with the system's widget corner;
      its buttons are radius 20dp, 8dp apart, Feed in feed.container with the feed icon in feed.strong, Diaper in
      diaper.container with the diaper icon in diaper.strong, the sleep button in sleep.container with the moon in
      sleep.strong; asleep, the sleep button is sleep.lit with a 2dp sleep.strong border and the wake icon
  copy
    picker.log          "Log and status"
    picker.quick        "Feed, diaper and sleep"
    picker.cover        "Cover screen"
    spoken.feed         "Log feed"
    spoken.diaper       "Log diaper"
    spoken.lastFeed     "Log feed, Last feed {since}"
    spoken.lastDiaper   "Log diaper, Last diaper {since}"
  children  LogWidget, QuickWidget, CoverWidget
  sources
    docs/design/DESIGN_SPEC.md §3.3.6 (P5 widgets), §8.5 (Glance at night), §10.11 (implementation, breakpoints)
    docs/design/BABIES_AND_BACKUP.md §2.4 (widgets per baby); V1_ADDITIONS.md §2.1 (the widgets' Feed opens the Feed sheet)
    commits 80ac83c, 83681b0, bc37694, 97abb7c
    driven on Robolectric, 2026-11-27: the picker's three names, Feed and Diaper on a 358 × 208 widget (the sheets
      "Feed · 6:30 PM" and "Diaper · 6:30 PM"), Food and a poop logged from them ("just now" on both)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/widget/ (the widgets, their buttons and updates), app/…/launch/HomeLaunch.kt
```
