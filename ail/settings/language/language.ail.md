# Language

System default, English or Dansk. The app is written in English and Danish;
the phone's own language decides until the parent chooses.

```ail
node Language : flow   level: experience
  why   The app is made in Denmark for parents who may run their phone in English
        or Danish, or want the app in the other one.   <- Settings
  what
    Scenario: a new install follows the phone's language
      Given a new install on a phone set to English, its first run skipped
      When the parent opens Settings
      Then Language offers "System default", "English" and "Dansk", with "System default" chosen
    Scenario: choosing dansk
      Given Settings with "System default" chosen
      When the parent taps Dansk
      Then Dansk is chosen
  how
    decide the choice is applied as the app's own language through Android's per-app languages (in system settings too on Android 13 and later), which recreates the screen in that language; it is also synced to the watch
      because both apps declare English and Danish, and the watch follows the phone (V1_ADDITIONS §5)
    decide the language names are written in their own language, "English" and "Dansk", in both languages
      because a parent who can't read the current language must still find their own (core/…/values/strings.xml)
    decide the checks show the choice, not the recreated screen: under Robolectric choosing Dansk is stored and shown chosen, but the screen is not recreated in Danish; the Danish copy is shown on a phone set to Danish instead (DanishPhone)
      because the per-app language goes through the system's locale manager, which the JVM phone does not run (driven 2026-11-27)
    decide clock times are 24-hour in Danish, and in English as the phone is set
      because Danish always writes 24-hour time (V1_ADDITIONS §5)
  look
    Three radio rows, each size.row tall in color.surface.container, the language's name in type.titleMedium color.text and a radio
      button at the end, the chosen one filled color.primary
  copy
    system   "System default"   ; da "Systemstandard"
    english  "English"
    danish   "Dansk"
  children  DanishPhone
  sources
    docs/design/V1_ADDITIONS.md §5 (Internationalisation: the language setting, formatting)
    docs/design/DESIGN_SPEC.md §3.3.5
    driven on Robolectric, 2026-11-27: "System default" chosen on a new install; Dansk tapped is chosen, the screen
      stays in English (no recreation on the JVM)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/app/AppLocales.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsViewModel.kt, the language: stored first, then applied
```
