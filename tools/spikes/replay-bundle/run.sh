#!/usr/bin/env bash
# Replay spike (2026-09-28): record the DEMO processor's inputs as YAML replay records, replay them into a fresh
# processor with data-driven time, and compare the two audit logs. Needs JDK 21 and the Fluxtion 1.0.16 jars in the
# local Maven repository (fluxtion-builder-api-all-java8 carries the replay classes and the runtime). No compiler key:
# the DEMO processor's generated source is committed. Run from the repo root after `mvn -o -q compile`.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
WORK=${1:-$HERE/work}
CP="$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-builder-api-all-java8/1.0.16/fluxtion-builder-api-all-java8-1.0.16.jar"
rm -rf "$WORK"; mkdir -p "$WORK/src" "$WORK/classes"
cp -R "$REPO/examples/fixture-generator/src/main/java/." "$WORK/src/"
cp "$HERE/Events.java" "$WORK/src/com/acme/demo/event/Events.java"      # the input events as JavaBeans (finding 1)
javac -proc:none -d "$WORK/classes" -cp "$CP" \
  $(find "$WORK/src" -name "*.java" ! -name GenerateFixtures.java ! -path "*/builder/*") "$HERE/ReplaySpike.java"
for mode in per-event per-read; do
  java -cp "$WORK/classes:$CP" ReplaySpike "$WORK/out-$mode" "$mode" 2>/dev/null
  if cmp -s "$WORK/out-$mode/captured-audit.yaml" "$WORK/out-$mode/replayed-audit.yaml"; then
    echo "$mode: replayed audit log is BYTE-IDENTICAL to the captured one"
  else
    echo "$mode: differs only in:"; diff "$WORK/out-$mode/captured-audit.yaml" "$WORK/out-$mode/replayed-audit.yaml" \
      | grep '^[<>]' | sed 's/^[<>] *//' | cut -d: -f1 | sort | uniq -c
  fi
done
