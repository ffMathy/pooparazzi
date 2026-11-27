# The cover-screen widget

"Cover screen": the 4×2 widget on the small outer screen of Samsung's flip
phones, at that screen's one size.

```ail
node CoverWidget : flow   level: experience
  why   A parent with a flip phone shut can log from its cover screen without
        opening the phone.   <- Widgets
  what
    Scenario: the cover widget shows the three buttons
      Given a cover-screen widget at 352 × 339
      Then it shows Feed, Diaper and Start sleep, spoken "Log feed", "Log diaper" and "Start sleep"
    Scenario: Start sleep on the cover widget logs at once
      Given a cover-screen widget at 352 × 339, with the baby awake
      When the parent taps its Start sleep
      Then it reads "Sleep started at" the time now, with Undo, and its sleep button "Woke up"
  how
    decide the same content as the 4×2, at one fixed size (352 × 339dp), from a third provider that only Samsung's cover screen lists
      because launchers list only home-screen widgets, and with one layout per size the widget stayed on its loading layout on a Flip's cover screen (DESIGN_SPEC §3.3.6)
    decide from 280dp of height the three buttons are stacked, each a full-width row: the icon at the start, the label in 16sp over its lines in 13sp
      because the cover screen is square, and rows read like the watch's (DESIGN_SPEC §10.11; commit 80ac83c)
    decide Feed and Diaper open the app's sheet on the cover screen, and when it closes the app finishes so the cover shows the widget again; on the main screen Home stays
      because Home has no room on the cover screen (DESIGN_SPEC §3.3.6)
    decide refreshes go to every placed widget by its provider's ids
      because the cover screen never asks for an update, and would never be drawn otherwise (DESIGN_SPEC §3.3.6)
  look
    352 × 339, padding 12dp: the 48dp status row, then Feed, Diaper and the sleep button stacked 8dp apart, each a full-width
      radius 20dp row in its container colour (feed.container, diaper.container, sleep.container) with a 24dp icon 20dp from
      its start, 14dp, then the label in 16sp over its lines in 13sp
  copy
    picker.cover        "Cover screen"
    receipt.started     "Sleep started at {time}"
  sources
    docs/design/DESIGN_SPEC.md §3.3.6 (Cover screen), §10.11 (the tall breakpoint)
    commit 80ac83c (stacked rows on a tall widget)
    driven on Robolectric, 2026-11-27: "Cover screen" placed at 352 × 339 (Feed, Diaper, Start sleep); the finish after a sheet
      was not driven, as no second display runs here
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/widget/LogWidget.kt (CoverWidget), WidgetActions.kt, MainActivity.kt,
      res/xml/widget_cover_info.xml, widget_cover_samsung.xml
```
