#!/usr/bin/env bash
# Build the four quote-engine control arms: {Java, C++} x {LOWEST_LATENCY, LOW_LATENCY_AUDIT+sparse}.
#
# This is the kit's only REPRESENTATIVE control - six nodes doing what a quoting engine does, rather
# than two or three chosen to isolate one operator. The shapes answer "what does a merge cost"; this
# answers "what does a system cost", and the two questions have different answers.
#
# The engine is HAND-WRITTEN nodes, not a DSL flow, because that is the low-latency path to trading:
# a node is a class with fields and a callback, and the framework contributes dispatch and nothing
# else. Its callbacks return void - which needs failBuildIfMissingBooleanReturn = false - so the
# graph carries no dirty flags and no guards. Written with boolean callbacks it carried a guard before
# every node, correctly: a boolean return IS the propagation decision and dropping it would make the
# graph wrong rather than fast. See GenQuoteEngine's class comment.
#
# The classpath comes from branch-classpath.sh, which REFUSES if any fluxtion jar is present. Every
# figure this kit published before 2026-09-10 resolved framework classes from ~/.m2 rather than the
# worktrees under test; they happened to be current, which is exactly why nothing looked wrong.
set -euo pipefail
OUT=${DSL_OUT:?set DSL_OUT to a scratch directory}
JH=${JAVA_HOME:?set JAVA_HOME}
HERE=$(cd "$(dirname "$0")" && pwd)
KIT=$(cd "$HERE/.." && pwd)

eval "$("$KIT/branch-classpath.sh")"
CP=${BENCH_CP:?branch-classpath.sh did not export BENCH_CP}
"$KIT/branch-classpath.sh" --verify >/dev/null || {
  echo "REFUSED: the classpath does not resolve to the branch under test" >&2; exit 1; }

GENCPP=${CPP_RUNTIME:-$(cd "$KIT/../../.." && pwd)/../compiler-baseline/fluxtion-generator-cpp/src/main/resources/cpp-runtime}
[ -d "$GENCPP" ] || GENCPP=$HOME/IdeaProjects/telamin/worktrees/compiler-baseline/fluxtion-generator-cpp/src/main/resources/cpp-runtime
[ -d "$GENCPP" ] || { echo "cannot find the C++ header-only runtime; set CPP_RUNTIME" >&2; exit 1; }

mkdir -p "$OUT/qeclasses"
"$JH/bin/javac" -nowarn -d "$OUT/qeclasses" -cp "$CP" "$HERE/src/GenQuoteEngine.java"

for audit in false true; do
  arm=java-plain; [ "$audit" = true ] && arm=java-audit
  mkdir -p "$OUT/$arm"/{gen,build}
  "$JH/bin/java" -cp "$OUT/qeclasses:$CP" -Daudit="$audit" -Dtarget=java \
      -DoutDir="$OUT/$arm/gen" app.GenQuoteEngine >/dev/null
  # The guard count is an ASSERTION, not a note: this engine's whole claim is straight-line dispatch,
  # and a build that quietly reinstated dirty flags would still run and still produce a number.
  guards=$(grep -c 'guardCheck_' "$OUT/$arm/gen/app/gen/QuoteEngineProcessor.java" || true)
  [ "$guards" = "0" ] || { echo "REFUSED $arm: $guards guard(s) emitted - a callback returned boolean" >&2; exit 1; }
  "$JH/bin/javac" -nowarn -d "$OUT/$arm/build" -cp "$OUT/qeclasses:$CP" \
      "$OUT/$arm/gen/app/gen/QuoteEngineProcessor.java" "$HERE/src/GenQuoteEngine.java" \
      "$HERE/src/BenchQuoteEngine.java" "$HERE/../src/app/HarnessVersion.java" 2>&1 | grep -v deprecat || true
  echo "built $arm (java jit, guards=0)"

  # ---- Java native AOT + PGO, optional ------------------------------------------------------
  # Gated on GRAALVM_HOME and SKIPPED rather than faked: an arm that silently failed to build would
  # make a two-arm comparison read as three. The PGO dance is instrument, collect a profile from a
  # REAL run, rebuild against it - a native image that misses its profile lands in a different
  # performance class entirely, and the profile is what decides which.
  #
  # A PGO PROFILE EMBEDS CLASS NAMES. The .iprof written below carries the fully-qualified name of
  # every method profiled, so it is a channel the repo's text sweep cannot protect - the same category
  # as screenshots and git metadata. These land under target/, which is gitignored, and they must stay
  # there: never commit one, and never attach one to an issue or a report without reading it first.
  #
  # Epsilon is safe here, unlike the flatMap shape whose profile collection exhausted the heap: this
  # engine allocates ZERO bytes per event, proven by the JVM's own accounting and by both JIT arms
  # running 25M events under a non-collecting GC on a 32MB heap.
  if [ -n "${GRAALVM_HOME:-}" ]; then
    nat=java-native-plain; [ "$audit" = true ] && nat=java-native-audit
    jarm=java-plain; [ "$audit" = true ] && jarm=java-audit
    N="$OUT/$nat"; mkdir -p "$N"
    CPN="$OUT/$jarm/build:$OUT/$jarm/gen:$CP"
    "$GRAALVM_HOME/bin/native-image" -cp "$CPN" --no-fallback --gc=epsilon -R:MaxHeapSize=2g \
        -H:-SpawnIsolates --pgo-instrument -o "$N/inst" app.BenchQuoteEngine > "$N/inst.log" 2>&1
    ( cd "$N" && "$N/inst" -Diters=2000000 -Dwarm=500000 -Dbatches=2 -Daudit="$audit" \
        -XX:ProfilesDumpFile="$N/qe.iprof" > "$N/collect" 2>&1 ) || true
    grep -q '^RESULT' "$N/collect" || { echo "REFUSED $nat: profile collection produced no RESULT" >&2; exit 1; }
    "$GRAALVM_HOME/bin/native-image" -cp "$CPN" --no-fallback --gc=epsilon -R:MaxHeapSize=2g \
        -H:-SpawnIsolates --pgo="$N/qe.iprof" -o "$N/native" app.BenchQuoteEngine > "$N/native.log" 2>&1
    # Assert the image is what it claims. A build that silently fell back to sampled defaults reads as
    # a PGO number and is not one.
    for chk in "PGO: user-provided" "Garbage collector: Epsilon GC"; do
      grep -q "$chk" "$N/native.log" || { echo "REFUSED $nat: build log missing '$chk'" >&2; exit 1; }
    done
    echo "built $nat (java native aot + pgo, gc=epsilon)"
  else
    echo "GRAALVM_HOME unset - skipping the native arm rather than reporting two arms as three"
  fi

  arm=cpp-plain; [ "$audit" = true ] && arm=cpp-audit
  rm -rf "$OUT/$arm"; mkdir -p "$OUT/$arm"/{gen,build}
  "$JH/bin/java" -cp "$OUT/qeclasses:$CP" -Daudit="$audit" -Dtarget=cpp \
      -DoutDir="$OUT/$arm/gen" app.GenQuoteEngine >/dev/null
  guards=$(grep -c 'guardCheck_' "$OUT/$arm/gen/app/gen/QuoteEngineProcessor.java" || true)
  [ "$guards" = "0" ] || { echo "REFUSED $arm: $guards guard(s) emitted" >&2; exit 1; }
  python3 - "$OUT/$arm/gen/app/gen/QuoteEngineProcessor.java" "$OUT/$arm/build/QuoteEngineProcessor.h" <<'PY'
import sys
s = open(sys.argv[1]).read()
# The event structs are the author's side of the contract, exactly as the shape bench does it.
s = s.replace("namespace app::gen {", "namespace app::gen {\n"
    "struct MarketTick { int32_t symbol=0, bidPx=0, askPx=0, bidQty=0, askQty=0; };\n"
    "struct Fill { int32_t symbol=0, qty=0; };", 1)
open(sys.argv[2], "w").write(s)
PY
  cp "$GENCPP"/*.h "$OUT/$arm/build/"
  cp "$HERE/src/main-quoteengine.cpp" "$OUT/$arm/build/"
  ( cd "$OUT/$arm/build"
    extra=""; grep -q setLogSink QuoteEngineProcessor.h && extra="-DHAS_AUDIT"
    # The runtime identity is a digest of what this binary was built from - the emitted processor plus
    # the header-only runtime beside it. measure.sh refuses a figure that cannot say which runtime
    # produced it, and rightly.
    RT_ID=$(cat QuoteEngineProcessor.h fluxtion*.h 2>/dev/null | shasum -a 256 | cut -c1-12)
    clang++ -std=c++17 -O3 -Wall -Wextra -I. -DHARNESS_TAG='"h5"' -DRUNTIME_TAG="\"rt:$RT_ID\"" \
        $extra -o qebench main-quoteengine.cpp )
  echo "built $arm (cpp -O3, guards=0)"
done

cat <<'NOTE'

built. the four control commands:
  "$JAVA_HOME/bin/java" -cp "$DSL_OUT/java-plain/build:$BENCH_CP" -Diters=5000000 -Dwarm=2000000 -Dbatches=5 -Daudit=false app.BenchQuoteEngine
  "$JAVA_HOME/bin/java" -cp "$DSL_OUT/java-audit/build:$BENCH_CP" -Diters=5000000 -Dwarm=2000000 -Dbatches=5 -Daudit=true  app.BenchQuoteEngine
  $DSL_OUT/cpp-plain/build/qebench 5000000 2000000 5
  $DSL_OUT/cpp-audit/build/qebench 5000000 2000000 5

allocation (all four are zero, and each is checked a different way):
  -Dalloc=true                                      java, the JVM's own per-thread accounting
  -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xmx32m   java, a NON-COLLECTING gc
  heapCalls= in the cpp RESULT line                 cpp, a counting global operator new
NOTE
