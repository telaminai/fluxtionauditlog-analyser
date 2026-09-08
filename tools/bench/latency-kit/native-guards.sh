#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg18; mkdir -p "$D"
b () { local n="$1" cp="$2" main="$3"
  "$G/bin/native-image" -cp "$cp" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
      -o "$D/${n}_inst" "$main" > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Drecord=binary -Dclock=process -Dgraph=conv -Dwarm=300000 -Diters=1000000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/$n.collect" || { echo "$n COLLECT FAIL"; return 1; }
  "$G/bin/native-image" -cp "$cp" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
      -o "$D/$n" "$main" > "$D/$n.log" 2>&1
  echo "built $n"; }
b guards_off_audited "$SP/conv2:$SP/cv_res:$RT"  app.BenchConvLocal
b guards_off_fair    "$SP/fdir2:$SP/fr_res:$RT"  app.BenchFairLocal
echo GUARDSBUILT
