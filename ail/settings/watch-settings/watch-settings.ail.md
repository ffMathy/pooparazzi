# Settings › Watch

How the phone and the watch are getting on, a way to sync again, an offer to
install the watch app, and which estimates the watch face shows.

```ail
node WatchSettings : flow   level: experience
  why   A parent with a watch needs to see that it is in step with the phone, and a
        parent without one should learn that there is a watch app.   <- Settings
  what
    Scenario: a phone that can't reach the watch service
      Given a new install, its first run skipped, on a phone whose watch service fails
      When the parent opens Settings
      Then once it has tried, Watch reads "Couldn't save · Tap to retry" (while trying, "Syncing with watch…"), with "Sync now" and no "No watch yet"
    Scenario: what the watch face shows
      Given Settings on a new install
      Then "On the watch face" lists "Next feed", "Next poop" and "Next sleep and wake-up", all ticked
      And under them "The watch face shows the earliest of these, while the baby sleeps and while awake."
      When the parent taps "Next poop"
      Then "Next poop" is no longer ticked, and the other two still are
  how
    decide the line says how the phone and the watch are getting on: "Synced with watch · 14:31", "Syncing with watch…", "Saved on phone · will sync" (the watch out of reach) or "Couldn't save · Tap to retry", always with "Sync now"
      because the parent must see that a log reached the other device, and be able to retry (DESIGN_SPEC §4.5)
    decide without a watch or Google Play services the line is replaced by "No watch yet" and "Pooparazzi has a Wear OS watch app too. If you get a watch, log with a tap from your wrist; it syncs with this phone."
      because a parent without a watch should learn there is a watch app, not read an error (commit 88c22fa)
    decide a connected watch without Pooparazzi gets "Install on your watch" ("Pooparazzi isn't on your watch yet. Opens Google Play on the watch."), in place of "No watch yet" or after the line when another watch has the app; a tap opens its Play Store page on the watch and a toast says "Check your watch to install Pooparazzi." or "Couldn't reach your watch. Install Pooparazzi from Google Play on the watch."; asked again whenever Settings comes back to the front
      because the row should go away once the app is installed (commit f253184)
    decide the JVM phone has a failing watch service (Google Play services can't run there), so the checks see the error line; "No watch yet" and "Install on your watch" need a phone with Google Play services
      because the checks run the real app under Robolectric, without Google Play services (driven 2026-11-27)
    decide every estimate is ticked until the parent unticks some; the choice is synced to the watch, whose face shows the earliest of those ticked
      because they choose what the watch's ongoing activity shows (DESIGN_SPEC §3.3.5, §3.8)
  look
    The sync line in a color.row group with radius.row corners: the watch icon (with a tick, "…", a slash or "!") and
      the line in type.dataMedium color.text.dim (color.error for "Couldn't save"), "Sync now" as an outlined pill at
      the end under it. "On the watch face" in type.titleSmall color.text, then three checkbox rows of size.row in
      color.row, the label in type.titleMedium color.text, the hint in type.bodySmall color.text.dim
  copy
    synced      "Synced with watch · {time}"
    syncing     "Syncing with watch…"         ; da "Synkroniserer med ur…"
    offline     "Saved on phone · will sync"
    error       "Couldn't save · Tap to retry"  ; da "Ikke gemt · Prøv igen"
    syncNow     "Sync now"                    ; da "Synkroniser nu"
    noWatch     "No watch yet"
    noWatch.body "Pooparazzi has a Wear OS watch app too. If you get a watch, log with a tap from your wrist; it syncs with this phone."
    install     "Install on your watch"
    install.body "Pooparazzi isn't on your watch yet. Opens Google Play on the watch."
    install.opened "Check your watch to install Pooparazzi."
    install.failed "Couldn't reach your watch. Install Pooparazzi from Google Play on the watch."
    face        "On the watch face"           ; da "På urskiven"
    face.feed   "Next feed"                   ; da "Næste måltid"
    face.poop   "Next poop"                   ; da "Næste bæ"
    face.sleep  "Next sleep and wake-up"      ; da "Næste søvn og opvågning"
    face.hint   "The watch face shows the earliest of these, while the baby sleeps and while awake."
  sources
    docs/design/DESIGN_SPEC.md §3.3.5 (Watch), §3.8, §4.5 (sync status)
    git log: 88c22fa "Settings › Watch: without a watch, say Pooparazzi has a watch app", f253184 "Settings › Watch:
      offer to install Pooparazzi on a watch without it"
    driven on Robolectric, 2026-11-27: "Syncing with watch…", then "Couldn't save · Tap to retry" (Google Play
      services' wearable API fails to load on the JVM), Sync now, the three ticks, Next poop unticked
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt, the sync row and the watch face group
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/watch/WatchInstall.kt
    core/src/main/kotlin/com/ffmathy/pooparazzi/core/sync/EntrySync.kt, the sync states
```
