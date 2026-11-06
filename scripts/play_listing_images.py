#!/usr/bin/env python3
"""Upload the Google Play store images in docs/play/ (built by scripts/play_assets.py) to the store listing.

    python3 scripts/play_listing_images.py --dry-run                      # what would be uploaded, no credentials
    PLAY_SERVICE_ACCOUNT_JSON="$(cat key.json)" python3 scripts/play_listing_images.py

Through the Google Play Developer API (an edit: insert, replace the images, commit) it sets, for each listing
language (en-US from docs/play/en/, da-DK from docs/play/da/):

- phoneScreenshots: phone-01.png … in order
- wearScreenshots: watch-01.png … in order
- featureGraphic: feature-graphic.png
- icon: docs/play/icon-512.png

An image type whose files already match Play's (same SHA-256, same order) is left alone, and when nothing differs
the edit is discarded, so re-running it changes nothing and sends nothing for review. A language whose listing does
not exist yet in Play Console is skipped with a warning (create its translation there first). The service account
needs Play Console's "Manage store presence" permission. Needs google-auth and requests
(pip install google-auth requests); --dry-run needs neither.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLAY = ROOT / "docs" / "play"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"

# Folder in docs/play/ -> Play listing language.
LANGUAGES = {"en": "en-US", "da": "da-DK"}


def images(folder: str) -> dict[str, list[Path]]:
    out = PLAY / folder
    return {
        "phoneScreenshots": sorted(out.glob("phone-*.png")),
        "wearScreenshots": sorted(out.glob("watch-*.png")),
        "featureGraphic": [out / "feature-graphic.png"],
        "icon": [PLAY / "icon-512.png"],
    }


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def warn(message: str) -> None:
    prefix = "::warning::" if os.environ.get("GITHUB_ACTIONS") else "warning: "
    print(prefix + message, file=sys.stderr)


def check(plan: dict[str, dict[str, list[Path]]]) -> list[str]:
    problems = []
    for lang, kinds in plan.items():
        for kind, files in kinds.items():
            if not files:
                problems.append(f"{lang}: no {kind} images")
            problems += [f"missing {p.relative_to(ROOT)}" for p in files if not p.exists()]
        if len(kinds["phoneScreenshots"]) > 8 or len(kinds["wearScreenshots"]) > 8:
            problems.append(f"{lang}: Play takes at most 8 screenshots per type")
    return problems


class Play:
    def __init__(self, package: str, service_account: str):
        import requests
        from google.auth.transport.requests import Request
        from google.oauth2 import service_account as sa

        creds = sa.Credentials.from_service_account_info(json.loads(service_account), scopes=[SCOPE])
        creds.refresh(Request())
        self.http = requests.Session()
        self.http.headers["Authorization"] = f"Bearer {creds.token}"
        self.app = f"{API}/{package}"
        self.upload_app = f"{UPLOAD}/{package}"

    def call(self, method: str, url: str, **kwargs):
        r = self.http.request(method, url, timeout=120, **kwargs)
        if not r.ok:
            raise RuntimeError(f"{method} {url.split('/applications/')[-1]}: {r.status_code} {r.text.strip()}")
        return r.json() if r.content else {}

    def insert_edit(self) -> str:
        return self.call("POST", f"{self.app}/edits")["id"]

    def listings(self, edit: str) -> set[str]:
        return {l["language"] for l in self.call("GET", f"{self.app}/edits/{edit}/listings").get("listings", [])}

    def remote_hashes(self, edit: str, lang: str, kind: str) -> list[str]:
        return [i.get("sha256", "") for i in
                self.call("GET", f"{self.app}/edits/{edit}/listings/{lang}/{kind}").get("images", [])]

    def replace(self, edit: str, lang: str, kind: str, files: list[Path]) -> None:
        self.call("DELETE", f"{self.app}/edits/{edit}/listings/{lang}/{kind}")
        for f in files:
            self.call("POST", f"{self.upload_app}/edits/{edit}/listings/{lang}/{kind}",
                      params={"uploadType": "media"}, data=f.read_bytes(), headers={"Content-Type": "image/png"})

    def commit(self, edit: str) -> None:
        try:
            self.call("POST", f"{self.app}/edits/{edit}:commit")
        except RuntimeError as e:
            # Apps whose changes must be sent for review by hand (managed publishing, or a draft app) refuse an
            # automatic send; commit the changes and leave sending them to Play Console.
            if "changesNotSentForReview" not in str(e):
                raise
            self.call("POST", f"{self.app}/edits/{edit}:commit", params={"changesNotSentForReview": "true"})
            warn("Play did not accept sending the listing for review automatically: "
                 "the new images are saved, send them for review in Play Console › Publishing overview")

    def discard(self, edit: str) -> None:
        self.call("DELETE", f"{self.app}/edits/{edit}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--package", default=os.environ.get("PACKAGE_NAME", "com.ffmathy.pooparazzi"))
    parser.add_argument("--dry-run", action="store_true", help="list what would be uploaded and stop")
    args = parser.parse_args()

    plan = {play_lang: images(folder) for folder, play_lang in LANGUAGES.items()}
    problems = check(plan)
    if problems:
        print("Run scripts/play_assets.py first:", *problems, sep="\n  ", file=sys.stderr)
        return 1

    if args.dry_run:
        for lang, kinds in plan.items():
            for kind, files in kinds.items():
                print(f"{lang} {kind}: " + ", ".join(str(f.relative_to(PLAY)) for f in files))
        return 0

    key = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "")
    if not key:
        print("PLAY_SERVICE_ACCOUNT_JSON is not set", file=sys.stderr)
        return 1
    play = Play(args.package, key)
    edit = play.insert_edit()
    try:
        existing = play.listings(edit)
        changed = []
        for lang, kinds in plan.items():
            if lang not in existing:
                warn(f"Play has no {lang} store listing yet: add it in Play Console, then run this again")
                continue
            for kind, files in kinds.items():
                if play.remote_hashes(edit, lang, kind) == [sha256(f) for f in files]:
                    print(f"{lang} {kind}: up to date")
                    continue
                play.replace(edit, lang, kind, files)
                print(f"{lang} {kind}: uploaded {len(files)}")
                changed.append(f"{lang} {kind}")
        if not changed:
            play.discard(edit)
            print("The store listing images are up to date.")
            return 0
        play.commit(edit)
        print("Committed: " + ", ".join(changed))
        return 0
    except BaseException:
        try:
            play.discard(edit)
        except Exception:
            pass
        raise


if __name__ == "__main__":
    sys.exit(main())
