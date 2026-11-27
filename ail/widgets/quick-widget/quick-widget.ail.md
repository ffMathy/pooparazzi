# The 3×1 widget

"Feed, diaper and sleep": the 4×2's three buttons in one row, without the
status row or the times.

```ail
node QuickWidget : flow   level: experience
  why   A one-row widget fits a crowded home screen; it keeps the three taps a
        parent needs and drops everything else.   <- Widgets
  what
    Scenario: the quick widget shows three labelled buttons
      Given a 3×1 widget placed at 260 × 100
      Then it shows Feed, Diaper and Start sleep, spoken "Log feed", "Log diaper" and "Start sleep", with no times
    Scenario: the sleep button logs in place
      Given a 3×1 widget at 260 × 100, with the baby awake
      When the parent taps its Start sleep
      Then its sleep button reads "Woke up", spoken "Woke up, end sleep", and the app's Home reads "Asleep"
      And the widget shows no receipt
    Scenario: a narrow quick widget shows icons only
      Given a 3×1 widget placed at 180 × 100
      Then its three buttons show their icons only, spoken "Log feed", "Log diaper" and "Start sleep"
    Scenario: Feed on the quick widget opens the Feed sheet
      Given a 3×1 widget at 260 × 100
      When the parent taps its Feed
      Then the app opens on Home with the Feed sheet, titled "Feed ·" the time now
  how
    decide three equal buttons with an icon over a label of up to two lines, never broken inside a word; where a word of "Start sleep" does not fit, the label is "Sleep"; where any label still does not fit, every button is its icon alone, spoken by its name
      because every widget offers all three, and labels either all show or none (DESIGN_SPEC §3.3.6, §10.11)
    decide narrower than 180dp (an old 2×1) the padding is 8dp and the gaps 4dp
      because the three 24dp icons must still fit (DESIGN_SPEC §10.11)
    decide at 180 × 100 the labels do not fit and the buttons are icons only, while 220 and 260 wide show the labels
      because "unknown, confirm with the owner"
    decide no receipt, notice or times: its taps still go through the guard and the mis-tap rule, and their receipt shows on any 4×2 of the same baby
      because the 3×1 has no room for a status row (DESIGN_SPEC §3.3.6)
    decide it keeps the receiver it had as a 2×1 (Feed and Sleep)
      because placed widgets must survive the update that added Diaper (DESIGN_SPEC §3.3.6)
  look
    At 260 × 100: padding 12dp, three buttons 8dp apart, radius 20dp, each a 24dp icon over its label in 14sp in the button's
      on-container colour, Feed in feed.container, Diaper in diaper.container, the sleep button in sleep.container (asleep:
      sleep.lit with a 2dp sleep.strong border and the wake icon)
  copy
    label.feed          "Feed"
    label.diaper        "Diaper"
    label.start         "Start sleep"
    label.short         "Sleep"
    label.woke          "Woke up"
    spoken.end          "Woke up, end sleep"
  sources
    docs/design/DESIGN_SPEC.md §3.3.6 (3×1), §10.11 (breakpoints)
    driven on Robolectric, 2026-11-27: 260 × 100 and 220 × 100 (labels), 180, 160 and 110 × 100 (icons only), asleep ("Woke up,
      end sleep")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/widget/QuickWidget.kt, res/xml/widget_quick_info.xml
```
