#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg9; mkdir -p "$D"
CP="$SP/heavyonly:$SP/hv_res:$RT"
build () {
  local n="$1"; shift
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g "$@" --pgo-instrument \
      -o "$D/${n}_inst" app.BenchHeavyOnly > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Drecord=binary -Dclock=process -Dgraph=conv -Dwarm=200000 -Diters=600000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/$n.collect" || { echo "$n COLLECT FAILED"; tail -4 "$D/$n.collect"; return 1; }
  local b; b=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g "$@" --pgo="$D/$n.iprof" \
      -o "$D/$n" app.BenchHeavyOnly > "$D/$n.log" 2>&1
  [ "$(shasum -a1 "$D/$n.iprof" | cut -c1-12)" = "$b" ] || { echo "$n PROFILE MUTATED"; return 1; }
  grep -q "PGO: user-provided" "$D/$n.log" || { echo "$n PGO MISSING"; return 1; }
  grep -q "PriorityForceInline" "$D/$n.log" || { echo "$n INLINE DIRECTIVE MISSING"; return 1; }
  echo "built $n (pgo+inline verified, profile $b)"
}
build heavy_base
build heavy_noiso -H:-SpawnIsolates
echo HEAVYBUILT
