#!/usr/bin/env python3
"""Build the Google Play store images in docs/play/<lang>/ from the Roborazzi screenshots.

    ./gradlew :app:recordRoborazziDebug :wear:recordRoborazziDebug   # only when the app's look changed
    python3 scripts/play_assets.py

For each listing language (en for en-US, da for da-DK) it writes:

- phone-01.png … phone-08.png: 1080 x 1920 (9:16) phone screenshots, each a recorded screen on the Night background
  under a one-line caption. Play allows at most 2:1, and the recordings are taller than that (1170 x 2532).
- watch-01.png …: 454 x 454 Wear OS screenshots, the recorded round screens on black (Play wants square, opaque
  images of at least 384 x 384, without a frame).
- feature-graphic.png: 1024 x 500, the app's name and a tagline beside the home screen and the watch.
- icon-512.png (once, in docs/play/): the launcher icon at 512 x 512, the diaper glyph in parchment on the Night
  background (res/mipmap-anydpi/ic_launcher.xml).

Play Console › Store presence › Main store listing takes these files, and the Danish translation takes da/.
Texts are in docs/play/STORE_LISTING.md. Needs Pillow (pip install Pillow).
"""

from __future__ import annotations

import argparse
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT = ROOT / "core" / "src" / "main" / "res" / "font" / "google_sans_flex.ttf"

# The Night palette (DESIGN_SPEC §5): background, parchment text, muted text, and the outline that sets the dark
# screens apart from the dark background.
NIGHT = (12, 10, 8)
PARCHMENT = (232, 216, 195)
MUTED = (184, 164, 142)
OUTLINE = (58, 51, 44)

# The launcher glyph (DESIGN_SPEC §6.2 `diaper`), on its 24-unit grid with a stroke of 2.
DIAPER = (
    "M3 6.5h18v4a3.5 3.5 0 0 0-3.5 3.5v1.5a3 3 0 0 1-3 3h-5a3 3 0 0 1-3-3V14A3.5 3.5 0 0 0 3 10.5z",
    "M3 9h18",
)

# Recorded phone screen (app/screenshots/<lang>/), the part of it to show (None: all of it, else a box), and the
# caption per language. Order is the listing's order; Play shows at most 8.
PHONE = [
    ("03-home-asleep-night.png", None, {"en": "Made for 3 am: dim, and one tap to start a nap", "da": "Lavet til kl. 3: dæmpet, og ét tryk starter en lur"}),
    ("02-home-day.png", None, {"en": "See when the next feed and nap are due", "da": "Se, hvornår næste måltid og lur venter"}),
    ("07-breast-sheet-ticked.png", None, {"en": "Know which side is next", "da": "Husk, hvilken side der er den næste"}),
    ("08-diaper-sheet.png", None, {"en": "Pee, poop, colour and texture", "da": "Tis, afføring, farve og konsistens"}),
    ("10-history-day.png", None, {"en": "The whole day on one ribbon", "da": "Hele dagen på ét bånd"}),
    ("45-statistics-typical-day.png", None, {"en": "Averages and your baby's typical day", "da": "Gennemsnit og barnets typiske dag"}),
    # The recording stacks four widget states; the first two (Night, then Day with its Undo) are enough.
    ("17-widget-4x2.png", (0, 0, 1170, 1440), {"en": "Log from your home screen", "da": "Log fra startskærmen"}),
    ("37-settings-family.png", None, {"en": "Share with your family, if you like", "da": "Del med familien, hvis du vil"}),
]

# Recorded watch screens (wear/screenshots/<lang>/), in the listing's order.
WATCH = [
    "01-main-idle.png",
    "02-main-asleep.png",
    "06-side-ticked.png",
    "07b-diaper-options.png",
    "12-tile.png",
    "13-complications.png",
]

TAGLINE = {
    "en": "The baby tracker for your phone and watch, made for 3 am",
    "da": "Babyloggen til telefon og ur, lavet til klokken 3 om natten",
}

PHONE_SIZE = (1080, 1920)
CAPTION_TOP, CAPTION_HEIGHT = 96, 200
SHOT_BOTTOM_MARGIN = 72
SHOT_RADIUS = 44
FEATURE_SIZE = (1024, 500)
ICON_SIZE = 512
SUPERSAMPLE = 4


def font(size: int, weight: int) -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(str(FONT), size)
    f.set_variation_by_axes([min(max(size, 1), 144), weight, 0])  # optical size, weight, roundness
    return f


def path_points(d: str) -> list[list[tuple[float, float]]]:
    """Flattens an SVG path (the M m L l H h V v A a Z z commands the glyphs use) into polylines."""
    tokens = re.findall(r"[MmLlHhVvAaZz]|-?(?:\d+\.?\d*|\.\d+)", d)
    subpaths: list[list[tuple[float, float]]] = []
    x = y = 0.0
    start = (0.0, 0.0)
    i = 0
    cmd = ""

    def num() -> float:
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    while i < len(tokens):
        if re.fullmatch(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]
            i += 1
            if cmd in "Zz":
                subpaths[-1].append(start)
                x, y = start
                continue
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            start = (x, y)
            subpaths.append([(x, y)])
            cmd = "l" if rel else "L"  # further pairs are line-tos
        elif c == "L":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            subpaths[-1].append((x, y))
        elif c == "H":
            nx = num()
            x = x + nx if rel else nx
            subpaths[-1].append((x, y))
        elif c == "V":
            ny = num()
            y = y + ny if rel else ny
            subpaths[-1].append((x, y))
        elif c == "A":
            rx, ry, rot, large, sweep = num(), num(), num(), num(), num()
            nx, ny = num(), num()
            ex, ey = (x + nx, y + ny) if rel else (nx, ny)
            subpaths[-1].extend(arc(x, y, rx, ry, rot, bool(large), bool(sweep), ex, ey))
            x, y = ex, ey
        else:
            raise ValueError(f"Unsupported path command {cmd!r}")
    return subpaths


def arc(x1, y1, rx, ry, rot, large, sweep, x2, y2, steps=48):
    """Points along an SVG elliptical arc (endpoint parameterisation, SVG 1.1 appendix F.6)."""
    phi = math.radians(rot)
    cos, sin = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cos * dx + sin * dy, -sin * dx + cos * dy
    lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num_ = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coef = math.sqrt(max(num_, 0) / den) * (-1 if large == sweep else 1)
    cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
    cx = cos * cxp - sin * cyp + (x1 + x2) / 2
    cy = sin * cxp + cos * cyp + (y1 + y2) / 2
    t1 = math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
    t2 = math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx)
    dt = t2 - t1
    if sweep and dt < 0:
        dt += 2 * math.pi
    elif not sweep and dt > 0:
        dt -= 2 * math.pi
    return [
        (cx + rx * math.cos(t1 + dt * k / steps) * cos - ry * math.sin(t1 + dt * k / steps) * sin,
         cy + rx * math.cos(t1 + dt * k / steps) * sin + ry * math.sin(t1 + dt * k / steps) * cos)
        for k in range(1, steps + 1)
    ]


def glyph(size: int, colour: tuple[int, int, int], paths=DIAPER, stroke: float = 2) -> Image.Image:
    """The glyph's 24-unit grid drawn into a transparent square of [size] px, with round caps and joins."""
    big = size * SUPERSAMPLE
    scale = big / 24
    width = stroke * scale
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    r = width / 2
    for d in paths:
        for points in path_points(d):
            pts = [(px * scale, py * scale) for px, py in points]
            # Each segment as a quad of the stroke's width, with a disc at every vertex: round caps and joins that
            # stay smooth along the flattened arcs (ImageDraw's wide lines leave notches there).
            for (ax, ay), (bx, by) in zip(pts, pts[1:]):
                length = math.hypot(bx - ax, by - ay)
                if length:
                    nx, ny = -(by - ay) / length * r, (bx - ax) / length * r
                    draw.polygon([(ax + nx, ay + ny), (bx + nx, by + ny), (bx - nx, by - ny), (ax - nx, ay - ny)], fill=colour)
            for px, py in pts:
                draw.ellipse((px - r, py - r, px + r, py + r), fill=colour)
    return im.resize((size, size), Image.LANCZOS)


def rounded(im: Image.Image, radius: int) -> Image.Image:
    big = Image.new("L", (im.width * SUPERSAMPLE, im.height * SUPERSAMPLE), 0)
    ImageDraw.Draw(big).rounded_rectangle((0, 0, big.width - 1, big.height - 1), radius * SUPERSAMPLE, fill=255)
    out = im.convert("RGBA")
    out.putalpha(big.resize(im.size, Image.LANCZOS))
    return out


def fit_text(draw: ImageDraw.ImageDraw, text: str, max_width: int, size: int, weight: int) -> ImageFont.FreeTypeFont:
    while size > 20:
        f = font(size, weight)
        if draw.textlength(text, font=f) <= max_width:
            return f
        size -= 2
    return font(size, weight)


def phone_shot(src: Path, box, caption: str, dst: Path) -> None:
    canvas = Image.new("RGB", PHONE_SIZE, NIGHT)
    draw = ImageDraw.Draw(canvas)
    f = fit_text(draw, caption, PHONE_SIZE[0] - 120, 64, 600)
    draw.text((PHONE_SIZE[0] / 2, CAPTION_TOP + CAPTION_HEIGHT / 2), caption, font=f, fill=PARCHMENT, anchor="mm")
    with Image.open(src) as shot:
        shot = shot.convert("RGB")
        if box:
            shot = shot.crop(box)
        top = CAPTION_TOP + CAPTION_HEIGHT + 24
        max_w, max_h = PHONE_SIZE[0] - 160, PHONE_SIZE[1] - top - SHOT_BOTTOM_MARGIN
        scale = min(max_w / shot.width, max_h / shot.height)
        shot = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    x = (PHONE_SIZE[0] - shot.width) // 2
    ImageDraw.Draw(canvas).rounded_rectangle(
        (x - 3, top - 3, x + shot.width + 2, top + shot.height + 2), SHOT_RADIUS + 3, fill=OUTLINE)
    canvas.paste(rounded(shot, SHOT_RADIUS), (x, top), rounded(shot, SHOT_RADIUS))
    canvas.save(dst, optimize=True)


def watch_shot(src: Path, dst: Path) -> None:
    with Image.open(src) as im:
        im = im.convert("RGBA")
        out = Image.new("RGB", im.size, (0, 0, 0))
        out.paste(im, (0, 0), im)
        out.save(dst, optimize=True)


def feature_graphic(lang: str, dst: Path) -> None:
    canvas = Image.new("RGB", FEATURE_SIZE, NIGHT)
    draw = ImageDraw.Draw(canvas)
    icon = glyph(72, PARCHMENT)
    canvas.paste(icon, (64, 150), icon)
    draw.text((152, 186), "Pooparazzi", font=font(76, 650), fill=PARCHMENT, anchor="lm")
    words = TAGLINE[lang].split()
    lines, line = [], ""
    tag_font = font(30, 450)
    for word in words:  # wrap the tagline into the left half
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=tag_font) > 470 and line:
            lines.append(line)
            line = word
        else:
            line = trial
    lines.append(line)
    for n, text in enumerate(lines):
        draw.text((66, 262 + n * 42), text, font=tag_font, fill=MUTED, anchor="lm")

    # The phone's home screen, cut off by the bottom edge, and the watch beside it.
    with Image.open(ROOT / "app" / "screenshots" / lang / "01-home-night.png") as shot:
        shot = shot.convert("RGB").crop((0, 0, 1170, 1716))  # runs off the bottom edge
        shot = shot.resize((300, 440), Image.LANCZOS)
    px, py = 620, 60
    draw.rounded_rectangle((px - 2, py - 2, px + shot.width + 1, py + shot.height + 40), 30, fill=OUTLINE)
    canvas.paste(rounded(shot, 28), (px, py), rounded(shot, 28))
    with Image.open(ROOT / "wear" / "screenshots" / lang / "01-main-idle.png") as wshot:
        wshot = wshot.convert("RGBA").resize((210, 210), Image.LANCZOS)
    wx, wy = 790, 262
    draw.ellipse((wx - 4, wy - 4, wx + 213, wy + 213), fill=OUTLINE)
    canvas.paste(wshot, (wx, wy), wshot)
    canvas.save(dst, optimize=True)


def icon(dst: Path) -> None:
    """The adaptive icon's 72-unit visible area at 512 px: the glyph spans 54 of its 108 units."""
    canvas = Image.new("RGB", (ICON_SIZE, ICON_SIZE), NIGHT)
    size = round(ICON_SIZE * 54 / 72)
    g = glyph(size, PARCHMENT)
    canvas.paste(g, ((ICON_SIZE - size) // 2, (ICON_SIZE - size) // 2), g)
    canvas.save(dst, optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=ROOT / "docs" / "play", help="output folder")
    args = parser.parse_args()

    missing = [p for lang in ("en", "da") for p in
               [ROOT / "app" / "screenshots" / lang / s for s, _, _ in PHONE] +
               [ROOT / "wear" / "screenshots" / lang / s for s in WATCH] if not p.exists()]
    if missing:
        print("Missing (record the screenshots first):", *map(str, missing), sep="\n  ", file=sys.stderr)
        return 1

    args.out.mkdir(parents=True, exist_ok=True)
    icon(args.out / "icon-512.png")
    written = 1
    for lang in ("en", "da"):
        out = args.out / lang
        out.mkdir(exist_ok=True)
        for old in out.glob("*.png"):  # start clean, so a dropped image doesn't linger
            old.unlink()
        for n, (src, box, caption) in enumerate(PHONE, 1):
            phone_shot(ROOT / "app" / "screenshots" / lang / src, box, caption[lang], out / f"phone-{n:02}.png")
        for n, src in enumerate(WATCH, 1):
            watch_shot(ROOT / "wear" / "screenshots" / lang / src, out / f"watch-{n:02}.png")
        feature_graphic(lang, out / "feature-graphic.png")
        written += len(PHONE) + len(WATCH) + 1
    print(f"Wrote {written} images to {args.out.relative_to(ROOT) if args.out.is_relative_to(ROOT) else args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
