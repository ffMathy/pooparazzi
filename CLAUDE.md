# Working on Pooparazzi

## Keep the README screenshots current

Every change that alters what a user sees (a screen, sheet, widget, tile, complication, notification, colour, icon,
text or layout) must update the README screenshots in the same change:

1. Add or adjust a screenshot test when the changed visual has none (Roborazzi tests under `app/src/test/…` and
   `wear/src/test/…`, for example `WidgetScreenshots.kt` and `TileScreenshots.kt`), in English and Danish.
2. Re-record: `./gradlew :app:recordRoborazziDebug :wear:recordRoborazziDebug` (or the affected tests with `--tests`).
3. Rebuild the README images: `python3 scripts/readme_screenshots.py`. A new shot needs an entry in its `PHONE` or
   `WATCH` map, and a place in `README.md`.
4. Look at the re-recorded images before committing, and commit them (`app/screenshots/`, `wear/screenshots/`,
   `docs/screenshots/`) with the change.

Keep `README.md` and `docs/design/DESIGN_SPEC.md` in step with visual and behaviour changes too.
