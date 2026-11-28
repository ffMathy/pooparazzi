# Appearance

Auto, Night or Day: the phone's theme, and with it the widgets'.

```ail
node Appearance : flow   level: experience
  why   At night the screen must not wake anyone, by day it must be readable in
        sunlight; most parents never want to choose, so Auto follows the night and
        the baby's sleep.   <- Settings
  what
    Scenario: a new install follows auto
      Given a new install, its first run skipped
      When the parent opens Settings
      Then Appearance offers Auto, Night and Day, with Auto chosen
      And under it "Auto uses Night in the evening and at night, while a sleep runs, and when the phone is in dark theme."
    Scenario: choosing night
      Given Settings with Auto chosen
      When the parent taps Night
      Then Night is chosen, and Auto and Day are not
      And it is still chosen after going back to Home and opening Settings again
    Scenario: choosing day
      Given Settings with Night chosen
      When the parent taps Day
      Then Day is chosen, and Night is not
  how
    decide Auto is Night when any of these holds: the phone is in dark theme; the time is between 19:00 and 07:00; a sleep of any baby is running; Night and Day are fixed
      because a running sleep always gets the Night theme and lights-down, and the app asks for no location, so a fixed window stands in for sunset and sunrise (DESIGN_SPEC §8.1; BABIES_AND_BACKUP §2.3 for any baby)
    decide the choice is a setting synced to the watch with the others, and the widgets follow the app's Appearance, not the system's
      because the glance surfaces at night must match the app (DESIGN_SPEC §3.3.6, §8.5)
    decide the three are one connected choice group; the night window and "Dim for naps too" are not offered in Settings
      because "unknown, confirm with the owner"
  look
    A connected choice group of three equal segments, 48dp tall and 2dp apart, the group's outer corners 20dp and the inner
      6dp, each with a 1dp color.outline border, labels in type.labelLarge; the chosen one filled color.primary.container with
      a 16dp check before its label in color.on.primary.container; the others have no fill (the page shows through) and
      color.text labels; the hint
      under it in type.bodySmall color.text.dim, 8dp below. In Day the screen turns day.background with day.text and
      day.text.dim, the outlines and the chosen segment's fill taking the Day theme's lighter counterparts
  copy
    auto    "Auto"     ; da "Auto"
    night   "Night"    ; da "Nat"
    day     "Day"      ; da "Dag"
    hint    "Auto uses Night in the evening and at night, while a sleep runs, and when the phone is in dark theme."
    hint.da "Auto bruger Nat om aftenen og natten, mens barnet sover, og når telefonen bruger mørkt tema."
  sources
    docs/design/DESIGN_SPEC.md §3.3.5 (Appearance), §8.1 (the Appearance setting), §8.5
    docs/design/BABIES_AND_BACKUP.md §2.3 (Auto dims while any baby is asleep)
    driven on Robolectric, 2026-11-27: Auto chosen on a new install, Night, Day, back to Home and Settings again
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/theme/NightRule.kt (19:00–07:00, dark theme, a running sleep)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt and SettingsViewModel.kt
```
