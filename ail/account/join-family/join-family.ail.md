# Joining a family

An invite link opens the app, which asks, on a screen of its own, whether to
join the inviter's family.

```ail
node JoinFamily : flow   level: experience
  why   A partner or grandparent joins with one link, and must know before joining
        that their log and babies will be shared and merged.   <- Account
  what
    Scenario: an invite asks first
      Given a phone signed in as "partner@example.com", and an invite link from "parent@example.com" opened
      Then a screen "Family invite" reads "Opening the invite…", then asks "Join parent@example.com's family?" with "Your babies and your log on this phone are added to the family's and shared with its members. You'll see theirs too."
      And "Your first baby and the family's first baby become one baby.", with Join and "Not now"
    Scenario: joining
      Given the join screen for parent@example.com's invite
      When the partner taps Join
      Then it says "You're in the family. Your log and babies are being shared.", with Done
      When the partner taps Done
      Then Home, and Settings › Family lists both
    Scenario: not now
      Given the join screen for parent@example.com's invite
      When the partner taps "Not now"
      Then Home, and Settings › Family still lists only "partner@example.com"
    Scenario: an invite opened while signed out
      Given a phone signed out, on the Account screen, and an invite link from "parent@example.com" opened
      Then the Account screen reads "Sign in to join the family."
      When the partner signs in by email as "partner@example.com" and taps "I agree"
      Then the join screen asks "Join parent@example.com's family?"
    Scenario: an invite that was used
      Given partner@example.com joined with parent@example.com's invite
      When the partner opens the same invite link again
      Then the join screen says "This invite has expired or was already used. Ask for a new one.", with Done
    Scenario: an invite from your own family
      Given partner@example.com in parent@example.com's family, holding an invite to it
      When the partner opens that invite
      Then the join screen says "You're already in this family.", with Done
    Scenario: a link that isn't an invite
      Given a phone signed in as "partner@example.com"
      When the partner opens the link https://demo-pooparazzi.web.app/join/short
      Then the join screen says "This invite link isn't valid.", with Done
  how
    decide signed out, the app first asks to sign in (the Welcome or the Account screen, with "Sign in to join the family."), then for the consent if needed, then continues to the join screen; "Opening the invite…" while it is read
      because joining needs an account that agreed to the backup (BABIES_AND_BACKUP §3.5, §3.6)
    decide Join creates your membership with the invite, points your account at the family and deletes the invite in one step; then the phone leaves its old family as when deleting the account and syncs the new family from the beginning, merging it with the phone's log
      because nothing on the phone is lost when changing family (BABIES_AND_BACKUP §3.5, §4.5)
    decide babies match by id, and every phone's first baby has the same id, so the joiner's first baby and the family's become one baby, named as it was changed last
      because they are usually the same child; a family with a different first child should add a new baby before joining (BABIES_AND_BACKUP §3.5)
    decide refusals in words: "This invite has expired or was already used. Ask for a new one.", "This invite link isn't valid." (a code that can't be one), "You're already in this family.", "No connection. Try again." with "Try again"; "Join this family?" when the inviter's address is not known
      because the parent must know why and what to do (BABIES_AND_BACKUP §3.5)
    decide without the app, the link opens the website's join page in English or Danish, "Open this link on your Android phone with Pooparazzi installed", with a Google Play link
      because the link may be opened on a computer first (BABIES_AND_BACKUP §3.5)
    decide the checks run one phone on the local Firebase emulators: the inviter signs out and the invitee signs in on the same phone, and the link is opened as Android hands it to the app; the website's page and the offline refusal are not checked
      because the checks' phone (Robolectric) is a single app with no browser, and the emulators are always reachable (docs/BACKEND.md › Running the app against the emulators)
  look
    A screen titled "Family invite" with Close: the question in type.headlineSmall color.text, the two paragraphs in
      type.bodyMedium color.text.dim, Join filled color.primary.container and "Not now" as a text button; after joining
      or a refusal, the line and Done in the same places
  copy
    screen      "Family invite"
    close       "Close"
    loading     "Opening the invite…"
    title       "Join {email}'s family?"
    title.unknown "Join this family?"
    body        "Your babies and your log on this phone are added to the family's and shared with its members. You'll see theirs too."
    merge       "Your first baby and the family's first baby become one baby."
    join        "Join"
    notNow      "Not now"
    joined      "You're in the family. Your log and babies are being shared."
    done        "Done"
    expired     "This invite has expired or was already used. Ask for a new one."
    invalid     "This invite link isn't valid."
    member      "You're already in this family."
    tryAgain    "Try again"
  sources
    docs/design/BABIES_AND_BACKUP.md §3.5 (Opening an invite), §4.5
    docs/BACKEND.md (App Links, The rules, Running the app against the emulators)
    README.md, "Backup and families" (Join a family)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/family/JoinScreen.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/launch/AppLinks.kt
    driven on Robolectric against the Firebase emulators, 2026-11-27: an invite opened signed in and signed out, Join,
      Not now, a used invite, one's own family's invite, a malformed code
```
