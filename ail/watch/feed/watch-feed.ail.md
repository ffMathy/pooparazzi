# Feeds on the watch

Main's Feed row opens Feed type, stamped with the time of the Feed tap. Food and
Just the time log at once; Breast and Bottle open a screen for their details
(Side, Bottle), which logs at that same time.

```ail
node WatchFeed : flow   level: experience
  why   A feed is logged at the moment it starts, from the wrist, with its type when
        the parent cares to say it; a double tap must not log it twice.   <- Watch
  what
    Scenario: the feed type screen
      Given the watch's Main
      When the parent taps the Feed row
      Then Feed type reads "Feed · " and the time of the tap, over Breast, Bottle, Food and Just the time
      And for half a second none of them can be tapped
    Scenario: logging food on the watch
      Given Feed type, opened just now
      When the parent taps Food
      Then Logged reads "Food at" the time of the Feed tap, with Undo
      And once Logged has closed, the Feed row reads "just now", spoken "Feed. Last feed, food, just now, at" that time
    Scenario: logging just the time of a feed
      Given Feed type, opened just now
      When the parent taps Just the time
      Then Logged reads "Feed at" the time of the Feed tap, with Undo
    Scenario: the same feed twice within five seconds
      Given a food feed logged on the watch two seconds ago
      When the parent taps the Feed row and Food again
      Then Logged reads "Food at" the first feed's time over "Already logged", and no second feed is logged
      And once Logged has closed, Main's Feed row still reads the first feed, "just now"
    Scenario: swiping back from feed type logs nothing
      Given Feed type, opened just now
      When the parent swipes right
      Then Main is back, and the Feed row still reads "No feeds yet"
  how
    decide the Feed tap stamps the time; every screen of the flow shows it in its title, and the feed is logged at it
      because the time is never set on these screens: a watch log's time is moved afterwards, from Logged (V1_ADDITIONS §2.2; commit bc0ac14)
    decide Breast opens Side and Bottle opens Bottle, logging nothing yet; Food and Just the time log at once; Just the time logs a feed with no type
      because only the time is ever required (README; V1_ADDITIONS §2.2)
    decide no logging flow opens a screen of its own for a detail; the only steps are Main, Feed type, Side or Bottle, and Logged
      because every detail is picked on the screen that owns it (V1_ADDITIONS §2.2; commit 9da03a4)
    decide a feed of the same type within five seconds of another is not logged again: Logged shows the existing one, "Already logged"
      because a double tap must never log twice (DESIGN_SPEC §3.5.6, §4.4)
    decide every target ignores taps for 500 ms each time the screen appears
      because a double tap must never land on a new target (DESIGN_SPEC §5.9)
    decide the crown scrolls the list, nothing scrolls by itself, and a swipe right goes back from anywhere
      because no row drags sideways, so the swipe is always free (DESIGN_SPEC §3.5.3)
    decide Feed type has no Save: its buttons log or open the next screen
      because a tap there is the choice (DESIGN_SPEC §3.5.3)
  look
    Feed type: a list on watch.background under the time text; "Feed · 5:02 PM" in wear.titleMedium color.text, centred;
      Breast, Bottle and Food as full-width buttons in feed.container with wear.labelMedium feed.on.container,
      radius.watch.button; Just the time an outlined button with the clock icon; at rest Food sits at the bottom edge, narrowed
      and dimmed as the round screen's list morphs its last visible item, and Just the time under it below the fold
  copy
    feedType.title      "Feed · {time}"
    feedType.breast     "Breast"
    feedType.bottle     "Bottle"
    feedType.food       "Food"
    feedType.none       "Just the time"
    logged.food         "Food at {time}"
    logged.feed         "Feed at {time}"
    logged.duplicate    "Already logged"
  uses      WatchLogged
  children  WatchSide, WatchBottle
  sources
    docs/design/V1_ADDITIONS.md §2.2 (Watch feed flows), DESIGN_SPEC.md §3.5.3, §3.5.6, §3.5.11, §5.9
    commits bc0ac14 (every watch log's time moves from Logged), 9da03a4 (every detail on one screen)
    driven on Robolectric, 2026-11-27: Feed type ("Feed · 5:04 PM", disabled for half a second), Food ("Food at
      5:06 PM"), Just the time ("Feed at 5:14 PM"), Food again 3 s later ("Already logged"), swipe back to Main
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/feed/FeedTypeScreen.kt
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/MainViewModel.kt, beginning a feed and choosing its type
    core/src/main/kotlin/com/ffmathy/pooparazzi/core/data/RoomEntryRepository.kt, the five-second dedupe of a feed
```
