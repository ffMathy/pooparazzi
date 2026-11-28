# Settings › Family

While signed in, Settings lists the family's members, invites someone, leaves
the family and, for the one who started it, removes members.

```ail
node Family : flow   level: experience
  why   Two parents and a grandparent log for the same babies; the family is how
        they share, and who is in it must always be visible.   <- Account
  what
    Scenario: a family of one
      Given a phone signed in as "parent@example.com", alone in its family
      When the parent opens Settings
      Then a Family section under Account lists "parent@example.com" with "You · Started the family"
      And it says "Only you so far. Invite someone to share the log and the babies.", with "Invite someone" and no "Leave family"
    Scenario: inviting someone
      Given the Family section of parent@example.com
      When the parent taps "Invite someone"
      Then the share sheet opens with "Join my family on Pooparazzi to share our baby log:", the invite's link on the project's website (https://demo-pooparazzi.web.app/join/ and a 22-character code) and "(works once, for 6 days)"
    Scenario: a family of two
      Given partner@example.com joined the family parent@example.com started
      When the partner opens Settings
      Then Family lists "partner@example.com" with "You", then "parent@example.com" with "Started the family", with "Invite someone" and "Leave family", and no Remove
    Scenario: leaving the family
      Given the partner's Family section in a family of two
      When the partner taps "Leave family"
      Then it asks "Leave the family?" with "You'll stop seeing its log and babies. Your phone keeps its log and starts a new family of your own.", Cancel and Leave
      When the partner taps Cancel, then "Leave family" again and Leave
      Then Family lists only "partner@example.com" with "You · Started the family" and "Only you so far. Invite someone to share the log and the babies."
    Scenario: removing a member
      Given a family of two that parent@example.com started, the parent signed in again on the phone
      When the parent opens Settings
      Then partner@example.com's row has Remove, spoken "Remove partner@example.com"
      When the parent taps Remove
      Then it asks "Remove partner@example.com?" with "They'll stop seeing the family's log from now on. What's already on their phone stays there.", Cancel and Remove
      When the parent taps Remove
      Then Family lists only "parent@example.com" with "Only you so far. Invite someone to share the log and the babies."
  how
    decide the members are listed one row each, you first and then in the order they joined, each with its address; "You" on your own row, "Started the family" on the creator's, "You · Started the family" when both
      because every member sees who else is in the family (BABIES_AND_BACKUP §1, §3.4)
    decide an invite is single use, valid 6 days (a day short of the backend's 7-day limit, so a phone clock running ahead never breaks it), works only while its maker is still a member; the phone remembers its code, so leaving, joining another family or deleting the account deletes the invites still open
      because invites can't be listed, and a stale one must not let anyone in (BABIES_AND_BACKUP §3.4)
    decide a refused invite says "Couldn't create an invite. You may have been removed from the family, or your email address isn't verified yet."
      because only members with a verified address may invite (docs/BACKEND.md › The rules)
    decide Leave (shown with two or more members) deletes your membership and starts a new family of your own in one step, handing the old one over to the member who joined first if you started it; the phone uploads its log and babies there
      because a phone is always in exactly one family (BABIES_AND_BACKUP §3.4, §4.5)
    decide only the family's creator sees Remove on the others' rows; it deletes their membership; their invites stop working; a removed phone finds out at start, when it comes to the front or when an action is refused, and starts a family of its own
      because the family's creator decides who stays (BABIES_AND_BACKUP §3.4)
    decide "Loading the family…", "Setting up your family…" and "Couldn't load the family. Tap to try again." while the family is not known
      because the section must say what it is waiting for (app/…/family/FamilySection.kt)
    decide the checks run one phone on the local Firebase emulators, so the second member signs in on the same phone after the first signs out, and joins through the same screens their own phone has; the share sheet's text is read as Android receives it
      because the checks' phone (Robolectric) is a single app in a single process (docs/BACKEND.md › Running the app against the emulators)
  look
    A section headed "Family" under the account row: one color.surface.container row per member (address in type.titleMedium
      color.text, the role in type.bodySmall color.text.dim, Remove as a text button in color.error for the creator),
      then "Invite someone" as an outlined pill with the plus icon and "Leave family" as a text button; Leave and Remove
      ask in a dialog
  copy
    section     "Family"
    you         "You"
    creator     "Started the family"
    youCreator  "You · Started the family"
    alone       "Only you so far. Invite someone to share the log and the babies."
    invite      "Invite someone"
    invite.text "Join my family on Pooparazzi to share our baby log: {link} (works once, for 6 days)"
    invite.text.da "Bliv en del af min familie i Pooparazzi, så vi deler babyloggen: {link} (virker én gang og i 6 dage)"
    invite.refused "Couldn't create an invite. You may have been removed from the family, or your email address isn't verified yet."
    leave       "Leave family"
    leave.title "Leave the family?"
    leave.body  "You'll stop seeing its log and babies. Your phone keeps its log and starts a new family of your own."
    leave.confirm "Leave"
    remove      "Remove"
    remove.spoken "Remove {email}"
    remove.title "Remove {email}?"
    remove.body "They'll stop seeing the family's log from now on. What's already on their phone stays there."
    loading     "Loading the family…"
    waiting     "Setting up your family…"
    failed      "Couldn't load the family. Tap to try again."
  sources
    docs/design/BABIES_AND_BACKUP.md §3.4 (Settings › Family), §4.5
    docs/BACKEND.md (The rules, What the app does, Running the app against the emulators)
    README.md, "Backup and families" (Family), "Privacy" (Remove a member)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/family/FamilySection.kt and FamilyViewModel.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/FamilyService.kt
    driven on Robolectric against the Firebase emulators, 2026-11-27: a family of one, the invite's share text, a
      family of two, Leave (cancelled and done) and Remove
```
