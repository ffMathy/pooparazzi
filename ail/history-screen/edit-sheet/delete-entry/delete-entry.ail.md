# Deleting an entry

Delete in the edit sheet removes the entry at once and says so, with Undo:
a short note at the bottom of History, the receipt sheet on Home.

```ail
node DeleteEntry : flow   level: experience
  why   A wrong entry must be removable, and a wrong delete must be just as easy
        to take back.   <- EditSheet
  what
    Scenario: deleting from History, and Undo
      Given a sleep yesterday from 1:00 PM to 2:00 PM, its edit sheet open from History
      When the parent taps Delete
      Then the sheet closes, the row is gone, and the note reads "Sleep deleted" with Undo
      And tapping Undo lists "Sleep · 1h 00m" again
    Scenario: deleting from Home, and Undo
      Given a feed logged 15 minutes ago with Just the time, its edit sheet open from the Lamp
      When the parent taps Delete
      Then the receipt reads "Feed deleted" with Undo and Done, with no nudges and no Edit, and the Lamp reads "No feeds yet"
      And tapping Undo shows the notice "Undone" and the Lamp counts from the feed again
    Scenario: Done keeps the delete
      Given a feed deleted from the Lamp's edit sheet
      When the parent taps Done on its receipt
      Then the receipt closes and the Lamp still reads "No feeds yet"
  how
    decide Delete needs no confirmation: it closes the sheet and deletes at once, softly for 7 days, with Undo
      because deletes are soft for 7 days with an Undo receipt (DESIGN_SPEC §3.3.4, §4.4, §10.7)
    decide on History the receipt is a note at the bottom ("Sleep deleted", Undo) that stays a long while; on Home it is the receipt sheet titled "Feed deleted", with Undo and Done and nothing to move or edit
      because on Home a delete in the edit sheet is one of the logs that get a receipt (DESIGN_SPEC §3.3.1 receipt table "Deleted")
    decide the name is the entry's kind as History writes it: "Sleep deleted", "Feed deleted", "Pee + poop deleted"
      because of DESIGN_SPEC §3.3.1's receipt table
    decide Undo brings the entry back as it was; on Home the notice "Undone" shows for two seconds
      because every log can be taken back at once (DESIGN_SPEC §1, §3.3.1)
  look
    On History: a snackbar at the bottom, "Sleep deleted" and an Undo action. On Home: the receipt sheet of ail/sleep (fill
      color.sheet, radius.sheet), the entry's icon, "Feed deleted" in type.titleLarge, Undo at the end, Done alone in the last row
  copy
    receipt.deleted     "{kind} deleted"     ; "Sleep deleted", "Feed deleted", "Pee + poop deleted"
    receipt.undo        "Undo"
    receipt.done        "Done"
    notice.undone       "Undone"
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (Receipt sheet: Deleted), §3.3.4 (Delete), §4.4, §10.7
    driven on Robolectric, 2026-11-27: Delete of a sleep from History ("Sleep deleted", Undo) and of a feed from the Lamp
      ("Feed deleted", Undo, Done; "Undone")
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/history/HistoryScreen.kt (the receipt snackbar), ui/home/HomeViewModel.kt
```
