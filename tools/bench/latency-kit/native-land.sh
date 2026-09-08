#!/usr/bin/env bash
# Round 63 §16 — did the baseline LAND? Three independent profile+build cycles; the cliff is a
# build lottery (round 60), so one build cannot tell you which regime you are in.
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg13; mkdir -p "$D"
CP="$SP/plainonly:$SP/pl_res:$RT"
for i in 1 2 3; do
  n="land$i"
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
      -o "$D/${n}_inst" app.BenchPlain > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Dwarm=500000 -Diters=2000000 -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
      -o "$D/$n" app.BenchPlain > "$D/$n.log" 2>&1
  echo "built $n"
done
echo LANDBUILT
