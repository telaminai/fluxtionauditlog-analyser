#!/usr/bin/env bash
# Round 63 §13 — sweep the compiler args in the AUDIT regime. Each arm gets its own PGO profile.
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg8; mkdir -p "$D"
CP="$SP/convonly:$SP/cv_res:$RT"
ARGS="-Drecord=binary -Dclock=process -Dgraph=conv"

build () {
  local n="$1"; shift
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g "$@" --pgo-instrument \
      -o "$D/${n}_inst" app.BenchConvOnly > "$D/${n}_inst.log" 2>&1 || { echo "$n INSTRUMENT FAILED"; tail -12 "$D/${n}_inst.log"; return 1; }
  ( cd "$D" && "$D/${n}_inst" $ARGS -Dwarm=300000 -Diters=1000000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/$n.collect" || { echo "$n COLLECT FAILED"; tail -5 "$D/$n.collect"; return 1; }
  local before; before=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g "$@" --pgo="$D/$n.iprof" \
      -o "$D/$n" app.BenchConvOnly > "$D/$n.log" 2>&1 || { echo "$n BUILD FAILED"; tail -12 "$D/$n.log"; return 1; }
  [ "$(shasum -a1 "$D/$n.iprof" | cut -c1-12)" = "$before" ] || { echo "$n PROFILE MUTATED"; return 1; }
  grep -q "PGO: user-provided" "$D/$n.log" || { echo "$n PGO NOT APPLIED"; return 1; }
  echo "built $n"
}

build base
build noisolate  -H:-SpawnIsolates
build binit      --initialize-at-build-time=com.benchv,com.bench
build wideinline -H:+UnlockExperimentalVMOptions \
   "-H:PriorityForceInline=com.telamin.fluxtion.runtime.audit.*,com.benchv.BinaryLogRecord.*"
echo SWEEPBUILT
