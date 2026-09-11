#!/usr/bin/env bash
# Build the venue-core A/B: the SAME graph with its tuning as folded CONSTANTS and as captured NODE
# STATE, for both targets.
#
# Why it exists. The venue-core arms were built ad hoc and the steps were never written down, so the
# C++ arm could not be rebuilt at all - the event-type injection and the two identity -D flags lived
# only in a shell that had scrolled away. A control that cannot be regenerated stops defending its
# figures the moment anything under it moves, which is the argument build-dsl-controls.sh already
# makes and this graph was exempt from by accident.
#
# What the two arms differ by, exactly: eight values.
#   CONTROL  public static final int / constexpr  - Java compile-time constants the JIT folds, mirrored
#                                                   BY HAND in main-venuecore.cpp
#   CFG      constructor-assigned instance fields - declared once in the graph, captured into the
#                                                   generated Java source and the generated C++ struct
# Ten more constants deliberately do NOT move: BID/ASK, the four ack types and the four intent kinds
# are a tag ENCODING shared with everything that talks to the graph, not configuration. SUB_TICK_SHIFT
# and BASE_HALF_SPREAD are the price representation; STALE_NANOS is a contract with the event
# generator's timer cadence, enforced by a refusing invariant.
#
# Usage:  AB_OUT=<scratch> ./build-venuecore-ab.sh
set -euo pipefail
OUT=${AB_OUT:?set AB_OUT to a scratch directory}
JH=${JAVA_HOME:?set JAVA_HOME}
HERE=$(cd "$(dirname "$0")" && pwd)
KIT=$(cd "$HERE/.." && pwd)
eval "$("$KIT/branch-classpath.sh")"

mkdir -p "$OUT"/{classes,genC,genV,buildC,buildV,cppC,cppV,cppbuildC,cppbuildV,hdr}
"$JH/bin/javac" -nowarn -d "$OUT/classes" -cp "$BENCH_CP" \
    "$HERE/src/GenVenueCore.java" "$HERE/src/GenVenueCoreCfg.java"

for arm in C:GenVenueCore:VenueCoreProcessor V:GenVenueCoreCfg:VenueCoreCfgProcessor; do
  IFS=: read -r v gen proc <<<"$arm"
  "$JH/bin/java" -cp "$OUT/classes:$BENCH_CP" -Dtarget=java -DoutDir="$OUT/gen$v" "app.$gen" >/dev/null
  "$JH/bin/java" -cp "$OUT/classes:$BENCH_CP" -Dtarget=cpp  -DoutDir="$OUT/cpp$v" "app.$gen" >/dev/null
  bench=$( [ "$v" = C ] && echo BenchVenueCore || echo BenchVenueCoreCfg )
  "$JH/bin/javac" -nowarn -d "$OUT/build$v" -cp "$OUT/classes:$BENCH_CP" \
      "$OUT/gen$v/app/gen/$proc.java" "$HERE/src/$bench.java" "$HERE/src/$gen.java" \
      "$KIT/src/app/HarnessVersion.java"
  echo "built java arm $v ($proc)"
done

# ---- C++ arms ---------------------------------------------------------------------------------
cat > "$OUT/hdr/WriteHdr.java" <<'EOF'
public class WriteHdr {
    public static void main(String[] a) throws Exception {
        com.telamin.fluxtion.sourcegenerator.cppsrc.CppSourceGenerator
            .writeRuntimeHeaders(java.nio.file.Path.of(a[0]));
    }
}
EOF
"$JH/bin/javac" -nowarn -d "$OUT/hdr" -cp "$BENCH_CP" "$OUT/hdr/WriteHdr.java"

for arm in C:VenueCoreProcessor:main-venuecore.cpp V:VenueCoreCfgProcessor:main-venuecore-cfg.cpp; do
  IFS=: read -r v proc main <<<"$arm"
  d="$OUT/cppbuild$v"
  "$JH/bin/java" -cp "$OUT/hdr:$BENCH_CP" WriteHdr "$d"
  # THE EVENT TYPES, through the generator's hook. The emitter declares handlers for the author's
  # event types and cannot invent their C++ shape - they are the author's types - so it emits a
  # guarded include of "<Processor>.types.h" inside the namespace, and lists what belongs in it.
  #
  # This used to be a text insertion into the generated file, so regenerating discarded it. The
  # generated header is now copied VERBATIM. Field order and widths mirror the Java event classes
  # exactly; nothing verifies that, so a mismatch is a divergence the audit oracle catches only
  # afterwards - which is why the oracle comparison is part of this kit and not an optional extra.
  cp "$OUT/cpp$v/app/gen/$proc.java" "$d/$proc.h"
  cat > "$d/$proc.types.h" <<'TYPES'
struct MarketTick  { int32_t symbol=0, bidPx=0, askPx=0, bidQty=0, askQty=0; int64_t timestamp=0; };
struct OrderUpdate { int32_t symbol=0, side=0, type=0, qty=0, px=0; int64_t generation=0, timestamp=0; };
struct Execution   { int32_t symbol=0, side=0, px=0, qty=0; int64_t generation=0, timestamp=0; };
struct TimerTick   { int64_t now=0; int32_t symbol=0; };
TYPES
  cp "$HERE/src/$main" "$d/main.cpp"
  # Runtime identity, the C++ analogue of HarnessVersion.runtimeTag(): a digest of the runtime the arm
  # actually uses. For C++ that is the emitted headers, not a jar. measure.sh REFUSES a result that
  # cannot say which runtime produced it, so this is a gate input and not decoration.
  RT=$(cat $(ls "$d"/fluxtion*.h | sort) | shasum -a 1 | cut -c1-10)
  ( cd "$d" && clang++ -std=c++17 -O3 -I. \
        -DHARNESS_TAG="\"h5\"" -DRUNTIME_TAG="\"rt:$RT\"" -o bench main.cpp )
  echo "built cpp arm $v ($proc, rt:$RT)"
done
echo
echo "arms in $OUT:  java buildC/buildV   cpp cppbuild{C,V}/bench"
