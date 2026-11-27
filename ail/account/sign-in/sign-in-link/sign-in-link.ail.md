# The sign-in link

The link in the sign-in email opens the app and signs in, on the phone that
asked for it; anywhere else it asks for the address first.

```ail
node SignInLink : flow   level: experience
  why   The link must sign in only on the phone it was meant for, and a parent who
        opens it elsewhere or twice must know what to do.   <- SignIn
  what
    Scenario: the link in the email signs in
      Given the Account screen reading "Check your email" for parent@example.com
      When the parent opens the link in the email on this phone
      Then the consent asks "Back up to Google Firebase?" for parent@example.com
      And after "I agree", Home, its cloud icon spoken "Backed up · 14:31" (the time of the backup), and Settings › Account's row reads "parent@example.com" over "Backed up · 14:31"
    Scenario: a link opened on a phone that didn't ask for it
      Given a new install showing the Welcome, and a sign-in link to parent@example.com that another phone asked for
      When the parent opens the link on this phone
      Then the card asks "To finish signing in, enter the email address the link was sent to.", with the Email field and "Sign in"
      When the parent types "parent@example.com" and taps "Sign in"
      Then the consent asks "Back up to Google Firebase?" for parent@example.com
    Scenario: a link that was used
      Given a phone that signed in with a link and then signed out
      When the parent opens the same link again
      Then the Account screen asks "To finish signing in, enter the email address the link was sent to."
      When the parent types "parent@example.com" and taps "Sign in"
      Then it says "That link has expired or was already used. Send a new one."
  how
    decide the phone remembers the address it sent a link to, until the link signs in; the link opens the app (an App Link on the project's website) and completes the sign-in; a link opened on a phone that did not ask (or no longer remembers asking) asks for the address first; without the app the link lands on the website's sign-in page, which says to open it on the phone
      because the link must sign in only on the phone it was meant for (BABIES_AND_BACKUP §3.3; docs/BACKEND.md › App Links)
    decide signing in from the Account screen ends on Home after the consent, not back on the Account screen
      because "unknown, confirm with the owner": seen driving the app (the Welcome's sign-in goes on to Your baby); should a sign-in from Settings › Account go back to the Account screen?
    decide when an email link signs in to an account that existed before, any password on it is replaced with a random one, which also ends every other session
      because the app has no passwords, so a password on the account was set by someone who registered the address before its owner (BABIES_AND_BACKUP §3.3; docs/BACKEND.md › The rules)
    decide signing in merges the phone's log and babies with the family's (nothing is lost on either side) and uploads the phone's, after the consent
      because "everything logged so far is backed up" (BABIES_AND_BACKUP §3.3, §4.2)
    decide the checks read the email from the Authentication emulator's list of sent links and open it as an App Link, after the browser follows its redirect; another phone's request is the emulator call that phone's app makes
      because the checks run one phone on the local Firebase emulators (docs/BACKEND.md › Running the app against the emulators)
  look
    The address prompt in place of the email form, in type.bodyMedium color.text.dim, over the Email field and "Sign in"
      filled color.primary.container; the error in type.bodySmall color.error under the field
  copy
    needsEmail   "To finish signing in, enter the email address the link was sent to."
    signIn       "Sign in"
    linkInvalid  "That link has expired or was already used. Send a new one."
  uses      BackupConsent
  sources
    docs/design/BABIES_AND_BACKUP.md §3.3 (signing in)
    docs/BACKEND.md (App Links, The rules, Running the app against the emulators)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/account/AccountViewModel.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/FirebaseAccounts.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/launch/AppLinks.kt
    driven on Robolectric against the Firebase emulators, 2026-11-27: the link opened, a link asked for elsewhere, a
      used link
```
