# Babies

Settings › Baby (Babies, with two or more): a name and an optional birth date
per baby, adding and removing babies, and on Home the switcher that chooses
which baby the phone shows and logs for.

```
babies.ail.md                    Babies: the name and birth date fields in Settings
add-baby/add-baby.ail.md         AddBaby: the Add a baby dialog, labels, the limit of eight
remove-baby/remove-baby.ail.md   RemoveBaby: the remove button and its confirmation
switch-baby/switch-baby.ail.md   SwitchBaby: Home's switcher, the Switch baby sheet, each baby's own log
```

```ail
node Babies : flow   level: experience
  why   A baby's name makes the app speak of the baby, not "Baby", and twins or
        siblings each need their own log, sleep and predictions.   <- Pooparazzi
  what
    The log is kept per baby; the phone shows and logs for its current baby. With one baby every screen looks as if there were no babies at all.
    Scenario: naming the baby in settings
      Given a new install, its first run skipped, with one unnamed baby
      When the parent types "Noa" into Settings › Baby › Name and goes back to Home
      Then Home's top bar reads "Noa"
    Scenario: one baby has no remove button
      Given Settings with one baby
      Then the section is called "Baby", with one Name field, "Add birth date", "Add a baby" and no remove button
      And under them "The birth date is optional, but it improves the predictions and estimates: they then follow the baby's age." and "Names and birth dates stay on this phone and your watch."
    Scenario: giving and removing a birth date in settings
      Given Settings with one baby without a birth date
      When the parent taps "Add birth date", picks today and taps OK
      Then the pill reads "Born" and today's date, and the line about why a birth date helps is gone
      When the parent taps that pill and then Remove
      Then the pill reads "Add birth date" again
  how
    decide a name is saved as typed, 500 ms after the last keystroke, and at once on Done; an empty name shows the baby's label as the field's placeholder
      because typing a name must not wait for a save button (BABIES_AND_BACKUP §2.4)
    decide a baby's label is its name; unnamed, it is "Baby" while it is the only one, and "Baby 1", "Baby 2" … (its place) with several, in Danish too
      because twins without names must still be told apart (BABIES_AND_BACKUP §2.2; seen driving the app: the unnamed first baby reads "Baby 1" once a second is added)
    decide the line about why a birth date helps shows while any baby has none
      because it says why to give one, and is noise once all are given (commit 83a5708)
    decide the birth date picker offers Remove only when a date is set; Remove forgets it, Cancel leaves it as it was
      because a wrong birth date must be removable (DESIGN_SPEC §3.3.5)
    decide names and birth dates stay on this phone and the watch; with an account they also go to the family's backup, where only the family's members can read them, and the hint says so
      because names are shared only with the family (BABIES_AND_BACKUP §1)
    decide the first baby's id is "default" on every phone; there is always at least one baby, at most eight, in the order they were added
      because joining a family makes the two first babies one (BABIES_AND_BACKUP §2.1, §3.5)
  look
    Under the section heading, per baby: the Name field (outlined, 16dp corners, color.outline, label "Name", the label as
      placeholder, the text in type.titleMedium color.text), with two or more babies a size.target remove button with the
      delete icon at its end; under it the birth date pill (outlined, radius.chip, the plus icon or, once set, the edit
      icon). Then "Add a baby" as an outlined pill with the plus icon, the why line and the hint in type.bodySmall
      color.text.dim
  copy
    section        "Baby"                ; da "Barn"
    section.many   "Babies"              ; da "Børn"
    name           "Name"                ; da "Navn"
    label          "Baby"
    label.numbered "Baby {n}"
    birthAdd       "Add birth date"
    birthSet       "Born {date}"
    why            "The birth date is optional, but it improves the predictions and estimates: they then follow the baby's age."
    hint           "Names and birth dates stay on this phone and your watch."
    hint.family    "Names and birth dates are shared with your family and kept in your backup."
  children  AddBaby, RemoveBaby, SwitchBaby
  sources
    docs/design/BABIES_AND_BACKUP.md §2 (the model, labels, what is per baby, the phone's screens)
    docs/design/DESIGN_SPEC.md §3.3.5 (Babies, birth dates)
    README.md, "Several babies"
    git log: 83a5708 "Ask for the birth date when adding a baby"
    driven on Robolectric, 2026-11-27: a name typed in Settings, Home's top bar; a birth date given and removed
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt, the babies group
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsViewModel.kt, the name saved 500 ms after typing
```
