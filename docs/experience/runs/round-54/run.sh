#!/usr/bin/env bash
# Rebuilds and re-runs the THROUGHPUT LADDER from committed sources (BLOG-NUMBERS.md, first table).
# Covers that table only -- the collector, audit-cost, floor and latency tables are not scripted and
# their raw output is not committed. See BLOG-NUMBERS.md "Claims this evidence does NOT support".
set -euo pipefail
cd "$(dirname "$0")"
RT="${FLUXTION_RUNTIME:-$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.14/fluxtion-runtime-1.0.14.jar}"
[ -f "$RT" ] || { echo "set FLUXTION_RUNTIME to fluxtion-runtime-1.0.14.jar"; exit 2; }
[ -d generated ] || { echo "generated/ is gitignored (upstream copyright header, rule 1)."
                      echo "Regenerate first:  java -cp <builder-cp> gen/Gen.java generated reach"; exit 2; }
OUT=$(mktemp -d)
javac -nowarn -d "$OUT" -cp "$RT" \
  $(find src -name '*.java' ! -name Bench.java) plain/com/plain/*.java \
  generated/com/bench/gen/BenchProcessor.java blog/BlogBench.java
echo "arm,ns_per_event,bytes_per_event,breaches,updates,buffer"
for ARM in plainInline plainGuarded fluxtionStreamClock fluxtionDefault; do
  for _ in 1 2 3 4 5; do
    java -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xmx256m -Xms256m \
      -cp "$OUT:$RT" -Darm=$ARM -Dwarm=5000000 -Diters=200000000 BlogBench 2>/dev/null | grep '^RESULT'
  done
done
