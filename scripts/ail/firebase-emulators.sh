#!/usr/bin/env bash
# Starts the Firebase Authentication and Firestore emulators for the AIL checks, as ail.config.json's
# run.*.services.firebase says: the account checks (checks/e2e/account/) run the phone app against them, with a demo
# project (demo-pooparazzi, which needs no credentials and reaches no real Firebase project) and the real Firestore
# rules (firebase/firestore.rules). The ports are firebase/firebase.json's: Authentication 9099, Firestore 8080.
#
# Needs Bun (firebase-tools from firebase/package.json), Node.js 22 (/opt/node22, or node on PATH) and Java 21 for the
# Firestore emulator, as the rules' own tests do (docs/BACKEND.md › Testing the rules). The first run downloads the
# Firestore emulator into ~/.cache/firebase/emulators. It stays up until ail stops it (SIGTERM to its process group).
set -euo pipefail
cd "$(dirname "$0")/../../firebase"
if [[ -d /opt/node22/bin ]]; then export PATH="/opt/node22/bin:$PATH"; fi
if [[ ! -x node_modules/.bin/firebase ]]; then bun install --frozen-lockfile; fi
exec node_modules/.bin/firebase emulators:start --only auth,firestore --project demo-pooparazzi
