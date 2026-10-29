#!/usr/bin/env python3
"""Print the version name of the next release, from the release tags already pushed.

The release workflow tags every release `v<major>.<minor>.<patch>` (older releases: `v<name>-<base>`, for
example `v0.1.0-116`). The next name bumps the highest tagged version:

    git ls-remote --tags origin | python3 scripts/next_version.py --bump minor

- `--bump patch` (the default) 0.1.4 -> 0.1.5, `minor` 0.1.4 -> 0.2.0, `major` 0.1.4 -> 1.0.0.
- `pooparazziVersionName` in gradle/libs.versions.toml is a floor: when it is higher than every tag, it is used
  as is (no bump), so editing it there sets the next release's version by hand.
- No tags yet: the floor.

Reads tag names, or `git ls-remote` lines (`<sha>\trefs/tags/<name>`), on stdin; anything else is ignored.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

TAG = re.compile(r"(?:^|refs/tags/)v(\d+)\.(\d+)\.(\d+)(?:-\d+)?(?:\^\{\})?$")
FLOOR = re.compile(r'^pooparazziVersionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"', re.MULTILINE)
CATALOG = Path(__file__).resolve().parent.parent / "gradle" / "libs.versions.toml"

Version = tuple[int, int, int]


def latest_tag(lines: list[str]) -> Version | None:
    versions = [tuple(map(int, m.groups())) for m in (TAG.search(line.strip()) for line in lines) if m]
    return max(versions, default=None)


def bump(version: Version, level: str) -> Version:
    major, minor, patch = version
    if level == "major":
        return major + 1, 0, 0
    if level == "minor":
        return major, minor + 1, 0
    return major, minor, patch + 1


def next_version(lines: list[str], floor: Version, level: str) -> Version:
    latest = latest_tag(lines)
    if latest is None or floor > latest:
        return floor
    return bump(latest, level)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bump", choices=["patch", "minor", "major"], default="patch")
    parser.add_argument("--catalog", type=Path, default=CATALOG, help="the version catalog holding the floor")
    args = parser.parse_args()

    match = FLOOR.search(args.catalog.read_text(encoding="utf-8"))
    if not match:
        print(f"No pooparazziVersionName = \"X.Y.Z\" in {args.catalog}", file=sys.stderr)
        return 1
    floor = tuple(map(int, match.groups()))
    print(".".join(map(str, next_version(sys.stdin.read().splitlines(), floor, args.bump))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
