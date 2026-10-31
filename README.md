# Pooparazzi

**Pooparazzi** is a baby tracker for an Android phone and a Wear OS watch, built for one-handed use at 3am. It logs **sleep**, **feeds** and **diapers** with one or two taps, keeps the phone and the watch in sync, and shows "how long since…" at a glance on the home screen, the lock screen, a watch tile and watch-face complications.

Only the time is ever required: the feed type, the breast side, pee/poop and the poop's colour and texture are optional extras.

<p align="center">
  <img src="docs/screenshots/en/phone-home-night.png" width="240" alt="Phone home at night">
  &nbsp;&nbsp;
  <img src="docs/screenshots/en/watch-main-idle.png" width="200" alt="Watch main screen">
</p>

## Features

- **Sleep**: one button starts a sleep and the same button ("Woke up") ends it. The running sleep is shown as a ring with its length; the start can be adjusted while it runs.
- **Feeds**: Breast, Formula or Food, or **just the time**. Log it now or a little earlier: −15m / −10m / −5m / +5m nudge the time and add up, with "was 14:32" beside it.
- **Breast feeds with side order**: a breast feed is logged like any feed, at one time (no timer, no duration). After Breast, tick **Left** and/or **Right**: the order you tick is the order recorded ("Right → Left"). The side is optional; "Log feed" (watch: "Save") with nothing ticked records just the time. Wherever the last feed is shown (phone Home, the widget, the watch), its order is shown too ("Left → Right", or "L → R" where space is tight), so you know which side to start on next.
- **Formula with an amount**: after Formula (on the phone), an optional slider sets how much was given, in **ml** (10 ml steps up to 300 ml), or in **fl oz** (½ oz steps up to 10 oz) where the region measures in ounces (the US, Liberia, Myanmar). Leaving it at "No amount" records just the time. The amount shows on Home's Lamp, in History ("12:22 · 120 ml"), on the watch's Today list, and can be changed in the edit sheet.
- **Diapers**: Pee and Poop are independent checkboxes, both optional (nothing ticked is a plain change). With Poop on, pick an optional **colour** (yellow, green, brown, black, red, pale) and **texture** (watery, seedy, mushy, formed, hard); red, black and pale show a gentle "talk to your doctor" hint.
- **History, edit and undo**: a day view with a 24-hour ribbon and day totals; tap any entry to edit its times, type, side, formula amount or diaper details, or delete it; on Home, tap the big "Last fed" time (or a since-row) to edit that entry, or the running sleep to adjust its start; add a past entry; on the phone a Feed or Diaper sheet already sets the time, so it simply closes when the log is saved (tap the since-row to edit or delete it); Start sleep, Woke up, a widget log and a delete show an **Undo** receipt: a sheet with the time, Undo, chips to move it by a few minutes, Edit and Done (no timeout); on the watch a Logged screen with Undo.
- **What comes next**: Home's buttons show what this baby's last 7 days expect next: under Feed "in 1h 05m", under Start sleep when the next sleep is due and, while asleep, under Woke up when the baby is expected to wake. Under Diaper it shows the next poop when poop has been regular enough to predict. Each estimate is the median of the past week's comparable stretches (sleeps, wake windows, gaps between feeds or poops) that began at about the same time of day and lasted longer than the current one so far. With fewer than 5 such stretches, or too much spread, no estimate is shown. The 4×2 widget shows them too, as clock times: "Next ~15:35" under Feed, "Poop ~…" under Diaper when predictable, and "Next sleep ~15:25" or "Wakes ~05:25" under the sleep status. On the watch, scrolled: a "Next feed in 1h 05m" card (or a line on the Last fed card while asleep), "Next sleep in 55m" on the Awake card and "Wakes ~05:25" under Adjust start.
- **Statistics**: from History's chart button, average feeds, pee, poop and sleep per day for the period you pick: the last 30 days, or the last 7 or 90 days, all days, or dates you choose, with a bar chart of the period. Only whole days up to yesterday count (until the first whole day, today so far, so a single entry already shows), and days when nothing was logged are left out instead of counting as zero. With several babies it shows the current baby's.
- **Several babies** (twins, siblings): add, rename and remove them in Settings › Babies (removing a baby deletes its log, after asking), and switch the baby you log for from Home's top bar or the watch's baby button. Each device has its own current baby; a sleep runs per baby, and every sleeping baby keeps its notification.
- **Phone ↔ watch sync** over the Wearable **Data Layer**. Both apps log offline and merge by last-writer-wins; the babies' names and the language follow the phone. The sync state is a small icon, not text: a watch (and, while signed in, a cloud for the backup) under the name on the phone, a phone on the watch, each marked with a tick, "…", a slash or "!" (tap "!" to retry); TalkBack reads the full status.
- **Optional backup** to a small Firebase backend (free Spark plan, no Cloud Functions): sign in at any time, with Google or a sign-in link sent by email (no passwords), and everything logged so far is backed up; sign in on a new phone to restore it, babies and names included. **No account is needed**: the first-run Welcome offers "Only on this phone" as an equal choice beside the backup, and you can switch to the cloud later in Settings › Account; everything logged until then comes along. Setup: [docs/BACKEND.md](docs/BACKEND.md).
- **Families**: invite a partner or a grandparent with a single-use link (valid 6 days) from Settings › Family. Everyone in the family sees the same babies, with their names, and every member's log; joining adds your own log to the family's. Each phone keeps its own current baby. Leave at any time; the family's creator can remove members (and hands the family to the longest-standing member on leaving). Nothing is uploaded until you agree on a consent screen after signing in.
- **Privacy**: no ads, no analytics, no tracking. Names and the log are shared only with your family's members. The [privacy policy](docs/PRIVACY.md) is also published on the app's website and linked from Settings › About, beside **Download my data** (the log and the babies' names as a ZIP file of JSON files) and **Import data** (merges such a file back into the log, newest version of each entry winning). Deleted entries are backed up without what was logged, and a weekly job deletes accounts unused for 2 years. The records of processing, the impact assessment and the breach plan are in [docs/privacy/](docs/privacy).
- **Glanceable surfaces**:
  - Phone: two **Glance widgets** (a 4×2 log widget and a 3×1 quick widget, both with Feed, Diaper and Sleep; Feed and Diaper open the same sheets as in the app, the sleep toggle works in place), the 4×2 also on the **cover screen of Samsung flip phones**, and an **ongoing notification** for a running sleep (promoted to a Live Update where Android supports it) showing how long it has run, the last feed and diaper, and today's counts. "Woke up" on it leaves a notification with −5m / −10m / −15m to move the wake-up time.
  - Watch: a **Tile** (Feed and Diaper open the watch app's own screens, the sleep toggle works in place), four **complications** (since last feed, last diaper, asleep/awake, sleep and last feed) and an **Ongoing Activity** while a sleep runs.
- **Night Light design**: a dim, warm Night theme for the dark hours and a Day theme, chosen automatically (or fixed in Settings). Large targets, TalkBack descriptions in full words ("2 hours 10 minutes ago"), large-font and small-watch layouts.
- **English and Danish**, with an in-app language choice (Settings › Language) that the widgets, notifications and the watch follow.

## Screenshots

All screenshots are rendered by the Roborazzi screenshot tests under Robolectric (see [Screenshots](#screenshots-1) below), not captured from a device, and use the sample day in `core/.../sample/SampleData.kt`.

### Phone

| Home, Night | Home, Day | Asleep at night | Breast feed logged |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-night.png" width="200" alt="Home, Night theme"> | <img src="docs/screenshots/en/phone-home-day.png" width="200" alt="Home, Day theme"> | <img src="docs/screenshots/en/phone-home-asleep.png" width="200" alt="Home while asleep"> | <img src="docs/screenshots/en/phone-home-breast-logged.png" width="200" alt="Home right after logging a breast feed backdated by 15 minutes: Last fed 18m ago · 14:32 · Left → Right, with no receipt sheet"> |

| Forecast passed |
|:---:|
| <img src="docs/screenshots/en/phone-home-forecast-passed.png" width="200" alt="Home at 15:55 with nothing logged since: Feed 24m ago, Start sleep 36m ago"> |

| Receipt | Feed sheet | Breast: side order | Diaper sheet |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-receipt.png" width="200" alt="Receipt after Start sleep: Sleep started · 14:32 with Undo, offset chips, Edit and Done"> | <img src="docs/screenshots/en/phone-feed-sheet.png" width="200" alt="Feed sheet: Breast, Formula, Food, Just the time"> | <img src="docs/screenshots/en/phone-breast-sheet.png" width="200" alt="Feed sheet after Breast, with Right then Left ticked"> | <img src="docs/screenshots/en/phone-diaper-sheet.png" width="200" alt="Diaper sheet with poop colour and texture"> |

| Formula: amount | Edit formula feed | Diaper, backdated |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-formula-sheet.png" width="200" alt="Feed sheet after Formula, with the optional amount slider at 120 ml (or 4 fl oz where ounces are used)"> | <img src="docs/screenshots/en/phone-edit-formula.png" width="200" alt="Edit a formula feed with its optional amount"> | <img src="docs/screenshots/en/phone-diaper-sheet-nudged.png" width="200" alt="Diaper sheet after tapping −5m twice: Diaper · 14:22, was 14:32"> |

| History | History, Night | Edit feed | Edit diaper |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-history.png" width="200" alt="History with day ribbon"> | <img src="docs/screenshots/en/phone-history-night.png" width="200" alt="History at night"> | <img src="docs/screenshots/en/phone-edit-feed.png" width="200" alt="Edit a breastfeed"> | <img src="docs/screenshots/en/phone-edit-diaper.png" width="200" alt="Edit a diaper"> |

| Add past entry | Settings | Widget 4×2 | Widget 3×1 |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-add-past-entry.png" width="200" alt="Add a past entry"> | <img src="docs/screenshots/en/phone-settings.png" width="200" alt="Settings: Account, Baby with Add a baby, Appearance and Language"> | <img src="docs/screenshots/en/phone-widget-4x2.png" width="200" alt="4x2 log widget in its states"> | <img src="docs/screenshots/en/phone-widget-3x1.png" width="200" alt="3x1 quick widget in its sizes"> |

| Adjust start |
|:---:|
| <img src="docs/screenshots/en/phone-adjust-start.png" width="200" alt="Adjust start sheet for the running sleep"> |

| Cover screen (Samsung flip) | Sleep notifications | Statistics | Statistics, Night |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-widget-cover.png" width="200" alt="The 4x2 widget on a Samsung flip's cover screen, idle and asleep"> | <img src="docs/screenshots/en/phone-notifications.png" width="200" alt="The running-sleep notification with its duration, last feed, last diaper and today's counts, and the woke-up notification with its earlier offsets"> | <img src="docs/screenshots/en/phone-statistics.png" width="200" alt="Statistics: averages per day for the last 30 days, with the poop chart open"> | <img src="docs/screenshots/en/phone-statistics-night.png" width="200" alt="Statistics at night"> |

#### Several babies and backup

| Welcome (first run) | Account, signed out | Account, signed in | Settings, babies |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-welcome.png" width="200" alt="First-run Welcome: No account needed. Two cards: Only on this phone, with switching to cloud backup later in Settings and Continue without an account; then Back up to the cloud with Continue with Google and the email sign-in link"> | <img src="docs/screenshots/en/phone-account.png" width="200" alt="Account, signed out: Continue with Google, then or use email with Email me a sign-in link; no passwords"> | <img src="docs/screenshots/en/phone-account-signed-in.png" width="200" alt="Account, signed in: the address, Backed up 14:31, Back up now, Sign out and Delete account"> | <img src="docs/screenshots/en/phone-settings-babies.png" width="200" alt="Settings, Babies: name fields for Noa, Ida and an unnamed third, each with a remove button, Add a baby and the names hint"> |

| Home, two babies | Switch baby | History, two babies | Widgets, two babies |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-twins.png" width="200" alt="Home with two babies: Noa with a chevron is the switcher, the watch and cloud status icons under it"> | <img src="docs/screenshots/en/phone-switch-baby.png" width="200" alt="Switch baby sheet with Noa selected and Ida"> | <img src="docs/screenshots/en/phone-history-twins.png" width="200" alt="History labelled Noa under the title"> | <img src="docs/screenshots/en/phone-widget-twins.png" width="200" alt="4x2 widgets with Noa at the start of the status row, and the 3x1"> |

| Sleep notifications, two babies | Statistics, two babies | Removing a baby |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-notification-twins.png" width="200" alt="One sleep notification per baby: Noa is asleep and Ida is asleep, each with Woke up"> | <img src="docs/screenshots/en/phone-statistics-twins.png" width="200" alt="Statistics labelled Noa under the title: only Noa's log counts"> | <img src="docs/screenshots/en/phone-settings-remove-baby.png" width="200" alt="Remove Baby 3? Their log is deleted for everyone in your family and from your backup; Cancel and Remove"> |

#### Families and passwordless sign-in

| Check your email | Settings, family | Joining a family |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-account-link-sent.png" width="200" alt="Account: Check your email, the sign-in link went to parent@example.com, Send again and Use another email"> | <img src="docs/screenshots/en/phone-settings-family.png" width="200" alt="Settings, Family: you started the family, a partner and a grandparent with Remove, Invite someone and Leave family"> | <img src="docs/screenshots/en/phone-join-family.png" width="200" alt="Join partner@example.com's family? Your babies and log are added and shared; Join and Not now"> |

| Consent before the first upload | Removing a member | A deletion link asks again | Settings, About |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-consent.png" width="200" alt="Back up to Google Firebase? What is uploaded, who sees it, where, and changing your mind; I agree and Not now, sign out"> | <img src="docs/screenshots/en/phone-family-remove.png" width="200" alt="Remove partner@example.com? They'll stop seeing the family's log from now on; what's already on their phone stays there"> | <img src="docs/screenshots/en/phone-account-delete-link.png" width="200" alt="Delete account? This link deletes your account. Tap Delete to go ahead; Cancel and Delete"> | <img src="docs/screenshots/en/phone-settings-about.png" width="200" alt="Settings, About: the version, Privacy policy, Download my data, Import data and Font licences"> |

### Watch

| Main, idle | Main, asleep | Main, after a breast feed | Feed type |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-main-idle.png" width="170" alt="Watch main, idle"> | <img src="docs/screenshots/en/watch-main-asleep.png" width="170" alt="Watch main, asleep"> | <img src="docs/screenshots/en/watch-main-after-breast.png" width="170" alt="Watch main after a breast feed"> | <img src="docs/screenshots/en/watch-feed-type.png" width="170" alt="Watch feed type"> |

| Side | Diaper | Poop colour | Logged + Undo |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-side.png" width="170" alt="Watch side order after Breast, with Save"> | <img src="docs/screenshots/en/watch-diaper.png" width="170" alt="Watch diaper"> | <img src="docs/screenshots/en/watch-diaper-colour.png" width="170" alt="Watch poop colour list"> | <img src="docs/screenshots/en/watch-logged.png" width="170" alt="Watch logged breast feed with its side order and Undo"> |

| Undone | Today | Tile | Tile, asleep | Complications |
|:---:|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-undone.png" width="140" alt="Watch undone"> | <img src="docs/screenshots/en/watch-today.png" width="140" alt="Watch today list"> | <img src="docs/screenshots/en/watch-tile.png" width="140" alt="Watch tile"> | <img src="docs/screenshots/en/watch-tile-asleep.png" width="140" alt="Watch tile while asleep"> | <img src="docs/screenshots/en/watch-complications.png" width="140" alt="Watch complications preview"> |

| Next feed and sleep (scrolled) | Estimates passed (scrolled) | Expected wake-up (scrolled, asleep) | Next feed (scrolled, asleep) |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-main-awake-forecast.png" width="170" alt="Watch Next feed card, in 1h 00m, and the Awake card with Next sleep in 50m"> | <img src="docs/screenshots/en/watch-main-forecast-passed.png" width="170" alt="Watch Next feed card, 24m ago, and the Awake card with Next sleep 36m ago, once both estimates have passed"> | <img src="docs/screenshots/en/watch-main-asleep-forecast.png" width="170" alt="Watch Adjust start with Wakes ~05:25"> | <img src="docs/screenshots/en/watch-main-asleep-feed-forecast.png" width="170" alt="Watch Last fed card with Next feed in 2h 45m"> |

| Main, two babies | Baby picker | Tile, second baby |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-main-twins.png" width="170" alt="Watch main with two babies: Noa in the time text"> | <img src="docs/screenshots/en/watch-baby-picker.png" width="170" alt="Watch Switch baby picker with Noa selected and Ida"> | <img src="docs/screenshots/en/watch-tile-second-baby.png" width="170" alt="Watch tile for the second, unnamed baby: Baby 2"> |

## Project structure

| Module | What it holds |
|---|---|
| [`core/`](core) | Android library shared by both apps: the `Entry` model and validation, the Room database and repository, the Data Layer sync (`sync/`), the backup sync engine (`cloud/`), settings and babies, summaries, statistics and the Lamp state (`domain/`), text formatting, the design tokens, type and icons, and the sample data used by previews and screenshots. |
| [`app/`](app) | The phone app (Compose, Material 3): Home, the Feed (with its Breast step) / Diaper sheets, History, Statistics, Edit, Settings, Welcome and Account (`account/`: Firebase Authentication and the Firestore backup), the Glance widgets (`widget/`) and the running-sleep notifications (`notify/`). |
| [`wear/`](wear) | The Wear OS app (Compose for Wear OS, Material 3): Main, Feed type, Side, Diaper, Logged, Today and Adjust, plus the Tile (`tile/`), complications (`complication/`) and the Ongoing Activity (`ongoing/`). |
| [`docs/design/`](docs/design) | The design: [`DESIGN_SPEC.md`](docs/design/DESIGN_SPEC.md) (the "Night Light" direction: screens, tokens, icons, accessibility) and [`V1_ADDITIONS.md`](docs/design/V1_ADDITIONS.md) (feed types, tick-ordered sides, optional diaper details, English + Danish) and [`BABIES_AND_BACKUP.md`](docs/design/BABIES_AND_BACKUP.md) (several babies, the optional account, backup and families); each later one wins where they differ. |
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

The backend's Firestore security rules and the retention job have their own tests on the Firestore and Authentication emulators (Node.js 22 and JDK 21):

```sh
cd firebase && npm install && npm test
```

## Screenshots

The screenshot tests (Roborazzi + Robolectric) render every screen in English and Danish into `app/screenshots/{en,da}/` and `wear/screenshots/{en,da}/`. They are rendered on the JVM, not taken from a device, so fonts and shadows can differ slightly from a real phone or watch.

```sh
# Re-record all screenshots
./gradlew :app:recordRoborazziDebug :wear:recordRoborazziDebug

# Rebuild the README images in docs/screenshots/ (needs Pillow: pip install Pillow)
python3 scripts/readme_screenshots.py
```

`readme_screenshots.py` takes a curated subset, scales phone shots to 360 px wide and masks watch shots to a 300 px circle with a transparent outside, for both languages. Edit the `PHONE` and `WATCH` lists in the script to change the set.

On pull requests, the [`screenshots.yml`](.github/workflows/screenshots.yml) workflow does both on CI's Android SDK and pushes the result to the branch when anything changed, so the screenshots always show the UI being merged.

## Releasing

Every merge to `main` builds signed app bundles and APKs for the phone and the watch, attaches both APKs to a [GitHub Release](../../releases) for side-loading under a new version tag (a patch bump, or minor / major with the pull request's `release: minor` / `release: major` label), and uploads the bundles to Google Play (the `internal` track by default) with the [`release.yml`](.github/workflows/release.yml) workflow; pull requests run the unit tests and a debug build ([`ci.yml`](.github/workflows/ci.yml)). The secrets, the upload key, the Play service account and the one-time manual steps are in [docs/RELEASING.md](docs/RELEASING.md).

## Licences

The fonts bundled in the apps are licensed under the SIL Open Font License 1.1; their licence texts are in [`third_party/fonts/`](third_party/fonts) and are also shown in the phone app under Settings › Licences.
