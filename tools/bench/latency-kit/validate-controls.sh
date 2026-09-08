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
SP=${KIT_OUT:?set KIT_OUT to the directory holding the built control binaries}
BANDS=${BANDS:-$(dirname "$0")/control-bands.tsv}
HARNESS=${HARNESS:-h3}
FAIL=0

ns() { grep -o '[0-9][0-9.]* ns' | head -1 | sed 's/ ns//'; }

echo "== environment (recorded with every result; a band from another environment is not comparable)"
echo "   machine : $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m)"
echo "   jit     : $(${JAVA_HOME:?set JAVA_HOME}/bin/java -version 2>&1 | head -1)"
echo "   harness : $HARNESS"
echo "   load    : $(uptime | sed 's/.*averages://')"
echo ""

[ -f "$BANDS" ] || { echo "no band file at $BANDS — run with --update first to record one"; exit 2; }

printf "%-22s %-8s %10s %10s %10s  %s\n" CONTROL TOOLCHAIN LOW HIGH MEASURED VERDICT
while IFS=$'\t' read -r control toolchain harness low high cmd; do
  case "$control" in \#*|"") continue;; esac
  [ "$harness" = "$HARNESS" ] || {
      printf "%-22s %-8s %10s %10s %10s  %s\n" "$control" "$toolchain" "$low" "$high" "-" \
             "REFUSED: band recorded for $harness, running $HARNESS"; FAIL=1; continue; }
  # take the minimum of 5 — the honest statistic; the mean carries machine drift
  best=""
  for i in 1 2 3 4 5; do
    v=$(eval "$cmd" 2>/dev/null | ns)
    [ -n "$v" ] || continue
    best=$(awk -v a="$best" -v b="$v" 'BEGIN{print (a==""||b<a)?b:a}')
  done
  if [ -z "$best" ]; then
    printf "%-22s %-8s %10s %10s %10s  %s\n" "$control" "$toolchain" "$low" "$high" "-" \
           "FAIL: produced no result"; FAIL=1; continue
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
