#!/usr/bin/env python3
"""Builds the bundled Google Sans Flex fonts from the upstream variable font (fontTools 4.x).

    git show f869489:core/src/main/res/font/google_sans_flex.ttf > /tmp/gsf-upstream.ttf
    python3 -I core/fonts/build_fonts.py /tmp/gsf-upstream.ttf core/src/main/res/font

Outputs (DESIGN_SPEC §5.7, §10.12):
- google_sans_flex.ttf: the phone font. The unused `wdth` and `slnt` axes are pinned at their defaults (100, 0),
  which renders identically and roughly halves the file. Adds U+2192 RIGHTWARDS ARROW (missing upstream; every
  two-sided label uses it: "Left → Right", "Venstre → Højre").
- google_sans_flex_watch.ttf: the watch numerals font. ROND 100 and the default opsz are pinned too, only `wght`
  stays variable, subset to Latin letters, digits and the punctuation the watch numerals and "just now" need.

The arrow is a real outline (shaft + 45° head with a mitred tip) whose stroke follows the font: its points are
linear in the `minus` glyph's left edge, right edge, centre line and half thickness, so every variation of
`minus` (weight, optical size) is carried over exactly. ROND only rounds `minus`'s corners and is not copied.
"""

import io
import math
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables.TupleVariation import TupleVariation
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.varLib import instancer
from fontTools.varLib.iup import iup_delta

ARROW = "arrowright"
ARROW_CODEPOINT = 0x2192
ARM_LENGTH = 400.0  # head arm centre-line length, font units (2000 / em)
EXTRA_WIDTH = 560  # the arrow is this much longer than `minus` (and its advance this much wider)


def minus_params(coords):
    """Left x, right x, centre y and half thickness of the `minus` rectangle (16 points: 4 corners × 4)."""
    pts = list(coords)[:16]
    left = sum(p[0] for p in pts[0:8]) / 8
    right = sum(p[0] for p in pts[8:16]) / 8
    top = sum(p[1] for p in pts[4:12]) / 8
    bottom = (sum(p[1] for p in pts[0:4]) + sum(p[1] for p in pts[12:16])) / 8
    return left, right, (top + bottom) / 2, (top - bottom) / 2


def arrow_points(left, right, c, h):
    """The arrow outline, clockwise (TrueType outer contour): `minus`'s stroke, [EXTRA_WIDTH] longer, tip on the right."""
    right += EXTRA_WIDTH
    s2 = math.sqrt(2)
    a = ARM_LENGTH / s2
    tip = right - h * s2  # centre-line tip, so the mitred point lands on `right`
    pts = [
        (left, c + h),
        (tip - h - h * s2, c + h),
        (tip - a - h / s2, c + a - h / s2),
        (tip - a + h / s2, c + a + h / s2),
        (right, c),
        (tip - a + h / s2, c - a - h / s2),
        (tip - a - h / s2, c - a + h / s2),
        (tip - h - h * s2, c - h),
        (left, c - h),
    ]
    area = sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]))
    return pts if area < 0 else list(reversed(pts))


def add_arrow(font):
    glyf, gvar, hmtx = font["glyf"], font["gvar"], font["hmtx"]
    minus = glyf["minus"]
    coords, ends, _ = minus.getCoordinates(glyf)
    coords = [tuple(p) for p in coords]
    assert len(coords) == 16 and list(ends) == [15], "minus is expected to be one 16-point rectangle"
    base = minus_params(coords)
    base_pts = arrow_points(*base)

    pen = TTGlyphPen(None)
    pen.moveTo(tuple(round(v) for v in base_pts[0]))
    for p in base_pts[1:]:
        pen.lineTo(tuple(round(v) for v in p))
    pen.closePath()
    glyph = pen.glyph()

    order = font.getGlyphOrder()
    assert ARROW not in order
    font.setGlyphOrder(order + [ARROW])
    glyf.glyphOrder = font.getGlyphOrder()
    glyf[ARROW] = glyph
    glyph.recalcBounds(glyf)
    hmtx[ARROW] = (hmtx["minus"][0] + EXTRA_WIDTH, glyph.xMin)

    # Deltas: for each variation of `minus`, move the arrow's points as its rectangle moves. Sparse deltas are
    # completed with IUP first, exactly as a rasterizer infers them; the phantom points (origin, advance) are
    # copied, so side bearings and advance vary like `minus`.
    variations = []
    for tv in gvar.variations["minus"]:
        outline = list(tv.coordinates[:16])
        if None in outline:
            # iup_delta wants the 4 phantom points too; they never take part in the inference.
            outline = iup_delta(outline + [(0, 0)] * 4, coords + [(0, 0)] * 4, list(ends))[:16]
        moved = [(x + dx, y + dy) for (x, y), (dx, dy) in zip(coords, outline)]
        new_pts = arrow_points(*minus_params(moved))
        point_deltas = [(round(n[0] - b[0]), round(n[1] - b[1])) for n, b in zip(new_pts, base_pts)]
        phantom = [c if c is not None else (0, 0) for c in tv.coordinates[16:20]]
        all_deltas = point_deltas + phantom
        if any(dx or dy for dx, dy in all_deltas):
            variations.append(TupleVariation(dict(tv.axes), all_deltas))
    gvar.variations[ARROW] = variations

    # Advance width varies exactly like `minus`.
    hvar = font["HVAR"].table
    hvar.AdvWidthMap.mapping[ARROW] = hvar.AdvWidthMap.mapping["minus"]

    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[ARROW_CODEPOINT] = ARROW
    gdef = font["GDEF"].table
    if gdef.GlyphClassDef is not None:
        gdef.GlyphClassDef.classDefs[ARROW] = 1


def main(upstream, out_dir):
    out_dir = Path(out_dir)

    phone = TTFont(upstream, recalcTimestamp=False)  # deterministic output
    instancer.instantiateVariableFont(phone, {"wdth": 100, "slnt": 0}, inplace=True)
    add_arrow(phone)
    phone.save(out_dir / "google_sans_flex.ttf")

    watch = TTFont(out_dir / "google_sans_flex.ttf", recalcTimestamp=False)
    instancer.instantiateVariableFont(watch, {"ROND": 100, "opsz": 18}, inplace=True)
    buffer = io.BytesIO()
    watch.save(buffer)  # reload: the subsetter needs a fully built gvar
    buffer.seek(0)
    watch = TTFont(buffer, recalcTimestamp=False)
    text = (
        "".join(chr(c) for c in range(0x20, 0x7F))
        + " ·ÅÆØåæø–…→− "
    )
    options = subset.Options()
    options.layout_features = options.layout_features + ["tnum", "lnum", "zero"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.glyph_names = True
    sub = subset.Subsetter(options)
    sub.populate(text=text)
    sub.subset(watch)
    watch.save(out_dir / "google_sans_flex_watch.ttf")

    for name in ("google_sans_flex.ttf", "google_sans_flex_watch.ttf"):
        print(name, (out_dir / name).stat().st_size, "bytes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
