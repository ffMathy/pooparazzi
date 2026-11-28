# Signed in

The Account screen while signed in: the address and the backup's state, Back
up now, Sign out, and Delete account with its confirmation.

```ail
node SignedIn : flow   level: experience
  why   A parent should see that the log is backed up, be able to stop the backup at
        any time, and delete the account and what it stored from inside the app.   <- Account
  what
    Scenario: the account screen signed in
      Given a phone signed in by email as "parent@example.com", agreed to the backup, backed up at 14:31
      When the parent opens Settings › Account
      Then it reads "Signed in as" and "parent@example.com", "Backed up · 14:31", with "Back up now", "Sign out" and "Delete account…"
      And "Signing out keeps the log on this phone."
    Scenario: signing out
      Given the Account screen signed in as "parent@example.com"
      When the parent taps "Sign out"
      Then the Account screen is signed out, with the Email field and "Email me a sign-in link"
      And Settings › Account's row reads "Not signed in"
    Scenario: deleting the account asks first
      Given the Account screen signed in by email
      When the parent taps "Delete account…"
      Then "Delete account?" asks in place, saying "This deletes your account and your place in the family. If you're the family's only member, its backup is deleted too. The log on this phone stays."
      And "We'll email you a link to confirm. Open it on this phone.", with Cancel and "Email me a link"
      When the parent taps Cancel
      Then the confirmation closes and "Delete account…" is back
    Scenario: deleting the account with the emailed link
      Given the confirmation "Delete account?" of an account signed in by email as "parent@example.com"
      When the parent taps "Email me a link"
      Then it reads "Check your email. Tapping the link in it on this phone deletes your account.", with Cancel and "Send again"
      When the parent opens the link in the email on this phone
      Then the account is deleted from the backend, and the Account screen is signed out
  how
    decide signing out keeps the log on the phone, withdraws the consent on this phone and uploads nothing more
      because the backup must be stoppable at any time (BABIES_AND_BACKUP §3.3, §3.6)
    decide deleting asks to sign in again: Google's account picker for a Google account ("You'll sign in with Google again to confirm.", Cancel and Delete); for an email account the emailed link, which completes it while the confirmation is open, with no further tap; Back or Cancel closes the confirmation and forgets the link; a deletion link that opens the app while it is closed opens it again with "This link deletes your account. Tap Delete to go ahead."
      because nothing is ever deleted without the confirmation on screen, and Google Play requires deletion inside the app (BABIES_AND_BACKUP §3.3)
    decide deleting leaves the family (deleting its entries and babies first when nobody else is in it, handing it over to the member who joined first when you started it), deletes the invites made on this phone that are still open, the backup from before families and the account's record, and then the account; the log on the phone stays
      because what the backend holds for the account must go with it (BABIES_AND_BACKUP §3.3; docs/BACKEND.md › What the app does)
    decide partial failures are said as they are: "Couldn't delete your backup, so your account was kept. Try again." or "Your backup was deleted, but your account wasn't. Try again."; until then nothing is uploaded for the account, also after a restart, and the status reads "Backup deleted · Back up now to start again", which "Back up now" or another sign-in ends; closing Google's picker says nothing
      because the parent must know exactly what is left (BABIES_AND_BACKUP §3.3)
    decide "Sign in again to delete your account." and "That's another account. Choose the one you're signed in with." when the confirmation's sign-in doesn't fit
      because the deletion must be confirmed by the same account (app/…/account/AccountViewModel.kt)
    decide the checks use an email account on the local Firebase emulators; the Google confirmation, the partial failures and a deletion link opened while the app is closed are not checked
      because Google's account picker needs Google Play services, which the checks' phone (Robolectric) lacks, and the emulators can't be made to fail half way (docs/BACKEND.md › Running the app against the emulators)
  look
    Under the top bar, inside gutter.phone: "Signed in as" in type.labelMedium color.text.dim over the address in
      type.titleMedium color.text and the status in type.dataMedium color.text.dim; "Back up now" filled
      color.primary.container, "Sign out" outlined, the hint in type.bodyMedium color.text.dim, and "Delete account…" as a
      text button in color.error; the confirmation in place of the buttons, its title in type.titleLarge color.text, its
      body in type.bodyMedium color.text.dim, Cancel and the confirming button (color.error)
  copy
    signedInAs   "Signed in as"
    backUpNow    "Back up now"
    signOut      "Sign out"
    hint         "Signing out keeps the log on this phone."
    delete       "Delete account…"
    delete.title "Delete account?"
    delete.body  "This deletes your account and your place in the family. If you're the family's only member, its backup is deleted too. The log on this phone stays."
    delete.google "You'll sign in with Google again to confirm."
    delete.link  "We'll email you a link to confirm. Open it on this phone."
    delete.send  "Email me a link"
    delete.sent  "Check your email. Tapping the link in it on this phone deletes your account."
    delete.again "Send again"
    delete.ready "This link deletes your account. Tap Delete to go ahead."
    delete.backupFailed  "Couldn't delete your backup, so your account was kept. Try again."
    delete.accountFailed "Your backup was deleted, but your account wasn't. Try again."
    recentLogin  "Sign in again to delete your account."
    wrongAccount "That's another account. Choose the one you're signed in with."
  sources
    docs/design/BABIES_AND_BACKUP.md §3.2, §3.3 (signed in, Delete account)
    docs/BACKEND.md › What the app does, Running the app against the emulators
    README.md, "Backup and families" (Signed in), "Privacy" (Delete account)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/account/AccountScreen.kt and AccountViewModel.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/FamilyExit.kt
    driven on Robolectric against the Firebase emulators, 2026-11-27: signed in, Back up now, Sign out, Delete
      account by the emailed link (it completes as the link opens)
```
