# The review prompt

"Enjoying Pooparazzi?" asks for a rating on Google Play, once the app is in
real use, over an idle Home by day.

```ail
node ReviewPrompt : flow   level: experience
  why   Ratings help other tired parents find the app, but asking must never get in
        the way of a log, and never before the parent has really used it.   <- Pooparazzi
  what
    Scenario: a new install is not asked
      Given a new install, its first run skipped, with a sleep started and ended
      When Home is idle
      Then no "Enjoying Pooparazzi?" shows
  how
    decide ask only when all of these hold: Home was first seen on this phone at least 3 days ago; since then (counting entries backdated to the day before) at least 15 entries are in the log, on at least 3 different days; and the parent has not rated, said "Don't ask again", or said "Not now" twice
      because only real use counts: imports and restored backups, older than that, do not (commit 35b61fe)
    decide "Not now", Back or a tap outside asks once more, 30 days later at the earliest; "Rate on Google Play" opens Pooparazzi's page in the Play Store app (or on the web without it) and never asks again; "Don't ask again" never asks again
      because a parent who said no should be left alone (commit 35b61fe)
    decide it shows only over an idle Home by day: no sheet, no Switch baby sheet, nothing running (a sleep or a feed timer), no edit sheet, no link being handled, and the Day theme; 1.5 s after Home became idle; once shown it stays until answered
      because it must never cover a log, a running sleep or a night feed (app/…/home/HomeScreen.kt, the review prompt)
    decide the time Home was first seen starts when Home is first shown; the answers travel with Android's backup and a device transfer
      because a parent who rated or said "Don't ask again" must not be asked again on a new phone (app/…/review/ReviewPrompt.kt)
    decide the checks show only that a new install is not asked: the prompt needs three days of real logging, which a check can't make through the screen in real time
      because the checks act only as a parent does, in real time (checks/e2e/Phone.kt)
  look
    A dialog over Home with a color.scrim, fill color.dialog, radius.sheet: "Enjoying Pooparazzi?" in type.headlineSmall
      color.text, the body in type.bodyMedium color.text.dim, then stacked full width: "Rate on Google Play" filled
      color.primary, "Not now" as a text button in color.text, "Don't ask again" as a text button in color.text.dim
  copy
    title   "Enjoying Pooparazzi?"
    body    "If it makes the days and nights a little easier, a quick rating on Google Play helps other tired parents find it."
    rate    "Rate on Google Play"
    later   "Not now"
    never   "Don't ask again"
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 ("Enjoying Pooparazzi?")
    README.md, "First run" (the review prompt screenshot)
    git log: 35b61fe "Ask for a Google Play review after a few days of real logging"
    driven on Robolectric, 2026-11-27: a new install, a sleep started and ended, Home idle for several seconds, no prompt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/review/ReviewRule.kt (3 days, 15 entries, 3 days, 30 days, 2 asks)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/review/ReviewPrompt.kt and ReviewDialog.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeScreen.kt, when Home shows it (1.5 s after idle)
```
