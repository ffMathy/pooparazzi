# Pooparazzi

A baby tracker for an Android phone and a Wear OS watch, made for one hand at 3am. Log **sleep**, **feeds** and **diapers** with one or two taps; only the time is ever required.

<p align="center">
  <img src="docs/screenshots/en/phone-home-night.png" width="220" alt="Phone home at night">
  &nbsp;
  <img src="docs/screenshots/en/phone-home-day.png" width="220" alt="Phone home by day">
  &nbsp;
  <img src="docs/screenshots/en/watch-main-idle.png" width="190" alt="Watch main screen">
</p>

All screenshots are rendered by the screenshot tests from the sample day in `core/.../sample/SampleData.kt` ([how](#screenshots)).

## Sleep

One button starts a sleep, the same button ends it.

| Asleep | Adjust the start | Undo receipt | Edit a sleep |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-asleep.png" width="200" alt="Home while asleep"> | <img src="docs/screenshots/en/phone-home-adjust-start.png" width="200" alt="Adjust start sheet while asleep: 01:50, was 02:05, with −15m, −10m, −5m and +5m, Edit and Save"> | <img src="docs/screenshots/en/phone-home-receipt.png" width="200" alt="Receipt after Start sleep: Sleep started · 14:32 with Undo, offset chips, Edit and Done"> | <img src="docs/screenshots/en/phone-edit-sleep.png" width="200" alt="Edit sleep: fell asleep 12:35, was 12:40, woke up 13:27, with −5m, −1m, +1m and +5m, Duration 52m, was 47m"> |

## Feeds

Breast (with the order of the sides), Bottle (milk or formula, with an amount), Food, or just the time. −15m / −10m / −5m nudge the time back.

| Feed sheet | Breast: side order | Bottle | Logged |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-feed-sheet.png" width="200" alt="Feed sheet: Breast, Bottle, Food, Just the time"> | <img src="docs/screenshots/en/phone-breast-sheet.png" width="200" alt="Feed sheet after Breast, with Right then Left ticked"> | <img src="docs/screenshots/en/phone-bottle-sheet.png" width="200" alt="Feed sheet after Bottle, with the optional Milk or Formula choice and amount slider at 120 ml (or 4 fl oz where ounces are used)"> | <img src="docs/screenshots/en/phone-home-breast-logged.png" width="200" alt="Home right after logging a breast feed backdated by 15 minutes: Last fed 18m ago · 14:32 · Left → Right, with no receipt sheet"> |

## Forgot to log?

When a feed or sleep is logged long after it happened, its sheet offers **Expected** — the time the last 7 days expected it — under the nudge chips. A second tap goes back.

| Forgotten feed | Forgotten sleep |
|:---:|:---:|
| <img src="docs/screenshots/en/phone-feed-sheet-expected.png" width="200" alt="Feed sheet opened at 18:40 with Expected 15:25 picked: Feed, 15:25 · was 18:40"> | <img src="docs/screenshots/en/phone-home-receipt-expected.png" width="200" alt="Receipt after Start sleep at 18:20, offering Expected 15:15 under the offset chips"> |

## Diapers

Pee and Poop, with an optional colour and texture. Red, black and pale poop shows a gentle hint.

| Diaper sheet | Doctor hint | Backdated | While asleep |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-diaper-sheet.png" width="200" alt="Diaper sheet with poop colour and texture"> | <img src="docs/screenshots/en/phone-diaper-sheet-hint.png" width="200" alt="Diaper sheet with Poop and Pale ticked: pale, red or black poop can be a reason to call your doctor or health visitor"> | <img src="docs/screenshots/en/phone-diaper-sheet-nudged.png" width="200" alt="Diaper sheet after tapping −5m twice: Diaper, 14:22 · was 14:32"> | <img src="docs/screenshots/en/phone-diaper-sheet-asleep.png" width="200" alt="Diaper sheet at 03:12 while asleep, Pee ticked: Woke up will be logged too"> |

## What comes next

Home shows what the last 7 days expect next, also as silent notifications.

| Next feed, sleep, poop | Due now | Notifications | Which ones |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-day.png" width="200" alt="Home, Day theme"> | <img src="docs/screenshots/en/phone-home-forecast-passed.png" width="200" alt="Home at 15:55 with nothing logged since: Feed 24m ago, Start sleep 36m ago"> | <img src="docs/screenshots/en/phone-notification-forecasts.png" width="200" alt="Prediction notifications, one per kind: Next feed in 1h 05m, Next sleep in 50m, Expected to wake in 40m and Next poop soon, each with Around and a clock time"> | <img src="docs/screenshots/en/phone-settings-predictions.png" width="200" alt="Settings: On the watch face with Next feed and Next sleep and wake-up ticked and Next poop not, then Notifications with What comes next: Next feed On, Next poop Off, Next sleep and wake-up On"> |

## History and edits

| History | History, Night | Edit a feed | Edit a diaper |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-history.png" width="200" alt="History with day ribbon"> | <img src="docs/screenshots/en/phone-history-night.png" width="200" alt="History at night"> | <img src="docs/screenshots/en/phone-edit-feed.png" width="200" alt="Edit a breastfeed"> | <img src="docs/screenshots/en/phone-edit-diaper.png" width="200" alt="Edit a diaper"> |

| Edit a bottle | Add a past entry | Feed while asleep |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-edit-bottle.png" width="200" alt="Edit a bottle feed with its optional contents and amount"> | <img src="docs/screenshots/en/phone-add-past-entry.png" width="200" alt="Add a past entry"> | <img src="docs/screenshots/en/phone-feed-sheet-asleep.png" width="200" alt="Feed sheet at 03:12 while asleep since 02:05: Woke up will be logged too, under the nudge chips"> |

## Statistics

Averages per day, a bar chart, and the typical day. Unusual days are greyed out and left out.

| Averages | A bar pressed | An unusual day | Typical day |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-statistics.png" width="200" alt="Statistics: averages per day for the last 30 days, with the poop chart open"> | <img src="docs/screenshots/en/phone-statistics-bar.png" width="200" alt="Statistics with the highest poop bar pressed: the other bars fade, a guide marks its day, and its date and value show under it in place of the first and last dates"> | <img src="docs/screenshots/en/phone-statistics-unusual.png" width="200" alt="Statistics with Feeds open: a half-logged day's bar is grey and picked, reading Sat 10 Oct, 1, Unusual; the legend has Unusual day next to a grey bar, and a note says grey bars are days unlike the others, left out of the average"> | <img src="docs/screenshots/en/phone-statistics-typical-day.png" width="200" alt="Statistics scrolled to the end: the Typical day card for the last 30 days, with sleep, feed and diaper lanes by half hour and the usual time between feeds and diapers, sleep length and time awake, by day and by night"> |

## Widgets and notifications

A feed or diaper logged during a sleep ends it, and the phone says so in a notification, wherever it was logged.

| Widget 4×2 | Widget 3×1 | Samsung flip cover | Running sleep |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-widget-4x2.png" width="200" alt="4x2 log widget in its states"> | <img src="docs/screenshots/en/phone-widget-3x1.png" width="200" alt="3x1 quick widget in its sizes"> | <img src="docs/screenshots/en/phone-widget-cover.png" width="200" alt="The 4x2 widget on a Samsung flip's cover screen, idle and asleep"> | <img src="docs/screenshots/en/phone-notifications.png" width="200" alt="The running-sleep notification with its duration, last feed, last diaper and today's counts, and the woke-up notification with its earlier offsets"> |

| Woken by a feed |
|:---:|
| <img src="docs/screenshots/en/phone-notification-woke-by-feed.png" width="200" alt="Notification: Noa woke up at 03:12, The feed ended the sleep, Slept 1h 07m · 02:05 – 03:12, with −5m, −10m and −15m"> |

## Watch

Logs offline and syncs with the phone. A Tile, four complications and an Ongoing Activity show what comes next.

| Main | Asleep | Tile | Complications |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-main-idle.png" width="170" alt="Watch main, idle, as the Tile: the feed icon with 2h 10m since and 1h 00m to the next; the diaper icon with 48m since; a large Start sleep button, awake 1h 05m, next sleep in 45m"> | <img src="docs/screenshots/en/watch-main-asleep.png" width="170" alt="Watch main, asleep: Asleep 1h 07m, Feed with Next in 2h 45m, Diaper, Woke up with asleep 1h 07m and wake-up in 2h 15m"> | <img src="docs/screenshots/en/watch-tile.png" width="170" alt="Watch tile: Feed and Diaper rows, and Start sleep with awake 1h 05m and next sleep in 45m"> | <img src="docs/screenshots/en/watch-complications.png" width="170" alt="Watch complications preview"> |

| Feed type | Side | Bottle | Bottle amount |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-feed-type.png" width="170" alt="Watch feed type"> | <img src="docs/screenshots/en/watch-side.png" width="170" alt="Watch side order after Breast, with Save"> | <img src="docs/screenshots/en/watch-bottle.png" width="170" alt="Watch Bottle screen as it opens: Bottle · 14:32, Milk and Formula, and Save"> | <img src="docs/screenshots/en/watch-bottle-amount.png" width="170" alt="Watch Bottle screen scrolled to the amount: − 4 fl oz +, and the chips 3, 4 and 5 fl oz with 4 chosen (120 ml where millilitres are used)"> |

| Diaper | Colour | Texture | Logged + Undo |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-diaper.png" width="170" alt="Watch Diaper screen as it opens: Diaper · 14:32, Pee and Poop tiles, and Save"> | <img src="docs/screenshots/en/watch-diaper-options.png" width="170" alt="Watch Diaper screen with Pee and Poop ticked, scrolled to the colour chips: Yellow chosen with a check on its swatch, Green, Brown, and more below"> | <img src="docs/screenshots/en/watch-diaper-texture.png" width="170" alt="Watch Diaper screen scrolled to the texture chips: Seedy chosen, Watery, Mushy, and Pale chosen among the colours above"> | <img src="docs/screenshots/en/watch-logged.png" width="170" alt="Watch logged breast feed with its side order and Undo"> |

| Move the time | Woke up too | Logged bottle | Tile, asleep |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-time.png" width="170" alt="Watch Time screen, opened from Logged for every log: Feed · −7m over 14:25, −5m and +5m buttons and Done; the crown moves the time a minute at a time"> | <img src="docs/screenshots/en/watch-logged-woke-up.png" width="170" alt="Watch logged breast feed at 03:12 during a sleep: Left → Right, then Woke up too, and Undo"> | <img src="docs/screenshots/en/watch-logged-bottle.png" width="170" alt="Watch logged bottle feed: Bottle at 14:32, Formula · 4 fl oz, and Undo"> | <img src="docs/screenshots/en/watch-tile-asleep.png" width="170" alt="Watch tile while asleep"> |

| After a breast feed | Large font | Small watch | Complications, asleep |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-main-after-breast.png" width="170" alt="Watch main after a breast feed"> | <img src="docs/screenshots/en/watch-main-large-font.png" width="170" alt="Watch main at a large font size"> | <img src="docs/screenshots/en/watch-main-small.png" width="170" alt="Watch main on a small watch"> | <img src="docs/screenshots/en/watch-complications-asleep.png" width="170" alt="Watch complications while asleep: 1h 32m since the feed, 1h 20m since the pee, asleep 1h 07m, and Asleep 1h 07m · Fed 1h 32m ago"> |

| Undone | Diaper while asleep | Sleep started | Move the sleep's start |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-undone.png" width="170" alt="Watch undone"> | <img src="docs/screenshots/en/watch-logged-diaper-woke-up.png" width="170" alt="Watch logged pee at 03:12 during a sleep: Woke up too, and Undo"> | <img src="docs/screenshots/en/watch-logged-sleep-started.png" width="170" alt="Watch Logged after Start sleep: Asleep at 14:32 on a tonal pill with a clock, and Undo"> | <img src="docs/screenshots/en/watch-time-sleep-started.png" width="170" alt="Watch Time screen for a sleep's start: Asleep · −10m over 14:22, −5m and +5m buttons and Done"> |

## Several babies

Twins or siblings: each baby has its own log, sleep and notification.

| Home, two babies | Switch baby | Babies | Widgets |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-twins.png" width="200" alt="Home with two babies: Noa with a chevron is the switcher, the watch and cloud status icons under it"> | <img src="docs/screenshots/en/phone-switch-baby.png" width="200" alt="Switch baby sheet with Noa selected and Ida"> | <img src="docs/screenshots/en/phone-settings-babies.png" width="200" alt="Settings, Babies: name fields for Noa, Ida and an unnamed third, each with a remove button, Add a baby and the names hint"> | <img src="docs/screenshots/en/phone-widget-twins.png" width="200" alt="4x2 widgets with the Noa baby switcher at the top, and the 3x1 with its N baby button"> |

| Notifications | History | Add a baby | Birth date |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-notification-twins.png" width="200" alt="One sleep notification per baby: Noa is asleep and Ida is asleep, each with Woke up"> | <img src="docs/screenshots/en/phone-history-twins.png" width="200" alt="History labelled Noa under the title"> | <img src="docs/screenshots/en/phone-settings-add-baby.png" width="200" alt="Add a baby: the name field, Add birth date, and that the birth date is optional but improves the predictions and estimates, which then follow the baby's age; Cancel and Add"> | <img src="docs/screenshots/en/phone-settings-birth-date.png" width="200" alt="Settings, Baby: the date picker for the birth date, 14 August 2026 picked, with Remove, Cancel and OK"> |

| Watch | Watch picker | Tile, second baby |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-main-twins.png" width="170" alt="Watch main with two babies: Noa in the time text and the baby button as the title"> | <img src="docs/screenshots/en/watch-baby-picker.png" width="170" alt="Watch Switch baby picker with Noa selected and Ida"> | <img src="docs/screenshots/en/watch-tile-second-baby.png" width="170" alt="Watch tile for the second, unnamed baby: Baby 2"> |

## Backup and families (optional)

No account needed. Sign in with Google or an email link to back up, and invite a partner or grandparent. Setup: [docs/BACKEND.md](docs/BACKEND.md).

| Welcome | Sign in | Check your email | Signed in |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-welcome.png" width="200" alt="First-run Welcome: No account needed. Two cards: Only on this phone, with switching to cloud backup later in Settings and Continue without an account; then Back up to the cloud with Continue with Google and the email sign-in link"> | <img src="docs/screenshots/en/phone-account.png" width="200" alt="Account, signed out: Continue with Google, then or use email with Email me a sign-in link; no passwords"> | <img src="docs/screenshots/en/phone-account-link-sent.png" width="200" alt="Account: Check your email, the sign-in link went to parent@example.com, Send again and Use another email"> | <img src="docs/screenshots/en/phone-account-signed-in.png" width="200" alt="Account, signed in: the address, Backed up 14:31, Back up now, Sign out and Delete account"> |

| Consent | Family | Join a family | Sync status |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-consent.png" width="200" alt="Back up to Google Firebase? What is uploaded, who sees it, where, and changing your mind; I agree and Not now, sign out"> | <img src="docs/screenshots/en/phone-settings-family.png" width="200" alt="Settings, Family: you started the family, a partner and a grandparent with Remove, Invite someone and Leave family"> | <img src="docs/screenshots/en/phone-join-family.png" width="200" alt="Join partner@example.com's family? Your babies and log are added and shared; Join and Not now"> | <img src="docs/screenshots/en/phone-home-sync-errors.png" width="200" alt="Home with the watch and cloud sync icons under the name"> |

## Privacy

No ads, no analytics, no tracking. [Privacy policy](docs/PRIVACY.md) · [records of processing](docs/privacy).

| About | Delete account | Remove a member | Remove a baby |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-settings-about.png" width="200" alt="Settings, About: the version, Privacy policy, Download my data, Import data and Font licences, then the footer: Made with ❤️ by a father in Aarhus, Denmark, Pooparazzi is open source, and GitHub and LinkedIn logo buttons"> | <img src="docs/screenshots/en/phone-account-delete-link.png" width="200" alt="Delete account? This link deletes your account. Tap Delete to go ahead; Cancel and Delete"> | <img src="docs/screenshots/en/phone-family-remove.png" width="200" alt="Remove partner@example.com? They'll stop seeing the family's log from now on; what's already on their phone stays there"> | <img src="docs/screenshots/en/phone-settings-remove-baby.png" width="200" alt="Remove Baby 3? Their log is deleted for everyone in your family and from your backup; Cancel and Remove"> |

## First run

| Your baby | Introduction | Sleep | Feeds and diapers |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-baby-intro.png" width="200" alt="Your baby: both are optional, skip them now and add them later in Settings. The name field, Add birth date, why the birth date improves the predictions and estimates, Continue and Skip for now"> | <img src="docs/screenshots/en/phone-intro-welcome.png" width="200" alt="Introduction, page 1 of 6: Welcome to Pooparazzi. Log sleep, feeds and diapers with one or two taps, made for one hand at 3am. Skip at the top, Next at the bottom"> | <img src="docs/screenshots/en/phone-intro-sleep.png" width="200" alt="Introduction, page 2 of 6, Night: Sleep. Tap Start sleep when the baby falls asleep, and the same button, now Woke up, when they wake"> | <img src="docs/screenshots/en/phone-intro-log.png" width="200" alt="Introduction, page 3 of 6: Feeds and diapers. Only the time is needed; −15m, −10m and −5m move the time back"> |

| Fix anything | What comes next | Always at hand | Review prompt |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-intro-fix.png" width="200" alt="Introduction, page 4 of 6, Night: Fix anything. Tap Undo right after a sleep, or tap a time on Home or a row in History to edit or delete it"> | <img src="docs/screenshots/en/phone-intro-patterns.png" width="200" alt="Introduction, page 5 of 6: What comes next. Home's buttons show what the last 7 days expect; History and Statistics"> | <img src="docs/screenshots/en/phone-intro-everywhere.png" width="200" alt="Introduction, last page, Night: Always at hand. Widgets, the Wear OS watch, the family, and Settings › About to see it again; Get started"> | <img src="docs/screenshots/en/phone-review-prompt.png" width="200" alt="Enjoying Pooparazzi? over Home by day, after a few days of logging: a quick rating on Google Play helps other tired parents find it; Rate on Google Play, Not now and Don't ask again"> |

## Settings

Night and Day themes, English and Danish, and a watch install shortcut.

| Night theme | Settings | Install on the watch | No watch yet |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-statistics-night.png" width="200" alt="Statistics at night"> | <img src="docs/screenshots/en/phone-settings.png" width="200" alt="Settings: Account, Baby with Add a baby, Appearance and Language"> | <img src="docs/screenshots/en/phone-settings-watch-install.png" width="200" alt="Settings, Watch: Install on your watch. Pooparazzi isn't on your watch yet. Opens Google Play on the watch"> | <img src="docs/screenshots/en/phone-settings-no-watch.png" width="200" alt="Settings, Watch: No watch yet. Pooparazzi has a Wear OS watch app too. If you get a watch, log with a tap from your wrist; it syncs with this phone"> |

Made with ❤️ by a father in Aarhus, Denmark.

## Project structure

| Module | What it holds |
|---|---|
| [`core/`](core) | Android library shared by both apps: the `Entry` model and validation, the Room database and repository, the Data Layer sync (`sync/`), the backup sync engine (`cloud/`), settings and babies, summaries, statistics and the Lamp state (`domain/`), text formatting, the design tokens, type and icons, and the sample data used by previews and screenshots. |
| [`app/`](app) | The phone app (Compose, Material 3): Home, the Feed (with its Breast step) / Diaper sheets, History, Statistics, Edit, Settings, Welcome and Account (`account/`: Firebase Authentication and the Firestore backup), the Glance widgets (`widget/`) and the running-sleep notifications (`notify/`). |
| [`wear/`](wear) | The Wear OS app (Compose for Wear OS, Material 3): Main (which mirrors the Tile), Feed type, Side, Bottle, Diaper and Logged (with Time), plus the Tile (`tile/`), complications (`complication/`) and the Ongoing Activity (`ongoing/`). |
| [`docs/design/`](docs/design) | The design: [`DESIGN_SPEC.md`](docs/design/DESIGN_SPEC.md) (the Night and Day themes: screens, tokens, icons, accessibility) and [`V1_ADDITIONS.md`](docs/design/V1_ADDITIONS.md) (feed types, tick-ordered sides, optional diaper details, English + Danish) and [`BABIES_AND_BACKUP.md`](docs/design/BABIES_AND_BACKUP.md) (several babies, the optional account, backup and families); each later one wins where they differ. |
| [`firebase/`](firebase) | The backend: Firestore security rules, the retention job (`retention/`), their emulator tests, the Hosting website (`hosting/`: App Links file, invite and sign-in landing pages, privacy policy) and the deploy configuration ([docs/BACKEND.md](docs/BACKEND.md)). |
| [`scripts/`](scripts) | `readme_screenshots.py`, which builds the README images. |
| [`third_party/fonts/`](third_party/fonts) | Licences for the bundled fonts (Atkinson Hyperlegible Next and Google Sans Flex, both SIL OFL 1.1). |

## Build

Requirements: **JDK 21** (to run Gradle) and the **Android SDK with platform 37** (`compileSdk 37`; the apps target 36 and run on Android 11 / Wear OS 3 and later, `minSdk 30`). Point Gradle at the SDK with `ANDROID_HOME` or `sdk.dir` in `local.properties`.

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug :wear:assembleDebug
```

The APKs land in `app/build/outputs/apk/debug/app-debug.apk` and `wear/build/outputs/apk/debug/wear-debug.apk`.

Accounts and the backup need the Firebase project's `google-services.json` at `app/google-services.json` ([docs/BACKEND.md](docs/BACKEND.md#6-connect-the-app)). Keep it there locally (git ignores it); release builds get it from the `GOOGLE_SERVICES_JSON` repository secret. Without the file the phone app builds and runs as before, with accounts turned off.

## Install on devices

Both apps use the **same applicationId, `com.ffmathy.pooparazzi`**, and must be signed with the same key: the Wearable Data Layer only pairs a phone app and a watch app that match. Debug builds from one machine share its debug key; local release builds fall back to it too unless an upload key is configured (see [Releasing](docs/RELEASING.md)).

```sh
# Phone (USB or wireless debugging)
adb devices
adb -s <phone-serial> install -r app/build/outputs/apk/debug/app-debug.apk

# Watch: enable Developer options › ADB debugging (and Wireless debugging) on the watch, then
adb connect <watch-ip>:<port>        # or: adb pair <watch-ip>:<pair-port> first
adb -s <watch-ip>:<port> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

Open the phone app once and set the baby's name in Settings › Babies (and add more babies there); the watch picks them up (and the language) over the Data Layer. Add the Tile from the watch's tile carousel and the complications from a watch face's edit screen.

## Tests

```sh
./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :wear:testDebugUnitTest
```

Everything runs on the JVM with Robolectric (no emulator needed): view models, the repository and sync merge, formatting in both languages, TalkBack descriptions, navigation, widgets, the Tile layout and complications, and smoke tests of the apps' entry points. Statistics has plain JUnit tests of its rules in `core` (which days count, rounding, ranges, DST days and chart bins), plus view-model, TalkBack (English and Danish), navigation and screenshot tests in `app`.

The backend's Firestore security rules and the retention job have their own tests on the Firestore and Authentication emulators (Bun, Node.js 22 for the Firebase CLI, and JDK 21):

```sh
cd firebase && bun install && bun run test
```

Checks on the Android sources that no Gradle build catches (for example that both apps keep the Wear capabilities through release resource shrinking) are Bun tests in `tests/`:

```sh
bun test ./tests
```

## Screenshots

The screenshot tests (Roborazzi + Robolectric) render every screen in English and Danish into `app/screenshots/{en,da}/` and `wear/screenshots/{en,da}/`. They are rendered on the JVM, not taken from a device, so fonts and shadows can differ slightly from a real phone or watch.

```sh
# Re-record all screenshots
./gradlew :app:recordRoborazziDebug :wear:recordRoborazziDebug

# Rebuild the README images in docs/screenshots/ (needs Pillow: pip install Pillow)
python3 scripts/readme_screenshots.py
```

`readme_screenshots.py` takes a curated subset, scales phone shots to 360 px wide and masks watch shots to a 300 px circle. Edit its `PHONE` and `WATCH` maps to change the set.

On pull requests, the [`screenshots.yml`](.github/workflows/screenshots.yml) workflow does both on CI's Android SDK and pushes the result to the branch when anything changed, so the screenshots always show the UI being merged.

## Releasing

Every merge to `main` builds signed app bundles and APKs for the phone and the watch, attaches both APKs to a [GitHub Release](../../releases) for side-loading under a new version tag (a patch bump, or minor / major with the pull request's `release: minor` / `release: major` label), and uploads the bundles to Google Play (the production track by default) with the [`release.yml`](.github/workflows/release.yml) workflow; pull requests run the unit tests and a debug build ([`ci.yml`](.github/workflows/ci.yml)). The Google Play store images in `docs/play/` are rebuilt from the screenshots on every pull request and uploaded to the store listing when a merge changes them ([`store-listing.yml`](.github/workflows/store-listing.yml)). The secrets, the upload key, the Play service account and the one-time manual steps are in [docs/RELEASING.md](docs/RELEASING.md).

## Licences

The fonts bundled in the apps are licensed under the SIL Open Font License 1.1; their licence texts are in [`third_party/fonts/`](third_party/fonts) and are also shown in the phone app under Settings › Licences.
