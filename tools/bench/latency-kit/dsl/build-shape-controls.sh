#!/usr/bin/env bash
# Build the M55.1 shape controls - merge, notify, mapOnNotify - plus the plain chain as the reference.
#
# notify and mapOnNotify differ only in their last node, which is what makes P14 answerable at all: a
# difference between them is a difference between the constructs, not between two benchmarks.
set -euo pipefail
OUT=${DSL_OUT:?set DSL_OUT to a scratch directory}
CP=${CP_CPP:?set CP_CPP to a classpath containing fluxtion-generator-cpp and the builder}
JH=${JAVA_HOME:?set JAVA_HOME}
HERE=$(cd "$(dirname "$0")" && pwd)
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
SHAPES=${SHAPES:-"plain merge notify maponnotify"}
# AUDIT=true builds the same shapes with LOW_LATENCY_AUDIT + BINARY, so audit cost per event is the
# DIFFERENCE between two builds of one graph rather than a figure quoted on its own.
AUDIT=${AUDIT:-false}
# Epsilon never collects, which is right for shapes that do not allocate and fatal for those that do:
# flatMap allocates per element, so its profile collection exhausted the heap and produced no RESULT.
# The GC is therefore per shape, and an arm built with a collecting GC is NOT directly comparable to an
# epsilon one - that is stated wherever its number is.
native_gc() { case "$1" in flatmap) echo serial ;; *) echo epsilon ;; esac; }
SUFFIX=""; [ "$AUDIT" = "true" ] && SUFFIX="-audit"

mkdir -p "$OUT/shclasses"
"$JH/bin/javac" -nowarn -d "$OUT/shclasses" -cp "$CP" "$HERE/src/GenShapes.java"

for shape in $SHAPES; do
  shape_dir="$shape$SUFFIX"
  mkdir -p "$OUT/$shape_dir"/{gen,javabuild,cppbuild}
  "$JH/bin/java" -cp "$OUT/shclasses:$CP" -Dshape="$shape" -Daudit="$AUDIT" -Dtarget=java \
      -DoutDir="$OUT/$shape_dir/gen" app.GenShapes >/dev/null
  "$JH/bin/javac" -nowarn -d "$OUT/$shape_dir/javabuild" -cp "$OUT/shclasses:$CP" \
      "$OUT/$shape_dir/gen/app/gen/ShapeProcessor.java" "$HERE/src/BenchJavaShapes.java" \
      "$HERE/src/GenShapes.java" "$HERE/../src/app/HarnessVersion.java"

  rm -rf "$OUT/$shape_dir/gencpp"; mkdir -p "$OUT/$shape_dir/gencpp"
  "$JH/bin/java" -cp "$OUT/shclasses:$CP" -Dshape="$shape" -Daudit="$AUDIT" -Dtarget=cpp \
      -DoutDir="$OUT/$shape_dir/gencpp" app.GenShapes >/dev/null
  ( cd "$OUT/$shape_dir/cppbuild"
    python3 - "$OUT/$shape_dir/gencpp/app/gen/ShapeProcessor.java" <<'PY'
import sys
s = open(sys.argv[1]).read()
s = s.replace("namespace app::gen {", "namespace app::gen {\nstruct Tick { int32_t price = 0; };", 1)
open("ShapeProcessor.h", "w").write(s)
open("has_sink.txt", "w").write("1" if "struct Sink" in s else "0")
PY
    for entry in $(echo "$CP_CPP" | tr ':' ' '); do
      if [ -d "$entry/cpp-runtime" ]; then cp "$entry"/cpp-runtime/*.h . && break; fi
      case "$entry" in *fluxtion-generator-cpp*.jar)
        unzip -o -j "$entry" 'cpp-runtime/*.h' -d . >/dev/null 2>&1 && break ;;
      esac
    done
    extra=""
    [ "$(cat has_sink.txt)" = "1" ] && extra="-DHAS_SINK"
    grep -q "setLogSink" ShapeProcessor.h && extra="$extra -DHAS_AUDIT"
    grep -q "Emitter&" ShapeProcessor.h && extra="$extra -DHAS_FLATMAP"
    # The runtime identity is a digest of what this binary was actually built from - the emitted
    # processor plus the header-only runtime beside it. Without it measure.sh refuses the figure, and
    # rightly: a number that cannot say which runtime produced it is how stale figures survive.
    RT_ID=$(cat ShapeProcessor.h fluxtion*.h 2>/dev/null | shasum -a 256 | cut -c1-12)
    cp "$HERE/src/main-shapes.cpp" .
    clang++ -std=c++17 -O3 -Wall -Wextra -I. -DSHAPE_NAME="\"$shape\"" -DHARNESS_TAG="\"h5\"" -DRUNTIME_TAG="\"rt:$RT_ID\"" \
        $extra -o shbench main-shapes.cpp )
  echo "built $shape_dir (java jit + cpp -O3)"

  # ---- Java native AOT, optional ------------------------------------------------------------
  # Gated on GRAALVM_HOME, and SKIPPED rather than faked: the audit figures this kit records are all
  # JIT, and an arm that silently did not build would make a two-arm comparison read as three. The
  # PGO dance is the same as build-dsl-controls.sh - instrument, collect a profile from a real run,
  # rebuild against it - because a native image that misses its profile lands in a different
  # performance class entirely and the profile is what decides which.
  if [ -n "${GRAALVM_HOME:-}" ]; then
    D="$OUT/$shape_dir/nimg"; mkdir -p "$D"
    CPN="$OUT/$shape_dir/javabuild:$OUT/$shape_dir/gen:$RT"
    "$GRAALVM_HOME/bin/native-image" -cp "$CPN" --no-fallback --gc=epsilon -R:MaxHeapSize=2g \
        -H:-SpawnIsolates --pgo-instrument -o "$D/inst" app.BenchJavaShapes > "$D/inst.log" 2>&1
    ( cd "$D" && "$D/inst" -Diters=1000000 -Dwarm=200000 -Dbatches=2 -Dshape="$shape" \
        -Daudit="$AUDIT" -XX:ProfilesDumpFile="$D/shape.iprof" > "$D/collect" 2>&1 ) || true
    grep -q '^RESULT' "$D/collect" || { echo "native collect FAILED for $shape_dir"; exit 1; }
    "$GRAALVM_HOME/bin/native-image" -cp "$CPN" --no-fallback --gc=epsilon -R:MaxHeapSize=2g \
        -H:-SpawnIsolates --pgo="$D/shape.iprof" -o "$D/native" app.BenchJavaShapes \
        > "$D/native.log" 2>&1
    case "$GC" in epsilon) GCNAME="Epsilon GC" ;; serial) GCNAME="Serial GC" ;; esac
    for chk in "PGO: user-provided" "Garbage collector: $GCNAME"; do
      grep -q "$chk" "$D/native.log" || { echo "native MISSING: $chk"; exit 1; }
    done
    echo "built $shape_dir (java native aot + pgo, gc=$GC)"
  else
    echo "GRAALVM_HOME unset - skipping the native arm for $shape_dir rather than reporting two arms as three"
  fi
done
