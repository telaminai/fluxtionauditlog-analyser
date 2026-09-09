#!/usr/bin/env bash
# Build the C++ controls: generate each processor with the C++ target, then compile it against the
# node bodies in this directory.
#
# Fails loudly and names what is missing rather than producing a binary that measures the wrong thing.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${CPP_CONTROL_OUT:-$HERE/build}"
: "${JAVA_HOME:?set JAVA_HOME}"
: "${FIXTURE_CP:?set FIXTURE_CP to a classpath holding the compiled fixture classes and generators}"
: "${M2:=$HOME/.m2/repository}"

fail() { echo "build-controls: $*" >&2; exit 1; }

command -v clang++ >/dev/null || fail "clang++ not on PATH"
CPP_JAR=$(ls "$M2"/com/telamin/fluxtion/fluxtion-generator-cpp/*/fluxtion-generator-cpp-*.jar 2>/dev/null | head -1)
[ -n "$CPP_JAR" ] || fail "fluxtion-generator-cpp is not installed in $M2 — build the compiler reactor first"

mkdir -p "$OUT/include"
# The header-only C++ runtime ships as a resource of the generator, so the control cannot drift from
# the emitter that produced the source.
( cd "$OUT/include" && unzip -o -q -j "$CPP_JAR" 'cpp-runtime/*.h' ) || fail "cannot extract the C++ runtime"
echo "runtime headers: $(ls "$OUT/include" | wc -l | tr -d ' ')"

generate() {   # generate <mainClass> <pkg> <outDir> [extra -D...]
  local main="$1" pkg="$2" dir="$3"; shift 3
  rm -rf "$dir"; mkdir -p "$dir"
  "$JAVA_HOME/bin/java" -cp "$FIXTURE_CP" \
      -Dfluxtion.sourceGeneratorId=cpp -Dpkg="$pkg" -DsrcDir="$dir" -DresDir="$dir" "$@" "$main" \
      >/dev/null 2>&1 || fail "generation failed for $main"
  find "$dir" -name '*.java' | head -1
}

compile() {    # compile <name> <headerPath> <benchSource>
  local name="$1" header="$2" src="$3"
  cp "$header" "$OUT/include/$(basename "$header" .java).h"
  clang++ -O3 -march=native -std=c++17 -I"$OUT/include" -o "$OUT/$name" "$src" \
      || fail "$name did not compile — the generator emitted C++ the compiler rejects"
  echo "built $OUT/$name"
}

echo "== 30-node converging graph =="
H=$(generate app.GenerateDagConverging com.bench.convc "$OUT/gen-conv" -Dnoreentrancy=true -Dmode=none)
compile conv_generated "$H" "$HERE/conv/bench.cpp"

echo "== 4-node price ladder =="
H=$(generate app.GeneratePriceLadder com.pl.gen "$OUT/gen-ladder" -Dmode=lowest)
compile ladder_generated "$H" "$HERE/ladder/bench.cpp"

echo "== price ladder, one fat node, no re-entrancy =="
H=$(generate app.GenerateFat com.pl.fatc "$OUT/gen-fat" -Dnoreentrancy=true)
compile ladder_fat "$H" "$HERE/ladder/fat_bench.cpp"

echo
echo "controls built into $OUT — now check the checksums before trusting any timing:"
echo "  conv   expects v=120.5988"
echo "  ladder expects v=2185441000"
