# The introduction

A six-page tour of how to use the app, shown once after Your baby, and again
from Settings › About › Introduction.

```ail
node Introduction : flow   level: experience
  why   A parent new to the app should learn in a minute what one tap does, how to
        fix a log and where to look next, and be able to skip it at once.   <- FirstRun
  what
    Scenario: the six pages
      Given the introduction on its first page
      When the parent taps Next five times
      Then the pages read, in order, "Welcome to Pooparazzi", "Sleep", "Feeds and diapers", "Fix anything", "What comes next" and "Always at hand", each with its paragraph
      And the dots read "Page 1 of 6" to "Page 6 of 6"
      And the last page offers "Get started" in place of Next, and no Skip
    Scenario: back on the first page can't be tapped
      Given the introduction on its first page
      Then Skip and Next can be tapped, and Back is there but can't be tapped
    Scenario: back turns to the page before
      Given the introduction on its second page, "Sleep"
      When the parent taps Back
      Then the first page, "Welcome to Pooparazzi", shows again
    Scenario: skipping the introduction
      Given the first run's introduction on its third page
      When the parent taps Skip
      Then Home shows
    Scenario: getting started
      Given the first run's introduction on its last page
      When the parent taps "Get started"
      Then Home shows
    Scenario: the introduction from settings
      Given Settings
      When the parent taps Introduction under About
      Then the introduction opens on "Welcome to Pooparazzi"
      And going back on its first page returns to Settings
      When the parent opens it again and goes back after Next
      Then the first page shows again
  how
    decide six pages, swiped or stepped with Next; the last page's Next reads "Get started"; Skip at the top end closes it from any page but the last
      because a short, skippable tour must never stand between the parent and the log (commit 26f01d0)
    decide Back stays in its place on the first page, invisible and not tappable, so Next never moves
      because a button must not move under the thumb (app/…/intro/IntroScreen.kt)
    decide the system Back turns to the page before, and on the first page closes the introduction like Skip
      because Back means one step back, like the screen's own Back (DESIGN_SPEC §3.3.5)
    decide finishing or skipping it from Settings returns to Settings; on the first run it goes on to Home, and the first run never shows it again
      because Settings › About › Introduction shows it at any time, over Settings (DESIGN_SPEC §3.3.5)
  look
    Full screen on color.background. At the top end, Skip as a text button in type.labelLarge color.text. Each page
      centres an illustration of the app's own icons on round discs (the sleep, feed and diaper icons on their
      sleep.container, feed.container and diaper.container discs with the strong colour as the icon; undo, edit,
      history, chart and the phone on neutral discs), then the heading in type.headlineSmall color.text and the
      paragraph in type.bodyLarge color.text.dim, both centred. Under the pages, one dot per page, the current one
      24dp wide in color.text and the others 8dp in color.outline.variant; then Next (or "Get started") filled
      color.primary.container, full width inside gutter.phone, and Back as a text button under it
  copy
    skip       "Skip"                 ; da "Spring over"
    next       "Next"                 ; da "Næste"
    back       "Back"                 ; da "Tilbage"
    done       "Get started"          ; da "Kom i gang"
    page       "Page {n} of {count}"  ; da "Side {n} af {count}"
    welcome.title  "Welcome to Pooparazzi"
    welcome.body   "Log sleep, feeds and diapers with one or two taps, made for one hand at 3am. Here is how it works; it takes a minute."
    sleep.title    "Sleep"
    sleep.body     "Tap Start sleep when the baby falls asleep, and the same button, now Woke up, when they wake. While the baby sleeps, Home shows how long; tap the ring to change when the sleep started."
    log.title      "Feeds and diapers"
    log.body       "Tap Feed or Diaper. Only the time is needed: the type, the breast side, pee or poop and the rest are optional, so Just the time is fine. Did it happen a little earlier? −15m, −10m and −5m move the time back."
    fix.title      "Fix anything"
    fix.body       "Every log can be changed. Tap Undo right after a sleep, or tap a time on Home (Last fed, a since row) or any row in History to edit it or delete it."
    patterns.title "What comes next"
    patterns.body  "Under each button, Home shows what the last 7 days expect next, such as the next feed. History shows each day on a 24-hour ribbon, and Statistics the averages per day."
    everywhere.title "Always at hand"
    everywhere.body  "Add a widget to your home screen, and install Pooparazzi on your Wear OS watch for its tile and complications. Signed in, your family can log too. You can see this introduction again in Settings › About."
  sources
    docs/design/DESIGN_SPEC.md §3.3.5 (the Introduction)
    git log: 26f01d0 "Add a skippable introduction, shown once after Your baby and again from Settings › About"
    driven on Robolectric, 2026-11-27: the six pages with Next, "Get started", Back disabled on page 1, system Back on
      page 2 and page 1, Skip, and Settings › About › Introduction; in Danish on a phone set to Danish
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/intro/IntroScreen.kt
    app/src/main/res/values/strings.xml and values-da/strings.xml, the intro strings
```
