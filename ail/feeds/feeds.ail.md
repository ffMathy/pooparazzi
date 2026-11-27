# Feeds on the phone

Feed on Home opens the Feed sheet, stamped with the time of the tap. Its nudges
move that time back, and one tap on what the baby had logs the feed there.
Breast and Bottle first offer their optional details; Food and Just the time log
at once.

Written from the running app (Robolectric, a fresh install, 2026-11-27), the
design documents and the phone app's code. Times are the phone's own clock,
written as the phone writes them.

```
feeds.ail.md                            Feeds: the Feed sheet, its nudges, Food and Just the time
breast-feed/breast-feed.ail.md          BreastFeed: the side order and Log feed
bottle-feed/bottle-feed.ail.md          BottleFeed: Milk or Formula, the amount, and what decides ounces
repeat-feed/repeat-feed.ail.md          RepeatFeed: the same feed twice within five seconds
feed-while-asleep/feed-while-asleep.ail.md  FeedWhileAsleep: "Woke up will be logged too", and the sleep it ends
leaving-the-feed-sheet/leaving-the-feed-sheet.ail.md  LeavingTheFeedSheet: Cancel, back, a tap outside, and the cooldown
```

```ail
node Feeds : flow   level: experience
  why   A parent logs a feed with one hand, often a while after it began; the time
        is the one thing that matters, and the details are only offered.   <- Pooparazzi
  what
    Scenario: opening the Feed sheet
      Given the baby is awake on Home with nothing logged
      When the parent taps Feed
      Then the sheet "Feed" opens over the time now, with Cancel
      And it offers the nudges −15m, −10m, −5m and +5m, of which +5m can't be tapped
      And it offers Breast "Then the sides, if you like", Bottle "Then milk or formula and the amount, if you like", Food and Just the time
    Scenario: nudging the feed earlier
      Given the Feed sheet opened now
      When the parent taps −15m and then −5m
      Then the time reads 20 minutes ago, followed by "was" and the time now
      And +5m can be tapped again
    Scenario: logging food
      Given the Feed sheet opened now, nudged back 15 minutes with −15m
      When the parent taps Food
      Then the sheet closes and no receipt follows
      And the Lamp reads "Last fed" 15 minutes ago, with the feed's time and "Food"
      And Today counts 1 feed
    Scenario: logging just the time
      Given the Feed sheet opened now
      When the parent taps Just the time
      Then the sheet closes, and the Lamp reads "Last fed" "just now" with the feed's time and no type
  how
    decide the time is stamped when Feed is tapped, shown on a line of its own under the title, and the pane is called "Feed · <time>"
      because the time is the one fact every log needs, and a long title must never hide it (DESIGN_SPEC §3.3.2, V1_ADDITIONS §2.1)
    decide nudges add up from the stamp and show "was <stamp>" once moved; one that would reach the future can't be tapped
      because backdating at log time is the cheapest fix for a late log, and no log is in the future (DESIGN_SPEC §4.4)
    decide the type is optional: Just the time logs a feed with no type; Food and Just the time log at the sheet's time and close it
      because only the time is ever required (README); the parent picks once and is done (V1_ADDITIONS §2.1)
    decide a feed from the sheet gets no receipt; the Lamp and Today show it, and the Lamp opens its edit sheet
      because the sheet already picked the time, so a receipt would only ask again (DESIGN_SPEC §3.3.1)
    decide the sheet's chips and rows ignore taps for their first 500 ms, fading in from 60 %
      because the Feed button lies under the sheet's rows, and a double tap must not log a feed (DESIGN_SPEC §3.3.2, §5.9);
      the checks' clock finishes the fade before a tap, so no scenario shows it
    decide a feed never runs: every type is an instant at its time
      because a running feed, End feed and its notification were removed (V1_ADDITIONS §1, §2.1)
  look
    The sheet: a bottom sheet over Home and a color.scrim, fill color.sheet, top corners radius.sheet, padding gutter.sheet;
      the title in type.titleLarge color.text over the time line in type.dataMedium color.text.dim, Cancel at the end in
      type.labelLarge color.text.dim; under them one row of the four nudges, as on the sleep receipt
    The rows: full width, 72dp tall, radius.button, gap 8dp; Breast, Bottle and Food filled feed.container, a 32dp icon
      in feed.strong, the label in type.headlineSmall feed.on.container over the supporting line in type.bodyMedium
      feed.on.container; Just the time outlined 1dp color.outline with no fill, the clock icon, its label in color.text
  copy
    sheet.title         "Feed"
    sheet.pane          "Feed · {time}"
    sheet.was           "{time} · was {stamp}"
    sheet.cancel        "Cancel"
    row.breast          "Breast"     "Then the sides, if you like"
    row.bottle          "Bottle"     "Then milk or formula and the amount, if you like"
    row.food            "Food"
    row.none            "Just the time"
    lamp.lastFed        "Last fed"
    lamp.none           "No feeds yet"
    lamp.line           "{time} · Food"           ; no type: "{time}"; spoken "Last feed, food, 15 minutes ago, at 4:44 PM"
  children  BreastFeed, BottleFeed, RepeatFeed, FeedWhileAsleep, LeavingTheFeedSheet
  sources
    README.md, "Feeds"; docs/design/V1_ADDITIONS.md §1 (instants, optional type), §2.1 (the Feed sheet)
    docs/design/DESIGN_SPEC.md §3.3.1 (no receipt after a sheet, cooldowns), §3.3.2 (the sheet pattern), §4.4, §5.9, §11
    driven on Robolectric, 2026-11-27: Feed, −15m then −5m ("4:39 PM · was 4:59 PM"), Food ("4:44 PM · Food"), Just
      the time ("5:00 PM", spoken "Last feed, just now, at 5:00 PM")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/FeedSheets.kt, the sheet and its rows
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, the stamp, offsets and logging
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/XModalSheet.kt, the 500 ms picker readiness
```
