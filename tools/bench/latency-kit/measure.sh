#!/usr/bin/env bash
# The disciplined measurement entry point. It REFUSES to report an unstable or unattributable result.
#
# Round 63 produced five silent harness faults, each yielding a plausible number and a wrong
# conclusion, plus several results taken on a loaded machine. Every gate below exists because
# something got past its absence:
#
#   load gate        one run was taken at load average 83 with two orphaned native-image builds going
#   stability gate   the build lottery is +/-8 ns on the audited graph; a single rep cannot see it
#   identity gate    the harness AND the runtime both changed repeatedly, and neither is in a build log
#   drift gate       9.7 ns vs a recorded 3.41 was a harness change, not a regression
#
# Usage:
#   measure.sh "<label>" "<command>" [reps_per_batch] [max_cv_percent]
#   BATCHES=3 by default — the min must reproduce across batches, which is repeatability
#
# Reports min / median / max / spread / CV, and exits non-zero if the run is not trustworthy.
set -uo pipefail
LABEL="${1:?label}"; CMD="${2:?command}"; REPS="${3:-8}"; MAXCV="${4:-}"

# Default limits come from MEASURED repeatability on this graph, not from a round number:
#   native : the min repeats to ~1% — three independent builds read 2.263 / 2.265, and batch minima
#            of one binary sat inside 1 ns
#   JIT    : the min repeats to ~5% — batch minima of 57.50 / 60.65 / 64.18 on the audited graph,
#            because HotSpot's warm-up path differs run to run and no warm-up count removes it
# So a JIT difference under ~5% is not a difference, and this harness says so rather than letting a
# 2 ns "improvement" be reported as one.
if [ -z "$MAXCV" ]; then
  case "$CMD" in
    *"/bin/java "*) MAXCV=6.0 ;;   # JIT
    *)              MAXCV=2.0 ;;   # native image
  esac
fi

# ---- gate 1: the machine must be quiet -------------------------------------------------
LOAD=$(uptime | sed 's/.*averages*://' | awk '{printf "%.2f", $1}')
CORES=$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 8)
BUSY=$(awk -v l="$LOAD" -v c="$CORES" 'BEGIN{print (l > c/2) ? 1 : 0}')
if [ "$BUSY" = 1 ]; then
  echo "REFUSED: load average $LOAD on $CORES cores — wait for the machine to settle."
  echo "         A run taken under load measures the machine, not the change."
  exit 2
fi

# ---- gate 2: the result must identify what produced it ---------------------------------
FIRST=$(eval "$CMD" 2>/dev/null)
HARNESS=$(grep -o 'harness=[a-z0-9]*' <<<"$FIRST" | head -1)
RUNTIME=$(grep -o 'rt:[0-9a-f]*' <<<"$FIRST" | head -1)
if [ -z "$HARNESS" ] || [ -z "$RUNTIME" ]; then
  echo "REFUSED: the result carries no harness version and/or runtime identity."
  echo "         got: ${FIRST:0:120}"
  echo "         Both changed repeatedly in round 63 and neither appears in any build log."
  exit 3
fi

echo "== $LABEL"
echo "   $HARNESS  $RUNTIME"
echo "   machine : $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m), load $LOAD"
echo "   jdk     : $(${JAVA_HOME:-/usr}/bin/java -version 2>&1 | head -1)"

# ---- run: K batches of N reps, min per batch -------------------------------------------
# Gating on the CV of every rep is wrong: JIT warm-up variance is inherent, which is exactly why the
# MINIMUM is the reported statistic. What has to be stable is the min itself. So take the min of each
# batch and check whether THAT reproduces — which is repeatability, the thing actually being claimed.
BATCHES="${BATCHES:-3}"
MINS=()
for k in $(seq "$BATCHES"); do
  best=""
  for i in $(seq "$REPS"); do
    v=$(eval "$CMD" 2>/dev/null | grep -o '[0-9][0-9.]* ns' | head -1 | sed 's/ ns//')
    [ -n "$v" ] && best=$(awk -v a="$best" -v b="$v" 'BEGIN{print (a==""||b<a)?b:a}')
  done
  [ -n "$best" ] || { echo "REFUSED: batch $k produced no result."; exit 4; }
  MINS+=("$best")
  printf "   batch %d min = %s ns\n" "$k" "$best"
done

# ---- gate 3: the MINIMUM must reproduce across batches ---------------------------------
printf '%s\n' "${MINS[@]}" | awk -v maxcv="$MAXCV" -v label="$LABEL" '
  { v[NR]=$1; s+=$1; if (NR==1||$1<lo) lo=$1; if (NR==1||$1>hi) hi=$1 }
  END {
    n=NR; mean=s/n;
    for (i=1;i<=n;i++) { d=v[i]-mean; ss+=d*d }
    sd=(n>1)? sqrt(ss/(n-1)) : 0; cv=(mean>0)? 100*sd/mean : 0;
    printf "\n   batches=%d  min-of-mins=%.3f  spread-of-mins=%.3f  CV=%.2f%%\n", n, lo, hi-lo, cv;
    if (cv > maxcv) {
      printf "\nREFUSED: the MINIMUM varies by %.2f%% across batches, over the %.2f%% limit.\n", cv, maxcv;
      printf "         The measurement is not repeatable; do not compare it to anything.\n";
      exit 5
    }
    printf "   REPEATABLE: %.3f ns  (%.2f Mmsg/s)\n", lo, 1000/lo;
  }'
