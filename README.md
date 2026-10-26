# Xaternity Tracker

A baby tracker for Android phones and Wear OS watches, built for one-handed use at 3am.

- **Sleep**: start and stop, which records an interval.
- **Feeding**: one tap records the time.
- **Diapers**: one tap records the time, plus Pee, Poop or Both.

## Status

The design is in review and no app code exists yet. The visual mockups and the design spec come first, so changes can be decided before implementation starts.

- Design spec: [`docs/design/DESIGN_SPEC.md`](docs/design/DESIGN_SPEC.md) covers the "Night Light" direction: screens, data model, tokens, icons, accessibility and open questions.

## Planned stack

- **Phone**: Kotlin, Jetpack Compose with Material 3 Expressive, Glance widgets and Live Update notifications.
- **Watch**: Kotlin, Compose for Wear OS (Material 3), a Tile, complications and an Ongoing Activity.
- **Sync**: Wearable Data Layer between phone and watch. Both log offline.
