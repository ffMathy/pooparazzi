# The same feed twice

A feed of the same type logged again within five seconds is the same feed,
tapped twice: it is not logged again, and Home says so.

```ail
node RepeatFeed : flow   level: experience
  why   A double log at night, or the same feed logged on the phone and the watch,
        must not count as two feeds, and the refusal must never be silent.   <- Feeds
  what
    Scenario: the same feed again within five seconds
      Given Food logged from the Feed sheet a moment ago
      When the parent logs Food again from the Feed sheet, within five seconds of the first
      Then the notice reads "Food" over "Already logged at" the first feed's time
      And Today still counts 1 feed
      And two seconds later the notice is gone
    Scenario: another type within five seconds is a feed of its own
      Given Food logged from the Feed sheet a moment ago
      When the parent logs Just the time from the Feed sheet, within five seconds of the first
      Then no notice shows, and Today counts 2 feeds
  how
    decide two feeds of the same type (breast feeds whatever their side, and feeds with no type alike) less than 5 s apart are one; the first is kept
      because one tap logged twice, from any surface, must be one entry (DESIGN_SPEC §4.4, V1_ADDITIONS §1 "Dedupe")
    decide the window is measured between the feeds' times, so a repeat is a log whose sheet was opened within 5 s of the first's
      because every feed's time is its stamp plus the nudges (core/…/data, the repository's dedupe)
    decide a repeat shows the notice "Already logged at <time>" under the type's name for 2 s, in place of Today
      because no rule is silent (DESIGN_SPEC §3.3.1 notice card, §5.12 dedupe message 2 s)
  look
    The notice: in place of Today, a card of fill color.notice with radius.notice, the feed's icon and its type in
      type.titleMedium color.text, and under it "Already logged at …" in type.dataMedium color.text.dim
  copy
    notice.repeat       "Already logged at {time}"
  uses      Feeds
  sources
    docs/design/DESIGN_SPEC.md §3.3.1 (the notice card, Dedupe), §4.4 (Dedupe), §5.12
    docs/design/V1_ADDITIONS.md §1 (Dedupe)
    driven on Robolectric, 2026-11-27: Food, then Food again 1.6 s later ("Food", "Already logged at 5:06 PM", Today 1
      feed, gone 2 s later); Bottle then Food, and Food then Just the time, within 5 s (two feeds each)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/home/HomeViewModel.kt, the repeat notice
```
