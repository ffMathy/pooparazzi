# What comes next

Under the labels of Feed, Diaper and the sleep button, a short second line says
what this baby's last week expects next: the next feed, the next poop, the next
sleep or the wake-up.

```ail
node ForecastLines : flow   level: experience
  why   A parent plans the next hour around the next feed or nap; the baby's own
        recent rhythm is the best guide there is, said briefly and never as a promise.   <- HomeStatus
  what
    Scenario: no forecast without a week to go on
      Given a new install, and a feed logged now with Just the time
      Then Feed, Diaper and Start sleep have no second line
    Scenario: Feed says when the next feed is expected
      Given feeds at 8:00 AM and 11:00 AM four days ago, and at 8:00 AM, 11:00 AM and 2:00 PM three and two days ago
      And a feed logged now with Just the time
      Then Feed's second line reads "3h 00m"
      And it is spoken "next feed in about 3 hours, around" the time 3 hours from now
    Scenario: the feed forecast reads soon in its last minutes
      Given the same feeds four, three and two days ago, and a feed 2 hours 58 minutes ago
      Then Feed's second line reads "soon", spoken "next feed soon"
    Scenario: the feed forecast reads now once it is due
      Given the same feeds four, three and two days ago, and a feed 3 hours 10 minutes ago
      Then Feed's second line reads "now", spoken "next feed expected now"
    Scenario: Diaper says when the next poop is expected
      Given poop diapers at 9:00 AM and 9:00 PM on each of the last four days, and a poop diaper logged now
      Then Diaper's second line reads "12h 00m"
      And it is spoken "next poop in about 12 hours, around" the time 12 hours from now
    Scenario: Woke up says when the baby is expected to wake
      Given sleeps from 12:00 PM to 1:30 PM and from 3:00 PM to 4:30 PM two, three and four days ago
      When the parent taps Start sleep, then Done
      Then the sleep button's second line reads "1h 30m"
      And it is spoken "wakes up in about 1 hour 30 minutes, around" the time 90 minutes from now
  how
    decide the estimate looks only at this baby's last 7 days: sleep lengths, wake windows, gaps between feeds and between poops; gaps under 10 minutes are one, and feed or wake gaps over 12 hours (a forgotten log) are dropped; poop gaps are kept whatever their length
      because there is no validated model of one baby's rhythm; the last week follows a growing baby (V1_ADDITIONS §9 steps 1)
    decide only stretches that began within 2 hours of the current one's time of day count, widened to 4 hours, then the whole day, until there are 5 from at least 3 days; poop follows no clock, so every poop gap counts
      because night sleeps run longer than naps and evening feeds cluster (V1_ADDITIONS §9 step 2)
    decide no forecast with fewer than 5 stretches or fewer than 3 days, an interquartile range over 1.5 × the median, or a current stretch already twice the longest
      because a guess from too little, too spread out, or after logging stopped would mislead (V1_ADDITIONS §9 step 3)
    decide the estimate is the stretch's start plus the median, each stretch weighted by its age (halving every 3 days), and it stays fixed for the stretch
      because a growth spurt changes the rhythm for a few days, and a long nap must not push the guess along (V1_ADDITIONS §9 step 4)
    decide when poop gaps say nothing, a poop that keeps to the feeds (within 90 minutes of at least 70 % of the week's feeds) is expected after the last feed; with a birth date, no sleep forecast before 6 weeks and no poop-gap forecast for a breastfed baby from 6 weeks
      because of the gastrocolic reflex in newborns, and a newborn's sleep has no day–night rhythm yet (V1_ADDITIONS §9 steps 5 and 6)
    decide the line is the time left alone, rounded up to 5 minutes ("3h 00m", "55m"), "soon" under 5 minutes, then "now" once due, never a time in the past; it is spoken with the clock time ("next feed in about 3 hours, around 8:13 PM")
      because an estimate is not a promise, and the icon already says "next" (V1_ADDITIONS §9; commit 665a924)
    decide Diaper predicts poop only; the sleep button predicts the next sleep while awake and the wake-up while asleep
      because pee is not predicted (V1_ADDITIONS §9)
  look
    Under each button's label, start-aligned with it 12dp after the button's icon: a 16dp upcoming icon, 4dp, and the line in
      type.labelLarge in the button's on-container colour (feed.on.container, diaper.on.container, sleep.on.container) at 78 %,
      one line that shrinks rather than wraps; icon and text centred in the button as one group
  copy
    forecast.soon       "soon"
    forecast.now        "now"
    forecast.spoken     "next feed in about {duration}, around {time}" "next poop in about {duration}, around {time}" "sleeps again in about {duration}, around {time}" "wakes up in about {duration}, around {time}"
    forecast.spoken.soon  "next feed soon" "next poop soon" "sleeps again soon" "wakes up soon"
    forecast.spoken.now   "next feed expected now" "next poop expected now" "expected to sleep again now" "expected to wake up now"
  sources
    docs/design/V1_ADDITIONS.md §9 (Forecast: what comes next, the estimate, its steps); DESIGN_SPEC §3.3.1 (Forecast line)
    commit 665a924 (forecasts say "now" once due), 1b7624c (age-aware forecasts), 0071f65 (poop after a feed)
    driven on Robolectric, 2026-11-27: the feeds, poops and sleeps above made with History's Add entry, then a feed, a poop and
      Start sleep on Home ("3h 00m", "12h 00m", "1h 30m"; "now" for the poop due at 9:00 AM before today's)
    core/src/main/kotlin/com/ffmathy/pooparazzi/core/domain/Forecast.kt; app/…/ui/home/ForecastText.kt, HomeButtons.kt
```
