#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg17; mkdir -p "$D"; n=fair; CP="$SP/fdir:$SP/fr_res:$RT"
"$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
    -o "$D/${n}_inst" app.BenchFairLocal > "$D/${n}_inst.log" 2>&1
( cd "$D" && "$D/${n}_inst" -Dwarm=500000 -Diters=2000000 -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
grep -q '^RESULT' "$D/$n.collect" || { echo "COLLECT FAIL"; tail -3 "$D/$n.collect"; exit 1; }
"$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
    -o "$D/$n" app.BenchFairLocal > "$D/$n.log" 2>&1
grep -q "PriorityForceInline" "$D/$n.log" && echo "inline directive ok" || echo "WARNING no inline directive"
echo FAIRBUILT
