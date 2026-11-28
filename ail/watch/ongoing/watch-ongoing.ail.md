# The Ongoing Activity

While a sleep runs, its Ongoing Activity puts a sleep icon and one short phrase
on the watch face: what comes next, or how long the sleep has run.

```ail
node WatchOngoing : flow   level: experience
  why   A running sleep should be visible on the face at a glance, and one tap away
        from Woke up.   <- Watch
  what
    Scenario: a running sleep shows on the watch face
      Given the notification permission allowed, and no week of logs to predict from
      When the parent starts a sleep on the watch
      Then the watch face's Ongoing Activity reads "Asleep" and how long in clock minutes ("Asleep <1m" within the start's minute)
      And once the parent taps Woke up, the Ongoing Activity is gone
    Scenario: no ongoing activity while awake with nothing predicted
      Given a new install, with nothing logged
      When the parent looks at the watch face
      Then there is no Ongoing Activity
    Scenario: tapping the ongoing activity opens main
      Given a sleep started on the watch a moment ago, and the app left on Diaper
      When the parent taps the Ongoing Activity
      Then the app opens on Main, its sleep edge button reading "Woke up"
  how
    decide one short phrase that fits the face: the earliest of the week's estimates the phone's Settings › Watch ticks ("Wakes 55m", "Feed 2h 05m", "Poop 3h 10m", "Feed soon", "Feed now"), else how long the sleep has run ("Asleep 1h 07m")
      because what comes next matters more than the state, which the icon already says (DESIGN_SPEC §3.8)
    decide awake, it shows the earliest estimate ("Feed 55m", "Sleep 20m") with the upcoming icon, and nothing when none is predicted
      because an activity with nothing to say would only take room (DESIGN_SPEC §3.8)
    decide with two or more babies the baby leads ("Noa · Wakes 55m")
      because the parent must see which baby it is (BABIES_AND_BACKUP §2.5)
    decide it is redrawn every minute by an alarm that never wakes the watch; TalkBack reads clock times ("Asleep since 02:05, expected to wake around 05:25")
      because it must be current whenever the watch is awake, and a ticking value cannot be read (DESIGN_SPEC §3.8, §10.6)
    decide it needs the notification permission; without it nothing is shown and logging works as usual
      because the permission is asked in context, at the first sleep (wear/…/ongoing/RunningActivities.kt)
    decide it is dismissed on both devices when the sleep ends on either
      because one sleep has one activity (DESIGN_SPEC §3.8)
  copy
    ongoing.asleepFor "Asleep {duration}"        ; "Asleep <1m", "Asleep 1h 07m"
    ongoing.wakes     "Wakes {in}"               ; "Wakes 55m", "Wakes soon", "Wakes now"
    ongoing.feed      "Feed {in}"
    ongoing.poop      "Poop {in}"
    ongoing.sleep     "Sleep {in}"
    ongoing.spoken    "Asleep since {time}"
  sources
    docs/design/DESIGN_SPEC.md §3.8 (W11 Ongoing Activity), §10.6
    commit 874a042 (the notification shows the time asleep), 634958f "Keep the watch tile and Ongoing Activity in step"
    driven on Robolectric, 2026-11-27: the activity's status read from the posted notification ("Asleep <1m"), gone after
      a diaper ended the sleep, none while awake, a tap opening Main
    not driven: the estimates, which need a week of logs the checks cannot make through the watch in reasonable time
    wear/src/main/kotlin/com/ffmathy/pooparazzi/wear/ongoing/RunningActivities.kt
```
