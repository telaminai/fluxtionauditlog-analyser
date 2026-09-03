#!/usr/bin/env bash
# Three-stage PGO. Profiles are collected from ALL FOUR arms and merged, so the optimised
# image is not overfitted to the one arm we care about -- that would flatter the result.
set -euo pipefail
W="$(cd "$(dirname "$0")" && pwd)"
OH="$W/ogvm/Contents/Home"
RT="$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.14/fluxtion-runtime-1.0.14.jar"
COMMON=(-cp "$W/classes:$RT" --no-fallback --gc=epsilon -R:MaxHeapSize=256m
        -H:+UnlockExperimentalVMOptions
        -H:ReflectionConfigurationFiles="$W/reflect-config.json"
        --initialize-at-build-time=com.bench,com.plain,com.bench.gen)

echo "=== stage 0: baseline Oracle native (no PGO), for a like-for-like control ==="
"$OH/bin/native-image" "${COMMON[@]}" -o "$W/ob-native" GraalBench 2>&1 | tail -2

echo "=== stage 1: instrumented build ==="
"$OH/bin/native-image" "${COMMON[@]}" --pgo-instrument -o "$W/ob-instr" GraalBench 2>&1 | tail -2

echo "=== stage 2: collect profiles from every arm ==="
PROFS=()
for ARM in plainInline plainGuarded fluxtionStreamClock fluxtionDefault; do
  "$W/ob-instr" -XX:ProfilesDumpFile="$W/$ARM.iprof" \
      -Darm=$ARM -Dwarm=1000000 -Diters=20000000 >/dev/null 2>&1
  ls -la "$W/$ARM.iprof" | awk '{printf "  %s  %.0f KB\n", "'"$ARM"'", $5/1024}'
  PROFS+=("$W/$ARM.iprof")
done

echo "=== stage 3: optimised build from merged profiles ==="
IFS=, ; PGOARG="${PROFS[*]}" ; unset IFS
"$OH/bin/native-image" "${COMMON[@]}" --pgo="$PGOARG" -o "$W/ob-native-pgo" GraalBench 2>&1 | tail -2
ls -la "$W"/ob-native "$W"/ob-native-pgo
