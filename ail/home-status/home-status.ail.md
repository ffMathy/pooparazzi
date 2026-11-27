# Home: what the phone shows about the log

Home answers "when did the baby last eat, sleep and get a diaper, and what
comes next?" at a glance: the Lamp, the two since-rows, Today, the forecast
lines on the big buttons, and the top bar. Every one of them that shows an
entry opens it in the edit sheet, the one-tap correction path.

Written from the running app (Robolectric, a fresh install with the first run
skipped, 2026-11-27), the design documents and the phone app's code. Times are
the phone's own clock, written as the phone writes them (12- or 24-hour, as it
is set).

```
home-status.ail.md                  HomeStatus: the status part of Home, a new install, Night and Day
home-lamp/home-lamp.ail.md          HomeLamp: time since the last feed, or how long the baby has slept; opens that entry
since-rows/since-rows.ail.md        SinceRows: Awake or Last fed, and the last diaper; each opens its entry
today-grid/today-grid.ail.md        TodayGrid: today's counts, a link to History
forecast-lines/forecast-lines.ail.md  ForecastLines: "what comes next" under Feed, Diaper and the sleep button
top-bar/top-bar.ail.md              TopBar: the baby's name, the sync icons, History, Statistics and Settings
```

```ail
node HomeStatus : flow   level: experience
  why   A parent at 3am needs the one number that matters (time since the last
        feed, or how long the baby has slept) and the last diaper, without
        opening anything, and a way to fix any of them with one tap.   <- Pooparazzi
  what
    Home's status shows, top to bottom: the top bar, the Lamp, up to two since-rows, and Today; Feed, Diaper and the sleep button carry a forecast line when the past week predicts one.
    Scenario: a new install has nothing to show yet
      Given a new install, its first run skipped
      Then the top bar reads "Baby" with History, Statistics and Settings
      And the Lamp reads "Last fed" over "–" and "No feeds yet"
      And there are no since-rows
      And Today reads 0 feeds, 0 pee, 0 poop and "<1m" sleep
      And Feed, Diaper and Start sleep have no forecast line
  how
    decide each fact appears once on the screen: time since the last feed in the Lamp (or in row 1 while asleep), the wake-up and the last diaper in the since-rows, counts in Today
      because "each fact appears once per screen" (DESIGN_SPEC §2.1 principle 5)
    decide every status shown opens its entry in the edit sheet, which writes nothing until Save
      because Undo is never the only recovery path: the Lamp, the since-rows and History open the edit sheet (DESIGN_SPEC §10.7)
    decide nothing ticks: times and durations are whole minutes, re-drawn once a minute
      because "No seconds anywhere" (DESIGN_SPEC §2.1 principle 6, §7)
    decide Night (with Auto: the system's dark theme, the night window 19:00–07:00, or a running sleep) and Day change only colours: the Lamp is a glow at Night and a solid disc by Day; while a sleep runs at Night (lights-down) the baby's name and Today's numbers dim; no text, position or control changes
      because a calm, dim app for the night dims further while the baby sleeps, and a glow on paper reads as a stain (DESIGN_SPEC §2, §8.1, §8.2)
  look
    The bands of DESIGN_SPEC §3.3.1 at the reference 390 × 844: the top bar, the Lamp (Ø264, its text centred), since-row 1,
      since-row 2, the whisper slot (Today, a notice, or a prompt), Feed · Diaper, the sleep button; positions never change
      with the state or the theme
    Night: fill color.background; the idle Lamp is a static feed.container glow; while asleep a sleep.container glow with the
      orbit (a sleep.lit track, a sleep.strong ember and tail); the baby's name and Today's numbers in color.text.dim
    Day: fill day.background, text day.text and day.text.dim; the Lamp is a solid disc, day.feed.disc idle, day.sleep.disc with
      a day.sleep.track orbit while asleep
  copy
    lamp.lastFed        "Last fed"
    lamp.noFeedHero     "–"
    lamp.noFeed         "No feeds yet"
    baby.unnamed        "Baby"
  children  HomeLamp, SinceRows, TodayGrid, ForecastLines, TopBar
  sources
    docs/design/DESIGN_SPEC.md §2 (the Lamp, principles), §3.3.1 (P1 Home: bands, top bar, since-rows, Today, forecast line,
      Home states and interactions), §4.3 (derived values), §7 (formatting), §8 (Night behaviour), §10.7
    docs/design/V1_ADDITIONS.md §2.1 (the type on the Lamp), §9 (forecasts)
    driven on Robolectric (the real MainActivity, a fresh install with the first run skipped), 2026-11-27, at about 5 PM (Day)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeScreen.kt, HomeStateBuilder.kt, HomeLamp.kt, SinceRows.kt,
      WhisperSlot.kt, HomeTopBar.kt, HomeButtons.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/theme/NightRule.kt and NightLightTheme.kt
```
