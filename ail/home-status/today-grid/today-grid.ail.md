# Today

Under the since-rows, Today counts the day so far: feeds, pee, poop and sleep.
The whole block is a link to History. A notice or a prompt takes its place for
a moment (ail/sleep).

```ail
node TodayGrid : flow   level: experience
  why   "How many feeds today, and how much sleep?" is what a parent is asked by a
        partner or a health visitor, and the answer should be on Home.   <- HomeStatus
  what
    Scenario: Today before anything is logged
      Given a new install
      Then Today reads "0" feeds, "0" pee, "0" poop and "<1m" sleep
      And it is spoken "Today: 0 feeds, 0 pee, 0 poop, less than a minute sleep"
    Scenario: Today counts what was logged today
      Given a feed logged now with Just the time, a diaper with Pee and Poop logged now, then a sleep that started 20 minutes ago and still runs
      Then Today reads "1" "feed", "1" "pee", "1" "poop" and the running sleep's clock minutes ("20m")
      And it is spoken "Today: 1 feed, 1 pee, 1 poop, 20 minutes sleep"
    Scenario: tapping Today opens History
      Given a new install
      When the parent taps Today
      Then History opens on "Today ·" and today's date
  how
    decide feeds count every feed of any type (a feed with no type too); pee counts diapers with Pee, poop those with Poop, so Pee and Poop counts once in each and a plain change in neither; there is no diaper total
      because the stats row deliberately has no diaper total (V1_ADDITIONS §1, DESIGN_SPEC §4.2)
    decide sleep is the minutes of sleep inside today, from 00:00: only the part of a sleep after midnight counts, and a running sleep counts up to now
      because Today counts from the day start (DESIGN_SPEC §4.3)
    decide the caption is "feed" for 1 and "feeds" otherwise; "pee", "poop" and "sleep" never change
      because of the counts grammar (DESIGN_SPEC §7)
    decide with no sleep today, the sleep count reads "<1m" (spoken "less than a minute sleep") rather than 0
      because "unknown, confirm with the owner": the duration grammar writes anything under a minute as "<1m" (DESIGN_SPEC §7), but nothing was slept; keep it, or show "0m"?
    decide the whole block is one link to History
      because History is where the day's entries are, and Today is its summary (DESIGN_SPEC §3.3.1 Home interactions)
  look
    In the whisper slot (at least 128dp tall), padding 14dp 16dp, radius 20dp: the header "Today" in type.labelMedium
      color.text.dim with a 16dp chevron in color.outline; under it four equal columns 8dp apart, each a number in
      type.titleMedium color.text over its caption in type.labelMedium color.text.dim, the numbers on one baseline and the
      captions on another. Lights-down: the numbers in color.text.dim
  copy
    today.title         "Today"
    today.captions      "feeds" "feed" "pee" "poop" "sleep"
    today.spoken        "Today: {feeds}, {pee} pee, {poop} poop, {sleep} sleep"   ; "Today: 1 feed, 1 pee, 1 poop, 20 minutes sleep"
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (Whisper slot: Today grid), §4.3 (Today counts), §7 (counts)
    docs/design/V1_ADDITIONS.md §1 (Today counts)
    driven on Robolectric, 2026-11-27: a new install ("0 0 0 <1m"), a feed, Pee and Poop, a running sleep ("1 feed",
      "20m"), a tap on Today opening History
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/WhisperSlot.kt, HomeText.kt; core/…/domain, the day's counts
```
