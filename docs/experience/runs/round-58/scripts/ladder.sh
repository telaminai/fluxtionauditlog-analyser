#!/usr/bin/env bash
# ladder.sh <JAVA_HOME> <label> [extra jvm flags...]
set -euo pipefail
JH="$1"; LABEL="$2"; shift 2
W="$(cd "$(dirname "$0")" && pwd)"
RT="$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.14/fluxtion-runtime-1.0.14.jar"
for ARM in plainInline plainGuarded fluxtionStreamClock fluxtionDefault; do
  for REP in 1 2 3 4 5; do
    "$JH/bin/java" -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xmx256m -Xms256m "$@" \
      -cp "$W/classes:$RT" -Darm=$ARM -Dwarm=5000000 -Diters=200000000 GraalBench 2>/dev/null \
      | grep '^RESULT' | awk -v l="$LABEL" -v r="$REP" '{print l","$2","$3","$4","$5","$6","$7","r}'
  done
done
