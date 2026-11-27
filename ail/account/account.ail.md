# The account and the family backup

No account is needed. An optional one, on the phone only, backs the log up to
a small Firebase backend, restores it on a new phone and shares it with a
family. Builds without a Firebase configuration (and every unit test) have no
accounts at all; the account checks run a debug build on the local Firebase
emulators instead.

```
account.ail.md                         Account: Settings › Account, and a build without a backup
welcome/welcome.ail.md                 Welcome: the first run's choice, "No account needed"
sign-in/sign-in.ail.md                 SignIn: Google, the email link, Check your email, its errors
signed-in/signed-in.ail.md             SignedIn: the backup's state, Back up now, Sign out, Delete account
backup-consent/backup-consent.ail.md   BackupConsent: "Back up to Google Firebase?" before anything is uploaded
family/family.ail.md                   Family: the members, Invite someone, Leave, Remove
join-family/join-family.ail.md         JoinFamily: an invite link, Join, the refusals
```

```ail
node Account : flow   level: experience
  why   A phone gets lost or reset, and two parents log for the same baby; an
        account keeps the log safe and shared, but nobody must need one, and nothing
        is uploaded without the parent's yes.   <- Pooparazzi
  what
    Scenario: a build without a backup
      Given a new install of a build without a Firebase configuration
      When the app opens
      Then no Welcome and no sign-in is offered: Your baby comes first
      And Your baby's hint reads "Names and birth dates stay on this phone and your watch."
      When the parent skips the first run and opens Settings
      Then Account reads "Backup isn't set up in this build. Everything is saved on this phone." and can't be tapped
      And there is no Family section
  how
    decide without a Firebase configuration in the build (or in the unit tests and checks, which turn accounts off) no Firebase code runs: no Welcome, no consent, no cloud icon on Home, and Settings › Account says so and does nothing
      because the app builds and runs as before without the file; Firebase never starts in the tests (docs/BACKEND.md › Connect the app; app/build.gradle.kts)
    decide Settings › Account is the first section: one row that opens the Account screen and says where the log is: "Not signed in" · "Your log is only on this phone. Sign in to back it up."; or the address with "Backing up…", "Backed up · 14:31", "Saved on phone · will back up", "Couldn't back up · Tap to retry" (a tap also backs up again) or "Backup deleted · Back up now to start again"
      because the parent must always see where the log is (BABIES_AND_BACKUP §3.2)
    decide while signed in, Home's top bar shows the backup as a small cloud icon next to the watch's: a tick, an arrow while backing up, a slash offline, and "!" in the error colour (a button that backs up again) or for a deleted backup; spoken as the row's status line
      because the backup's state belongs where the parent looks, without words (BABIES_AND_BACKUP §3.2; DESIGN_SPEC §4.5)
    decide only the phone talks to the backend; the watch gets everything through the phone
      because the phone is the hub (BABIES_AND_BACKUP §1)
    decide a sync runs at start, on sign-in, when the app comes to the front (at most once a minute, every time after a failure), about 2 s after a local write, from Back up now, and in the background about 30 s after rows become pending; always a pull before a push, one at a time; a call without an answer within 30 s counts as offline
      because the free plan's quota must last, and a newer version from another phone must win before this one is sent (BABIES_AND_BACKUP §4.3, §4.4)
    decide this node's check is the build without a backup (accounts off, as in every unit test); the children's checks are the build with one: a debug build pointed at the local Firebase Authentication and Firestore emulators with a demo project, the emulators started for the checks; Google sign-in is never checked
      because the checks run the real app on the JVM, where Google Play services are missing, and a demo project needs no credentials (docs/BACKEND.md › Running the app against the emulators; app/…/account/FirebaseEmulators.kt)
    decide in the build with a backup, Your baby's hint reads "Names and birth dates are shared with your family and kept in your backup." once signed in, and "Names and birth dates stay on this phone and your watch." otherwise
      because the parent must know where the names go (BABIES_AND_BACKUP §2.1, §3.6)
  look
    The Account row: a color.surface.container group with radius.row corners, holding the line in type.bodyMedium color.text.dim with
      16dp padding; signed in, a link row with the address in type.titleMedium color.text and the status in
      type.dataMedium color.text.dim, color.error for an error or a deleted backup
  copy
    section      "Account"                                                          ; da "Konto"
    unavailable  "Backup isn't set up in this build. Everything is saved on this phone."
    unavailable.da "Backup er ikke sat op i denne version. Alt gemmes på denne telefon."
    signedOut    "Not signed in"
    signedOut.body "Your log is only on this phone. Sign in to back it up."
    syncing      "Backing up…"
    synced       "Backed up · {time}"
    offline      "Saved on phone · will back up"
    error        "Couldn't back up · Tap to retry"
    deleted      "Backup deleted · Back up now to start again"
  children  Welcome, SignIn, SignedIn, BackupConsent, Family, JoinFamily
  sources
    docs/design/BABIES_AND_BACKUP.md §1, §3 (account and family), §4 (sync)
    docs/BACKEND.md (the backend, Connect the app, What the app does)
    driven on Robolectric (accounts turned off, as in every test), 2026-11-27: no Welcome, Settings › Account reading
      "Backup isn't set up in this build…", no Family section; and against the Firebase emulators (the children)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/AppAccounts.kt and FirebaseConfig.kt (when accounts are off)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt, the Account row
    app/src/main/res/values/strings.xml, the account strings
```
