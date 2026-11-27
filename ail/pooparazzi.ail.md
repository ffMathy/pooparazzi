# Pooparazzi

A baby tracker for an Android phone and a Wear OS watch, made for one hand at
3am: sleep, feeds and diapers in one or two taps. This spec is being written in
reverse from the existing app (`/ail:adopt`), one slice at a time.

```
pooparazzi.ail.md      Pooparazzi (this file): target, glossary, tokens
sleep/sleep.ail.md     Sleep: Start sleep and Woke up on the phone's Home, the receipt, Undo and the guards
```

Not adopted yet, in the order they are planned: Feeds and Diapers on the phone
(their sheets, the nudges, "Woke up will be logged too"), the forgotten-log
"Expected" chip, Home's Lamp and since-rows, History and the edit sheet,
Statistics, the widgets and notifications, several babies, the first run,
Settings, the account and family backup, and the watch (Main, its logging
screens, Tile, complications). Until a slice is adopted, `README.md` and
`docs/design/` stay its description.

## Target

The app as it is built today. Changing this block, then `/ail:build`, moves it
to another technology; the end-to-end checks in `checks/e2e/` prove the new
app behaves like this one.

Nothing is generated yet: every adopted node is the existing, hand-written
app, recorded in `ail.lock.json` as built by it. `fn` and `data` nodes written
from now on are generated into `core/…/ail/`. The end-to-end checks drive the
real phone app headless on the JVM (Robolectric), because no emulator is
needed and none runs on CI or in a cloud session; they find things only by
what is on the screen.

```ail
target
  code         Kotlin 2.4 for Android (minSdk 30, compileSdk 37), Jetpack Compose with Material 3 on the phone, Compose for Wear OS on the watch; Room and DataStore in core
  structure    core (an Android library) holds the model, its rules, the database, sync and formatting; app is the phone app; wear is the watch app
  naming       Kotlin conventions: UpperCamelCase types, lowerCamelCase functions and properties, a file named after the node
  copy         user-visible text comes from the string resources, in English (values) and Danish (values-da), never from code
  path.fn      core/src/main/kotlin/com/ffmathy/pooparazzi/core/ail/{dir}/{name}.kt
  path.data    core/src/main/kotlin/com/ffmathy/pooparazzi/core/ail/{dir}/{name}.kt
  generated    core/src/main/kotlin/com/ffmathy/pooparazzi/core/ail/**
  tests        JUnit 4 on the JVM with Robolectric (sdk 36), run by Gradle; end-to-end checks are Kotlin tests in checks/e2e (a test source folder of :app), each a class `<Node>Checks` that extends Phone, with one test per scenario named exactly as the scenario in backticks
  checks.path  core/src/test/kotlin/com/ffmathy/pooparazzi/core/ail/{dir}/{name}Test.kt
  checks.run   ./gradlew --console=plain -q :core:testDebugUnitTest --tests 'com.ffmathy.pooparazzi.core.ail.*.{name}Test'
  checks.path.flow  checks/derived/{dir}/{name}Shots.kt
  checks.run.flow   scripts/ail/check.sh derived {name}
  checks.path.view  checks/derived/{dir}/{name}Shots.kt
  checks.run.view   scripts/ail/check.sh derived {name}
  test harness views and flows are the existing, hand-written phone app, so their derived checks drive it the same way the
               end-to-end checks do: a check is a Kotlin class in package com.ffmathy.pooparazzi.checks, named after its file,
               that extends Phone (checks/e2e/Phone.kt: the real app, PooparazziApp and MainActivity, on a 390 × 844 dp
               phone under Robolectric with native graphics). Each scenario is one JUnit 4 test, `@Test fun \`<the scenario's
               title>\`()`, that starts with freshInstall() (a new install, its first run skipped, on Home) and acts only
               through the screen: tap(text) taps what reads text or the button spoken as text; goBack() goes back;
               waitFor(text) and waitUntilGone(text) wait; shows(text), showsPart(part), canTap(text) look;
               assertShows(text), assertGone(text), assertCanTap(text), assertCannotTap(text) fail with every text on
               the screen; clock(minutesFromNow) is the phone's clock time as the app writes it ("14:32" or "2:32 PM"),
               and assertShowsAt(text, minutesFromNow) checks a text with "{time}" in it, allowing for the minute
               turning. pass(millis) really waits (the wall clock and the phone's uptime both move on): use it only
               for the few seconds a scenario names (a guard, a cooldown, a notice that goes away), never for minutes.
               A time in the past ("a sleep that started 20 minutes ago") is made as a parent makes it, with the
               nudges, through the shared steps of each slice in checks/e2e/<its folder>/ (for sleep, checks/e2e/sleep/
               Sleeps.kt: startSleepReceipt() and wokeUpReceipt() return the time the receipt shows, sleepStarted20Minutes
               Ago() returns the start it shows, waitOutTheGuard()); use them rather than writing new ones.
               Durations on screen are whole clock minutes between the two clock minutes, so a test never hard-codes
               "20m": it computes clockMinutesBetween(shownStart, shownEnd) and, for spoken names, spokenMinutes(n)
               ("less than a minute", "1 minute", "21 minutes"). The Lamp shows a duration's numbers and units as separate
               texts ("20", "m"); assert its spoken name instead ("Asleep, 20 minutes, since 4:20 PM"). The machine may be
               slow: wait for what a step changes with waitFor or waitUntil(what) { condition } before asserting it.
               It never calls the app's code. Each test ends with saveScreen("<its scenario's title in lower case, each run
               of other characters one ->"), which saves every window the phone shows (a sheet over Home included) into
               AIL_SHOTS_DIR as a PNG.
```

## Glossary

```ail
glossary
  term Home          plain "the phone app's main screen, with Feed, Diaper and the sleep button"           formal HomeScreen
  term "the sleep button" plain "the wide button at the bottom of Home that reads Start sleep, or Woke up while the baby sleeps"  formal the Sleep toggle
  term sleep         plain "a time the baby slept, from falling asleep to waking up; it runs until Woke up"  formal an Entry of kind SLEEP with a start and an end, or no end while it runs
  term receipt       plain "the sheet that comes up after a tap on the sleep button: what was logged, its time, Undo, nudges, Edit and Done"  formal the receipt sheet
  term nudge         plain "one of the −15m, −10m, −5m and +5m buttons, which move a time by that much"   formal an offset chip
  term notice        plain "a short message that takes the place of Today on Home for two seconds"          formal the whisper slot's notice card
  term guard         plain "the two seconds after a tap on the sleep button in which another tap changes nothing"  formal the double-toggle guard
  term "the Lamp"    plain "the big round display on Home: time since the last feed, or how long the baby has slept"  formal HomeLamp
```

## Design tokens

The phone's Night theme (DESIGN_SPEC §5.2 and §5.5), the one a running sleep
always shows. Day's values are in the design document until a slice needs them.

```ail
catalog
  tokens
    color.background=#0C0A08  color.sheet=#14110E  color.notice=#312922  color.text=#E8D8C3  color.text.dim=#B8A48E
    color.outline=#8A7864  color.primary=#E8D8C3  color.primary.container=#3F3A35  color.on.primary.container=#F2E6D6
    color.scrim=#00000099
    sleep.container=#222844  sleep.on.container=#D6DBF6  sleep.strong=#7C88C4  sleep.lit=#2E3660  sleep.text=#AEB6E6
    feed.container=#3B2715  feed.on.container=#FFDCBB  feed.strong=#F2B577
    diaper.container=#13312C  diaper.on.container=#BFE9E0  diaper.strong=#62B3A5
    radius.button=28dp  radius.sheet=28dp  radius.chip=20dp  radius.notice=28dp
    size.toggle=350x96dp  size.button=169x96dp  size.chip=40dp  size.target=48dp  gutter.phone=20dp  gutter.sheet=24dp
    type.headlineSmall=24sp/600  type.titleLarge=22sp/600  type.titleMedium=18sp/600  type.dataMedium=16sp/450  type.labelLarge=16sp/600
    watch.background=#000000  watch.surface.container=#1E1914  feed.lit=#4F351C  diaper.lit=#1A423B  diaper.text=#8FD0C3
    pee.strong=#8FD0C3  poop.container=#0E2622  poop.strong=#358276
    radius.watch.button=26dp  radius.watch.pill=16dp  radius.watch.chip=20dp  size.edge.medium=70dp  size.edge.small=56dp
    wear.titleLarge=20sp/600  wear.titleMedium=16sp/600  wear.titleSmall=14sp/600  wear.labelMedium=16sp/600
    wear.labelSmall=13sp/500  wear.bodyMedium=14sp/450
```

---

```ail
node Pooparazzi : system   level: product
  why   A parent alone with a baby at 3am needs to know when the baby last slept
        and ate, with one hand and without waking up properly.
  what
    1. One button starts a sleep, the same button ends it, and every tap can be taken back at once.   refined-by: Sleep
    2. The watch logs a feed, a diaper or a sleep from the wrist in a tap or two, without the phone, and its Tile, complications and Ongoing Activity show at a glance how long since each.   refined-by: Watch
  entry     Sleep
  children  Sleep, Watch
  sources
    README.md, "Pooparazzi" and "Sleep"
    docs/design/DESIGN_SPEC.md §1, the core requirement in the owner's words: "register start and stop actions for
      sleeping to track start and stop times, and trigger time for feeding and diapers (pooping/peeing)"
```
