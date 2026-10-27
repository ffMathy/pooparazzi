#!/usr/bin/env python3
"""Build the README images in docs/screenshots/ from the Roborazzi screenshots.

The source images are the ones the screenshot tests record (Robolectric, not a device):

    ./gradlew :app:recordRoborazziDebug :wear:recordRoborazziDebug
    python3 scripts/readme_screenshots.py

Phone shots are downscaled to 360 px wide. Watch shots are scaled to 300 x 300 and masked to a
circle (transparent outside), as a round watch shows them. Every image is written for English (en)
and Danish (da). The set is curated: one image per state the README shows, not every recorded state.
Needs Pillow (pip install Pillow).
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
LANGS = ("en", "da")
PHONE_WIDTH = 360
WATCH_SIZE = 300
# The circle mask is drawn this many times larger and scaled down, for a smooth (anti-aliased) edge.
MASK_SUPERSAMPLE = 4

# Recorded file (app/screenshots/<lang>/) -> README file (docs/screenshots/<lang>/phone-<name>.png).
PHONE = {
    "01-home-night.png": "home-night",
    "02-home-day.png": "home-day",
    "03-home-asleep-night.png": "home-asleep",
    "04-home-breastfeeding.png": "home-breastfeeding",
    "05-home-undo-card.png": "home-undo",
    "06-feed-sheet.png": "feed-sheet",
    "07-side-sheet-ticked.png": "side-sheet",
    "08-diaper-sheet.png": "diaper-sheet",
    "10-history-day.png": "history",
    "11-history-night.png": "history-night",
    "13-edit-breastfeed.png": "edit-feed",
    "14-edit-diaper.png": "edit-diaper",
    "15-add-past-entry.png": "add-past-entry",
    "16-settings.png": "settings",
    "17-widget-4x2.png": "widget-4x2",
    "18-widget-2x1.png": "widget-2x1",
}

# Recorded file (wear/screenshots/<lang>/) -> README file (docs/screenshots/<lang>/watch-<name>.png).
WATCH = {
    "01-main-idle.png": "main-idle",
    "02-main-asleep.png": "main-asleep",
    "03-main-breastfeeding.png": "main-breastfeeding",
    "05-feed-type.png": "feed-type",
    "06-side-ticked.png": "side",
    "07-diaper.png": "diaper",
    "08-diaper-colour-list.png": "diaper-colour",
    "09-logged.png": "logged",
    "15-undone.png": "undone",
    "11-today.png": "today",
    "12-tile.png": "tile",
    "12d-tile-breastfeeding.png": "tile-breastfeeding",
    "13-complications.png": "complications",
}


def phone(src: Path, dst: Path) -> None:
    with Image.open(src) as im:
        im = im.convert("RGBA")
        if im.getchannel("A").getextrema() == (255, 255):
            im = im.convert("RGB")  # fully opaque: no alpha channel, smaller file
        height = round(im.height * PHONE_WIDTH / im.width)
        im.resize((PHONE_WIDTH, height), Image.LANCZOS).save(dst, optimize=True)


def circle_mask(size: int) -> Image.Image:
    big = size * MASK_SUPERSAMPLE
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def watch(src: Path, dst: Path, mask: Image.Image) -> None:
    with Image.open(src) as im:
        im = im.convert("RGBA").resize((WATCH_SIZE, WATCH_SIZE), Image.LANCZOS)
        # Keep any transparency already in the shot, and cut everything outside the circle.
        alpha = Image.composite(im.getchannel("A"), Image.new("L", im.size, 0), mask)
        im.putalpha(alpha)
        im.save(dst, optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=ROOT / "docs" / "screenshots", help="output folder")
    args = parser.parse_args()

    mask = circle_mask(WATCH_SIZE)
    missing: list[Path] = []
    written = 0
    for lang in LANGS:
        out = args.out / lang
        # Start clean, so an image dropped from the lists above doesn't linger.
        if out.exists():
            shutil.rmtree(out)
        out.mkdir(parents=True)
        jobs = [(ROOT / "app" / "screenshots" / lang / s, out / f"phone-{n}.png", False) for s, n in PHONE.items()]
        jobs += [(ROOT / "wear" / "screenshots" / lang / s, out / f"watch-{n}.png", True) for s, n in WATCH.items()]
        for src, dst, is_watch in jobs:
            if not src.exists():
                missing.append(src)
                continue
            watch(src, dst, mask) if is_watch else phone(src, dst)
            written += 1

    print(f"Wrote {written} images to {args.out.relative_to(ROOT) if args.out.is_relative_to(ROOT) else args.out}")
    if missing:
        print("Missing (record the screenshots first):", *map(str, missing), sep="\n  ", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
