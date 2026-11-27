# The Welcome

The first time the app opens in a build with a backup and nobody signed in, it
explains the choice before anything else: only on this phone, or backed up.

```ail
node Welcome : flow   level: experience
  why   A parent must know, before logging, that without an account the log lives only
        on this phone, and that an account can come later.   <- Account
  what
    Scenario: the welcome on a first run with backup
      Given a new install of a build with a backup, nobody signed in
      When the app opens
      Then the screen reads "No account needed" over "Pooparazzi works fully without an account. Choose where your log is kept. You can change your mind later."
      And a first card "Only on this phone" says the log is lost with the phone, that cloud backup can be switched on later in Settings › Account, and offers "Continue without an account"
      And a second card "Back up to the cloud" offers "Continue with Google", then "or use email" with an Email field and "Email me a sign-in link", and "By continuing you agree to the privacy policy."
    Scenario: continuing without an account
      Given the Welcome
      When the parent taps "Continue without an account"
      Then Your baby follows, and the Welcome never shows again on this phone
  how
    decide the two choices are equal cards, the one without an account first
      because it must never be hidden below the sign-in form (BABIES_AND_BACKUP §3.1)
    decide signing in, "Continue without an account" or Back answers it; a widget, notification or invite link that opened the app is followed after it; the answer is kept on this phone only, out of Android's backup
      because a new phone should ask again (BABIES_AND_BACKUP §3.1)
    decide a phone that is already signed in (an update) never shows it, nor does a build without a Firebase configuration
      because there is nothing to choose then (app/…/app/AppViewModel.kt)
    decide it can't be checked here: it needs a build with a Firebase configuration, which the checks turn off
      because the checks run with accounts turned off (app/build.gradle.kts)
  look
    Full screen on color.background, inside gutter.phone: the title in type.headlineSmall color.text, the intro in
      type.bodyMedium color.text.dim, then two cards of color.row with radius.row corners, each with its icon (the phone,
      the cloud), its title in type.titleMedium color.text and its body in type.bodyMedium color.text.dim; the first ends
      with the filled "Continue without an account" (color.primary.container), the second with the sign-in choices
  copy
    title        "No account needed"
    intro        "Pooparazzi works fully without an account. Choose where your log is kept. You can change your mind later."
    local.title  "Only on this phone"
    local.body   "Everything you log is saved only on this phone and your watch. If the phone is lost or reset, the log is lost with it."
    later        "You can switch to cloud backup at any time in Settings › Account. Everything logged until then is backed up as soon as you do."
    continue     "Continue without an account"
    cloud.title  "Back up to the cloud"
    cloud.body   "Sign in to back up your log and share it with your family."
  uses      SignIn
  sources
    docs/design/BABIES_AND_BACKUP.md §3.1 (First run)
    docs/design/DESIGN_SPEC.md §3.3.5 (the Welcome)
    README.md, "Backup and families" (the Welcome screenshot)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/account/AccountScreen.kt, the Welcome
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/app/AppViewModel.kt, when it shows
    not driven: accounts are off in every test and check (app/build.gradle.kts)
```
