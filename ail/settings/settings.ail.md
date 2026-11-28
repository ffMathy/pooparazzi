# Settings

One scrolling screen, opened from Home's settings button: the account, the
babies, the look, the language, the watch, the notifications and what the app
is, with a footer at the very bottom.

```
settings.ail.md                                      Settings: the screen and its sections, in order
appearance/appearance.ail.md                         Appearance: Auto, Night, Day
language/language.ail.md                             Language: System default, English, Dansk
language/danish-phone/danish-phone.ail.md            DanishPhone: the setup and Settings on a phone set to Danish
watch-settings/watch-settings.ail.md                 WatchSettings: the watch's line, Sync now, On the watch face
notification-settings/notification-settings.ail.md   NotificationSettings: the notifications link, What comes next
about/about.ail.md                                   About: version, Introduction, privacy, data, licences, footer
```

The Account and Babies sections are their own nodes (Account, Babies).

```ail
node Settings : flow   level: experience
  why   What a parent sets once (the babies, the look, the language) and what they
        rarely need (the watch, the notifications, the privacy policy, their data)
        must be in one place, out of the way of logging.   <- Pooparazzi
  what
    Scenario: the sections of settings
      Given a new install, its first run skipped, on Home
      When the parent taps Settings
      Then the screen reads "Settings" with Back, and its sections in order: Account, Baby, Appearance, Language, Watch, Notifications and About
      And at the very bottom "Made with ❤️ by a father in Aarhus, Denmark", "Pooparazzi is open source", and buttons spoken "Source code on GitHub" and "The maker on LinkedIn"
    Scenario: back returns to home
      Given Settings opened from Home
      When the parent taps Back
      Then Home shows again
  how
    decide one scrolling screen with headed sections: Account first, then Family (signed in only), Baby (Babies with two or more), Appearance, Language, Watch, Notifications, About, and the footer
      because Settings is referenced but not drawn in the design, so it follows the mockups' language (DESIGN_SPEC §3.3.5)
    decide Settings acts only while it is on screen: a second tap during a transition does nothing
      because a double tap must not close Home or open a screen twice (app/…/settings/SettingsScreen.kt)
    decide the notifications' state and each prediction's channel are read again whenever Settings comes back to the front, and so is whether the watch lacks Pooparazzi
      because they can change in the system's settings or on the watch while the app is away (DESIGN_SPEC §3.3.5)
  look
    On color.background: a top bar with the back icon (size.target) and "Settings" in type.titleLarge color.text; under it
      the sections scroll inside gutter.phone. Each section heading is type.labelMedium color.text.dim, 24dp above and 8dp
      under it. Grouped rows are color.surface.container, the first and last with radius.row corners and radius.row.inner between them,
      2dp apart. The footer is centred: the two lines in type.bodySmall color.text.dim, then the GitHub and LinkedIn
      logos as size.target buttons tinted color.text.dim
  copy
    title     "Settings"                                         ; da "Indstillinger"
    back      "Back"                                             ; da "Tilbage"
    account   "Account"                                          ; da "Konto"
    baby      "Baby"                                             ; da "Barn"
    babies    "Babies"                                           ; da "Børn"
    appearance "Appearance"                                      ; da "Udseende"
    language  "Language"                                         ; da "Sprog"
    watch     "Watch"                                            ; da "Ur"
    notifications "Notifications"                                ; da "Notifikationer"
    about     "About"                                            ; da "Om"
    madeBy    "Made with ❤️ by a father in Aarhus, Denmark"     ; da "Lavet med ❤️ af en far i Aarhus"
    openSource "Pooparazzi is open source"                       ; da "Pooparazzi er open source"
    github    "Source code on GitHub"                            ; da "Kildekoden på GitHub"
    linkedin  "The maker on LinkedIn"                            ; da "Udvikleren på LinkedIn"
  children  Appearance, Language, WatchSettings, NotificationSettings, About
  sources
    docs/design/DESIGN_SPEC.md §3.3.5 (Settings), §8.1 (Appearance)
    README.md, "Settings"
    driven on Robolectric (a fresh install, the first run skipped), 2026-11-27: Home › Settings, every section in
      order, the footer, Back
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/settings/SettingsScreen.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/navigation/PooparazziNavHost.kt, Settings and what it opens
```
