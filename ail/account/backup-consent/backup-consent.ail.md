# The backup consent

Signing in only creates the account. Before anything is uploaded, a full
screen asks for the parent's explicit yes.

```ail
node BackupConsent : flow   level: experience
  why   A baby's log can say something about its health; the law and the parent's
        trust need an explicit, informed yes before any of it leaves the phone.   <- Account
  what
    Scenario: the consent after signing in
      Given a new install that has just signed in on the Welcome as "parent@example.com", not agreed on this phone
      Then a full screen asks "Back up to Google Firebase?" with "You're signed in as parent@example.com. Nothing has been uploaded yet. Before anything is, please agree to this:"
      And it says what is uploaded, who sees it, where, and how to change one's mind, then "Details are in the privacy policy."
      And it offers "I agree" and "Not now, sign out"
      And nothing is in the backend yet
    Scenario: not now signs out
      Given the consent screen on a new install, signed in on the Welcome as "parent@example.com"
      When the parent taps "Not now, sign out"
      Then Your baby follows, its hint reading "Names and birth dates stay on this phone and your watch."
      And after the first run, Settings › Account reads "Not signed in", and nothing was uploaded
    Scenario: agreeing starts the backup
      Given the consent screen on a new install, signed in on the Welcome as "parent@example.com"
      When the parent taps "I agree"
      Then the backend holds a family for parent@example.com, and after the first run Settings › Family lists "parent@example.com" with "You · Started the family"
  how
    decide until the account has agreed on this phone, nothing is read from or written to the backend for it, and the screen comes before everything else (after the Welcome, before Home, the Account screen or a join); links wait behind it; Back does what "Not now, sign out" does
      because this is the explicit consent of GDPR articles 6 and 9 (explicit consent) that the privacy policy rests on (BABIES_AND_BACKUP §3.6; docs/PRIVACY.md §3)
    decide the answer is kept per account on this phone, out of Android's backup, so a new phone asks again; signing out or deleting the account withdraws it, so the next sign-in on this phone asks again
      because consent is given per device (BABIES_AND_BACKUP §3.6)
    decide it is kept per version of the text; version 2 (November 2026) added the babies' birth dates, so every signed-in account sees it again before anything more is uploaded
      because consent covers what is uploaded, and that changed (commit 83a5708)
    decide "I agree" puts the account in its family (creating one on the first sign-in) and starts the backup
      because the family is the unit of sharing (BABIES_AND_BACKUP §3.3, §4.1)
    decide the checks look at the backend as its owner does in the console (the Firestore emulator's documents): users and families are empty until "I agree"
      because "nothing has been uploaded yet" is a promise about the backend, not the screen (docs/BACKEND.md › The model)
  look
    Laid out like the Welcome: the title in type.headlineSmall color.text, the intro in type.bodyMedium color.text.dim,
      four headed paragraphs (type.titleSmall color.text over type.bodyMedium color.text.dim), the policy line with its
      link, "I agree" filled color.primary.container and "Not now, sign out" as a text button
  copy
    title     "Back up to Google Firebase?"
    intro     "You're signed in as {email}. Nothing has been uploaded yet. Before anything is, please agree to this:"
    what.title "What is uploaded"
    what      "Your log (feeds, diapers and sleep), your babies' names and any birth dates you give, and your email address. A log like this can say something about your baby's health."
    who.title "Who sees it"
    who       "You and the members of your family: everyone in it sees the family's log, the babies' names and the members' email addresses."
    where.title "Where"
    where     "Google Firebase. The database is in the EU; Google processes it for us and uses it for nothing else."
    withdraw.title "Changing your mind"
    withdraw  "Sign out to upload nothing more, leave the family, or delete your account (Settings › Account) to delete what is stored."
    policy    "Details are in the {privacy policy}."
    agree     "I agree"
    decline   "Not now, sign out"
  uses      Welcome
  sources
    docs/design/BABIES_AND_BACKUP.md §3.6 (Consent before the first upload)
    docs/PRIVACY.md §3
    git log: 83a5708 (the consent versioned for birth dates)
    README.md, "Backup and families" (Consent)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/account/ConsentScreen.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/BackupConsent.kt
    driven on Robolectric against the Firebase emulators, 2026-11-27: the consent after an emailed link, Not now, sign
      out (no documents in the backend), and I agree
```
