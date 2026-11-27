# Your baby

The first run's first screen asks for the baby's name and birth date, both
optional, with why a birth date helps. Continue saves what was given; "Skip
for now" and Back save nothing.

```ail
node YourBaby : flow   level: experience
  why   The baby's name makes every screen and notification read naturally, and a
        birth date makes the predictions fit the baby's age; but a tired parent must
        be able to skip both and add them later.   <- FirstRun
  what
    Scenario: what your baby asks
      Given a new install
      When the app opens
      Then the screen reads "Your baby" over "Both are optional. You can skip them now and add them later in Settings › Baby."
      And it offers a Name field, "Add birth date", Continue and "Skip for now"
      And it says why a birth date helps, and that names and birth dates stay on this phone and the watch
    Scenario: naming the baby and giving a birth date
      Given a new install on Your baby
      When the parent types "Noa", taps "Add birth date", picks today and taps OK
      Then the pill reads "Born" and today's date, as "Born Nov 27, 2026"
      When the parent taps Continue and skips the introduction
      Then Home's top bar reads "Noa"
      And Settings shows the name "Noa" and the same "Born" date
    Scenario: a birth date can't be in the future
      Given the birth date picker open on Your baby
      Then it is titled "Birth date", shows the current month, and tomorrow can't be tapped
      When the parent taps Cancel
      Then the pill still reads "Add birth date"
    Scenario: continuing with nothing given
      Given a new install on Your baby
      When the parent taps Continue without typing a name or picking a date
      Then the introduction follows, and afterwards Home's top bar reads "Baby"
    Scenario: going back skips your baby
      Given a new install on Your baby with "Noa" typed
      When the parent goes back
      Then the introduction follows, and afterwards Home's top bar reads "Baby"
  how
    decide Continue saves the name (trimmed, at most the baby name's length) and the birth date, each only when given, for the first baby; "Skip for now" and Back save nothing; any of the three answers the screen for good
      because both are optional and said to be so; the screen must never stand between the parent and the log (commit cab6762)
    decide the birth date picker is Material's, titled "Birth date", with today and earlier only and years back to five years ago; OK picks, Cancel leaves it as it was
      because a birth date in the future is impossible, and babies older than five are not expected (app/…/settings/SettingsScreen.kt, the birth date dialog)
    decide a picked date reads "Born" and the date in the app language's medium style ("Born Nov 27, 2026"; Danish "Født 27. nov. 2026")
      because it is the same pill as in Settings › Babies (DESIGN_SPEC §3.3.5)
    decide the hint says where names and birth dates go: this phone and the watch, or, signed in, the family and the backup
      because names are shared only with the family, and the parent is told where they go (BABIES_AND_BACKUP §1)
    decide the screen's line still says "Settings › Baby", although with two or more babies that section is called Babies
      because "unknown, confirm with the owner"
  look
    Full screen on color.background, padding gutter.phone, a column with 12dp between its parts: "Your baby" in
      type.headlineSmall color.text, the optional line in type.bodyMedium color.text.dim, the Name field (outlined,
      16dp corners, color.outline, label "Name", placeholder "Baby", the text in type.titleMedium), the birth date pill
      (outlined, the plus icon, or the edit icon once a date is picked), the why line in type.bodyMedium color.text,
      the hint in type.bodySmall color.text.dim; then Continue, filled color.primary.container, full width, and
      "Skip for now" as a full-width text button in type.labelLarge color.text
  copy
    title          "Your baby"                      ; da "Dit barn"
    optional       "Both are optional. You can skip them now and add them later in Settings › Baby."
    name           "Name"                           ; da "Navn"
    namePlaceholder "Baby"
    birthAdd       "Add birth date"                 ; da "Tilføj fødselsdato"
    birthSet       "Born {date}"                    ; da "Født {date}"
    why            "The birth date is optional, but it improves the predictions and estimates: they then follow the baby's age."
    hintLocal      "Names and birth dates stay on this phone and your watch."
    hintFamily     "Names and birth dates are shared with your family and kept in your backup."
    continue       "Continue"                       ; da "Fortsæt"
    skip           "Skip for now"                   ; da "Spring over for nu"
    picker.title   "Birth date"                     ; da "Fødselsdato"
    picker.ok      "OK"
    picker.cancel  "Cancel"                         ; da "Annuller"
    picker.remove  "Remove"                         ; da "Fjern"
  sources
    docs/design/DESIGN_SPEC.md §3.3.5 (Your baby, the Babies birth dates)
    docs/design/BABIES_AND_BACKUP.md §2.4 (birth dates, Your baby)
    git log: cab6762 "Ask for the first baby's name and birth date on the first run"
    driven on Robolectric, 2026-11-27: Your baby, "Noa" typed, the date picker (November 2026, 28 and 29 November
      disabled), today picked ("Born Nov 27, 2026"), Continue, the introduction skipped, Home reading "Noa"; Back from
      the picker; in Danish on a phone set to Danish ("Dit barn", "Født 27. nov. 2026")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/BabyIntroScreen.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt, the name field, the birth date pill and picker
    app/src/main/res/values/strings.xml and values-da/strings.xml, the baby intro strings
```
