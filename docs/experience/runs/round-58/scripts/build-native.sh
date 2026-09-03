#!/usr/bin/env bash
# Builds the benchmark as a native image. Epsilon GC keeps the zero-allocation proof intact:
# if the steady state allocated, the image would exhaust its heap and die rather than collect.
set -euo pipefail
W="$(cd "$(dirname "$0")" && pwd)"
GH="$W/gvm/Contents/Home"
RT="$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.14/fluxtion-runtime-1.0.14.jar"
NAME="${1:-graalbench-native}"; shift || true
"$GH/bin/native-image" \
  -cp "$W/classes:$RT" \
  --no-fallback \
  --gc=epsilon \
  -R:MaxHeapSize=256m \
  -H:+UnlockExperimentalVMOptions \
  --initialize-at-build-time=com.bench,com.plain,com.bench.gen \
  "$@" \
  -o "$W/$NAME" \
  GraalBench 2>&1 | tail -40
ls -la "$W/$NAME"
