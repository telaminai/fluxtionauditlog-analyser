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
SHAPES=${SHAPES:-"plain merge notify maponnotify"}

mkdir -p "$OUT/shclasses"
"$JH/bin/javac" -nowarn -d "$OUT/shclasses" -cp "$CP" "$HERE/src/GenShapes.java"

for shape in $SHAPES; do
  mkdir -p "$OUT/$shape"/{gen,javabuild,cppbuild}
  "$JH/bin/java" -cp "$OUT/shclasses:$CP" -Dshape="$shape" -Dtarget=java \
      -DoutDir="$OUT/$shape/gen" app.GenShapes >/dev/null
  "$JH/bin/javac" -nowarn -d "$OUT/$shape/javabuild" -cp "$OUT/shclasses:$CP" \
      "$OUT/$shape/gen/app/gen/ShapeProcessor.java" "$HERE/src/BenchJavaShapes.java" \
      "$HERE/src/GenShapes.java"

  rm -rf "$OUT/$shape/gencpp"; mkdir -p "$OUT/$shape/gencpp"
  "$JH/bin/java" -cp "$OUT/shclasses:$CP" -Dshape="$shape" -Dtarget=cpp \
      -DoutDir="$OUT/$shape/gencpp" app.GenShapes >/dev/null
  ( cd "$OUT/$shape/cppbuild"
    python3 - "$OUT/$shape/gencpp/app/gen/ShapeProcessor.java" <<'PY'
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
    cp "$HERE/src/main-shapes.cpp" .
    clang++ -std=c++17 -O3 -Wall -Wextra -I. -DSHAPE_NAME="\"$shape\"" $extra -o shbench main-shapes.cpp )
  echo "built $shape (java jit + cpp -O3)"
done
