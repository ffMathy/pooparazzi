# Switching baby

With two or more babies, the name in Home's top bar is a button that opens the
Switch baby sheet. Everything on the phone then shows and logs for that baby.

```ail
node SwitchBaby : flow   level: experience
  why   A parent of twins logs for one baby, then the other, and must always see
        which baby a tap is for.   <- Babies
  what
    Scenario: the switch baby sheet
      Given two babies, "Noa" and "Ida", with Noa the current baby on Home
      When the parent taps the top bar's button spoken "Noa. Switch baby"
      Then a sheet titled "Switch baby" lists Noa and Ida, with Noa chosen, and Cancel
    Scenario: switching to the other baby
      Given the Switch baby sheet with Noa chosen
      When the parent taps Ida
      Then the sheet closes and the top bar is spoken "Ida. Switch baby"
    Scenario: cancelling keeps the baby
      Given the Switch baby sheet with Noa chosen
      When the parent taps Cancel
      Then the sheet closes and the top bar is still spoken "Noa. Switch baby"
    Scenario: each baby has its own sleep
      Given two babies, "Noa" and "Ida", with Ida the current baby
      When the parent starts a sleep for Ida and switches to Noa
      Then Home's sleep button reads "Start sleep" for Noa
      When the parent switches back to Ida
      Then the sleep button reads "Woke up": Ida's sleep is still running
    Scenario: history names the current baby
      Given two babies, "Noa" and "Ida", with Ida the current baby
      When the parent opens History
      Then "Ida" shows under the title "History"
  how
    decide the current baby is this phone's own: never synced to the watch or the family; the watch chooses its own
      because each device logs for the baby in front of it (BABIES_AND_BACKUP §2.1)
    decide a tap on a row chooses that baby, plays the segment tick and closes the sheet; Cancel or Back keeps the baby
      because a switch is one tap, and the sheet must not linger (BABIES_AND_BACKUP §2.4)
    decide switching closes any open card on Home and resets the two-second guard
      because a card's Undo belongs to the other baby, and the first tap for the other baby must count (BABIES_AND_BACKUP §2.4)
    decide everything in the log is per baby: Home, History, Statistics, the edit sheet, the widgets, the tile and the complications; one running sleep per baby, so twins can sleep at the same time; duplicates are found among one baby's entries only
      because twins' sleeps and diapers often fall together (BABIES_AND_BACKUP §2.3)
    decide two things span all babies: the running-sleep notification (one per sleeping baby, each with its own Woke up) and Auto appearance, which dims while any baby sleeps
      because a parent must not lose sight of a sleeping baby when switching to the other (BABIES_AND_BACKUP §2.3)
    decide History shows the current baby's label under its title only with two or more babies; Statistics shows none
      because with one baby every screen looks as before, and Statistics is reached from that baby's History (BABIES_AND_BACKUP §2.4; commit 85cd56c)
  look
    Home's top bar: the baby's name as a size.target button with a 16dp chevron-down after it, in the name's colours.
      The sheet: a bottom sheet of color.sheet with radius.sheet top corners over a color.scrim, "Switch baby" in
      type.titleLarge color.text with Cancel at the end, then one size.row radio row per baby, its label in
      type.titleMedium color.text, the chosen one's radio filled color.primary
  copy
    switcher.cd   "{label}. Switch baby"    ; da "{label}. Skift barn"
    title         "Switch baby"             ; da "Skift barn"
    cancel        "Cancel"
  sources
    docs/design/BABIES_AND_BACKUP.md §2.3 (what is per baby), §2.4 (Home's top bar, the Switch baby sheet, History)
    README.md, "Several babies"
    git log: 85cd56c "Statistics: no baby label under the title"
    driven on Robolectric, 2026-11-27: Noa and Ida, the sheet ("Switch baby", Cancel, Noa chosen), Ida chosen, a sleep
      started for Ida, Noa's Home reading Start sleep, History labelled with the current baby
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeTopBar.kt, the switcher
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/sheets/HomeSheets.kt, the Switch baby sheet
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, switching (the card and the guard)
```
