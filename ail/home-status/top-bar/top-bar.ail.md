# Home's top bar

The baby's name, the sync status as small icons under it, and the buttons to
History, Statistics and Settings.

```ail
node TopBar : flow   level: experience
  why   The parent must see whose log this is and whether the other devices have
        it, and reach the day's list, the averages and the settings in one tap.   <- HomeStatus
  what
    Scenario: the top bar of a new install
      Given a new install, its first run skipped without a name
      Then the top bar reads "Baby" with the buttons History, Statistics and Settings
      And the watch icon is spoken "Syncing with watch…", though no watch was ever paired
    Scenario: the top bar opens History, Statistics and Settings
      Given a new install
      When the parent taps History, goes back, taps Statistics, goes back and taps Settings
      Then each opens its screen ("History", "Statistics", "Settings"), and back returns to Home
  how
    decide the name is the current baby's, "Baby" when the first run was skipped without one
      because the name is shown in the top bar (DESIGN_SPEC §3.3.1, BABIES_AND_BACKUP §2.2)
    decide the sync state shows as icons with no visible text, spoken as a sentence: the watch first (none without a watch), then the cloud while an account is signed in; only an error state is a 48dp retry button
      because the state is device-level and must not crowd the night screen; Settings › Watch sync shows it as text (DESIGN_SPEC §4.5)
    decide on this phone, with no watch ever paired, the watch icon reads "Syncing with watch…" from the start, and some seconds after a log (anywhere from 1 to over 20) it may turn into the error button "Couldn't save · Tap to retry"
      because "unknown, confirm with the owner": the design shows no icon without a watch (DESIGN_SPEC §4.5 "No watch / phone: no icon"); seen on Robolectric, where the watch's data layer fails rather than saying it is unavailable; is this what a phone without a watch shows? It is too irregular here to be a checked scenario
    decide History, Statistics and Settings are 48dp icon buttons, in that order, on the name's line
      because Statistics is found without going through History (V1_ADDITIONS §8; commits fd3435a, 2c8bab7)
  look
    The bar under the status bar inset: the name in type.titleLarge color.text at gutter.phone, on one line with the three
      48dp icon buttons at the end (history, chart and settings icons, 24dp, color.text.dim); the status icons hang under the
      name: 20dp glyphs 12dp apart in color.text.dim (an error in color.error, as a 48dp button reaching up over the name).
      Lights-down: the name in color.text.dim
  copy
    topbar.history      "History"
    topbar.statistics   "Statistics"
    topbar.settings     "Settings"
    sync.synced         "Synced with watch · {time}"
    sync.syncing        "Syncing with watch…"
    sync.saved          "Saved on phone · will sync"
    sync.error          "Couldn't save · Tap to retry"
    backup              "Backing up…" "Backed up · {time}" "Saved on phone · will back up" "Couldn't back up · Tap to retry"
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (Top bar), §4.5 (Sync state), §8.2 (lights-down); BABIES_AND_BACKUP.md §2.2, §3.2
    docs/design/V1_ADDITIONS.md §8 (Statistics' placement); commits fd3435a, 2c8bab7, 02d085f
    driven on Robolectric, 2026-11-27: "Baby", "Syncing with watch…" on a new install, "Couldn't save · Tap to retry" after
      the first feed; History, Statistics and Settings opened and closed with Back
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeTopBar.kt, ui/components/SyncIcons.kt
```
