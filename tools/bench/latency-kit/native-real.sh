#!/usr/bin/env bash
# Round 63 §12 — the real audited graph, native. Every build input is VERIFIED in the log, not assumed.
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg6; mkdir -p "$D"
# both generated resource dirs carry their processor's inlining directive
CP="$SP/rgclasses:$SP/cv_res:$SP/lla_res:$RT"

build () {   # name graph record
  local n="$1" g="$2" m="$3"
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
      -o "$D/${n}_inst" app.BenchAudited > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" -Drecord=$m -Dclock=process -Dgraph=$g -Dwarm=300000 -Diters=1000000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 )
  grep -q '^RESULT' "$D/$n.collect" || { echo "COLLECT FAILED $n:"; tail -5 "$D/$n.collect"; exit 1; }
  local before; before=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
      -o "$D/$n" app.BenchAudited > "$D/$n.log" 2>&1
  [ "$(shasum -a1 "$D/$n.iprof" | cut -c1-12)" = "$before" ] || { echo "PROFILE MUTATED $n"; exit 3; }

  # --- verify the four build inputs, do not assume them ---
  grep -q "PGO: user-provided"            "$D/$n.log" || { echo "$n: PGO NOT APPLIED"; exit 4; }
  grep -q "Garbage collector: Epsilon GC" "$D/$n.log" || { echo "$n: NOT EPSILON GC"; exit 5; }
  grep -q "PriorityForceInline"           "$D/$n.log" || { echo "$n: INLINING DIRECTIVE MISSING"; exit 6; }
  grep -q "target machine: armv8.1-a"     "$D/$n.log" || { echo "$n: UNEXPECTED TARGET MACHINE"; exit 7; }
  echo "$n: PGO ok | epsilon ok | inline directive ok | target armv8.1-a ok | profile $before"
}

build conv_bin  conv binary
build conv_text conv text
build tail_bin  tail binary
echo ALLBUILT
