#!/usr/bin/env bash
# Build the DSL controls — the SAME graph emitted for both targets, from the generator.
#
# These are rebuildable rather than merely re-runnable, and that is the point: the figures they defend
# are about the artefact the generator produces, and a control that cannot be regenerated stops
# defending that the moment the emitter changes. Round 63 published "specialised C++ is 23x the Java
# DSL" from a HAND-WRITTEN C++ chain the generator does not emit; the generated form is 3.8x. A control
# built from source the generator wrote cannot make that mistake again.
#
# Usage:  DSL_OUT=<scratch> CP_CPP=<classpath with fluxtion-generator-cpp> ./build-dsl-controls.sh
set -euo pipefail
OUT=${DSL_OUT:?set DSL_OUT to a scratch directory}
CP=${CP_CPP:?set CP_CPP to a classpath containing fluxtion-generator-cpp and the builder}
JH=${JAVA_HOME:?set JAVA_HOME}
G=${GRAALVM_HOME:-}
HERE=$(cd "$(dirname "$0")" && pwd)
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar

mkdir -p "$OUT"/{classes,genjava,gencpp,javabuild,cppbuild}
"$JH/bin/javac" -nowarn -d "$OUT/classes" -cp "$CP" "$HERE/src/Gen.java" "$HERE/src/BenchJava.java"

# ---- generate the SAME graph for both targets -------------------------------------------------
"$JH/bin/java" -cp "$OUT/classes:$CP" -Dtarget=java -DoutDir="$OUT/genjava" -Dlowest=true app.Gen >/dev/null
"$JH/bin/java" -cp "$OUT/classes:$CP" -Dtarget=cpp  -DoutDir="$OUT/gencpp"  -Dlowest=true app.Gen >/dev/null

# ---- Java arm ---------------------------------------------------------------------------------
"$JH/bin/javac" -nowarn -d "$OUT/javabuild" -cp "$OUT/classes:$CP" \
    "$OUT/genjava/app/gen/DslProcessor.java" "$HERE/src/BenchJava.java" "$HERE/src/Gen.java"
echo "built c-dsl java (jit)"

# ---- C++ arm ----------------------------------------------------------------------------------
# -O3, NOT -O2. Measured: 3.55 -> 2.76 ns, a 22% win, against a prediction that -O2 already inlined
# everything. It does not. march=native, LTO and PGO are all worth nothing here and PGO is 6% WORSE,
# so the control is built at plain -O3 rather than at the most flags available.
( cd "$OUT/cppbuild"
  "$JH/bin/java" -cp "$CP" -e 2>/dev/null || true
  python3 - "$OUT/gencpp/app/gen/DslProcessor.java" <<'PY'
import sys
s = open(sys.argv[1]).read()
s = s.replace("namespace app::gen {", "namespace app::gen {\nstruct Tick { int32_t price = 0; };", 1)
open("DslProcessor.h", "w").write(s)
PY
  cp "$HERE/src/main.cpp" .
  clang++ -std=c++17 -O3 -Wall -Wextra -I. -o bench main.cpp )
echo "built c-dsl cpp (-O3)"

# ---- Java native AOT, optional ----------------------------------------------------------------
if [ -n "$G" ]; then
  D="$OUT/nimg"; mkdir -p "$D"
  CPN="$OUT/javabuild:$OUT/genjava:$RT"
  "$G/bin/native-image" -cp "$CPN" --no-fallback --gc=epsilon -R:MaxHeapSize=1g -H:-SpawnIsolates \
      --pgo-instrument -o "$D/dsl_inst" app.BenchJava > "$D/dsl_inst.log" 2>&1
  ( cd "$D" && "$D/dsl_inst" -Diters=3000000 -Dwarm=500000 -Dbatches=3 \
      -XX:ProfilesDumpFile="$D/dsl.iprof" > "$D/dsl.collect" 2>&1 ) || true
  grep -q '^RESULT' "$D/dsl.collect" || { echo "native collect FAILED"; exit 1; }
  "$G/bin/native-image" -cp "$CPN" --no-fallback --gc=epsilon -R:MaxHeapSize=1g -H:-SpawnIsolates \
      --pgo="$D/dsl.iprof" -o "$D/dsl_native" app.BenchJava > "$D/dsl.log" 2>&1
  for chk in "PGO: user-provided" "Garbage collector: Epsilon GC"; do
    grep -q "$chk" "$D/dsl.log" || { echo "native MISSING: $chk"; exit 1; }
  done
  echo "built c-dsl java (native aot)"
else
  echo "GRAALVM_HOME unset — skipping the native arm rather than silently reporting two arms as three"
fi
