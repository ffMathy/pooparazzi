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
    # A check under account/ (other than Account's own) runs the app against the Firebase emulators. ail starts them
    # for the end-to-end checks (ail.config.json's service "firebase"), but not when it pictures a node: start them
    # here when nothing answers on their ports, and stop them afterwards.
    emulators=""
    if find "$dir/account" -mindepth 2 -name "${node}${suffix}.kt" 2>/dev/null | grep -q . && ! curl -sf http://127.0.0.1:9099/ >/dev/null; then
      log="$(mktemp)"
      setsid scripts/ail/firebase-emulators.sh >"$log" 2>&1 &
      emulators=$!
      # Stop them, and wait until they are gone, so the next check doesn't take a stopping emulator for a running one.
      trap 'kill -TERM -- "-$emulators" 2>/dev/null || true
            for _ in $(seq 1 60); do curl -sf http://127.0.0.1:9099/ >/dev/null || curl -sf http://127.0.0.1:8080/ >/dev/null || break; sleep 1; done' EXIT
      for _ in $(seq 1 150); do
        curl -sf http://127.0.0.1:9099/ >/dev/null && curl -sf http://127.0.0.1:8080/ >/dev/null && break
        sleep 2
      done
      curl -sf http://127.0.0.1:8080/ >/dev/null || { tail -20 "$log" >&2; exit 1; }
    fi
    "${gradle[@]}" ":$module:testDebugUnitTest" --tests "$class" --rerun -Pail.shots="${AIL_SHOTS_DIR:-}" "${derived[@]}" || status=$?
    if [[ $kind == e2e ]]; then
      if [[ -f "$results/TEST-$class.xml" ]]; then cp "$results/TEST-$class.xml" "$3"; fi
    fi
    exit $status ;;
  *)
    echo "usage: $0 build | e2e <Node> <report> | derived <Node>" >&2; exit 2 ;;
esac
