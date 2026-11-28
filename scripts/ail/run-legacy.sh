#!/usr/bin/env bash
# Starts the existing phone app for the AIL checks, as ail.config.json's run.legacy says.
#
# The app runs headless on the JVM with Robolectric, in each check's own process (checks/e2e/Phone.kt): no emulator
# or device is needed, which is how the app's own tests already run on CI. So "starting" it means building it and its
# checks once; then this stays up until ail stops it, and .ail/legacy/ready says it can be driven.
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p .ail/legacy
rm -f .ail/legacy/ready
scripts/ail/check.sh build > .ail/legacy/build.log 2>&1 || { tail -40 .ail/legacy/build.log; exit 1; }
touch .ail/legacy/ready
trap 'rm -f .ail/legacy/ready' EXIT
while true; do sleep 3600; done
