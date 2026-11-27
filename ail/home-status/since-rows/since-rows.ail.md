# The since-rows

Two rows under the Lamp: row 1 says how long the baby has been awake (or,
while asleep, since the last feed), row 2 the last diaper. Each opens its
entry in the edit sheet.

```ail
node SinceRows : flow   level: experience
  why   Besides the last feed, a parent needs to know since when the baby has been
        awake and when the last diaper was, and fix either with one tap.   <- HomeStatus
  what
    Scenario: a diaper shows in the second row
      Given a pee diaper logged now
      Then the second row reads "Pee" over "just now ·" and the diaper's time
      And it is spoken "Last diaper, pee, just now, at" the diaper's time
    Scenario: tapping the diaper row opens the diaper
      Given a poop diaper logged now
      When the parent taps its row
      Then "Edit diaper" opens with its Time, Pee, Poop, Colour and Texture
    Scenario: after waking the first row reads Awake
      Given a sleep that started 20 minutes ago, ended with Woke up and Done
      Then the first row reads "Awake" over "<1m ·" and the wake-up's time
      And it is spoken "Awake less than a minute, since" the wake-up's time
    Scenario: tapping the Awake row opens the last sleep
      Given a sleep that started 20 minutes ago, ended with Woke up and Done
      When the parent taps the Awake row
      Then "Edit sleep" opens with Fell asleep at the sleep's start and Woke up at its end
    Scenario: while asleep the first row is the last feed
      Given a feed logged 15 minutes ago with Just the time, then a sleep started now
      Then the first row reads "Last fed" over "15m ·" and the feed's time
      And tapping it opens "Edit feed" at the feed's time
  how
    decide row 1 is "Awake" since the end of the last sleep while none runs, and "Last fed" while one runs (the Lamp then shows the sleep); with no sleep ended yet there is no row 1, and with no diaper no row 2
      because each fact appears once per screen: the time since the last feed moves to row 1 when the Lamp shows the sleep (DESIGN_SPEC §2.1, §3.3.1)
    decide row 2 leads with the kind: "Pee", "Poop", "Both" or "Diaper" for a plain change, with its icon
      because status surfaces lead with the kind word (DESIGN_SPEC §4.2, §7; V1_ADDITIONS §1)
    decide the detail line is the time since, then " · " and the clock time, and for a breast feed while asleep the short order "L → R"
      because the history icon says it is time since, so no "ago" (DESIGN_SPEC §3.3.1; V1_ADDITIONS §2.1)
    decide under a minute, the diaper and Last fed rows read "just now" but the Awake row reads "<1m" (spoken "Awake less than a minute")
      because "unknown, confirm with the owner": the grammar says time since under a minute is "just now" (DESIGN_SPEC §7); keep the Awake row's "<1m"?
    decide a tap opens the row's latest entry in the edit sheet: the last sleep, the last feed while asleep, the last diaper
      because this is the one-tap correction path (DESIGN_SPEC §3.3.1)
  look
    Each row: a full-width button of at least 56dp, radius 12dp, padding 4dp 16dp: a 40dp icon spanning both lines (the wake
      icon in sleep.strong, the feed type's icon in feed.strong, or the diaper kind's icon in pee.strong, poop.strong or two-tone),
      the title in type.titleMedium color.text over the detail in type.dataMedium color.text.dim after an 18dp history icon,
      and a 16dp chevron in color.outline at the end; each line one line, ellipsized
  copy
    row.awake           "Awake"
    row.lastFed         "Last fed"
    row.detail          "{since} · {time}"          ; "15m · 4:53 PM", "just now · 5:08 PM", "<1m · 5:08 PM"
    row.spoken.awake    "Awake {duration}, since {time}"
    row.spoken.diaper   "Last diaper, {kind}, {since}, at {time}"   ; "Last diaper, pee, just now, at 4:58 PM"
    row.spoken.feed     "Last feed, {since}, at {time}"
    kind                "Pee" "Poop" "Both" "Diaper"
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (since-rows: states, kind icon, tap), §4.2, §7, §9.4
    docs/design/V1_ADDITIONS.md §2.1 (since-row 1, the short breast order)
    driven on Robolectric, 2026-11-27: Pee and Poop from the Diaper sheet ("just now · 5:08 PM"), Woke up after a sleep
      nudged 20 minutes back ("Awake", "<1m · 5:08 PM"), a feed nudged 15 minutes back and a running sleep ("Last fed",
      "15m · 4:53 PM"); each tap opened "Edit diaper", "Edit sleep" or "Edit feed"
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/SinceRows.kt, HomeStateBuilder.kt (row1, row2)
```
