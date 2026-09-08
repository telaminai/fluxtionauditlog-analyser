#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg14; mkdir -p "$D"; CP="$SP/plainonly:$SP/pl_res:$RT"
for i in 1 2; do
  n="local$i"
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
      -o "$D/${n}_inst" app.BenchPlainLocal > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Dwarm=500000 -Diters=2000000 -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
      -o "$D/$n" app.BenchPlainLocal > "$D/$n.log" 2>&1
  echo "built $n"
done
echo LOCALBUILT
