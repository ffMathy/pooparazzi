# Complications

Four complications put the time since the last feed, the last diaper and the
sleep on the watch face. They never log: a tap opens the app.

```ail
node WatchComplications : flow   level: experience
  why   The watch face is what the parent sees most; the time since the last feed
        or diaper should be there without opening anything.   <- Watch
  what
    The face's editor lists them as "Since last feed", "Last diaper", "Asleep or awake" (short) and "Sleep and last feed" (long).
    Scenario: complications before anything is logged
      Given a new install, with nothing logged
      When the parent looks at a face with all four complications
      Then "Since last feed" reads "--", spoken "No feeds yet"; "Last diaper" reads "--", spoken "No diapers yet"
      And "Asleep or awake" reads "--", spoken "No sleep yet"; "Sleep and last feed" reads "No sleep yet"
    Scenario: complications after a feed and a pee
      Given a food feed and then a pee logged on the watch a moment ago
      When the parent looks at the face
      Then "Since last feed" reads the time since the feed ("<1m", or "1m" once the minute has turned), spoken "Last feed" and how long ago
      And "Last diaper" reads the time since the pee with the title "Pee", spoken "Last diaper, pee," and how long ago
    Scenario: complications while asleep
      Given a sleep started on the watch a moment ago
      When the parent looks at the face
      Then "Asleep or awake" reads how long in clock minutes ("<1m" within the start's minute) with the title "Asleep", spoken "Asleep" that duration ", since" the sleep's start ("Asleep less than a minute, since")
      And "Sleep and last feed" reads "Asleep" and that duration ("Asleep <1m")
    Scenario: tapping a complication opens the app
      Given a sleep started on the watch a moment ago, and the app left on Feed type
      When the parent taps "Asleep or awake"
      Then the app opens on Main, with the sleep running
  how
    decide short text with a monochrome icon (the face supplies the colour), times relative and ticking; "--" with nothing to count from
      because the face's colours come from the face (DESIGN_SPEC §3.7, §5.6)
    decide "Last diaper" shows the kind as an image, with the kind as the title for faces that draw no image
      because a face may show either (DESIGN_SPEC §3.7)
    decide "Sleep and last feed" reads "Awake 1h 05m" (title "since 13:27") while awake and "Asleep 1h 07m" (title "Fed 1h 32m ago") while asleep
      because the long slot has room for the state and its start (DESIGN_SPEC §3.7)
    decide a tap opens the matching app screen and never logs; no ranged "orbit" complication and never a stopwatch
      because progress semantics would be misused, and a tap on the face is often a mis-tap (DESIGN_SPEC §3.7)
    decide the complications follow the watch's current baby
      because the watch logs for one baby at a time (BABIES_AND_BACKUP §2.5)
  copy
    comp.none         "--"
    comp.noFeed       "No feeds yet"             ; spoken
    comp.noDiaper     "No diapers yet"           ; spoken
    comp.noSleep      "No sleep yet"
    comp.lastFeed     "Last feed {ago}"          ; spoken: "Last feed 1 minute ago"
    comp.asleepFor    "Asleep {duration}"
    comp.awakeFor     "Awake {duration}"
    comp.fedAgo       "Fed {ago}"
  sources
    docs/design/DESIGN_SPEC.md §3.7 (W10 Complications), §5.6
    driven on Robolectric through the complication services, as a watch face asks them, 2026-11-27: before anything,
      after Food and Pee ("1m", "Pee"), asleep ("<1m", "Asleep", "Asleep <1m"), after a diaper that cancelled a sleep
      ("<1m", "Diaper"), a tap opening Main
    not driven: a real watch face; the checks read the data the face would draw
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/complication/ComplicationServices.kt, ComplicationContent.kt
```
