#!/usr/bin/env bash
# Run the control set and FAIL if any control falls outside its recorded band.
#
# This is the gate that has to pass before any other number from this kit means anything. Round 63
# produced five silent harness faults and one profile that disabled the audit log; every one of them
# would have been caught here, because each moved a control outside its band while leaving every
# recorded input identical.
#
# Bands come from RECORDED-BASELINES.md and are keyed by (control, toolchain, harness version). A band
# from a different harness version is NOT comparable and is refused rather than compared — the harness
# is an input, and a processor that escapes its loop method costs 4.3x on native with nothing in the
# build log to show for it.
#
# Usage:  KIT_OUT=<scratch> ./validate-controls.sh [--update]
set -uo pipefail
# Exported, because the band commands reference $SP and measure.sh runs as a subprocess. Without the
# export every command evaluated to an empty path and every control was refused for "no identity" —
# which looked like a harness fault rather than a missing variable.
export SP=${KIT_OUT:?set KIT_OUT to the directory holding the built control binaries}
# The C++ controls are REBUILDABLE rather than merely re-runnable: CPPB points at the output of
# cpp/build-controls.sh. Defaulted so a run without it still resolves rather than reporting a missing
# binary as a failing band.
export CPPB=${CPPB:-$(cd "$(dirname "$0")" && pwd)/cpp/build}
export JAVA_HOME=${JAVA_HOME:?set JAVA_HOME}
export RT=${RT:?set RT to the fluxtion-runtime jar the controls were built against}
BANDS=${BANDS:-$(dirname "$0")/control-bands.tsv}
# The harness a band was recorded under. NOT a default to assume — each run's actual harness is read
# from the binary's own output and compared to this, because comparing a declared value to another
# declared value checks nothing. An h5 binary passed an h3 band check until this was fixed.
EXPECT_HARNESS=${EXPECT_HARNESS:-h5}
FAIL=0

ns() { grep -o '[0-9][0-9.]* ns' | head -1 | sed 's/ ns//'; }

echo "== environment (recorded with every result; a band from another environment is not comparable)"
echo "   machine : $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m)"
echo "   jit     : $(${JAVA_HOME:?set JAVA_HOME}/bin/java -version 2>&1 | head -1)"
echo "   bands   : expecting harness $EXPECT_HARNESS"
echo "   load    : $(uptime | sed 's/.*averages://')"
echo ""

[ -f "$BANDS" ] || { echo "no band file at $BANDS — run with --update first to record one"; exit 2; }

printf "%-22s %-8s %10s %10s %10s  %s\n" CONTROL TOOLCHAIN LOW HIGH MEASURED VERDICT
while IFS=$'\t' read -r control toolchain harness low high cmd; do
  case "$control" in \#*|"") continue;; esac
  # what the binary ACTUALLY reports, not what the band file claims
  probe=$(eval "$cmd" 2>/dev/null)
  actual_h=$(grep -o 'harness=[a-z0-9]*' <<<"$probe" | head -1 | cut -d= -f2)
  actual_rt=$(grep -o 'rt:[0-9a-f]*' <<<"$probe" | head -1)
  if [ -z "$actual_h" ]; then
      printf "%-22s %-8s %10s %10s %10s  %s\n" "$control" "$toolchain" "$low" "$high" "-" \
             "REFUSED: binary reports no harness version"; FAIL=1; continue
  fi
  if [ "$harness" != "$actual_h" ]; then
      printf "%-22s %-8s %10s %10s %10s  %s\n" "$control" "$toolchain" "$low" "$high" "-" \
             "REFUSED: band is $harness, binary is $actual_h"; FAIL=1; continue
  fi
  [ -n "$actual_rt" ] || {
      printf "%-22s %-8s %10s %10s %10s  %s\n" "$control" "$toolchain" "$low" "$high" "-" \
             "REFUSED: binary reports no runtime digest"; FAIL=1; continue; }
  # Delegate to measure.sh so there is ONE definition of "the number". The two tools computed it
  # differently until this was fixed — measure.sh took the minimum over 3 batches of 6, this took the
  # minimum of 5 — and with JIT's 5.49% variance the bands recorded by one did not transfer to the
  # other. A band is only meaningful if the thing measuring it is the thing that recorded it.
  out=$("$(dirname "$0")/measure.sh" "$control/$toolchain" "$cmd" 6 2>&1)
  best=$(grep -o 'REPEATABLE: [0-9.]*' <<<"$out" | head -1 | awk '{print $2}')
  if [ -z "$best" ]; then
    reason=$(grep -o 'REFUSED:.*' <<<"$out" | head -1)
    printf "%-22s %-8s %10s %10s %10s  %s\n" "$control" "$toolchain" "$low" "$high" "-" \
           "${reason:-FAIL: no result}"; FAIL=1; continue
  fi
  verdict=$(awk -v v="$best" -v lo="$low" -v hi="$high" \
      'BEGIN{ if (v<lo) print "FAIL: faster than band - inputs changed?"; \
              else if (v>hi) print "FAIL: slower than band - DRIFT"; else print "ok" }')
  printf "%-22s %-8s %10s %10s %10s  %s\n" "$control" "$toolchain" "$low" "$high" "$best" "$verdict"
  case "$verdict" in FAIL*) FAIL=1;; esac
done < "$BANDS"

echo ""
if [ "$FAIL" -ne 0 ]; then
  echo "CONTROLS FAILED — no other measurement from this kit is comparable to the recorded set."
  echo "Check, in this order: harness version, processor escaping its loop method, -D placement,"
  echo "machine load, the generated inlining directive on the build classpath, the PGO profile SHA."
  exit 1
fi
echo "controls ok — measurements from this kit are comparable to the recorded set"
