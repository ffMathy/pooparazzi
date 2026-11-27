# Leaving the Feed sheet

The Feed sheet closes without logging by Cancel, by going back or by a tap on
the dimmed Home above it, and Home takes the next tap at once. After a feed is
logged, Home rests for a moment, so a double tap can't open the sheet again.

```ail
node LeavingTheFeedSheet : flow   level: experience
  why   Opening the sheet by mistake must cost nothing, and a double tap on a row
        must never land on Feed and open the sheet again.   <- Feeds
  what
    Scenario: cancel logs nothing
      Given the Feed sheet opened now
      When the parent taps Cancel
      Then the sheet closes and the Lamp still reads "No feeds yet"
      And Feed opens the sheet again at once
    Scenario: going back logs nothing
      Given the Feed sheet opened now
      When the parent goes back
      Then the sheet closes and the Lamp still reads "No feeds yet"
    Scenario: a tap outside the sheet logs nothing
      Given the Feed sheet opened now
      When the parent taps the dimmed Home above it
      Then the sheet closes and the Lamp still reads "No feeds yet"
    Scenario: Home rests a moment after a feed is logged
      Given a feed logged with Food a moment ago
      When the parent taps Feed again at once
      Then nothing opens
      And a second later Feed opens the sheet
  how
    decide Cancel, going back and a tap outside close the sheet, log nothing, and leave Home free at once
      because closing is never a choice to log; only a log is followed by the cooldown (DESIGN_SPEC §3.3.2, §5.9)
    decide after a feed is logged, Feed, Diaper and the sleep button ignore taps for 600 ms once the sheet has closed, with no notice
      because a double tap must never land on a different target (DESIGN_SPEC §5.9, §11)
    decide what the sheet had picked is forgotten: the next Feed stamps a new time and starts with no nudge
      because every sheet is a new log (app/…/ui/home/HomeViewModel.kt)
  copy
    sheet.cancel        "Cancel"
    lamp.none           "No feeds yet"
  uses      Feeds
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (Home interactions, cooldowns), §3.3.2 (Cancel or a scrim tap), §5.9
    driven on Robolectric, 2026-11-27: Cancel, back and a tap above the sheet (nothing logged, Feed opens again at
      once); Feed 0.3 s after Food (ignored) and 1.3 s after (opened)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, dismissing a sheet and the cooldown
```
