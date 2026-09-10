#!/usr/bin/env bash
# Measure ONE arm of ONE DSL shape, through the kit's own gate.
#
# This exists because the controls could be BUILT correctly and then measured any old way. They were:
# figures published from this kit on 2026-09-09/10 were taken single-shot, in one order, with the two
# languages interleaved, and had to be corrected across four documents. Two harness bugs came with them:
#
#   - interleaving the Java and C++ arms inflated every C++ number by roughly 2x (notify read 1.59 ns
#     interleaved and 0.75 ns alone), because a JVM winding down is not a quiet machine;
#   - a hand-rolled loop initialised its running minimum to 99, so every arm slower than 99 ns reported
#     exactly 99.0. The Java flatMap arm is 119-139 ns, so it did.
#
# ../measure.sh already implements the method control-bands.tsv describes - batches of reps, minimum
# per batch, and a REFUSAL when the batch minima disagree by more than the arm's CV limit. It also
# refuses on a loaded machine, and its minimum starts empty rather than at a magic number. So the fix is
# not a better loop here; it is to stop writing loops here.
#
# Usage: DSL_OUT=<dir> ./measure-dsl.sh <shape> <arm: java|cpp|native> [audit: true|false]
set -euo pipefail
OUT=${DSL_OUT:?set DSL_OUT to the directory build-shape-controls.sh wrote}
SHAPE=${1:?shape, e.g. plain or flatmap}
ARM=${2:?arm: java, cpp or native}
AUDIT=${3:-false}
HERE=$(cd "$(dirname "$0")" && pwd)
JH=${JAVA_HOME:?set JAVA_HOME}
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar

SUFFIX=""; [ "$AUDIT" = "true" ] && SUFFIX="-audit"
DIR="$OUT/$SHAPE$SUFFIX"
[ -d "$DIR" ] || { echo "no such arm: $DIR — run build-shape-controls.sh first"; exit 1; }

ITERS=${ITERS:-3000000}
WARM=${WARM:-500000}
BATCHES_IN=${BATCHES_IN:-4}

case "$ARM" in
  java)
    CMD="\"$JH/bin/java\" -cp \"$DIR/javabuild:$DIR/gen:$RT:${CP_CPP:-}\" -Diters=$ITERS -Dwarm=$WARM -Dbatches=$BATCHES_IN -Dshape=$SHAPE -Daudit=$AUDIT app.BenchJavaShapes"
    ;;
  native)
    [ -x "$DIR/nimg/native" ] || { echo "no native arm for $SHAPE$SUFFIX — GRAALVM_HOME was unset at build time"; exit 1; }
    CMD="$DIR/nimg/native -Diters=$ITERS -Dwarm=$WARM -Dbatches=$BATCHES_IN -Dshape=$SHAPE -Daudit=$AUDIT"
    ;;
  cpp)
    CMD="$DIR/cppbuild/shbench $ITERS $WARM $BATCHES_IN"
    ;;
  *)
    echo "unknown arm '$ARM' — java, cpp or native"; exit 1 ;;
esac

# ONE language per invocation, deliberately. Measuring two arms in one pass is what inflated the C++
# numbers; the gate cannot see that, so the shape of this script has to prevent it.
exec "$HERE/../measure.sh" "$SHAPE$SUFFIX-$ARM" "$CMD" "${REPS:-6}"
