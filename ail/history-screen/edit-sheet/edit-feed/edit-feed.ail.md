# Editing a feed

A feed's edit sheet changes its type (or clears it), a breast feed's side
order, a bottle's contents and amount, and its time.

```ail
node EditFeed : flow   level: experience
  why   Only the time of a feed is ever required; the type and its extras are
        optional, so they must be changeable and removable afterwards too.   <- EditSheet
  what
    Scenario: a bottle feed's sheet
      Given a Bottle feed yesterday at 11:00 AM
      When the parent taps its row on yesterday
      Then "Edit feed" opens with "Type · Optional" and Bottle picked, "Contents · Optional" with Milk and Formula, and "Amount · Optional"
      And one Time block reads "Yesterday" and 11:00 AM
    Scenario: changing a feed's type to breast
      Given the edit sheet of a Bottle feed yesterday at 11:00 AM
      When the parent taps Breast, ticks Left and taps Save
      Then yesterday lists "Breast · Left" at 11:00 AM
    Scenario: tapping the picked type clears it
      Given the edit sheet of a Bottle feed yesterday at 11:00 AM
      When the parent taps Bottle and then Save
      Then yesterday lists "Feed" at 11:00 AM
    Scenario: moving a feed's time
      Given the edit sheet of a Bottle feed yesterday at 11:00 AM
      When the parent taps +5m under Time
      Then Time reads 11:05 AM and "was 11:00 AM", and Save lists "Bottle" at 11:05 AM
  how
    decide the type is a connected group Breast | Bottle | Food labelled "Type · Optional"; tapping the picked one clears it, leaving a feed with just its time
      because the type is optional and only the times are ever required (V1_ADDITIONS §1, §4)
    decide Breast adds "Side · Optional" (Left and Right ticked in feeding order, the summary "Left → Right" or "No side"); Bottle adds "Contents · Optional" (Milk | Formula) and "Amount · Optional" (a slider); switching type drops the extras, switching back restores the original's
      because of the Feed sheet's Breast and Bottle steps (V1_ADDITIONS §2.1, §4)
    decide a feed has one time block and no duration
      because a breast feed is one time, not a timer (V1_ADDITIONS intro, §1)
  look
    The edit sheet's look (EditSheet); the type group full width, the picked one filled color.primary.container with a check;
      the side tiles in feed.container with a 2dp feed.strong border when ticked and an order badge; the slider in feed.strong
      on feed.container
  copy
    edit.type           "Type · Optional"   "Breast" "Bottle" "Food"
    edit.side           "Side · Optional"   "Left" "Right" "No side" "Left → Right"
    edit.contents       "Contents · Optional"   "Milk" "Formula"
    edit.amount         "Amount · Optional"   "No amount"
    row.feed            "Feed" "Breast · Left" "Bottle · Milk" "Food"
  sources
    docs/design/V1_ADDITIONS.md §2.1 (Breast and Bottle steps), §4 (Edit sheet: Feed)
    driven on Robolectric, 2026-11-27: Edit feed from the Lamp and from a History row; Bottle (Contents, Amount), Breast (Side)
    app/src/main/kotlin/com/ffmathy/pooparazzi/phone/ui/edit/EditSheet.kt (the feed form), EditViewModel.kt
```
