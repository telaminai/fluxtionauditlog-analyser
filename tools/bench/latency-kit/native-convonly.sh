#!/usr/bin/env bash
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg7; mkdir -p "$D"; n=convonly
CP="$SP/convonly:$SP/cv_res:$RT"
"$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
    -o "$D/${n}_inst" app.BenchConvOnly > "$D/${n}_inst.log" 2>&1
( cd "$D" && "$D/${n}_inst" -Drecord=binary -Dclock=process -Dgraph=conv -Dwarm=300000 -Diters=1000000 \
    -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 )
grep -q '^RESULT' "$D/$n.collect" || { echo "COLLECT FAILED"; tail -5 "$D/$n.collect"; exit 1; }
before=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
"$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
    -o "$D/$n" app.BenchConvOnly > "$D/$n.log" 2>&1
[ "$(shasum -a1 "$D/$n.iprof" | cut -c1-12)" = "$before" ] || { echo "PROFILE MUTATED"; exit 3; }
for chk in "PGO: user-provided" "Garbage collector: Epsilon GC" "PriorityForceInline" "target machine: armv8.1-a"; do
  grep -q "$chk" "$D/$n.log" || { echo "MISSING: $chk"; exit 4; }
done
echo "convonly: PGO ok | epsilon ok | inline ok | target ok | profile $before"
grep -E "types, .* fields" "$D/$n.log" | head -1
