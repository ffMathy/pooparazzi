# The same diaper twice

A diaper of the same kind saved again within five seconds is the same change,
saved twice: it is not logged again, and Home says so.

```ail
node RepeatDiaper : flow   level: experience
  why   A double save at night, or the same change logged on the phone and the
        watch, must not count twice, and the refusal must never be silent.   <- Diapers
  what
    Scenario: the same diaper again within five seconds
      Given a pee saved from the Diaper sheet a moment ago
      When the parent saves a pee again, within five seconds of the first
      Then the notice reads "Pee" over "Already logged at" the first change's time
      And Today still counts 1 pee
  how
    decide two diapers of the same kind (Pee, Poop, Both or a plain change) less than 5 s apart are one; the first is kept
      because one change logged twice, from any surface, must be one entry (DESIGN_SPEC §4.4 Dedupe)
    decide a repeat shows "Already logged at <time>" under the kind's name for 2 s, in place of Today
      because no rule is silent (DESIGN_SPEC §3.3.1 notice card, §5.12)
  look
    The notice as for a repeated feed (RepeatFeed), with the diaper kind's icon and name
  copy
    notice.repeat       "Already logged at {time}"
  uses      Diapers, RepeatFeed
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (Dedupe), §4.4, §5.12
    driven on Robolectric, 2026-11-27: Pee, then Pee again about 2 s later ("Pee", "Already logged at 5:02 PM"); a plain
      change then a pee 2.5 s later (two changes)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, the repeat notice
```
