# Adding a baby

"Add a baby" opens a dialog that asks for the new baby's name and birth date,
both optional. Adding does not change which baby the phone shows.

```ail
node AddBaby : flow   level: experience
  why   Twins or a sibling should get their own log in a few taps, without the
        phone switching away from the baby the parent is looking after.   <- Babies
  what
    Scenario: what add a baby asks
      Given Settings with one baby, "Noa"
      When the parent taps "Add a baby"
      Then a dialog reads "Add a baby" with a Name field, "Add birth date", why a birth date helps, where names and birth dates go, Cancel and Add
    Scenario: adding a second baby
      Given Settings with one baby, "Noa"
      When the parent adds a baby named "Ida"
      Then the section is called "Babies", with the fields of Noa and Ida and buttons spoken "Remove Noa" and "Remove Ida"
      And back on Home the top bar is a button spoken "Noa. Switch baby": Noa stays the current baby
    Scenario: adding a baby with a birth date
      Given the Add a baby dialog
      When the parent taps "Add birth date", picks today, taps OK and then Add
      Then the new baby's pill in Settings reads "Born" and today's date
    Scenario: cancelling adds nothing
      Given the Add a baby dialog with "Ida" typed
      When the parent taps Cancel
      Then the section is still called "Baby", with one Name field and no remove button
    Scenario: an unnamed second baby
      Given Settings with one unnamed baby
      When the parent adds a baby without a name
      Then the remove buttons are spoken "Remove Baby 1" and "Remove Baby 2"
      And Home's top bar is spoken "Baby 1. Switch baby"
    Scenario: at most eight babies
      Given Settings with seven babies
      When the parent adds an eighth
      Then "Add a baby" is gone, and there are eight Name fields
  how
    decide Add adds the baby with what was given, both optional; Cancel, Back or a tap outside adds nothing; the current baby stays
      because adding must not switch the baby the parent is logging for (BABIES_AND_BACKUP §2.4)
    decide the dialog's Name field shows the new baby's label as its placeholder ("Baby 2" when it will be second)
      because that is what it will be called until it is named (app/…/settings/SettingsScreen.kt, the Add a baby dialog)
    decide "Add a baby" is hidden at eight babies
      because eight is the most the app supports (BABIES_AND_BACKUP §2.1)
    decide while a paired watch runs a Pooparazzi from before several babies, "Add a baby" can't be tapped and "Update Pooparazzi on your watch first." shows under it
      because such a watch ignores babies and would take one twin's sleep or diaper for a duplicate of the other's (BABIES_AND_BACKUP §2.4)
    decide a new baby gets a new random id and goes to the watch, and, signed in, to the family's backup
      because babies are matched by id, never by name (BABIES_AND_BACKUP §2.1)
  look
    A dialog over the settings screen with a color.scrim: a card of color.dialog with radius.sheet corners and 24dp padding, 280 to
      560dp wide: "Add a baby" in type.headlineSmall color.text, the Name field, the birth date pill, the why line in
      type.bodyMedium color.text, the hint in type.bodySmall color.text.dim, and Cancel and Add as text buttons at the end
  copy
    title       "Add a baby"           ; da "Tilføj et barn"
    confirm     "Add"                  ; da "Tilføj"
    cancel      "Cancel"               ; da "Annuller"
    watchUpdate "Update Pooparazzi on your watch first."
  sources
    docs/design/BABIES_AND_BACKUP.md §2.1 (at most 8), §2.4 (Add a baby, the old watch)
    docs/design/DESIGN_SPEC.md §3.3.5 (the Add a baby dialog)
    git log: 83a5708 "Ask for the birth date when adding a baby"
    driven on Robolectric, 2026-11-27: Add a baby with "Ida" and today as the birth date, Cancel, an unnamed second
      baby ("Remove Baby 1", "Baby 1. Switch baby"), eight babies ("Add a baby" gone)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt, the Add a baby dialog
```
