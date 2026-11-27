# Settings › About

What the app is, the introduction again, the privacy policy, the parent's
data (download and import), the font licences, and the footer.

```ail
node About : flow   level: experience
  why   A parent must be able to read what is stored and where, take their whole log
        with them or bring it back, and see who made the app and that it is open.   <- Settings
  what
    Scenario: what about offers
      Given Settings on a new install
      Then About reads "Pooparazzi" and "Version 0.1.0"
      And its rows read "Introduction" ("How to use Pooparazzi, step by step"), "Privacy policy" ("What is stored, where, and your rights"), "Download my data" ("The whole log and the babies' names as a ZIP file"), "Import data" ("Merge a downloaded ZIP file into the log") and "Font licences" ("Google Sans Flex and Atkinson Hyperlegible Next, SIL Open Font License 1.1"), in that order
    Scenario: the font licences
      Given Settings
      When the parent taps "Font licences"
      Then a screen titled "Font licences" shows "Google Sans Flex" with its licence, starting "Copyright 2015 The Google Sans Flex Authors", and further down "Atkinson Hyperlegible Next" with its own
      When the parent taps Back
      Then Settings shows again
  how
    decide "Privacy policy" opens the policy in the browser: the project's website (its privacy page) in builds with a backup, else the policy in the repository on GitHub
      because the privacy policy is linked from Settings › About and next to sign-in (BABIES_AND_BACKUP §1; app/…/account/PrivacyPolicy.kt)
    decide "Download my data" asks where to save "pooparazzi-log-<date>.zip" (the system's file picker) and then says "Saved 214 entries" (one: "Saved 1 entry") or "Couldn't save the file. Try again." in a toast; the file holds every baby with its name and every entry that is not deleted, of every baby, oldest first; it works with or without an account
      because the parent has a right to their data in a portable form (GDPR art. 15 and 20; BABIES_AND_BACKUP §3.2)
    decide "Import data" opens the system's file picker and merges the chosen file into the log (babies it lacks are added and a newer name wins), entries merge by the last writer, so the same file twice, or an older one, changes nothing; the toast says "Imported 214 entries", "Nothing new to import" or "Couldn't read the file. Pick a ZIP file from Download my data."; the older single JSON file is read too
      because importing must never duplicate or lose a log, exactly as a sync from the other device (BABIES_AND_BACKUP §3.2)
    decide "Font licences" shows the full licence text of each bundled font, read from the app's own copy in third_party
      because the SIL Open Font License asks for its text to travel with the fonts (app/build.gradle.kts, the font licences)
    decide the footer's GitHub button opens the repository on GitHub and LinkedIn the maker's profile, in the browser
      because Pooparazzi is open source, and made by a father in Aarhus (DESIGN_SPEC §3.3.5)
    decide the checks show the rows but not what the browser, the file picker or a toast does: those are the system's, outside the app
      because the checks see only the app's own screen (checks/e2e/Phone.kt)
  look
    In color.surface.container groups: "Pooparazzi" in type.titleMedium color.text over the version in type.dataMedium color.text.dim;
      then each link row with its title in type.titleMedium color.text, its summary in type.bodySmall color.text.dim and a
      chevron, 2dp apart, radius.row at the group's ends. The font licences screen: a top bar with Back and "Font
      licences", then each font's name in type.titleMedium color.text over its licence in type.bodySmall color.text.dim
  copy
    app            "Pooparazzi"
    version        "Version {version}"
    intro          "Introduction"                    ; da "Introduktion"
    intro.body     "How to use Pooparazzi, step by step"
    privacy        "Privacy policy"                  ; da "Privatlivspolitik"
    privacy.body   "What is stored, where, and your rights"
    export         "Download my data"                ; da "Download mine data"
    export.body    "The whole log and the babies' names as a ZIP file"
    export.done    "Saved {count} entries"           ; one: "Saved 1 entry"
    export.failed  "Couldn't save the file. Try again."
    import         "Import data"                     ; da "Importér data"
    import.body    "Merge a downloaded ZIP file into the log"
    import.done    "Imported {count} entries"        ; one: "Imported 1 entry"
    import.none    "Nothing new to import"
    import.failed  "Couldn't read the file. Pick a ZIP file from Download my data."
    licences       "Font licences"                   ; da "Skrifttypelicenser"
    licences.body  "Google Sans Flex and Atkinson Hyperlegible Next, SIL Open Font License 1.1"
  sources
    docs/design/DESIGN_SPEC.md §3.3.5 (About, the footer)
    docs/design/BABIES_AND_BACKUP.md §3.2 (Privacy policy, Download my data, Import data)
    docs/PRIVACY.md
    driven on Robolectric, 2026-11-27: About's rows, Font licences and Back; Privacy policy, Download my data and the
      GitHub button leave Settings on screen (the browser and the file picker are outside the app)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt, the About group, the footer, export
      and import
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/LicencesScreen.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/PrivacyPolicy.kt
```
