#!/usr/bin/env bash
# Round 63 §9 — isolate the two §8 method errors: GC choice, and receiver provability.
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg2; mkdir -p "$D"

build_and_run () {   # name  classpath  mainclass  gcflag  runargs...
  local n="$1" cp="$2" main="$3" gc="$4"; shift 4
  echo "== $n : instrument"
  "$G/bin/native-image" -cp "$cp" --no-fallback $gc -R:MaxHeapSize=1g --pgo-instrument \
      -o "$D/${n}_inst" "$main" > "$D/${n}_inst.log" 2>&1 || { tail -25 "$D/${n}_inst.log"; return 1; }
  echo "== $n : collect"
  ( cd "$D" && "$D/${n}_inst" "$@" -Dwarm=300000 -Diters=1500000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 )
  [ -f "$D/$n.iprof" ] || { echo "no profile for $n"; return 1; }
  local before; before=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  echo "== $n : final (profile $before)"
  "$G/bin/native-image" -cp "$cp" --no-fallback $gc -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
      -o "$D/$n" "$main" > "$D/$n.log" 2>&1 || { tail -25 "$D/$n.log"; return 1; }
  [ "$(shasum -a1 "$D/$n.iprof" | cut -c1-12)" = "$before" ] || { echo "PROFILE MUTATED $n"; return 1; }
  grep -E "Garbage collector|PGO:" "$D/$n.log" | sed "s/^/   $n /"
  grep -E "types, .* fields, and .* methods found reachable" "$D/$n.log" | sed "s/^/   $n /"
  for r in 1 2 3; do "$D/$n" "$@" -Dwarm=500000 -Diters=3000000 2>/dev/null | grep '^RESULT'; done
}

MONO="$SP/monoclasses:$RT"
POLY="$SP/tclasses:$SP/tvendor:$RT"

echo "########## A: poly + epsilon  (isolates GC vs §8's serial) ##########"
build_and_run poly_text_eps  "$POLY" app.BenchBinary   "--gc=epsilon" -Drecord=text -Dclock=live
echo "########## B: mono + epsilon  (isolates provability) ##########"
build_and_run mono_text_eps  "$MONO" app.BenchMonoText "--gc=epsilon"
echo "########## C: mono + serial   (provability alone, GC held at §8's) ##########"
build_and_run mono_text_ser  "$MONO" app.BenchMonoText ""
