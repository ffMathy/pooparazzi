# The typical day

Under the averages, the pattern of the range's days: when the baby usually
sleeps, feeds and gets a diaper, and the usual time between them, by day and
by night.

```ail
node TypicalDay : flow   level: experience
  why   A parent planning a day, or asked "when does she usually nap?", needs the
        usual rhythm rather than one day's.   <- StatisticsScreen
  what
    Scenario: a typical day needs three logged days
      Given a pee diaper at 9:00 AM yesterday and two days ago
      When the parent opens Statistics
      Then the Typical day card reads "A typical day shows once 3 days in this range are logged."
    Scenario: the usual time between feeds and the usual sleep
      Given on each of the last four days a feed at 8:00 AM and at 11:00 AM, and a sleep from 12:00 PM to 1:30 PM
      When the parent opens Statistics
      Then "Typical day" reads "Last 30 days", "By day" and "By night"
      And "Between feeds" reads "3h 00m" over "3h 00m–3h 00m" by day and "–" by night
      And "Sleep" reads "1h 30m" over "1h 30m–1h 30m" by day and "–" by night, and "Between diapers" and "Awake" read "–"
      And "Between feeds" is spoken "Between feeds. By day: about 3 hours, usually 3 hours to 3 hours. By night: not logged."
  how
    decide the typical day uses the averages' days (today so far while no whole day is logged), only days with something logged, and shows nothing before 3 of them
      because a pattern from one or two days says nothing (V1_ADDITIONS §8.6)
    decide the plot splits each lane into 48 half-hour cells whose strength is the share of days (or of the half hour asleep) with one in it
      because of V1_ADDITIONS §8.6
    decide each interval is the median and the interquartile range, to the nearest 5 minutes, "–" under 3 stretches; a stretch is by night when it starts 19:00–07:00; feeds or diapers under 10 minutes apart are one, gaps over 12 hours are left out
      because these are the forecast's own rules (V1_ADDITIONS §8.6, §9)
    decide unusual days leave their lane, and stretches far from the middle one (3 scaled median absolute deviations, at least a quarter and 15 minutes off) are left out
      because the pattern should rest on values that look alike: a forgotten wake-up or a one-off long nap must not widen it (V1_ADDITIONS §8.6)
  look
    A card, radius 20dp, fill color.surface.container: "Typical day" in type.titleMedium color.text and the range's name in
      type.labelSmall color.text.dim; the plot 100dp tall on History's ribbon geometry, three lanes of half-hour cells in
      sleep.strong, feed.strong and diaper.strong; then "By day" and "By night" over two columns, and four rows (a 10dp dot,
      the name in type.dataSmall), each column's median in type.dataMedium color.text over the range in type.labelSmall
      color.text.dim
  copy
    typical.title       "Typical day"
    typical.empty       "A typical day shows once 3 days in this range are logged."
    typical.columns     "By day" "By night"
    typical.rows        "Between feeds" "Between diapers" "Sleep" "Awake"
    typical.range       "{from}–{to}"       ; "3h 00m–3h 00m"
    typical.spoken      "{row}. By day: about {median}, usually {from} to {to}. By night: not logged."
    typical.spoken.plot "When sleep, feeds and diapers usually happen through the day"
  sources
    docs/design/V1_ADDITIONS.md §8.6 (Typical day), §9 (the forecast's stretches); commits 8460ceb, 22761bd
    driven on Robolectric, 2026-11-27: four past days of feeds at 8:00 and 11:00 AM and sleeps 12:00–1:30 PM ("3h 00m",
      "1h 30m", "–" by night)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/statistics/TypicalDay.kt
```
