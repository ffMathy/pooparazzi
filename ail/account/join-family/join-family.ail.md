# Joining a family

An invite link opens the app, which asks, on a screen of its own, whether to
join the inviter's family.

```ail
node JoinFamily : flow   level: experience
  why   A partner or grandparent joins with one link, and must know before joining
        that their log and babies will be shared and merged.   <- Account
  what
    Scenario: an invite asks first
      Given a phone signed in, and an invite link from "partner@example.com" opened
      Then a screen asks "Join partner@example.com's family?" with "Your babies and your log on this phone are added to the family's and shared with its members. You'll see theirs too."
      And "Your first baby and the family's first baby become one baby.", with Join and "Not now"
    Scenario: joining
      Given the join screen
      When the parent taps Join
      Then it says "You're in the family. Your log and babies are being shared.", with Done
    Scenario: an invite that was used
      Given a phone signed in, and an invite link that was already used
      Then the join screen says "This invite has expired or was already used. Ask for a new one."
  how
    decide signed out, the app first asks to sign in (the Welcome or the Account screen, with "Sign in to join the family."), then for the consent if needed, then continues to the join screen; "Opening the invite…" while it is read
      because joining needs an account that agreed to the backup (BABIES_AND_BACKUP §3.5, §3.6)
    decide Join creates your membership with the invite, points your account at the family and deletes the invite in one step; then the phone leaves its old family as when deleting the account and syncs the new family from the beginning, merging it with the phone's log
      because nothing on the phone is lost when changing family (BABIES_AND_BACKUP §3.5, §4.5)
    decide babies match by id, and every phone's first baby has the same id, so the joiner's first baby and the family's become one baby, named as it was changed last
      because they are usually the same child; a family with a different first child should add a new baby before joining (BABIES_AND_BACKUP §3.5)
    decide refusals in words: "This invite has expired or was already used. Ask for a new one.", "This invite link isn't valid.", "You're already in this family.", "No connection. Try again." with "Try again"; "Join this family?" when the inviter's address is not known
      because the parent must know why and what to do (BABIES_AND_BACKUP §3.5)
    decide without the app, the link opens the website's join page in English or Danish, "Open this link on your Android phone with Pooparazzi installed", with a Google Play link
      because the link may be opened on a computer first (BABIES_AND_BACKUP §3.5)
    decide it can't be checked here: it needs an invite from a signed-in account, opened through the website's App Link
      because the checks run with accounts turned off, and the App Links exist only in builds with a Firebase configuration (app/build.gradle.kts)
  look
    A screen titled "Family invite" with Back: the question in type.headlineSmall color.text, the two paragraphs in
      type.bodyMedium color.text.dim, Join filled color.primary.container and "Not now" as a text button; after joining
      the line and Done in the same places
  copy
    screen      "Family invite"
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
    docs/BACKEND.md (App Links, The rules)
    README.md, "Backup and families" (Join a family)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/family/JoinScreen.kt
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/launch/AppLinks.kt
    not driven: accounts are off in every test and check
```
