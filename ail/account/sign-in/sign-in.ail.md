# Signing in

The Account screen signed out: Google first, then a sign-in link by email.
There are no passwords.

```ail
node SignIn : flow   level: experience
  why   A tired parent must never have to invent, remember or reset a password, and
        a password nobody has can't leak.   <- Account
  what
    Scenario: the account screen signed out
      Given a build with a backup, nobody signed in, the Welcome answered with "Continue without an account"
      When the parent opens Settings › Account, whose row reads "Not signed in" and "Your log is only on this phone. Sign in to back it up."
      Then it reads "Without an account, everything you log is saved only on this phone and your watch. If the phone is lost or reset, the log is lost with it." and "You can create an account at any time in Settings. Everything logged until then is backed up as soon as you do, and you can share it with your family."
      And it offers "Continue with Google", then "or use email" with an Email field and "Email me a sign-in link", and "By continuing you agree to the privacy policy."
      And there is no password field, no "Forgot password?" and no "Create account"
    Scenario: asking for a sign-in link
      Given the Account screen signed out
      When the parent types "parent@example.com" and taps "Email me a sign-in link"
      Then an email with a sign-in link goes to parent@example.com
      And the screen reads "Check your email" and "We sent a sign-in link to parent@example.com. Tap the link in it on this phone.", with "Send again" and "Use another email"
      When the parent taps "Send again"
      Then a second email goes out, and the screen still reads "Check your email"
      When the parent taps "Use another email"
      Then the Email field and "Email me a sign-in link" are back
    Scenario: an address that doesn't look right
      Given the Account screen signed out
      When the parent types "parent" and taps "Email me a sign-in link"
      Then it says "Enter a valid email address", and no email is sent
  how
    decide Google is one tap (Android's credential manager); the email link is Firebase's passwordless sign-in, sent in the app's language; the first sign-in creates the account
      because there are no passwords at all (BABIES_AND_BACKUP §1, §3.3)
    decide errors are said in words: "That link has expired or was already used. Send a new one.", "Too many sign-in emails today. Try again tomorrow, or use Google." (the free plan's daily limit), "Too many tries. Wait a moment and try again.", "No Google account on this phone. Add one in system settings, or use email.", "No connection. Try again.", "Something went wrong. Try again."; closing Google's account picker says nothing
      because the parent must know what to do next (BABIES_AND_BACKUP §3.3)
    decide a call without an answer within 30 s says "No answer yet. It may still go through in a moment."; should the sign-in go through after all, the screen follows the account
      because Firebase keeps trying in the background (BABIES_AND_BACKUP §3.3)
    decide an invite link opened while signed out shows "Sign in to join the family." on this screen
      because joining needs an account (BABIES_AND_BACKUP §3.5)
    decide the checks sign in by email on the local Firebase emulators: the email is the Authentication emulator's list of sent links, opened as an App Link after the browser follows its redirect; Google sign-in, the quota, rate, offline and timeout errors are not checked (the link itself: SignInLink)
      because Google's account picker needs Google Play services, which the checks' phone (Robolectric) lacks, and the emulators don't enforce the free plan's limits (docs/BACKEND.md › Running the app against the emulators)
  look
    Full screen with a top bar ("Account", Back) on color.background, inside gutter.phone: the two explaining paragraphs
      in type.bodyMedium color.text.dim, "Continue with Google" filled color.primary.container with the Google logo,
      "or use email" in type.labelMedium color.text.dim, the outlined Email field and "Email me a sign-in link" as an
      outlined button; the consent line in type.bodySmall color.text.dim with "privacy policy" underlined in color.text;
      errors in type.bodySmall color.error under the field
  copy
    body.local   "Without an account, everything you log is saved only on this phone and your watch. If the phone is lost or reset, the log is lost with it."
    body.later   "You can create an account at any time in Settings. Everything logged until then is backed up as soon as you do, and you can share it with your family."
    google       "Continue with Google"
    or           "or use email"
    email        "Email"
    link         "Email me a sign-in link"
    privacy      "By continuing you agree to the {privacy policy}."
    sent.title   "Check your email"
    sent         "We sent a sign-in link to {email}. Tap the link in it on this phone."
    again        "Send again"
    another      "Use another email"
    invalid      "Enter a valid email address"
    linkInvalid  "That link has expired or was already used. Send a new one."
    quota        "Too many sign-in emails today. Try again tomorrow, or use Google."
    tooMany      "Too many tries. Wait a moment and try again."
    noGoogle     "No Google account on this phone. Add one in system settings, or use email."
    offline      "No connection. Try again."
    timedOut     "No answer yet. It may still go through in a moment."
    other        "Something went wrong. Try again."
    joinWaiting  "Sign in to join the family."
  children  SignInLink
  sources
    docs/design/BABIES_AND_BACKUP.md §3.3 (Account screen and signing in), §3.5
    docs/BACKEND.md (Turn on sign-in, App Links, Connect the app, Running the app against the emulators)
    README.md, "Backup and families" (Sign in, Check your email)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/account/AccountScreen.kt and AccountViewModel.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/FirebaseAccounts.kt
    app/src/main/res/values/strings.xml, the account strings
    driven on Robolectric against the Firebase emulators, 2026-11-27: the signed-out Account screen, a wrong address,
      the link sent, sent again and opened, a link asked for elsewhere, a used link
```
