#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg12; mkdir -p "$D"; AUD="$SP/convonly:$RT"
build () {
  local n="$1"; shift
  "$G/bin/native-image" -cp "$AUD" --no-fallback --gc=epsilon -R:MaxHeapSize=1g -H:-SpawnIsolates "$@" \
      --pgo-instrument -o "$D/${n}_inst" app.BenchConvOnly > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Drecord=binary -Dclock=process -Dgraph=conv -Dwarm=300000 -Diters=1000000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/$n.collect" || { echo "$n COLLECT FAIL"; return 1; }
  "$G/bin/native-image" -cp "$AUD" --no-fallback --gc=epsilon -R:MaxHeapSize=1g -H:-SpawnIsolates "$@" \
      --pgo="$D/$n.iprof" -o "$D/$n" app.BenchConvOnly > "$D/$n.log" 2>&1
  echo "built $n"
}
# CONTROL: a pattern that matches NOTHING. If this lands where aud_methodlevel did, the
# "method-level works" result is the build lottery, not the flag.
build ctl_nonsense  -H:+UnlockExperimentalVMOptions "-H:PriorityForceInline=com.bench.conv.NoSuchClass.nothing*"
# REPLICATE: the same method-level flag again, fresh profile. Two builds of one config bound the lottery.
build ctl_methodlevel2 -H:+UnlockExperimentalVMOptions "-H:PriorityForceInline=com.bench.conv.ConvProcessor.handleEvent*"
# REPLICATE: no directive again
build ctl_nodirective2
echo CONTROLBUILT
