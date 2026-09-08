#!/usr/bin/env bash
# Round 63 §8 — native + PGO for the audit encoder arms, one image per arm with its OWN profile.
# One shared instrumented image collects; each final image is built from the profile of the arm it
# will run, because round 60 established that the profile decides the AOT mode.
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding tclasses/, tvendor/ and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
CP="$SP/tclasses:$SP/tvendor:$RT"
D=$SP/nimg; mkdir -p "$D"
ARMS="text:live text:process binary:live binary:process"
COMMON="--no-fallback -R:MaxHeapSize=512m --initialize-at-build-time=com.benchv --enable-monitoring="

echo "== java: $($G/bin/java -version 2>&1 | head -1)"
echo "== instrumented build"
"$G/bin/native-image" -cp "$CP" --no-fallback -R:MaxHeapSize=512m --pgo-instrument \
    -o "$D/inst" app.BenchBinary > "$D/inst.log" 2>&1 || { tail -30 "$D/inst.log"; exit 1; }

for a in $ARMS; do
  rec=${a%%:*}; clk=${a##*:}; n="${rec}_${clk}"
  echo "== collect $n"
  ( cd "$D" && "$D/inst" -Drecord=$rec -Dclock=$clk -Dwarm=300000 -Diters=1500000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || { tail -20 "$D/$n.collect"; exit 1; }
  [ -f "$D/$n.iprof" ] || { echo "no profile produced for $n"; exit 1; }
  echo "   profile $(shasum -a1 "$D/$n.iprof" | cut -c1-12)  $(stat -f%z "$D/$n.iprof") bytes"
done

for a in $ARMS; do
  rec=${a%%:*}; clk=${a##*:}; n="${rec}_${clk}"
  before=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  echo "== final build $n"
  "$G/bin/native-image" -cp "$CP" --no-fallback -R:MaxHeapSize=512m --pgo="$D/$n.iprof" \
      -o "$D/$n" app.BenchBinary > "$D/$n.log" 2>&1 || { tail -30 "$D/$n.log"; exit 1; }
  after=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  [ "$before" = "$after" ] || { echo "PROFILE MUTATED for $n"; exit 3; }
done

echo "== results (each binary run under the arm it was profiled for, 3 reps)"
for a in $ARMS; do
  rec=${a%%:*}; clk=${a##*:}; n="${rec}_${clk}"
  for r in 1 2 3; do
    "$D/$n" -Drecord=$rec -Dclock=$clk -Dwarm=500000 -Diters=3000000 2>/dev/null | grep '^RESULT' || echo "RUN FAILED $n"
  done
done
