#!/usr/bin/env bash
# Builds and runs the AIL checks of the phone and watch apps, as ail.config.json's `tests` and the target's `checks.run.*` say.
#   scripts/ail/check.sh build                 builds both apps and every check (checks/e2e and checks/derived)
#   scripts/ail/check.sh e2e <Node> <report>   runs <Node>'s end-to-end check (its class <Node>Checks), writing a JUnit report
#   scripts/ail/check.sh derived <Node>        runs <Node>'s derived check (its class <Node>Shots); with AIL_SHOTS_DIR set,
#                                              each scenario saves its last screen there
# The checks are Kotlin tests run on Robolectric: the phone's are :app's (its test source set includes checks/), the
# watch's, under checks/e2e/watch/ and checks/derived/watch/, are :wear's.
set -euo pipefail
cd "$(dirname "$0")/../.."
if [[ -z "${ANDROID_HOME:-}" && ! -f local.properties ]]; then
  for d in "$HOME/Android/Sdk" /opt/android-sdk /usr/local/lib/android/sdk; do [[ -d "$d" ]] && export ANDROID_HOME="$d" && break; done
fi
gradle=(./gradlew --console=plain -q)
case "$1" in
  build)
    exec "${gradle[@]}" :app:compileDebugUnitTestKotlin :wear:compileDebugUnitTestKotlin ;;
  e2e|derived)
    kind=$1 node=$2
    suffix=$([[ $kind == e2e ]] && echo Checks || echo Shots)
    class="com.ffmathy.pooparazzi.checks.${node}${suffix}"
    # The module whose tests hold the check: the watch's under checks/<kind>/watch/, else the phone's.
    dir=checks/$([[ $kind == e2e ]] && echo e2e || echo derived)
    module=app
    if [[ -d "$dir/watch" ]] && find "$dir/watch" -name "${node}${suffix}.kt" | grep -q .; then module=wear; fi
    results=$module/build/test-results/testDebugUnitTest
    rm -f "$results/TEST-$class.xml"
    status=0
    derived=(); [[ $kind == derived ]] && derived=(-Pail.derived=true)
    "${gradle[@]}" ":$module:testDebugUnitTest" --tests "$class" --rerun -Pail.shots="${AIL_SHOTS_DIR:-}" "${derived[@]}" || status=$?
    if [[ $kind == e2e ]]; then
      if [[ -f "$results/TEST-$class.xml" ]]; then cp "$results/TEST-$class.xml" "$3"; fi
    fi
    exit $status ;;
  *)
    echo "usage: $0 build | e2e <Node> <report> | derived <Node>" >&2; exit 2 ;;
esac
