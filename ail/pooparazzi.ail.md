# Pooparazzi

A baby tracker for an Android phone and a Wear OS watch, made for one hand at
3am: sleep, feeds and diapers in one or two taps. This spec is being written in
reverse from the existing app (`/ail:adopt`), the whole app, one slice at a
time.

```
pooparazzi.ail.md      Pooparazzi (this file): target, glossary, tokens
sleep/                 Sleep: Start sleep and Woke up on the phone's Home, the receipt, Undo and the guards
feeds/                 Feeds: the Feed sheet (breast, bottle, food, just the time), repeats, a feed during a sleep
diapers/               Diapers: the Diaper sheet, poop colour and texture, the doctor hint, a diaper during a sleep
forgot-to-log/         ForgotToLog: the Expected chip for a feed or sleep logged long after it happened
home-status/           HomeStatus: the Lamp, the since-rows, Today, the forecast lines and the top bar
history-screen/        HistoryScreen: the day ribbon and list, Add past entry, the edit sheet, deleting
statistics-screen/     StatisticsScreen: averages per day, the chart, unusual days, the typical day
widgets/               Widgets: the 4×2, 3×1 and cover-screen widgets, their receipt and Undo
notifications/         Notifications: the running sleep, Woke up and its offsets, a log that ends a sleep, predictions
watch/                 Watch: the Wear OS app: Main, logging, Logged and Time, the Tile, complications, Ongoing Activity
first-run/             FirstRun: Your baby (name, birth date) and the six-page introduction
review-prompt/         ReviewPrompt: "Enjoying Pooparazzi?", when it is due and when it shows
settings/              Settings: Appearance, Language (and a Danish phone), Watch, Notifications, About
babies/                Babies: names and birth dates, Add a baby, Remove, the Switch baby sheet
account/               Account: a build without a backup; the Welcome, sign-in, consent, family and joining
```

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
               The phone is en-US (12-hour clock, fluid ounces) unless a scenario's Given names a country: then its test
               carries that region's qualifier, e.g. `@Config(qualifiers = "en-rGB-w390dp-h844dp-xxhdpi")` for a phone
               in the UK (24-hour clock, millilitres), as checks/e2e/feeds/bottle-feed/BottleFeedChecks.kt does.
               While a dialog window is open (Add a baby, Remove, a date picker, the Switch baby sheet), Robolectric never
               lets Compose go idle, so plain tap and waitFor hang: use the dialog steps in checks/e2e/babies/Babies.kt
               (settleDialogWindows(), tapInTopWindow(text), waitForInTopWindow(text), waitUntilGoneInTopWindow(text),
               typeBabyName(name)). A Danish phone is the class qualifier "da-w390dp-h844dp-xxhdpi".
               Phone is a build without a backup (accounts off). The nodes under ail/account/ other than Account itself
               are the build with one: their checks extend BackedUpPhone (checks/e2e/account/Accounts.kt), the same app
               pointed at the Firebase Authentication and Firestore emulators (ail.config.json's service firebase,
               demo project demo-pooparazzi, emptied before each test). There a new install opens on the Welcome, not
               Your baby, so a test starts with newInstallSignedInAs(email) (signed in by email, agreed, first run
               skipped: Home) or waits for "No account needed" itself, not freshInstall(). The steps: typeEmail(text),
               signInByEmail(email) (sends the link, reads it from the emulator's sent emails and opens it, ending on
               the consent), agreeToTheBackup(), skipTheFirstRun(), openTheAccountScreen(row), signOutFromSettings(email),
               inviteSomeone() (returns the link the share sheet shares), openLink(url) (as tapping a link does),
               familyOfTwo() (parent@example.com invites, partner@example.com joins on the same phone: one phone runs
               the checks). Google sign-in is never tapped: it needs Google Play services.
               A node's derived check is a subclass of its end-to-end check, checks/e2e/<its folder>/<Node>Checks.kt
               (an open class that already plays every scenario against the app): `class <Node>Shots : <Node>Checks()`
               with a TestName rule and one @After that calls pictureTheScreen(<the scenario's title in lower case, each
               run of other characters one "-">), as checks/derived/feeds/FeedsShots.kt does. It adds nothing else.
               Home ignores taps on Feed, Diaper and the sleep button for 600 ms after any sheet closes (the shared
               steps wait it out); a sheet ignores taps for its first 500 ms.
               It never calls the app's code. pictureTheScreen(name) pictures what the parent is looking at when the
               scenario ends, into AIL_SHOTS_DIR as name.png; by default it is saveScreen(name), which lets the screen
               settle (nothing moving for two looks 100 ms apart, at most 1.5 s) and saves every window the phone shows (a
               sheet over Home included). An end-to-end check whose scenarios end outside the app's window overrides it:
               the widget checks with pictureTheHomeScreen(name) (checks/e2e/widgets/HomeScreen.kt: the widget last
               placed or tapped, drawn at its size from what the app last sent, alone on a plain wallpaper; the app's
               screen when a tap on the widget opened the app), the notification checks with pictureTheShade(name)
               (checks/e2e/notifications/Shade.kt: the shade over the top of the app, each notification newest first as
               the system's template draws it expanded, or "No notifications").
               The watch's views and flows (the nodes under ail/watch/) are the existing watch app. Their checks are Kotlin
               classes in the same package under checks/e2e/watch/ (derived: checks/derived/watch/), compiled in :wear's
               tests, that extend Watch (checks/e2e/watch/Watch.kt: the real WearApp and MainActivity on a 227 dp round
               watch, API 36, under Robolectric with native graphics, with no phone in reach). freshInstall() is Main with
               nothing logged. The helpers keep the Phone names (tap, tapStarting(prefix) for Main's rows "Feed. …" and
               "Diaper. …", waitFor, waitUntil, waitUntilGone, shows, showsPart (spoken states too), canTap, assert…,
               clock, recentClocks, clockMinutesBetween, pass, screen, saveScreen) plus tapTheBackground(),
               swipeToDismiss() (the system back the swipe becomes on Wear OS 6), turnCrown(steps) (one 18 dp detent,
               clockwise later), scrollTo(text) and scrollBackTo(text) (the crown scrolls a list). The Tile:
               openTile(), tileShows, tileScreen, tapTile(text, or null for its background). The face:
               complication("Since last feed" | "Last diaper" | "Asleep or awake" | "Sleep and last feed") returns its
               text, title and spoken name one per line; tapComplication(name); ongoingActivity() (its status, or
               null); tapOngoingActivity(). pass() moves the wall clock, the uptime clock and the frame clock together,
               so Logged's five seconds and inert second last as long as on a watch. Past times are made through
               Logged's time with the shared steps in checks/e2e/watch/Watches.kt (logFood(), logPee(),
               logPlainDiaper(), startSleepOnTheWatch(), sleepStartedMinutesAgo(n), closeLogged(),
               waitOutLoggedInertStart()). A derived check's pictureTheScreen(name) is saveScreen(name), which lets the
               screen settle (the same pixels for two looks 100 ms apart, at most 1.5 s, the clocks moving as in pass)
               and saves the watch's screen.
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
  term Main          plain "the watch app's main screen: the title line, the Feed and Diaper rows and the sleep edge button"  formal the wear MainScreen
  term Logged        plain "the watch screen after every log: what was logged and when, Undo, and the time to move it"  formal LoggedScreen
  term "the Tile"    plain "the Pooparazzi tile in the watch's tile carousel"  formal PooparazziTileService
  term "the edge button" plain "the wide button pinned to the bottom edge of a watch screen"  formal EdgeButton
  term "the crown"   plain "the watch's rotating crown or bezel"  formal rotary input
  term widget        plain "a Pooparazzi panel on the phone's home screen"  formal a Glance app widget
  term shade         plain "the list of notifications pulled down from the top of the phone"  formal the notification shade
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
    feed.text=#F2B577
    pee.container=#13312C  pee.on.container=#BFE9E0  poop.on.container=#A8DCD1
    swatch.yellow=#E2B33C  swatch.green=#6F8F3A  swatch.brown=#8A5A2B  swatch.black=#2B2622  swatch.red=#B23A2E  swatch.pale=#ECE6D6
    radius.button=28dp  radius.sheet=28dp  radius.chip=20dp  radius.notice=28dp
    size.toggle=350x96dp  size.button=169x96dp  size.chip=40dp  size.target=48dp  gutter.phone=20dp  gutter.sheet=24dp
    type.headlineSmall=24sp/600  type.titleLarge=22sp/600  type.titleMedium=18sp/600  type.dataMedium=16sp/450  type.labelLarge=16sp/600
    watch.background=#000000  watch.surface.container=#1E1914  feed.lit=#4F351C  diaper.lit=#1A423B  diaper.text=#8FD0C3
    pee.strong=#8FD0C3  poop.container=#0E2622  poop.strong=#358276
    radius.watch.button=26dp  radius.watch.pill=16dp  radius.watch.chip=20dp  size.edge.medium=70dp  size.edge.small=56dp
    wear.titleLarge=20sp/600  wear.titleMedium=16sp/600  wear.titleSmall=14sp/600  wear.labelMedium=16sp/600
    wear.labelSmall=13sp/500  wear.bodyMedium=14sp/450
    color.surface.container=#1C1713  color.time.field=#26201A  color.outline.variant=#3F352C  color.ribbon.night=#080706  color.error=#F2A497
    day.background=#FBF6F0  day.text=#221A12  day.text.dim=#54473A  day.feed.disc=#FFE1C4  day.sleep.disc=#E1E4F8  day.sleep.track=#6A73B3
    type.heroNumeral=76sp/600  type.heroUnit=34sp/500  type.heroWord=48sp/500  type.displaySmall=48sp/500  type.dataSmall=14sp/500
    type.labelMedium=14sp/600  type.labelSmall=13sp/600  type.bodySmall=14sp/400
    type.bodyMedium=16sp/400
    color.dialog=#26201A  day.row=#F0E6DA  day.dialog=#EADDCF  day.error=#9C3328
    type.bodyLarge=18sp/400  type.titleSmall=16sp/600  radius.row=20dp  radius.row.inner=6dp  size.row=56dp
```

---

```ail
node Pooparazzi : system   level: product
  why   A parent alone with a baby at 3am needs to know when the baby last slept
        and ate, with one hand and without waking up properly.
  what
    1. One button starts a sleep, the same button ends it, and every tap can be taken back at once.   refined-by: Sleep
    2. A feed is logged with one tap on what the baby had, at the time Feed was tapped or nudged back to; the side order, the bottle's contents and amount are optional.   refined-by: Feeds
    3. A diaper change is logged with Pee, Poop, both or neither, at its time; a poop's colour and texture are optional, and worrying colours point to a doctor.   refined-by: Diapers
    4. A feed or sleep logged long after it happened can be put where the baby's past week expected it, with one tap that a second tap takes back.   refined-by: ForgotToLog
    5. Home shows at a glance the time since the last feed (or how long the baby has slept), since when the baby is awake, the last diaper, today's counts and what comes next, and each opens its entry to be fixed.   refined-by: HomeStatus
    6. History shows one day at a time, newest first, and any entry can be added later, fixed by the minute or deleted, with Undo.   refined-by: HistoryScreen
    7. Statistics shows the average day over a range of whole days, with a chart, the unusual days left out, and the typical day.   refined-by: StatisticsScreen
    8. The watch logs a feed, a diaper or a sleep from the wrist in a tap or two, without the phone, and its Tile, complications and Ongoing Activity show at a glance how long since each.   refined-by: Watch
    9. A new install asks for the baby's name and birth date and shows a short tour once, and both can be skipped.   refined-by: FirstRun
    10. Once the app is in real use, it asks once or twice for a rating on Google Play, never at night or over a sheet.   refined-by: ReviewPrompt
    11. Settings holds the babies, the look, the language, the watch, the notifications and what the app is.   refined-by: Settings
    12. Twins and siblings each have their own log; the phone shows and logs for one baby at a time, switched on Home.   refined-by: Babies
    13. No account is needed; an optional one backs the log up and shares it with a family.   refined-by: Account
    14. Home-screen widgets start and end a sleep in place, with Undo, and open the app's Feed and Diaper sheets, showing how long since each.   refined-by: Widgets
    15. A running sleep shows in a silent notification that ends it from the lock screen and then lets its end be moved; a feed or diaper that ends a sleep says so; and what comes next shows as notifications the parent can turn off.   refined-by: Notifications
  entry     Sleep
  children  Sleep, Feeds, Diapers, ForgotToLog, HomeStatus, HistoryScreen, StatisticsScreen, Watch, FirstRun, ReviewPrompt, Settings, Babies, Account, Widgets, Notifications
  sources
    README.md, "Pooparazzi", "Sleep" and "Watch"
    docs/design/DESIGN_SPEC.md §1, the core requirement in the owner's words: "register start and stop actions for
      sleeping to track start and stop times, and trigger time for feeding and diapers (pooping/peeing)"
```
