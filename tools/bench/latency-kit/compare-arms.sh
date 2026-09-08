#!/usr/bin/env bash
# Compare two arms, REFUSING to report unless they differ by one variable.
#
# Gaussian elimination only works if exactly one coefficient moves. Round 63 produced three wrong
# conclusions from comparisons where two did:
#   - "AOT is 1.44x slower"        : arms run in blocks, so machine state moved with the compiler
#   - "native beats JIT by 13%"    : native got clock=process, JIT silently got clock=live
#   - "guards off is 23% slower"   : a pre-h3 binary compared against an h3 one
# In every case the number was real and the attribution was not.
#
# So this script extracts the recorded inputs from BOTH arms' own output and stops if anything other
# than the named variable differs. The harness version is checked first, because it is the input that
# nothing else records and it is worth 4.3x on native.
#
# Usage:  ./compare-arms.sh "<label A>" "<cmd A>" "<label B>" "<cmd B>" [reps] [expected-differing-key]
set -uo pipefail
LA="${1:?label A}"; CA="${2:?command A}"; LB="${3:?label B}"; CB="${4:?command B}"
REPS="${5:-6}"; VARY="${6:-}"

field() { grep -o "$2=[A-Za-z0-9._-]*" <<<"$1" | head -1 | cut -d= -f2; }
ns()    { grep -o '[0-9][0-9.]* ns' <<<"$1" | head -1 | sed 's/ ns//'; }

a1=$(eval "$CA" 2>/dev/null); b1=$(eval "$CB" 2>/dev/null)
[ -n "$a1" ] && [ -n "$b1" ] || { echo "one arm produced no output"; exit 2; }

echo "== declared inputs"
mismatch=0
# rt = the runtime digest. It changed six times in round 63 and appears in no build log,
# so a comparison across two runtimes is exactly as wrong as one across two harnesses.
for k in harness rt graph record clock; do
  if [ "$k" = rt ]; then
    va=$(grep -o 'rt:[0-9a-f]*' <<<"$a1" | head -1); vb=$(grep -o 'rt:[0-9a-f]*' <<<"$b1" | head -1)
  else
    va=$(field "$a1" "$k"); vb=$(field "$b1" "$k")
  fi
  if [ "$va" != "$vb" ] && [ "$k" != "$VARY" ]; then
    printf "   %-9s %-14s %-14s  <-- DIFFERS and is not the declared variable\n" "$k" "${va:-<none>}" "${vb:-<none>}"
    mismatch=1
  else
    printf "   %-9s %-14s %-14s  %s\n" "$k" "${va:-<none>}" "${vb:-<none>}" \
           "$([ "$va" != "$vb" ] && echo '(the declared variable)' || echo ok)"
  fi
done
if [ -z "$(field "$a1" harness)" ] || [ -z "$(field "$b1" harness)" ]; then
  echo "   REFUSED: an arm carries no harness version. A pre-versioned binary cannot be compared —"
  echo "            the harness is worth 4.3x on native and nothing else records it."
  exit 3
fi
if ! grep -q 'rt:' <<<"$a1" || ! grep -q 'rt:' <<<"$b1"; then
  echo "   REFUSED: an arm carries no runtime digest. The runtime changed six times in round 63 and"
  echo "            a figure taken before one of those is not comparable to one taken after."
  exit 3
fi
[ "$mismatch" = 0 ] || { echo; echo "REFUSED: more than one variable differs. Fix the inputs, not the conclusion."; exit 4; }

# also refuse if the arms did different work
for k in recPerEvent avgRecBytes v; do
  if [ "$k" = rt ]; then
    va=$(grep -o 'rt:[0-9a-f]*' <<<"$a1" | head -1); vb=$(grep -o 'rt:[0-9a-f]*' <<<"$b1" | head -1)
  else
    va=$(field "$a1" "$k"); vb=$(field "$b1" "$k")
  fi
  [ -z "$va" ] && continue
  [ "$va" = "$vb" ] || { echo; echo "REFUSED: $k differs ($va vs $vb) — the arms did not do the same work."; exit 5; }
done

echo; echo "== interleaved, min of $REPS"
ba=""; bb=""
for i in $(seq "$REPS"); do
  v=$(ns "$(eval "$CA" 2>/dev/null)"); [ -n "$v" ] && ba=$(awk -v a="$ba" -v b="$v" 'BEGIN{print (a==""||b<a)?b:a}')
  v=$(ns "$(eval "$CB" 2>/dev/null)"); [ -n "$v" ] && bb=$(awk -v a="$bb" -v b="$v" 'BEGIN{print (a==""||b<a)?b:a}')
done
printf "   %-28s %10s ns\n" "$LA" "$ba"
printf "   %-28s %10s ns\n" "$LB" "$bb"
awk -v a="$ba" -v b="$bb" 'BEGIN{ d=b-a; printf "   %-28s %10.2f ns  (%.1f%%)\n", "difference", d, 100*d/a;
  if (d<8 && d>-8) print "\n   NOTE: within the +/-8 ns build lottery measured on the audited graph.\n         Build each configuration at least twice before calling this a difference." }'
