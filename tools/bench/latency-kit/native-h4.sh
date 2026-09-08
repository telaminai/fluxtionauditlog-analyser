#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg20; mkdir -p "$D"
# two builds per configuration, because the lottery is +/-8 ns on the audited graph
b () { local n="$1" cp="$2" main="$3" collect="$4"
  "$G/bin/native-image" -cp "$cp" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
      -o "$D/${n}_inst" "$main" > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && eval "$D/${n}_inst $collect -Dwarm=300000 -Diters=1000000 -XX:ProfilesDumpFile=$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/$n.collect" || { echo "$n COLLECT FAIL"; tail -3 "$D/$n.collect"; return 1; }
  "$G/bin/native-image" -cp "$cp" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
      -o "$D/$n" "$main" > "$D/$n.log" 2>&1
  for c in "PGO: user-provided" "Epsilon GC" "PriorityForceInline"; do
    grep -q "$c" "$D/$n.log" || echo "  WARNING $n missing: $c"; done
  echo "built $n"; }
for i in 1 2; do
  b "aud$i"  "$SP/conv2:$SP/cv_res:$RT"  app.BenchConvLocal  "-Drecord=binary -Dclock=process -Dgraph=conv"
  b "base$i" "$SP/fdir2:$SP/fr_res:$RT"  app.BenchFairLocal  ""
done
echo H4BUILT
