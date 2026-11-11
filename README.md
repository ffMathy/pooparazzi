# Pooparazzi

**Pooparazzi** is a baby tracker for an Android phone and a Wear OS watch, built for one-handed use at 3am. It logs **sleep**, **feeds** and **diapers** with one or two taps, keeps the phone and the watch in sync, and shows "how long since…" at a glance on the home screen, the lock screen, a watch tile and watch-face complications.

Only the time is ever required: the feed type, the breast side, pee/poop and the poop's colour and texture are optional extras.

<p align="center">
  <img src="docs/screenshots/en/phone-home-night.png" width="240" alt="Phone home at night">
  &nbsp;&nbsp;
  <img src="docs/screenshots/en/watch-main-idle.png" width="200" alt="Watch main screen">
</p>

## Features

- **Sleep**: one button starts a sleep and the same button ("Woke up") ends it. The running sleep is shown as a ring with its length; tap it to edit the sleep (its start, or end it) while it runs.
- **Feeds**: Breast, Bottle or Food, or **just the time**. Log it now or a little earlier: −15m / −10m / −5m / +5m nudge the time and add up, with "was 14:32" beside it. On the watch every log (a feed, a diaper or a wake-up) takes the time of the tap and is moved **afterwards**, the same way for all of them: tap the time on the Logged screen (or turn the crown there), and the Time screen moves it a minute per crown step or 5 minutes per button; Undo still works. The logging screens (Feed type, Side, Bottle and Diaper) have no time controls, and nothing on the watch opens a screen of its own until Save.
- **Breast feeds with side order**: a breast feed is logged like any feed, at one time (no timer, no duration). After Breast, tick **Left** and/or **Right**: the order you tick is the order recorded ("Right → Left"). The side is optional; "Log feed" (watch: "Save") with nothing ticked records just the time. Wherever the last feed is shown (phone Home, the widget, the watch), its order is shown too ("Left → Right", or "L → R" where space is tight), so you know which side to start on next.
- **Bottle: milk or formula, with an amount**: after Bottle, on the phone and on the watch, an optional **Milk | Formula** choice records what was in the bottle, and an optional slider sets how much was given, in **ml** (10 ml steps up to 300 ml), or in **fl oz** (½ oz steps up to 10 oz) where the region measures in ounces (the US, Liberia, Myanmar). On the watch, Bottle opens one screen with Milk and Formula, an amount stepper (−/+, in the same steps), three amount chips centred on the last bottle of the past week (60 · 90 · 120 ml, or 2 · 3 · 4 fl oz, without one), then Save; tap a chosen option again to clear it. Leaving both unset records just the time. The contents show in History's title ("Bottle · Formula"), the amount in its supporting line ("12:22 · 120 ml"); both can be changed in the edit sheet. Bottles logged before this change (as "Formula") read as a bottle with no contents picked.
- **Diapers**: Pee and Poop are independent checkboxes, both optional (nothing ticked is a plain change). With Poop on, pick an optional **colour** (yellow, green, brown, black, red, pale) and **texture** (watery, seedy, mushy, formed, hard); on the phone red, black and pale show a gentle "talk to your doctor" hint. On the watch Pee | Poop are two tiles, and the colour and texture chips appear right under them on the same screen.
- **History, edit and undo**: a day view with a 24-hour ribbon and day totals; tap any entry to edit its times, type, side, bottle contents (milk or formula) and amount, or diaper details, or delete it; on Home, tap the big "Last fed" time (or a since-row) to edit that entry, or the running sleep to edit it; add a past entry; on the phone a Feed or Diaper sheet already sets the time, so it simply closes when the log is saved (tap the since-row to edit or delete it); a feed or diaper logged while the baby sleeps also logs **Woke up** at its time (the phone's sheet says "Woke up will be logged too" before you save, the watch's Logged says "Woke up too" after, and its Undo resumes the sleep); Start sleep, Woke up, a widget log and a delete show an **Undo** receipt: a sheet with the time, Undo, chips to move it by a few minutes, Edit and Done (no timeout); on the watch a Logged screen with Undo, where tapping the time (or turning the crown) moves the time just logged. History and editing are otherwise on the phone only: the watch app is the Tile's glance and its logging screens.
- **What comes next**: Home's buttons show what this baby's last 7 days expect next: under Feed (in 1h 05m), under Start sleep when the next sleep is due and, while asleep, under Woke up when the baby is expected to wake. Under Diaper it shows the next poop when poop has been regular enough to predict. Each estimate is the median of the past week's comparable stretches (sleeps, wake windows, gaps between feeds or poops) that began at about the same time of day and lasted longer than the current one so far. With fewer than 5 such stretches, or too much spread, no estimate is shown. When poop gaps say nothing but the baby reliably poops soon after feeding (after at least 70% of the week's feeds, within 90 minutes), Diaper instead shows the poop expected after the last feed, until a poop is logged. The recent days count more than the older ones (a stretch 3 days old counts half as much as today's), so the estimates follow a growth spurt within a few days. With the baby's **birth date** (optional, in Settings › Babies) they also follow the baby's age: no sleep estimate before 6 weeks (the day–night rhythm is still forming), and from 6 weeks a breastfed baby's poop is no longer estimated from the gaps between poops (days without one are normal then). On Home and the 4×2 widget a small "upcoming" clock icon says it is what comes next, so the line is just the time: "55m", "soon" under 5 minutes, then "now" once it is due (never a time in the past); the watch (its Tile and app alike) shows them the same way.
- **Prediction notifications**: the same estimates as silent, ongoing notifications on the phone while the app is closed ("Next feed in 1h 05m · Around 15:30", "Next poop soon", "Next sleep in 20m" or, asleep, "Expected to wake in 40m"), one per kind and baby, shown only while that kind is predicted. Each kind has its own Android notification channel (Next feed, Next poop, Next sleep and wake-up), so you choose which ones show in Android's notification settings; Settings › Notifications lists the three with On or Off, and a tap opens that channel's settings. They are on until you turn them off; one swiped away stays away until the next estimate of its kind. Wherever a time since the last one is shown (Home, the widget, the watch), a "history" clock likewise replaces "ago": "2h 10m".
- **Statistics**: one tap from Home (the chart button next to History) or from History's Statistics button (and Statistics has a History button back), average feeds, pee, poop and sleep per day for the period you pick: the last 30 days, or the last 7 or 90 days, all days, or dates you choose, with a bar chart of the period: press a bar (or drag across the chart, or hover it with a mouse) to see its day and value. A day unlike the others for a kind (half logged or logged twice, so its feeds or diapers are far from the usual count, or its sleep in all far from the usual day's) is noise: its bar is drawn **grey**, it is left out of the averages, and a legend item and a note under the chart say so. Under the averages, the **typical day** of the period: a 24-hour plot like History's ribbon, where each half hour of the sleep, feed and diaper lanes is as strong as it is typical, then the usual time **between feeds**, **between diapers**, how long a **sleep** lasts and how long the baby is **awake** between sleeps, by day and by night (19:00–07:00), each as the middle value with the range half of them fall in ("3h 00m" over "2h 15m–3h 25m"). Noise is left out here too, so the pattern rests on values that look alike: the same unusual days are left out of that lane, and a stretch far from the rest (a forgotten wake-up, a one-off long nap) is left out of its interval. It shows from 3 logged days in the period on. Only whole days up to yesterday count (until the first whole day, today so far, so a single entry already shows), and days when nothing was logged are left out instead of counting as zero. With several babies it shows the current baby's.
- **Several babies** (twins, siblings): add, rename and remove them in Settings › Babies (adding one asks for its name and, optionally, its birth date, which improves the predictions and estimates; the first run asks the same for the first baby, both optional, with Skip for now), and give any baby a birth date there later (removing a baby deletes its log, after asking), and switch the baby you log for from Home's top bar or the watch's baby button. Each device has its own current baby; a sleep runs per baby, and every sleeping baby keeps its notification.
- **Phone ↔ watch sync** over the Wearable **Data Layer**. Both apps log offline and merge by last-writer-wins; the babies' names and the language follow the phone. The sync state is a small icon, not text: a watch (and, while signed in, a cloud for the backup) under the name on the phone, a phone on the watch, each marked with a tick, "…", a slash or "!" (tap "!" to retry); TalkBack reads the full status. When a watch is connected but doesn't have Pooparazzi yet, Settings › Watch offers **Install on your watch**, which opens Pooparazzi's Google Play page on the watch (the phone then says to check the watch); the row goes once the app is installed. Without a watch, Settings › Watch says **No watch yet** and that Pooparazzi has a Wear OS app for when you get one.
- **Optional backup** to a small Firebase backend (free Spark plan, no Cloud Functions): sign in at any time, with Google or a sign-in link sent by email (no passwords), and everything logged so far is backed up; sign in on a new phone to restore it, babies and names included. **No account is needed**: the first-run Welcome offers "Only on this phone" as an equal choice beside the backup, and you can switch to the cloud later in Settings › Account; everything logged until then comes along. Setup: [docs/BACKEND.md](docs/BACKEND.md).
- **Families**: invite a partner or a grandparent with a single-use link (valid 6 days) from Settings › Family. Everyone in the family sees the same babies, with their names, and every member's log; joining adds your own log to the family's. Each phone keeps its own current baby. Leave at any time; the family's creator can remove members (and hands the family to the longest-standing member on leaving). Nothing is uploaded until you agree on a consent screen after signing in.
- **Privacy**: no ads, no analytics, no tracking. Names, birth dates and the log are shared only with your family's members; when what is uploaded changes (as with birth dates), every signed-in account is asked for its consent again. The [privacy policy](docs/PRIVACY.md) is also published on the app's website (like every page there, in English or Danish after the browser's language) and linked from Settings › About, beside **Download my data** (the log and the babies' names as a ZIP file of JSON files) and **Import data** (merges such a file back into the log, newest version of each entry winning). Deleted entries are backed up without what was logged, and a daily job deletes expired invites and accounts unused for 2 years. The records of processing, the impact assessment and the breach plan are in [docs/privacy/](docs/privacy).
- **Glanceable surfaces**:
  - Phone: two **Glance widgets** (a 4×2 log widget and a 3×1 quick widget, both with Feed, Diaper and Sleep; Feed and Diaper open the same sheets as in the app, the sleep toggle works in place; with two or more babies each widget has its own baby, switched with a tap on the widget), the 4×2 also on the **cover screen of Samsung flip phones**, and an **ongoing notification** for a running sleep (promoted to a Live Update where Android supports it) showing how long it has run, the last feed and diaper, and today's counts. "Woke up" on it leaves a notification with −5m / −10m / −15m to move the wake-up time.
  - Watch: a **Tile** (Feed and Diaper open the watch app's own screens, the sleep toggle works in place) that the watch app's main screen mirrors, both showing the time since the last feed and diaper and the estimated next feed, poop and sleep or wake-up; four **complications** (since last feed, last diaper, asleep/awake, sleep and last feed) and an **Ongoing Activity** on the watch face that says what comes next: while the baby sleeps ("Wakes 55m", or "Asleep 55m" when nothing is predicted) and while awake ("Feed 55m", "Sleep 20m"; nothing when nothing is predicted). Which estimates it uses (next feed, next poop, next sleep and wake-up) is chosen in the phone's Settings › Watch › On the watch face; all three until you untick some.
- **Night and Day themes**: a dim, warm Night theme for the dark hours and a Day theme, chosen automatically (or fixed in Settings). Large targets, TalkBack descriptions in full words ("2 hours 10 minutes ago"), large-font and small-watch layouts.
- **Open source**: made with ❤️ by a father in Aarhus, Denmark. The bottom of Settings says so, with a GitHub logo that opens this repository and a LinkedIn logo that opens the maker's profile.
- **Introduction**: after the first run's "Your baby", a six-page tour shows how to log sleep, feeds and diapers, fix a log, read what comes next, and where else Pooparazzi is (widgets, the watch, the family). Swipe or tap Next; **Skip** closes it from any page. A phone that already has a log never gets it on its own, and Settings › About › **Introduction** shows it again at any time.
- **A polite review prompt**: after a few days of real use (3 days since the app was first opened and at least 15 entries logged since then, on 3 different days; an import or a restored backup doesn't count), Home asks once, by day and only while nothing is running or open, **Enjoying Pooparazzi?** with **Rate on Google Play** (opens the app's Play Store page), **Not now** (asks once more a month later, then never) and **Don't ask again**.
- **English and Danish**, with an in-app language choice (Settings › Language) that the widgets, notifications and the watch follow.

## Screenshots

All screenshots are rendered by the Roborazzi screenshot tests under Robolectric (see [Screenshots](#screenshots-1) below), not captured from a device, and use the sample day in `core/.../sample/SampleData.kt`.

### Phone

| Home, Night | Home, Day | Asleep at night | Breast feed logged |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-night.png" width="200" alt="Home, Night theme"> | <img src="docs/screenshots/en/phone-home-day.png" width="200" alt="Home, Day theme"> | <img src="docs/screenshots/en/phone-home-asleep.png" width="200" alt="Home while asleep"> | <img src="docs/screenshots/en/phone-home-breast-logged.png" width="200" alt="Home right after logging a breast feed backdated by 15 minutes: Last fed 18m ago · 14:32 · Left → Right, with no receipt sheet"> |

| Forecast passed | Feed while asleep | Diaper while asleep |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-forecast-passed.png" width="200" alt="Home at 15:55 with nothing logged since: Feed 24m ago, Start sleep 36m ago"> | <img src="docs/screenshots/en/phone-feed-sheet-asleep.png" width="200" alt="Feed sheet at 03:12 while asleep since 02:05: Woke up will be logged too, under the nudge chips"> | <img src="docs/screenshots/en/phone-diaper-sheet-asleep.png" width="200" alt="Diaper sheet at 03:12 while asleep, Pee ticked: Woke up will be logged too"> |

| Receipt | Feed sheet | Breast: side order | Diaper sheet |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-receipt.png" width="200" alt="Receipt after Start sleep: Sleep started · 14:32 with Undo, offset chips, Edit and Done"> | <img src="docs/screenshots/en/phone-feed-sheet.png" width="200" alt="Feed sheet: Breast, Bottle, Food, Just the time"> | <img src="docs/screenshots/en/phone-breast-sheet.png" width="200" alt="Feed sheet after Breast, with Right then Left ticked"> | <img src="docs/screenshots/en/phone-diaper-sheet.png" width="200" alt="Diaper sheet with poop colour and texture"> |

| Bottle: milk or formula, amount | Edit bottle feed | Diaper, backdated |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-bottle-sheet.png" width="200" alt="Feed sheet after Bottle, with the optional Milk | Formula choice and amount slider at 120 ml (or 4 fl oz where ounces are used)"> | <img src="docs/screenshots/en/phone-edit-bottle.png" width="200" alt="Edit a bottle feed with its optional contents and amount"> | <img src="docs/screenshots/en/phone-diaper-sheet-nudged.png" width="200" alt="Diaper sheet after tapping −5m twice: Diaper, 14:22 · was 14:32"> |

| History | History, Night | Edit feed | Edit diaper |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-history.png" width="200" alt="History with day ribbon"> | <img src="docs/screenshots/en/phone-history-night.png" width="200" alt="History at night"> | <img src="docs/screenshots/en/phone-edit-feed.png" width="200" alt="Edit a breastfeed"> | <img src="docs/screenshots/en/phone-edit-diaper.png" width="200" alt="Edit a diaper"> |

| Add past entry | Settings | Widget 4×2 | Widget 3×1 |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-add-past-entry.png" width="200" alt="Add a past entry"> | <img src="docs/screenshots/en/phone-settings.png" width="200" alt="Settings: Account, Baby with Add a baby, Appearance and Language"> | <img src="docs/screenshots/en/phone-widget-4x2.png" width="200" alt="4x2 log widget in its states"> | <img src="docs/screenshots/en/phone-widget-3x1.png" width="200" alt="3x1 quick widget in its sizes"> |

| Cover screen (Samsung flip) | Sleep notifications | Statistics | Statistics, Night |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-widget-cover.png" width="200" alt="The 4x2 widget on a Samsung flip's cover screen, idle and asleep"> | <img src="docs/screenshots/en/phone-notifications.png" width="200" alt="The running-sleep notification with its duration, last feed, last diaper and today's counts, and the woke-up notification with its earlier offsets"> | <img src="docs/screenshots/en/phone-statistics.png" width="200" alt="Statistics: averages per day for the last 30 days, with the poop chart open"> | <img src="docs/screenshots/en/phone-statistics-night.png" width="200" alt="Statistics at night"> |

| Statistics, typical day | Prediction notifications | Settings, what comes next | Settings, birth date |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-statistics-typical-day.png" width="200" alt="Statistics scrolled to the end: the Typical day card for the last 30 days, with sleep, feed and diaper lanes by half hour and the usual time between feeds and diapers, sleep length and time awake, by day and by night"> | <img src="docs/screenshots/en/phone-notification-forecasts.png" width="200" alt="Prediction notifications, one per kind: Next feed in 1h 05m, Next sleep in 50m, Expected to wake in 40m and Next poop soon, each with Around and a clock time"> | <img src="docs/screenshots/en/phone-settings-predictions.png" width="200" alt="Settings: On the watch face with Next feed and Next sleep and wake-up ticked and Next poop not, then Notifications with What comes next: Next feed On, Next poop Off, Next sleep and wake-up On"> | <img src="docs/screenshots/en/phone-settings-birth-date.png" width="200" alt="Settings, Baby: the date picker for the birth date, 14 August 2026 picked, with Remove, Cancel and OK"> |

| Statistics, a bar pressed | Statistics, an unusual day |
|:---:|:---:|
| <img src="docs/screenshots/en/phone-statistics-bar.png" width="200" alt="Statistics with the highest poop bar pressed: the other bars fade, a guide marks its day, and its date and value show under it in place of the first and last dates"> | <img src="docs/screenshots/en/phone-statistics-unusual.png" width="200" alt="Statistics with Feeds open: a half-logged day's bar is grey and picked, reading Sat 10 Oct, 1, Unusual; the legend has Unusual day next to a grey bar, and a note says grey bars are days unlike the others, left out of the average"> |

#### Several babies and backup

| Welcome (first run) | Account, signed out | Account, signed in | Settings, babies |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-welcome.png" width="200" alt="First-run Welcome: No account needed. Two cards: Only on this phone, with switching to cloud backup later in Settings and Continue without an account; then Back up to the cloud with Continue with Google and the email sign-in link"> | <img src="docs/screenshots/en/phone-account.png" width="200" alt="Account, signed out: Continue with Google, then or use email with Email me a sign-in link; no passwords"> | <img src="docs/screenshots/en/phone-account-signed-in.png" width="200" alt="Account, signed in: the address, Backed up 14:31, Back up now, Sign out and Delete account"> | <img src="docs/screenshots/en/phone-settings-babies.png" width="200" alt="Settings, Babies: name fields for Noa, Ida and an unnamed third, each with a remove button, Add a baby and the names hint"> |

| Home, two babies | Switch baby | History, two babies | Widgets, two babies |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-home-twins.png" width="200" alt="Home with two babies: Noa with a chevron is the switcher, the watch and cloud status icons under it"> | <img src="docs/screenshots/en/phone-switch-baby.png" width="200" alt="Switch baby sheet with Noa selected and Ida"> | <img src="docs/screenshots/en/phone-history-twins.png" width="200" alt="History labelled Noa under the title"> | <img src="docs/screenshots/en/phone-widget-twins.png" width="200" alt="4x2 widgets with the Noa baby switcher at the top, and the 3x1 with its N baby button"> |

| Sleep notifications, two babies | Removing a baby | Watch without the app | No watch yet |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-notification-twins.png" width="200" alt="One sleep notification per baby: Noa is asleep and Ida is asleep, each with Woke up"> | <img src="docs/screenshots/en/phone-settings-remove-baby.png" width="200" alt="Remove Baby 3? Their log is deleted for everyone in your family and from your backup; Cancel and Remove"> | <img src="docs/screenshots/en/phone-settings-watch-install.png" width="200" alt="Settings, Watch: Install on your watch. Pooparazzi isn't on your watch yet. Opens Google Play on the watch"> | <img src="docs/screenshots/en/phone-settings-no-watch.png" width="200" alt="Settings, Watch: No watch yet. Pooparazzi has a Wear OS watch app too. If you get a watch, log with a tap from your wrist; it syncs with this phone"> |

| Your baby (first run) | Adding a baby | Review prompt |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-baby-intro.png" width="200" alt="Your baby: both are optional, skip them now and add them later in Settings. The name field, Add birth date, why the birth date improves the predictions and estimates, Continue and Skip for now"> | <img src="docs/screenshots/en/phone-settings-add-baby.png" width="200" alt="Add a baby: the name field, Add birth date, and that the birth date is optional but improves the predictions and estimates, which then follow the baby's age; Cancel and Add"> | <img src="docs/screenshots/en/phone-review-prompt.png" width="200" alt="Enjoying Pooparazzi? over Home by day, after a few days of logging: a quick rating on Google Play helps other tired parents find it; Rate on Google Play, Not now and Don't ask again"> |

#### Introduction

After "Your baby", a new phone gets a short tour of how to use the app. **Skip** closes it from any page; Settings › About › **Introduction** shows it again at any time.

| Welcome | Sleep | Feeds and diapers |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-intro-welcome.png" width="200" alt="Introduction, page 1 of 6: Welcome to Pooparazzi. Log sleep, feeds and diapers with one or two taps, made for one hand at 3am. Skip at the top, Next at the bottom"> | <img src="docs/screenshots/en/phone-intro-sleep.png" width="200" alt="Introduction, page 2 of 6, Night: Sleep. Tap Start sleep when the baby falls asleep, and the same button, now Woke up, when they wake"> | <img src="docs/screenshots/en/phone-intro-log.png" width="200" alt="Introduction, page 3 of 6: Feeds and diapers. Only the time is needed; −15m, −10m and −5m move the time back"> |

| Fix anything | What comes next | Always at hand |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-intro-fix.png" width="200" alt="Introduction, page 4 of 6, Night: Fix anything. Tap Undo right after a sleep, or tap a time on Home or a row in History to edit or delete it"> | <img src="docs/screenshots/en/phone-intro-patterns.png" width="200" alt="Introduction, page 5 of 6: What comes next. Home's buttons show what the last 7 days expect; History and Statistics"> | <img src="docs/screenshots/en/phone-intro-everywhere.png" width="200" alt="Introduction, last page, Night: Always at hand. Widgets, the Wear OS watch, the family, and Settings › About to see it again; Get started"> |

#### Families and passwordless sign-in

| Check your email | Settings, family | Joining a family |
|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-account-link-sent.png" width="200" alt="Account: Check your email, the sign-in link went to parent@example.com, Send again and Use another email"> | <img src="docs/screenshots/en/phone-settings-family.png" width="200" alt="Settings, Family: you started the family, a partner and a grandparent with Remove, Invite someone and Leave family"> | <img src="docs/screenshots/en/phone-join-family.png" width="200" alt="Join partner@example.com's family? Your babies and log are added and shared; Join and Not now"> |

| Consent before the first upload | Removing a member | A deletion link asks again | Settings, About |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/phone-consent.png" width="200" alt="Back up to Google Firebase? What is uploaded, who sees it, where, and changing your mind; I agree and Not now, sign out"> | <img src="docs/screenshots/en/phone-family-remove.png" width="200" alt="Remove partner@example.com? They'll stop seeing the family's log from now on; what's already on their phone stays there"> | <img src="docs/screenshots/en/phone-account-delete-link.png" width="200" alt="Delete account? This link deletes your account. Tap Delete to go ahead; Cancel and Delete"> | <img src="docs/screenshots/en/phone-settings-about.png" width="200" alt="Settings, About: the version, Privacy policy, Download my data, Import data and Font licences, then the footer: Made with ❤️ by a father in Aarhus, Denmark, Pooparazzi is open source, and GitHub and LinkedIn logo buttons"> |

### Watch

| Main, idle | Main, asleep | Main, after a breast feed | Feed type |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-main-idle.png" width="170" alt="Watch main, idle, as the Tile: the feed icon with 2h 10m since and 1h 00m to the next; the diaper icon with 48m since; a large Start sleep button, awake 1h 05m, next sleep in 45m"> | <img src="docs/screenshots/en/watch-main-asleep.png" width="170" alt="Watch main, asleep: Asleep 1h 07m, Feed with Next in 2h 45m, Diaper, Woke up with asleep 1h 07m and wake-up in 2h 15m"> | <img src="docs/screenshots/en/watch-main-after-breast.png" width="170" alt="Watch main after a breast feed"> | <img src="docs/screenshots/en/watch-feed-type.png" width="170" alt="Watch feed type"> |

| Side | Diaper | Diaper, colour | Diaper, texture |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-side.png" width="170" alt="Watch side order after Breast, with Save"> | <img src="docs/screenshots/en/watch-diaper.png" width="170" alt="Watch Diaper screen as it opens: Diaper · 14:32, Pee and Poop tiles, and Save"> | <img src="docs/screenshots/en/watch-diaper-options.png" width="170" alt="Watch Diaper screen with Pee and Poop ticked, scrolled to the colour chips: Yellow chosen with a check on its swatch, Green, Brown, and more below"> | <img src="docs/screenshots/en/watch-diaper-texture.png" width="170" alt="Watch Diaper screen scrolled to the texture chips: Seedy chosen, Watery, Mushy, and Pale chosen among the colours above"> |

| Bottle | Bottle amount | Logged bottle | Time (from Logged) |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-bottle.png" width="170" alt="Watch Bottle screen as it opens: Bottle · 14:32, Milk and Formula, and Save"> | <img src="docs/screenshots/en/watch-bottle-amount.png" width="170" alt="Watch Bottle screen scrolled to the amount: − 4 fl oz +, and the chips 3, 4 and 5 fl oz with 4 chosen (120 ml where millilitres are used)"> | <img src="docs/screenshots/en/watch-logged-bottle.png" width="170" alt="Watch logged bottle feed: Bottle at 14:32, Formula · 4 fl oz, and Undo"> | <img src="docs/screenshots/en/watch-time.png" width="170" alt="Watch Time screen, opened from Logged for every log: Feed · −7m over 14:25, −5m and +5m buttons and Done; the crown moves the time a minute at a time"> |

| Logged + Undo | Undone | Tile | Tile, asleep |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-logged.png" width="170" alt="Watch logged breast feed with its side order and Undo"> | <img src="docs/screenshots/en/watch-undone.png" width="140" alt="Watch undone"> | <img src="docs/screenshots/en/watch-tile.png" width="140" alt="Watch tile: Feed and Diaper rows, and Start sleep with awake 1h 05m and next sleep in 45m"> | <img src="docs/screenshots/en/watch-tile-asleep.png" width="140" alt="Watch tile while asleep"> |

| Tile, second baby | Complications | Main, two babies | Baby picker |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/en/watch-tile-second-baby.png" width="170" alt="Watch tile for the second, unnamed baby: Baby 2"> | <img src="docs/screenshots/en/watch-complications.png" width="140" alt="Watch complications preview"> | <img src="docs/screenshots/en/watch-main-twins.png" width="170" alt="Watch main with two babies: Noa in the time text and the baby button as the title"> | <img src="docs/screenshots/en/watch-baby-picker.png" width="170" alt="Watch Switch baby picker with Noa selected and Ida"> |

| Logged while asleep | Diaper while asleep |
|:---:|:---:|
| <img src="docs/screenshots/en/watch-logged-woke-up.png" width="170" alt="Watch logged breast feed at 03:12 during a sleep: Left → Right, then Woke up too, and Undo"> | <img src="docs/screenshots/en/watch-logged-diaper-woke-up.png" width="170" alt="Watch logged pee at 03:12 during a sleep: Woke up too, and Undo"> |

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

`readme_screenshots.py` takes a curated subset, scales phone shots to 360 px wide and masks watch shots to a 300 px circle with a transparent outside, for both languages. Edit the `PHONE` and `WATCH` lists in the script to change the set.

On pull requests, the [`screenshots.yml`](.github/workflows/screenshots.yml) workflow does both on CI's Android SDK and pushes the result to the branch when anything changed, so the screenshots always show the UI being merged.

## Releasing

Every merge to `main` builds signed app bundles and APKs for the phone and the watch, attaches both APKs to a [GitHub Release](../../releases) for side-loading under a new version tag (a patch bump, or minor / major with the pull request's `release: minor` / `release: major` label), and uploads the bundles to Google Play (the closed-testing track, `alpha`, by default) with the [`release.yml`](.github/workflows/release.yml) workflow; pull requests run the unit tests and a debug build ([`ci.yml`](.github/workflows/ci.yml)). The Google Play store images in `docs/play/` are rebuilt from the screenshots on every pull request and uploaded to the store listing when a merge changes them ([`store-listing.yml`](.github/workflows/store-listing.yml)). The secrets, the upload key, the Play service account and the one-time manual steps are in [docs/RELEASING.md](docs/RELEASING.md).

## Licences

The fonts bundled in the apps are licensed under the SIL Open Font License 1.1; their licence texts are in [`third_party/fonts/`](third_party/fonts) and are also shown in the phone app under Settings › Licences.
