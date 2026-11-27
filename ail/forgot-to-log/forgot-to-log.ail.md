# Forgot to log?

A feed or sleep logged long after it happened can be put where the past week
says it most likely happened: the Feed sheet and the sleep receipt offer
"Expected" under their nudges, and a second tap goes back.

```ail
node ForgotToLog : flow   level: experience
  why   A parent who forgot to log a feed or a sleep remembers only roughly when it
        was; the baby's own week is a better guess than nudging back blind.   <- Pooparazzi
  what
    Scenario: a forgotten feed is offered its expected time
      Given feeds 30 minutes apart at about this time of day, three on each of the two days before today and two on the day before those, and Food logged an hour ago
      When the parent taps Feed
      Then under the nudges the sheet offers "Expected" with the time 30 minutes after that feed, to the nearest 5 minutes, not picked
      And the Diaper sheet offers no Expected
    Scenario: picking the expected time moves the feed there
      Given the same week, Food logged an hour ago, and the Feed sheet offering Expected
      When the parent taps Expected and then Food
      Then the sheet's time reads the expected time, followed by "was" and the time now, and Expected is picked
      And the Lamp reads "Last fed" at the expected time, with "Food"
    Scenario: a second tap goes back
      Given the same week, Food logged an hour ago, and the Feed sheet offering Expected
      When the parent taps Expected twice
      Then the sheet's time is the time now again, with no "was", and Expected is not picked
    Scenario: an expected time close to now is not offered
      Given the same week, and Food logged 30 minutes ago
      When the parent taps Feed
      Then the sheet offers no Expected
    Scenario: without a week to go on there is no Expected
      Given Food logged an hour ago on a fresh install
      When the parent taps Feed
      Then the sheet offers no Expected
    Scenario: a late Start sleep is offered its expected start
      Given sleeps of 15 minutes with 15 minutes awake between them at about this time of day, three on each of the two days before today and two on the day before those, and a sleep today that ended 25 minutes ago
      When the parent taps Start sleep
      Then the receipt offers "Expected" with the time 15 minutes after that wake-up, to the nearest 5 minutes
      And a tap on it moves the sleep's start there: the time line reads it followed by "was" and the time now, and the Lamp reads "Asleep" since then
  how
    decide the Feed sheet offers the next feed the past week expected after the last feed; the Sleep started receipt and Adjust start the sleep expected after the last wake-up; the Woke up receipt the wake-up expected for that sleep; the Diaper sheet nothing
      because these are the logs that get forgotten and have an estimate (commit c2297ad; DESIGN_SPEC §3.3.2 "Expected 15:25")
    decide the estimate is the week's own: stretches of the same kind (gaps between feeds, time awake between sleeps) from the last 7 days, those that began within 2 hours of this one's time of day, widened to 4 hours and then the whole day until 5 from at least 3 days are found; their median, recent days weighing more, rounded to 5 minutes
      because babies differ widely and no validated model predicts one baby's rhythm (V1_ADDITIONS §9, steps 1 to 4)
    decide gaps under 10 minutes and feed or wake gaps over 12 hours are left out; too few or too spread out stretches give no Expected
      because a feed per side is one feed, a gap that long is a forgotten log, and a guess from noise would mislead (V1_ADDITIONS §9)
    decide Expected is kept even long after the estimate passed, but offered only when it is at least 5 minutes before the sheet's time and the time may go there
      because a forgotten log is exactly when it is needed, and the nudges already reach the last 5 minutes (commit c2297ad; DESIGN_SPEC §3.3.2)
    decide a tap moves the time there as the nudges would, shown as "was"; picked, a second tap returns to the time of the tap
      because the parent must be able to take the guess back with the same control (README "Forgot to log?")
  look
    Under the nudges, a chip that hugs its label: 48dp tall holding a 40dp pill, radius.chip, a 1dp color.outline border,
      the upcoming icon 18dp in color.text and "Expected …" in type.labelLarge color.text; picked, it is filled
      color.primary.container with the label in color.on.primary.container, and a check stands where the icon was
  copy
    expected            "Expected {time}"          ; "Expected 4:40 PM"
  uses      Feeds, Diapers, StartSleep
  sources
    README.md, "Forgot to log?"; docs/design/DESIGN_SPEC.md §3.3.2; docs/design/V1_ADDITIONS.md §9 (the estimate)
    commit c2297ad "Offer the expected time for forgotten logs, and notify when a log ends a sleep"
    driven on Robolectric, 2026-11-27, with the week made in History › Add entry (Previous day, the day chip's Earlier
      date…, the −5m nudges): Food at 4:10 PM, Feed at 5:10 PM offered "Expected 4:40 PM"; tapped ("4:40 PM · was 5:10
      PM"); Breast and Log feed there (Lamp "4:40 PM · Breast"); a sleep receipt offered the start 15 minutes after the
      last wake-up
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeStateBuilder.kt, expected time and offset
    core/src/main/kotlin/com/ffmathy/pooparazzi/core/domain/Forecast.kt, the estimate, kept stale
```
