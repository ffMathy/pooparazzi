# The Statistics chart

The open row's bar chart: one bar per day of the range, the range's average
as a dashed line, a pressed bar's day and value, and grey bars for days unlike
the others.

```ail
node StatisticsChart : flow   level: experience
  why   An average hides a trend and an odd day; the bars show both, and a day the
        parent half forgot must not drag the average down.   <- StatisticsScreen
  what
    Scenario: the open row's chart
      Given poop diapers at 9:00 AM on each of the last four days, and one more at 1:00 PM yesterday
      When the parent opens Statistics
      Then Poop's chart reads its scale "2", the range's first and last dates, and the range's name
      And it is spoken "Poop per day," the range's dates, "Highest 2 on" yesterday, "Lowest 1 on" two days ago, and "Days before" the first counted day "are left out."
    Scenario: pressing a bar shows its day and value
      Given the same poops
      When the parent presses yesterday's bar, then presses it again
      Then the dates make way for yesterday's short date and "· 2", and the second press takes it away
    Scenario: an unusual day is grey and left out of the average
      Given sleeps from 12:00 PM to 1:30 PM on each of the last five days, and another from 3:00 PM to 7:00 PM yesterday
      When the parent opens Sleep
      Then Sleep reads "1h 30m"
      And the chart shows "Unusual day" and "Grey bars are days unlike the others, such as a half-logged day. They are left out of the average."
      And it is spoken with "1 day unlike the others is left out of the average."
      And pressing yesterday's bar reads yesterday's short date, "· 5h 30m · Unusual"
  how
    decide one bar per day up to 62 days; longer ranges use 7-day bars cut from the end; a bar is the average per counted day, a gap draws nothing, a real zero draws a 2dp mark
      because there are never more than 62 bars, and a zero must be told from a gap (V1_ADDITIONS §8.4)
    decide the scale is counts up to the next whole number (at least 1), sleep up to the next whole hour
      because of V1_ADDITIONS §8.4
    decide a counted day more than 3 scaled median absolute deviations from the middle day is unusual for that kind, with a minimum tolerance (counts at least 40 % and 1 off, sleep a quarter of the usual day); under 3 counted days none is; it is grey, left out of the average and named in the readout and the spoken summary
      because a half-logged or double-logged day, a forgotten night or a sleep left running must not move the average (V1_ADDITIONS §8.2; commit d508ffa)
    decide pressing, dragging or hovering picks the bar under the pointer: the others fade to 35 %, its readout ("Thu 26 Nov · 2", "· Unusual" for a grey bar) replaces the dates; it stays until the same bar is pressed again, the column scrolls or a mouse leaves
      because it is a visual aid only; TalkBack keeps the chart's one summary, which already names the highest and lowest bars (V1_ADDITIONS §8.4; commit dd02fc3)
    decide the spoken summary names the highest and the lowest bar, the latest one on a tie, and says which days are left out
      because "unknown, confirm with the owner" for the tie-break: seen driving the app ("Highest 2 on Thursday 26 November. Lowest 2 on Thursday 26 November.")
  look
    The block: the scale label in type.labelSmall over a 1dp color.outline.variant hairline; the plot full width × 112dp; bars
      60 % of their slot (2–12dp wide, at least 3dp tall), radius 3dp, in the kind's strong colour; an unusual bar in
      color.outline; the average a dashed 2dp color.text.dim line on a 4dp halo of color.surface.container; the first and
      last dates (or the readout, centred under the bar, type.labelSmall color.text); the legend and the notes
  copy
    chart.legend.unusual  "Unusual day"
    chart.note.unusual    "Grey bars are days unlike the others, such as a half-logged day. They are left out of the average."
    chart.note.gaps       "Days with no feeds logged are left out." "Days with no diapers logged are left out." "Days with no sleep logged are left out."
    chart.empty           "Nothing logged on these days."
    chart.readout         "{short date} · {value}"  "{short date} · {value} · Unusual"   ; "Thu 26 Nov · 2"
    chart.spoken          "{kind} per day, {first} to {last}. Highest {value} on {day}. Lowest {value} on {day}."
    chart.spoken.unusual  "1 day unlike the others is left out of the average."
    chart.spoken.before   "Days before {date} are left out."
  sources
    docs/design/V1_ADDITIONS.md §8.2 (unusual days), §8.4 (chart, picking a bar), §8.5 (TalkBack)
    commits dd02fc3 (press or hover a bar), d508ffa (grey unusual days), 596819c
    driven on Robolectric, 2026-11-27: poops on four days (scale "2", "Thu 26 Nov · 2" on a press, gone on the second), sleeps
      with one long day (grey, left out)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/statistics/StatisticsChart.kt, StatisticsText.kt
```
