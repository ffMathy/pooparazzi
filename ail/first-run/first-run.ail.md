# The first run

A new install asks for the baby's name and birth date ("Your baby"), then
shows a short introduction, and only then Home. Both can be skipped, and
neither comes back on its own.

Written from the running app (Robolectric, a fresh install, 2026-11-27), the
design documents and the phone app's code. In this build there is no backup
(no Firebase configuration), so the Welcome and the consent of the account
never come first (see Account).

```
first-run.ail.md                  FirstRun: when the first run shows, and its order
your-baby/your-baby.ail.md        YourBaby: the name, the birth date, Continue and Skip for now
introduction/introduction.ail.md  Introduction: the six pages, Next, Back, Skip, Get started
```

```ail
node FirstRun : flow   level: experience
  why   A parent who has just installed the app should be able to start logging at
        once, yet be offered the baby's name and a short tour once, never again.   <- Pooparazzi
  what
    A new install shows Your baby, then the introduction, then Home; each is shown once per phone.
    Scenario: a new install starts with your baby
      Given a new install with nothing logged
      When the app opens
      Then "Your baby" shows, not Home
      And nothing about an account or a backup is asked first
    Scenario: skipping both ends on Home
      Given a new install on Your baby
      When the parent taps "Skip for now" and then Skip on the introduction
      Then Home shows with the baby unnamed, its top bar reading "Baby"
      And nothing is logged: Last fed reads "No feeds yet"
  how
    decide the order is the Welcome (only in builds with a backup, while nobody is signed in), the consent (only while a signed-in account has not agreed), Your baby, the introduction, and then Home; a link that opened the app waits behind them
      because the Welcome explains the choice before anything is stored, and the consent must come before anything is uploaded (BABIES_AND_BACKUP §3.1, §3.6); the baby's screen and the tour come after (DESIGN_SPEC §3.3.5)
    decide Your baby shows only on a fresh start: nothing logged, and one baby with neither a name nor a birth date; once answered (Continue, Skip for now or Back) it is never asked again on this phone
      because updates and babies restored from the family's backup must never see it (commit cab6762)
    decide the introduction shows once, after Your baby, only while nothing is logged; Settings › About › Introduction shows it again at any time
      because a phone that already has a log, after an update or a restore, needs no tour (commit 26f01d0)
    decide what was answered is kept on this phone only, out of Android's backup and device transfer
      because a new phone should ask again (app/…/account/AppAccounts.kt, the first-run memory)
    decide the splash screen stays until the log and the settings have loaded
      because the first frame must already know whether Your baby or Home comes, and in the right theme (DESIGN_SPEC §3.3.5)
  children  YourBaby, Introduction
  sources
    docs/design/DESIGN_SPEC.md §3.3.5 (Your baby, the Introduction, the Welcome, the consent)
    docs/design/BABIES_AND_BACKUP.md §2.4 (Your baby), §3.1 (the Welcome), §3.6 (the consent)
    git log: cab6762 "Ask for the first baby's name and birth date on the first run", 26f01d0 "Add a skippable
      introduction, shown once after Your baby and again from Settings › About"
    driven on Robolectric (the real MainActivity, a fresh install), 2026-11-27: Your baby first, Skip for now, the
      introduction, Skip, Home reading "Baby" and "Last fed, No feeds yet"
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/app/AppViewModel.kt, when the Welcome, the consent, Your baby
      and the introduction show
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/navigation/PooparazziNavHost.kt, their order before Home
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/MainActivity.kt, the splash screen
```
