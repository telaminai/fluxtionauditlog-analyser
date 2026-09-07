#!/usr/bin/env bash
# Fluxtion latency kit — build a processor from a vendor jar and measure it across JVMs.
#
#   ./run.sh --compiler-cp "<classpath with fluxtion-builder + generator-core + velocity>" \
#            [--jvm name=/path/to/java]... [--native /path/to/graalvm/home] [--iters N]
#
# Every figure it prints names its shape. See ../../docs/experience/runs/round-59/NOTES.md.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$HERE/target}"
PKG=com.bench.gen
ITERS=200000000; WARM=5000000
# bytecode level for the benchmark classes, so ONE build runs on every JVM under test
RELEASE="${RELEASE:-17}"
COMPILER_CP=""; GRAAL_HOME=""; JVMS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --compiler-cp) COMPILER_CP="$2"; shift 2;;
    --jvm)         JVMS+=("$2"); shift 2;;
    --native)      GRAAL_HOME="$2"; shift 2;;
    --iters)       ITERS="$2"; shift 2;;
    --warm)        WARM="$2"; shift 2;;
    *) echo "unknown arg: $1" >&2; exit 2;;
  esac
done
[ -n "$COMPILER_CP" ] || { echo "--compiler-cp is required" >&2; exit 2; }
JAVAC="${JAVAC:-javac}"; JAR="${JAR:-jar}"

rm -rf "$OUT"; mkdir -p "$OUT"/{vendor,gen,res,classes,app}

echo "== 1. compile the nodes as a SEPARATE vendor jar (the generator sees bytecode only)"
"$JAVAC" -nowarn --release "$RELEASE" -d "$OUT/vendor" -cp "$COMPILER_CP" \
    "$HERE"/src/com/benchv/Nodes.java "$HERE"/src/com/bench/MarketTick.java
"$JAR" cf "$OUT/vendor-nodes.jar" -C "$OUT/vendor" .

echo "== 2. generate the processor"
"$JAVAC" -nowarn -d "$OUT/classes" -cp "$OUT/vendor-nodes.jar:$COMPILER_CP" "$HERE/src/app/Generate.java"
java -cp "$OUT/classes:$OUT/vendor-nodes.jar:$COMPILER_CP" \
     -Dfluxtion.sourceGeneratorId=local \
     -DsrcDir="$OUT/gen" -DresDir="$OUT/res" -Dpkg="$PKG" app.Generate

echo "== 3. compile the benchmark"
sed "s/__PKG__/$PKG/" "$HERE/src/app/Runner.java.template" > "$OUT/app/Runner.java"
RT=$(tr ':' '\n' <<< "$COMPILER_CP" | grep 'fluxtion-runtime' | head -1)
"$JAVAC" -nowarn --release "$RELEASE" -d "$OUT/classes" -cp "$OUT/vendor-nodes.jar:$RT" \
    $(find "$OUT/gen" -name '*.java') "$HERE"/src/com/benchv/HandBase.java \
    "$OUT/app/Runner.java" "$HERE/src/app/Bench.java"
CP="$OUT/classes:$OUT/res:$OUT/vendor-nodes.jar:$RT"

echo
printf '%-28s %12s %12s\n' "runtime" "generated" "hand-rolled"
printf '%-28s %12s %12s\n' "----------------------------" "------------" "------------"

run_jvm() { # label, java
  local g h
  g=$("$2" -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xmx256m -Xms256m -cp "$CP" \
        -Darm=generated -Dwarm=$WARM -Diters=$ITERS app.Bench 2>/dev/null | awk '/^RESULT/{print $3}')
  h=$("$2" -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xmx256m -Xms256m -cp "$CP" \
        -Darm=hand -Dwarm=$WARM -Diters=$ITERS app.Bench 2>/dev/null | awk '/^RESULT/{print $3}')
  printf '%-28s %12s %12s\n' "$1" "$g" "$h"
}
for spec in "${JVMS[@]}"; do run_jvm "${spec%%=*}" "${spec#*=}"; done

if [ -n "$GRAAL_HOME" ]; then
  NI="$GRAAL_HOME/bin/native-image"
  # no PGO
  "$NI" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=256m -o "$OUT/bench-nopgo" app.Bench >/dev/null 2>&1
  # PGO: profile EVERY arm you deploy -- an unprofiled path is worse than no profile at all
  "$NI" -cp "$CP" --no-fallback --pgo-instrument -R:MaxHeapSize=2g -o "$OUT/bench-instr" app.Bench >/dev/null 2>&1
  P=""
  for arm in generated hand; do
    "$OUT/bench-instr" -XX:ProfilesDumpFile="$OUT/$arm.iprof" -Darm=$arm -Dwarm=1000000 -Diters=20000000 >/dev/null 2>&1 || true
    [ -f "$OUT/$arm.iprof" ] && P="$P,$OUT/$arm.iprof"
  done
  "$NI" -cp "$CP" --no-fallback --gc=epsilon -R:MaxHeapSize=256m --pgo="${P#,}" -o "$OUT/bench-pgo" app.Bench >/dev/null 2>&1
  for img in bench-nopgo bench-pgo; do
    g=$("$OUT/$img" -Darm=generated -Dwarm=$WARM -Diters=$ITERS 2>/dev/null | awk '/^RESULT/{print $3}')
    h=$("$OUT/$img" -Darm=hand      -Dwarm=$WARM -Diters=$ITERS 2>/dev/null | awk '/^RESULT/{print $3}')
    printf '%-28s %12s %12s\n' "native-image ${img#bench-}" "$g" "$h"
  done
fi
echo
echo "ns/event. Output is verified identical across arms by tools/bench/dispatch-bench.py;"
echo "run that against app.Bench for a gated comparison."
