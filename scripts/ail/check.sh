#!/usr/bin/env bash
# Builds and runs the AIL checks of the phone app, as ail.config.json's `tests` and the target's `checks.run.*` say.
#   scripts/ail/check.sh build                 builds the app and every check (checks/e2e and checks/derived)
#   scripts/ail/check.sh e2e <Node> <report>   runs <Node>'s end-to-end check (its class <Node>Checks), writing a JUnit report
#   scripts/ail/check.sh derived <Node>        runs <Node>'s derived check (its class <Node>Shots); with AIL_SHOTS_DIR set,
#                                              each scenario saves its last screen there
# The checks are Kotlin tests of :app (its test source set includes checks/), run on Robolectric.
set -euo pipefail
cd "$(dirname "$0")/../.."
if [[ -z "${ANDROID_HOME:-}" && ! -f local.properties ]]; then
  for d in "$HOME/Android/Sdk" /opt/android-sdk /usr/local/lib/android/sdk; do [[ -d "$d" ]] && export ANDROID_HOME="$d" && break; done
fi
gradle=(./gradlew --console=plain -q)
results=app/build/test-results/testDebugUnitTest
case "$1" in
  build)
    exec "${gradle[@]}" :app:compileDebugUnitTestKotlin ;;
  e2e|derived)
    kind=$1 node=$2
    class="com.ffmathy.pooparazzi.checks.${node}$([[ $kind == e2e ]] && echo Checks || echo Shots)"
    rm -f "$results/TEST-$class.xml"
    status=0
    "${gradle[@]}" :app:testDebugUnitTest --tests "$class" --rerun -Pail.shots="${AIL_SHOTS_DIR:-}" || status=$?
    if [[ $kind == e2e ]]; then
      if [[ -f "$results/TEST-$class.xml" ]]; then cp "$results/TEST-$class.xml" "$3"; fi
    fi
    exit $status ;;
  *)
    echo "usage: $0 build | e2e <Node> <report> | derived <Node>" >&2; exit 2 ;;
esac
