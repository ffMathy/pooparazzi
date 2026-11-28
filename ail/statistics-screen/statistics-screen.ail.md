# Statistics

Many days at once: the average per day of feeds, pee, poop and sleep over one
range (the last 30 days unless another is chosen), each with a bar chart of the
range, and the typical day under them.

Written from the running app (Robolectric, a fresh install, 2026-11-27), the
design documents and the phone app's code. The days of history its scenarios
need are made with History's Add entry, as a parent would.

```
statistics-screen.ail.md                    StatisticsScreen: empty state, the range, the four averages, the footnote
statistics-chart/statistics-chart.ail.md    StatisticsChart: the open row's bar chart, a pressed bar, unusual days
typical-day/typical-day.ail.md              TypicalDay: when things usually happen, and the usual time between them
```

```ail
node StatisticsScreen : flow   level: experience
  why   Parents, health visitors and doctors ask "how many feeds a day, how much
        sleep?" about weeks, not about today; the answer must not count the
        days nothing was logged.   <- Pooparazzi
  what
    Scenario: Statistics before anything is logged
      Given a new install
      When the parent taps Statistics on Home
      Then "Statistics" opens with Back and a History button, and reads "Statistics start as soon as something is logged."
    Scenario: averages per day over the last 30 days
      Given on each of the last four days a feed at 8:00 AM, a Bottle at 11:00 AM, a pee diaper at 9:00 AM (with Poop too four and two days ago) and a sleep from 12:00 PM to 1:30 PM
      When the parent opens Statistics
      Then the range reads "Last 30 days", spoken with its first and last day and "3 of 30 days logged"
      And Feeds reads "2.0", Pee "1.0", Poop "0.3" and Sleep "1h 30m", spoken "Feeds. Last 30 days: 2.0 a day." and so on
      And the footnote begins "Averages count whole days up to yesterday."
    Scenario: today so far until a whole day is logged
      Given a feed logged now with Just the time, and nothing before it
      When the parent opens Statistics
      Then Feeds reads "1.0" and the footnote reads "No whole day has been logged yet, so these averages count today so far. From the first whole day on, they count whole days up to yesterday."
    Scenario: changing the range
      Given the same four days
      When the parent taps the range, then Last 7 days
      Then the menu offered Last 7 days, Last 30 days, Last 90 days, All days and "Choose dates…"
      And the range reads "Last 7 days", spoken with its first and last day and "3 of 7 days logged"
      And All days is spoken with the first counted day and yesterday, and no days-logged count
    Scenario: a row opens its chart
      Given the same four days
      When the parent opens Statistics, then taps Feeds
      Then Poop's chart shows first, and after the tap Feeds' chart shows and Poop's is closed
  how
    decide only whole days count: a range ends yesterday, and the first logged day is left out as probably partial; until there is a whole day, the averages count today so far, and say so
      because the numbers then change only at midnight or when an entry changes, never as today goes on (V1_ADDITIONS §8.1)
    decide a day counts for a kind only if an entry of that kind starts on it (any diaper for Pee and Poop, a plain change too); a counted day with diapers and no poop is a real zero; a day with nothing logged is a gap
      because "forgot to log" is never 0 (V1_ADDITIONS §8.2)
    decide averages are over the counted days that are not unusual: counts to tenths ("2.0", "0.3"), sleep to whole minutes ("1h 30m"), each rounded half up; a kind with no counted day reads "–", spoken "not logged"
      because integers round the same on every device (V1_ADDITIONS §8.2)
    decide the ranges are Last 7 days, Last 30 days (the default), Last 90 days, All days (the first counted day to yesterday), and chosen dates; a preset is always its full length, and "3 of 30 days logged" counts the days with an entry
      because presets are nominal and relative (V1_ADDITIONS §8.1)
    decide the rows are a single-select accordion; Poop is open on every new visit
      because "unknown, confirm with the owner": V1_ADDITIONS §8 states it, without a reason
    decide Statistics opens from Home and from History; its History button returns to History when it is right below, otherwise opens it; Back returns where it came from
      because switching back and forth never piles up screens (V1_ADDITIONS §8, §8.3; commit cc08b47)
  look
    Top bar: Back, "Statistics" in type.titleLarge (a heading), the "History" pill at the end. The range chip, inset 16dp, with
      its dates under it in type.dataSmall color.text.dim. Four grouped cards (2dp apart, radius 20dp outside and 6dp inside,
      fill color.surface.container): the kind's 20dp glyph in its strong colour, the name in type.labelLarge, a chevron while
      closed, the average in type.displaySmall. The footnote in type.bodySmall color.text.dim; the Typical day card 24dp under it
  copy
    statistics.title    "Statistics"
    statistics.history  "History"
    statistics.empty    "Statistics start as soon as something is logged."
    statistics.ranges   "Last 7 days" "Last 30 days" "Last 90 days" "All days" "Chosen dates" "Choose dates…"
    statistics.rows     "Feeds" "Pee" "Poop" "Sleep"
    statistics.spoken.range  "{range}, {first} to {last}, {n} of {N} days logged"   ; "Last 30 days, 28 October to 26 November, 3 of 30 days logged"
    statistics.spoken.row    "{kind}. {range}: {average} a day."   ; "Poop. Last 30 days: 0.3 a day.", "Feeds. Last 30 days: not logged."
    statistics.footnote "Averages count whole days up to yesterday. A day counts for feeds, diapers or sleep only if any were logged that day. The first day of logging is left out, and so is a day unlike the others (half logged, or logged twice)."
    statistics.soFar    "No whole day has been logged yet, so these averages count today so far. From the first whole day on, they count whole days up to yesterday."
  children  StatisticsChart, TypicalDay
  sources
    docs/design/V1_ADDITIONS.md §8 (Statistics: placement, ranges, which days count, layout, TalkBack)
    commits cc08b47, 85cd56c, d508ffa
    driven on Robolectric, 2026-11-27: four past days made with Add entry (feeds, a bottle, pee and poop, sleeps), Statistics
      from Home and History, the range menu (Last 7 days, All days), the rows
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/statistics/StatisticsScreen.kt, StatisticsStateBuilder.kt,
      StatisticsText.kt, StatisticsViewModel.kt
```
