#!/usr/bin/env bash
# R1 (2026-09-28): our own replay writer and reader (spec R-D8) on processors GENERATED with the writer compiled in.
# Copies examples/fixture-generator, adds ReplayCapture/ReplayReader and two builders (risk limit 2, and a changed
# build with limit 3), regenerates through the fluxtion-maven-plugin (the plugin reads the compiler key; nothing here
# does), and runs R1Run: capture on a clock that ticks per read, replay with the matching runner, compare.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../../.." && pwd)
WORK=${1:-$HERE/work}
rm -rf "$WORK"; mkdir -p "$WORK"
rsync -a --exclude target "$REPO/examples/fixture-generator/" "$WORK/"
S="$WORK/src/main/java/com/acme/demo"
mkdir -p "$S/replay" "$S/r1" "$S/r2" "$S/probe"
cp "$HERE/ReplayCapture.java" "$HERE/ReplayReader.java" "$HERE/EventTypes.java" "$S/replay/"
cp "$HERE/R1Run.java" "$S/r1/"
cp "$HERE/R2Run.java" "$S/r2/"
cp "$HERE/NestingProbe.java" "$S/probe/"
cp "$HERE/DemoQuoteCaptureProcessorBuilder.java" "$HERE/DemoQuoteChangedProcessorBuilder.java" "$S/builder/"
cd "$WORK"
mvn -q process-classes
mvn -q exec:java -Dexec.mainClass=com.acme.demo.r1.R1Run -Dexec.args="$WORK/out" 2>&1 | grep -v '^updating'
diff "$WORK/out/captured-audit.yaml" "$WORK/out/same-build-replayed-audit.yaml" \
  | grep '^[<>]' | sed 's/^[<>] *//' | cut -d: -f1 | sort | uniq -c || true      # diff exits 1 when they differ
echo "== nesting probe: does a graph-raised event arrive inside its input's cycle?"
mvn -q exec:java -Dexec.mainClass=com.acme.demo.probe.NestingProbe 2>&1 | sed -n '/^<< returned/,$p'
echo "== R2: record by identity at the consumption point, replay by plain injection"
mvn -q exec:java -Dexec.mainClass=com.acme.demo.r2.R2Run -Dexec.args="$WORK/out-r2" 2>&1 | grep -v '^updating'
for l in same-build changed-build; do
  echo "   $l differs in:"; diff "$WORK/out-r2/captured-audit.yaml" "$WORK/out-r2/$l-replayed-audit.yaml" \
    | grep '^[<>]' | sed 's/^[<>] *//' | cut -d: -f1 | sort | uniq -c || true
done
