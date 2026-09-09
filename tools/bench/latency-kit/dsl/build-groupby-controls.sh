#!/usr/bin/env bash
# Build the groupBy controls at a chosen CARDINALITY — the same graph emitted for both targets.
#
# Why this exists: the 6.4x groupBy figure was measured over FOUR keys, against a C++ store that was a
# linear scan while Java's was a HashMap. Four is far to the left of any crossover, so the number
# described the benchmark more than the target. This control takes the key count as a runtime argument
# so the crossover can be found rather than assumed.
#
# Usage: DSL_OUT=<scratch> CP_CPP=<classpath> ./build-groupby-controls.sh
set -euo pipefail
OUT=${DSL_OUT:?set DSL_OUT to a scratch directory}
CP=${CP_CPP:?set CP_CPP to a classpath containing fluxtion-generator-cpp and the builder}
JH=${JAVA_HOME:?set JAVA_HOME}
HERE=$(cd "$(dirname "$0")" && pwd)

mkdir -p "$OUT"/{gbclasses,gbjava,gbcpp,gbjavabuild,gbcppbuild}
# Only the generator here: the bench imports the processor that does not exist until it has run.
"$JH/bin/javac" -nowarn -d "$OUT/gbclasses" -cp "$CP" "$HERE/src/GenGroupBy.java"

"$JH/bin/java" -cp "$OUT/gbclasses:$CP" -Dtarget=java -DoutDir="$OUT/gbjava" app.GenGroupBy >/dev/null
"$JH/bin/java" -cp "$OUT/gbclasses:$CP" -Dtarget=cpp  -DoutDir="$OUT/gbcpp"  app.GenGroupBy >/dev/null

"$JH/bin/javac" -nowarn -d "$OUT/gbjavabuild" -cp "$OUT/gbclasses:$CP" \
    "$OUT/gbjava/app/gen/GroupByProcessor.java" "$HERE/src/BenchJavaGroupBy.java" "$HERE/src/GenGroupBy.java"
echo "built c-groupby java (jit)"

( cd "$OUT/gbcppbuild"
  python3 - "$OUT/gbcpp/app/gen/GroupByProcessor.java" <<'PY'
import sys, re
s = open(sys.argv[1]).read()
s = s.replace("namespace app::gen {",
              "namespace app::gen {\nstruct Tick { int32_t price = 0; int32_t key = 0; };", 1)
open("GroupByProcessor.h", "w").write(s)
# The store's struct name is GENERATED. Discover it rather than hardcoding a name that changes when
# the node numbering does - the oracle chain hardcodes one and would break silently the same way.
m = re.search(r"struct (Dsl_groupBy\w*)", s)
if not m:
    raise SystemExit("could not find the generated groupBy struct in the emitted header")
open("struct_name.txt", "w").write(m.group(1))
print("groupBy struct:", m.group(1))
PY
  # The C++ runtime headers ship INSIDE the generator artefact. Take them from whatever is on the
  # classpath rather than from a path in someone's checkout, so the control builds wherever the
  # generator does.
  found=0
  for entry in $(echo "$CP_CPP" | tr ':' ' '); do
    if [ -d "$entry/cpp-runtime" ]; then cp "$entry"/cpp-runtime/*.h . && found=1 && break; fi
    case "$entry" in *fluxtion-generator-cpp*.jar)
      if unzip -o -j "$entry" 'cpp-runtime/*.h' -d . >/dev/null 2>&1; then found=1; break; fi ;;
    esac
  done
  [ "$found" = 1 ] || { echo "could not find cpp-runtime headers on CP_CPP"; exit 1; }
  cp "$HERE/src/main-groupby.cpp" .
  clang++ -std=c++17 -O3 -Wall -Wextra -I. \
      -DGROUPBY_STRUCT="$(cat struct_name.txt)" -o gbbench main-groupby.cpp )
echo "built c-groupby cpp (-O3)"
