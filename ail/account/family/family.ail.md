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
      And it says "Only you so far. Invite someone to share the log and the babies.", with "Invite someone"
    Scenario: inviting someone
      Given the Family section
      When the parent taps "Invite someone"
      Then the share sheet opens with "Join my family on Pooparazzi to share our baby log:", the invite's link and "(works once, for 6 days)"
    Scenario: leaving the family asks first
      Given a family of two
      When the parent taps "Leave family"
      Then it asks "Leave the family?" with "You'll stop seeing its log and babies. Your phone keeps its log and starts a new family of your own.", Cancel and Leave
  how
    decide the members are listed one row each, you first and then in the order they joined, each with its address; "You" on your own row, "Started the family" on the creator's, "You · Started the family" when both
      because every member sees who else is in the family (BABIES_AND_BACKUP §1, §3.4)
    decide an invite is single use, valid 6 days (a day short of the backend's 7-day limit, so a phone clock running ahead never breaks it), works only while its maker is still a member; the phone remembers its code, so leaving, joining another family or deleting the account deletes the invites still open
      because invites can't be listed, and a stale one must not let anyone in (BABIES_AND_BACKUP §3.4)
    decide a refused invite says "Couldn't create an invite. You may have been removed from the family, or your email address isn't verified yet."
      because only members with a verified address may invite (docs/BACKEND.md › The rules)
    decide Leave (two or more members) deletes your membership and starts a new family of your own in one step, handing the old one over to the member who joined first if you started it; the phone uploads its log and babies there
      because a phone is always in exactly one family (BABIES_AND_BACKUP §3.4, §4.5)
    decide only the family's creator sees Remove on the others' rows; it asks "Remove parent2@example.com?" with "They'll stop seeing the family's log from now on. What's already on their phone stays there." and deletes their membership; their invites stop working; a removed phone finds out at start, when it comes to the front or when an action is refused, and starts a family of its own
      because the family's creator decides who stays (BABIES_AND_BACKUP §3.4)
    decide "Loading the family…", "Setting up your family…" and "Couldn't load the family. Tap to try again." while the family is not known
      because the section must say what it is waiting for (app/…/family/FamilySection.kt)
    decide it can't be checked here: it needs signed-in accounts in a family
      because the checks run with accounts turned off
  look
    A section headed "Family" under the account row: one color.row row per member (address in type.titleMedium
      color.text, the role in type.bodySmall color.text.dim, Remove as a text button in color.error for the creator),
      then "Invite someone" as an outlined pill with the plus icon and "Leave family" as a text button
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
    remove.title "Remove {email}?"
    remove.body "They'll stop seeing the family's log from now on. What's already on their phone stays there."
    loading     "Loading the family…"
    waiting     "Setting up your family…"
    failed      "Couldn't load the family. Tap to try again."
  sources
    docs/design/BABIES_AND_BACKUP.md §3.4 (Settings › Family), §4.5
    docs/BACKEND.md (The rules, What the app does)
    README.md, "Backup and families" (Family), "Privacy" (Remove a member)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/family/FamilySection.kt and FamilyViewModel.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/account/FamilyService.kt
    not driven: accounts are off in every test and check
```
