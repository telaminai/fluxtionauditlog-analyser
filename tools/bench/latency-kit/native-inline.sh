#!/usr/bin/env bash
# Round 63 §10 — rebuild WITH the generated inlining directive on the classpath.
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the class trees, generated resources and GRAALVM_HOME.txt}
G=$(cat "$SP/GRAALVM_HOME.txt")
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
D=$SP/nimg3; mkdir -p "$D"

run () {  # name classes main collectArgs...
  local n="$1" classes="$2" main="$3"; shift 3
  local CP="$classes:$SP/sr_res:$RT"     # sr_res carries META-INF/native-image with the directive
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo-instrument \
      -o "$D/${n}_inst" "$main" > "$D/${n}_inst.log" 2>&1
  ( cd "$D" && "$D/${n}_inst" "$@" -Dwarm=300000 -Diters=1500000 \
      -XX:ProfilesDumpFile="$D/$n.iprof" > "$D/$n.collect" 2>&1 )
  local before; before=$(shasum -a1 "$D/$n.iprof" | cut -c1-12)
  "$G/bin/native-image" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=1g --pgo="$D/$n.iprof" \
      -o "$D/$n" "$main" > "$D/$n.log" 2>&1
  [ "$(shasum -a1 "$D/$n.iprof" | cut -c1-12)" = "$before" ] || { echo "PROFILE MUTATED $n"; exit 3; }
  echo "== $n"
  grep -iE "PriorityForceInline|UnlockExperimental" "$D/$n.log" | head -2 || echo "   (directive not echoed in log)"
}

run inl_bin  "$SP/binclasses_sr"  app.BenchMonoBin  -Dclock=process
run inl_text "$SP/monoclasses_sr" app.BenchMonoText
echo "BUILDS DONE"
