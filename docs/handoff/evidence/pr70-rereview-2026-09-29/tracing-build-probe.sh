#!/bin/bash
# PR #70 re-review, finding 3: a tracing-on build of the DEMO processor, replayed by the runner at the reviewed head.
# Run from the repository root after:
#   mvn -o -q package -DskipTests
#   python3 docs/handoff/evidence/pr70-review-2026-09-28/reproduce.py --out <new-dir>
# Usage: tracing-build-probe.sh <reproduce-out-dir> <new-scratch-dir>
# DEMO data only. Writes only under <new-scratch-dir>; nothing tracked is changed.
set -eu
R=$1; T=$2
[ -e "$T" ] && { echo "scratch dir must not exist: $T"; exit 2; }
J=${JAVA_HOME:+$JAVA_HOME/bin/}
RT=$(ls ~/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.16/fluxtion-runtime-1.0.16.jar)
JAR=target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar
SRC=examples/fixture-generator/src/main/java/com/acme/demo/generated/DemoQuoteRecordedProcessor.java
mkdir -p "$T/src" "$T/classes"
cp -R "$R/classes/." "$T/classes/"
# the only change: the generated build traces (a generator option), everything else is the committed DEMO
sed -e 's/eventLogger.trace = false;/eventLogger.trace = true;/' \
    -e 's/eventLogger.traceLevel = LogLevel.NONE;/eventLogger.traceLevel = LogLevel.INFO;/' "$SRC" > "$T/src/DemoQuoteRecordedProcessor.java"
"${J}javac" -proc:none -cp "$T/classes:$RT:$JAR" -d "$T/classes" "$T/src/DemoQuoteRecordedProcessor.java"
set +e
for v in plain trace; do
  C="$R/classes"; [ "$v" = trace ] && C="$T/classes"
  "${J}java" -Xmx64m -Duser.home="$T/home" -cp "$C:$RT:$JAR" ReplayBundle \
      --processor com.acme.demo.generated.DemoQuoteRecordedProcessor --cp "$C" \
      --bundle "$R/normal.fexp" --out "$T/$v.yaml" > "$T/$v-run.log" 2>&1
  echo "$v: runner exit=$? $(grep -o '([0-9]* audit records)' "$T/$v-run.log")"
  "${J}java" -jar "$JAR" --replay-compare "$R/normal.fexp" "$T/$v.yaml" 2>&1 | grep -m1 'replay:'
  echo "$v: EventLogControlEvent records in the output: $(grep -c 'EventLogControlEvent' "$T/$v.yaml")"
done
