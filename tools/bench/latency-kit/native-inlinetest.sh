#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg11; mkdir -p "$D"
# NOTE: cv_res is deliberately OMITTED so the generated directive is not picked up
AUD="$SP/convonly:$RT"
BASE="$SP/plainonly:$RT"

build () {   # name classpath main extraflags...
  local n="$1" cp="$2" main="$3"; shift 3
  "$G/bin/native-image" -cp "$cp" --no-fallback --gc=epsilon -R:MaxHeapSize=1g -H:-SpawnIsolates "$@" \
      --pgo-instrument -o "$D/${n}_inst" "$main" > "$D/${n}_inst.log" 2>&1 || { echo "$n INSTR FAIL"; tail -8 "$D/${n}_inst.log"; return 1; }
  ( cd "$D" && "$D/${n}_inst" -Drecord=binary -Dclock=process -Dgraph=conv -Dwarm=300000 -Diters=1000000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/$n.collect" || { echo "$n COLLECT FAIL"; tail -4 "$D/$n.collect"; return 1; }
  local b; b=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  "$G/bin/native-image" -cp "$cp" --no-fallback --gc=epsilon -R:MaxHeapSize=1g -H:-SpawnIsolates "$@" \
      --pgo="$D/$n.iprof" -o "$D/$n" "$main" > "$D/$n.log" 2>&1 || { echo "$n BUILD FAIL"; tail -8 "$D/$n.log"; return 1; }
  local inl; inl=$(grep -c "PriorityForceInline" "$D/$n.log" || true)
  echo "built $n  (PriorityForceInline lines in log: $inl)"
}

# audited, NO directive
build aud_nodirective "$AUD" app.BenchConvOnly
# audited, whole-class directive supplied explicitly
build aud_wholeclass  "$AUD" app.BenchConvOnly -H:+UnlockExperimentalVMOptions "-H:PriorityForceInline=com.bench.conv.ConvProcessor.*"
# audited, METHOD-LEVEL directive — does selecting individual methods work at all?
build aud_methodlevel "$AUD" app.BenchConvOnly -H:+UnlockExperimentalVMOptions "-H:PriorityForceInline=com.bench.conv.ConvProcessor.handleEvent*"
# baseline, NO directive — the control for W3
build base_nodirective "$BASE" app.BenchPlain
echo INLINETESTBUILT
