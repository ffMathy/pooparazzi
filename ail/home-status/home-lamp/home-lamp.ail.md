# The Lamp

The big round display on Home holds the one number that matters now: the time
since the last feed while the baby is awake, how long the baby has slept while
a sleep runs. Its text is one tap target that opens that entry.

```ail
node HomeLamp : flow   level: experience
  why   The time since the last feed is what a parent checks most at night, and
        a wrong feed time must be fixable from where it is read.   <- HomeStatus
  what
    Scenario: the Lamp before the first feed
      Given a new install
      Then the Lamp reads "Last fed" over "–" and "No feeds yet", spoken "Last fed, No feeds yet"
      And tapping it opens nothing
    Scenario: the Lamp just after a feed
      Given a feed logged now with Just the time
      Then the Lamp reads "Last fed" over the clock minutes since the feed ("just now" within its minute) and the feed's time
      And it is spoken "Last feed, just now, at" the feed's time
    Scenario: the Lamp counts the time since the last feed
      Given a breast feed logged 75 minutes ago, Left then Right
      Then the Lamp reads the clock minutes since the feed ("1h 15m" right after it was logged, "1h 16m" once the minute turns), over the feed's time and "Left → Right"
      And it is spoken "Last feed, breast, left then right," that duration "ago, at" the feed's time ("1 hour 15 minutes ago")
    Scenario: tapping the Lamp opens the last feed
      Given a feed logged 15 minutes ago with Just the time
      When the parent taps the Lamp
      Then "Edit feed" opens with Time at the feed's time, Delete, Cancel and Save
    Scenario: tapping the Lamp while asleep opens the running sleep
      Given a sleep that started 20 minutes ago
      When the parent taps the Lamp
      Then "Edit sleep" opens with Fell asleep at the sleep's start, and Woke up reading "Still asleep" with "End now"
  how
    decide idle, the Lamp shows the time since the last feed's time, whatever its type; while a sleep runs, how long it has run
      because the Lamp holds the one number that matters right now (DESIGN_SPEC §2.1, §4.3); a feed is an instant, counted start to start (V1_ADDITIONS §1)
    decide the hero is the duration grammar's numbers and units ("15m", "1h 15m", "2d 3h"), "just now" under a minute and "–" before the first feed; the line under it is the clock time, then " · " and the type word or the breast order ("Left → Right"), no "ago"
      because the history icon before the line already says it is time since (DESIGN_SPEC §3.3.1, §7; commit 1cd4db5)
    decide the spoken form is one sentence: "Last feed, breast, left then right, 1 hour 15 minutes ago, at 3:56 PM", or "Asleep, 20 minutes, since 4:48 PM"; never a live region
      because TTS reads "48m" as metres, and a ticking value must not interrupt (DESIGN_SPEC §9.4, §9.5)
    decide the label, hero and line are one tap target: the last feed's edit sheet idle, the running sleep's while asleep (with "Still asleep" and "End now"); before the first feed it is not a target
      because the Lamp works like a since-row, and the running Lamp replaced the Adjust start chip (DESIGN_SPEC §2.2; commit bbb61c3)
    decide durations count whole clock minutes, from the feed's minute to now's, as every duration in the app
      because a time and a duration shown together must agree (ail/sleep/sleep.ail.md)
  look
    The Lamp: Ø264, centred under the top bar. Night idle: a static feed.container glow; Day idle: a day.feed.disc disc.
      While asleep: a sleep.container glow (Night) or a day.sleep.disc disc (Day), with the orbit: a 6dp sleep.lit track, the
      sleep.strong ember at (minutes mod 60) × 6° and its tail
    Its text, centred: the label row (the feed type's icon in feed.strong and "Last fed" in type.labelLarge feed.strong, or the
      moon in sleep.strong and "Asleep" in sleep.text, then a 16dp chevron in color.outline when it opens an entry); the hero
      in type.heroNumeral numbers and type.heroUnit units, or "just now" in type.heroWord, color.text; the line in
      type.dataMedium color.text.dim after an 18dp history icon
  copy
    lamp.lastFed        "Last fed"
    lamp.asleep         "Asleep"
    lamp.noFeedHero     "–"
    lamp.noFeed         "No feeds yet"
    lamp.justNow        "just now"
    lamp.spoken.idle    "Last feed, {type and side, }{duration} ago, at {time}"   ; "Last feed, just now, at 4:58 PM"
    lamp.spoken.asleep  "Asleep, {duration}, since {time}"
    lamp.spoken.none    "Last fed, No feeds yet"
    side.order          "Left → Right" "Right → Left" "Left" "Right"   ; spoken "left then right"
  sources
    docs/design/DESIGN_SPEC.md §2.1–§2.4 (the Lamp, its states, geometry and content), §3.3.1 (Home interactions: Lamp text),
      §7 (formatting), §9.4, §9.5
    docs/design/V1_ADDITIONS.md §2.1 (the feed type on the Lamp, the breast order on every last-feed line)
    commits bbb61c3 (the running Lamp opens the sleep's edit sheet) and 1cd4db5 (the history icon instead of "ago")
    driven on Robolectric, 2026-11-27: "Last fed, No feeds yet"; Just the time ("just now", "4:58 PM"); a breast feed nudged
      75 minutes back, Left then Right ("1h 15m", "3:56 PM · Left → Right"); feeds two days old ("2d 3h"); a tap opens
      "Edit feed", while asleep "Edit sleep" with "Still asleep" and "End now"
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeLamp.kt, HomeStateBuilder.kt (the Lamp's state), HomeText.kt
```
