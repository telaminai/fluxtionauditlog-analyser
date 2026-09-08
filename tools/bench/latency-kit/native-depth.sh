#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg16; mkdir -p "$D"
for d in 3 6 12 24; do
  n="depth$d"; CP="$SP/dep$d:$SP/d${d}_res:$RT"
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
      -o "$D/${n}_inst" app.BenchDepth > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Ddepth=$d -Dwarm=300000 -Diters=1000000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/$n.collect" || { echo "$n COLLECT FAIL"; tail -3 "$D/$n.collect"; exit 1; }
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
      -o "$D/$n" app.BenchDepth > "$D/$n.log" 2>&1
  grep -q "PriorityForceInline" "$D/$n.log" || echo "  WARNING $n: no inline directive"
  echo "built $n"
done
echo DEPTHBUILT
