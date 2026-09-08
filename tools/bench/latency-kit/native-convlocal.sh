#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg15; mkdir -p "$D"; CP="$SP/convonly:$SP/cv_res:$RT"
for i in 1 2; do
  n="convlocal$i"
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g -H:-SpawnIsolates \
      --pgo-instrument -o "$D/${n}_inst" app.BenchConvLocal > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Drecord=binary -Dclock=process -Dgraph=conv -Dwarm=300000 -Diters=1000000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/$n.collect" || { echo "$n COLLECT FAIL"; tail -3 "$D/$n.collect"; exit 1; }
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g -H:-SpawnIsolates \
      --pgo="$D/$n.iprof" -o "$D/$n" app.BenchConvLocal > "$D/$n.log" 2>&1
  echo "built $n"
done
echo CONVLOCALBUILT
