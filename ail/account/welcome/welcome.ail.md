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
      Then Your baby follows, its hint reading "Names and birth dates stay on this phone and your watch."
      And after the first run, Home, with Settings › Account reading "Not signed in"
    Scenario: signing in on the welcome
      Given the Welcome
      When the parent types "parent@example.com", taps "Email me a sign-in link" and opens the link in the email
      Then the second card reads "Check your email" until the link is opened, and then the consent asks "Back up to Google Firebase?"
      And after "I agree", Your baby follows, its hint reading "Names and birth dates are shared with your family and kept in your backup."
  how
    decide the two choices are equal cards, the one without an account first
      because it must never be hidden below the sign-in form (BABIES_AND_BACKUP §3.1)
    decide signing in, "Continue without an account" or Back answers it; a widget, notification or invite link that opened the app is followed after it; the answer is kept on this phone only, out of Android's backup
      because a new phone should ask again (BABIES_AND_BACKUP §3.1)
    decide a phone that is already signed in (an update) never shows it, nor does a build without a Firebase configuration
      because there is nothing to choose then (app/…/app/AppViewModel.kt)
    decide the checks run the app on the local Firebase emulators (a debug build's switch); "Continue with Google" is shown but not tapped
      because Google's account picker needs Google Play services, which the checks' phone (Robolectric) lacks (docs/BACKEND.md › Running the app against the emulators)
  look
    Full screen on color.background, inside gutter.phone: the title in type.headlineSmall color.text, the intro in
      type.bodyMedium color.text.dim, then two cards of color.surface.container with radius.row corners, each with its icon (the phone,
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
    babyHint.local  "Names and birth dates stay on this phone and your watch."
    babyHint.backup "Names and birth dates are shared with your family and kept in your backup."
  uses      SignIn
  sources
    docs/design/BABIES_AND_BACKUP.md §3.1 (First run)
    docs/design/DESIGN_SPEC.md §3.3.5 (the Welcome)
    README.md, "Backup and families" (the Welcome screenshot)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/account/AccountScreen.kt, the Welcome
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/app/AppViewModel.kt, when it shows
    driven on Robolectric against the Firebase Authentication and Firestore emulators, 2026-11-27: the Welcome, Continue
      without an account, and signing in by the emailed link (read from the emulator's sent emails)
```
