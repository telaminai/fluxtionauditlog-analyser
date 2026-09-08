#!/usr/bin/env bash
# Round 63 §11 — same as the §10 winner, plus -march=native. The JIT has always compiled for the
# actual CPU; every earlier native build targeted a generic armv8.1-a baseline.
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg5; mkdir -p "$D"
CP="$SP/binclasses_lla:$SP/lla_res:$RT"
build () {  # name extraflags...
  local n="$1"; shift
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g "$@" --pgo-instrument \
      -o "$D/${n}_inst" app.BenchMonoBin > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Dclock=process -Dwarm=300000 -Diters=1500000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 )
  local before; before=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g "$@" --pgo="$D/$n.iprof" \
      -o "$D/$n" app.BenchMonoBin > "$D/$n.log" 2>&1
  [ "$(shasum -a1 "$D/$n.iprof" | cut -c1-12)" = "$before" ] || { echo "PROFILE MUTATED $n"; exit 3; }
  echo "== $n : $(grep -o 'target machine: [a-z0-9.-]*' "$D/$n.log" | head -1)"
}
build base
build march -march=native
echo BUILDS DONE
