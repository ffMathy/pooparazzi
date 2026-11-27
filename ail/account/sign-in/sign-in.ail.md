# Signing in

The Account screen signed out: Google first, then a sign-in link by email.
There are no passwords.

```ail
node SignIn : flow   level: experience
  why   A tired parent must never have to invent, remember or reset a password, and
        a password nobody has can't leak.   <- Account
  what
    Scenario: the account screen signed out
      Given a build with a backup, nobody signed in
      When the parent opens Settings › Account
      Then it explains the choice as the Welcome does, and offers "Continue with Google", then "or use email" with an Email field and "Email me a sign-in link"
      And under them "By continuing you agree to the privacy policy.", the policy a link
      And there is no password field, no "Forgot password?" and no Create account
    Scenario: asking for a sign-in link
      Given the Account screen signed out
      When the parent types "parent@example.com" and taps "Email me a sign-in link"
      Then the screen reads "Check your email" and "We sent a sign-in link to parent@example.com. Tap the link in it on this phone.", with "Send again" and "Use another email"
    Scenario: an address that doesn't look right
      Given the Account screen signed out
      When the parent types "parent" and taps "Email me a sign-in link"
      Then it says "Enter a valid email address"
  how
    decide Google is one tap (Android's credential manager); the email link is Firebase's passwordless sign-in, sent in the app's language; the first sign-in creates the account
      because there are no passwords at all (BABIES_AND_BACKUP §1, §3.3)
    decide the phone remembers the address it sent a link to; the link opens the app (an App Link on the project's website) and completes the sign-in; a link opened on a phone that did not ask first asks for the address ("To finish signing in, enter the email address the link was sent to.", Sign in); without the app the link lands on the website's sign-in page, which says to open it on the phone
      because the link must sign in only on the phone it was meant for (BABIES_AND_BACKUP §3.3; docs/BACKEND.md › App Links)
    decide errors are said in words: "That link has expired or was already used. Send a new one.", "Too many sign-in emails today. Try again tomorrow, or use Google." (the free plan's daily limit), "Too many tries. Wait a moment and try again.", "No Google account on this phone. Add one in system settings, or use email.", "No connection. Try again.", "Something went wrong. Try again."; closing Google's account picker says nothing
      because the parent must know what to do next (BABIES_AND_BACKUP §3.3)
    decide a call without an answer within 30 s says "No answer yet. It may still go through in a moment."; should the sign-in go through after all, the screen follows the account
      because Firebase keeps trying in the background (BABIES_AND_BACKUP §3.3)
    decide when an email link signs in to an account that existed before, any password on it is replaced with a random one, which also ends every other session
      because the app has no passwords, so a password on the account was set by someone who registered the address before its owner (BABIES_AND_BACKUP §3.3; docs/BACKEND.md › The rules)
    decide signing in merges the phone's log and babies with the family's (nothing is lost on either side) and uploads the phone's, after the consent
      because "everything logged so far is backed up" (BABIES_AND_BACKUP §3.3, §4.2)
    decide an invite link opened while signed out shows "Sign in to join the family." on this screen
      because joining needs an account (BABIES_AND_BACKUP §3.5)
    decide it can't be checked here: a real sign-in needs a Firebase project (or the app pointed at the local emulators, which it can't be) and Google Play services for Google
      because the checks run on the JVM with accounts turned off (docs/BACKEND.md › Connect the app)
  look
    Full screen with a top bar ("Account", Back) on color.background, inside gutter.phone: "Continue with Google"
      filled color.primary.container with the Google logo, "or use email" in type.labelMedium color.text.dim, the outlined
      Email field and "Email me a sign-in link" as an outlined button; the consent line in type.bodySmall color.text.dim
      with "privacy policy" underlined in color.text; errors in type.bodySmall color.error under the field
  copy
    google       "Continue with Google"
    or           "or use email"
    email        "Email"
    link         "Email me a sign-in link"
    privacy      "By continuing you agree to the {privacy policy}."
    sent.title   "Check your email"
    sent         "We sent a sign-in link to {email}. Tap the link in it on this phone."
    again        "Send again"
    another      "Use another email"
    needsEmail   "To finish signing in, enter the email address the link was sent to."
    signIn       "Sign in"
    invalid      "Enter a valid email address"
    linkInvalid  "That link has expired or was already used. Send a new one."
    quota        "Too many sign-in emails today. Try again tomorrow, or use Google."
    tooMany      "Too many tries. Wait a moment and try again."
    noGoogle     "No Google account on this phone. Add one in system settings, or use email."
    offline      "No connection. Try again."
    timedOut     "No answer yet. It may still go through in a moment."
    other        "Something went wrong. Try again."
    joinWaiting  "Sign in to join the family."
  sources
    docs/design/BABIES_AND_BACKUP.md §3.3 (Account screen and signing in), §3.5
    docs/BACKEND.md (Turn on sign-in, App Links, Connect the app)
    README.md, "Backup and families" (Sign in, Check your email)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/account/AccountScreen.kt and AccountViewModel.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/FirebaseAccounts.kt
    app/src/main/res/values/strings.xml, the account strings
    not driven: accounts are off in every test and check
```
