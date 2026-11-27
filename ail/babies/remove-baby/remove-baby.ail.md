# Removing a baby

With two or more babies, each name field ends with a remove button, which asks
first and then deletes the baby and its log.

```ail
node RemoveBaby : flow   level: experience
  why   A baby added by mistake must be removable, but never by a stray tap, because
        its log goes with it.   <- Babies
  what
    Scenario: removing asks first
      Given Settings with two babies, "Noa" and "Ida"
      When the parent taps the button spoken "Remove Ida"
      Then a dialog asks "Remove Ida?" with "Their log is deleted from this phone and your watch. This can't be undone.", Cancel and Remove
    Scenario: cancelling keeps the baby
      Given the "Remove Ida?" dialog
      When the parent taps Cancel
      Then the section still lists Noa and Ida
    Scenario: removing a baby
      Given the "Remove Ida?" dialog
      When the parent taps Remove
      Then the section is called "Baby" again, with Noa's field and no remove button
      And Home's top bar reads "Noa", without a switcher
    Scenario: removing the current baby
      Given two babies, "Noa" and "Ida", with Ida the current baby on Home
      When the parent removes Ida in Settings
      Then Home shows Noa, its top bar reading "Noa"
  how
    decide the remove button shows only with two or more babies; the last baby can never be removed
      because there is always at least one baby (BABIES_AND_BACKUP §2.1)
    decide Remove takes the baby out and deletes all its entries; it reaches the watch, and, signed in, the family's other phones, whose copy says "Their log is deleted for everyone in your family and from your backup. This can't be undone."
      because the baby's log must go with it everywhere, and the parent is told where (BABIES_AND_BACKUP §2.1, §2.4)
    decide a phone whose current baby was removed falls back to the first baby
      because the phone always shows one of the babies it has (BABIES_AND_BACKUP §2.1)
    decide a removed baby leaves a mark, so neither its entries arriving later nor an older watch can bring it back; a later rename on another phone does
      because last writer wins per baby, and a removal is a write like a rename (BABIES_AND_BACKUP §2.1)
  look
    A dialog over the settings screen with a color.scrim, fill color.dialog: "Remove Ida?" in type.headlineSmall color.text, the
      body in type.bodyMedium color.text.dim, then Cancel as a text button in color.text and Remove as a text button in
      color.error
  copy
    cd           "Remove {label}"                ; da "Fjern {label}"
    title        "Remove {label}?"               ; da "Fjern {label}?"
    body         "Their log is deleted from this phone and your watch. This can't be undone."
    body.family  "Their log is deleted for everyone in your family and from your backup. This can't be undone."
    cancel       "Cancel"
    confirm      "Remove"                        ; da "Fjern"
  sources
    docs/design/BABIES_AND_BACKUP.md §2.1 (removing a baby), §2.4 (the remove dialog)
    README.md, "Privacy" (the Remove a baby screenshot)
    driven on Robolectric, 2026-11-27: "Remove Ida?" with Cancel and Remove, the section back to "Baby", Home back to
      "Noa"; Ida removed while current
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt, the remove dialog
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsViewModel.kt, removing a baby
```
