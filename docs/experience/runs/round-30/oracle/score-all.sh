#!/usr/bin/env bash
# score-all.sh <project-dir> <main-class>  — decisions AND evaluation order, both arms scored alike.
D="$1"; MAIN="$2"
O="$(cd "$(dirname "$0")" && pwd)"
[ -f "$D/cp.txt" ] || (cd "$D" && mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt 2>/dev/null)
CP="$D/target/classes:$(cat "$D/cp.txt" 2>/dev/null)"

echo "--- decisions ---"
pass=0; total=0
for s in "$O"/probes/*.csv; do
  n=$(basename "$s" .csv); total=$((total+1))
  rm -f /tmp/d.txt /tmp/e.txt
  java -cp "$CP" "$MAIN" "$s" /tmp/d.txt /tmp/e.txt >/dev/null 2>&1
  if [ ! -f /tmp/d.txt ]; then printf "  [FAIL] %-26s (no output)\n" "$n"; continue; fi
  got=$(sort /tmp/d.txt); want=$(sort "$O/probes/$n.expected")
  # an engine numbering events from 0 rather than 1 is ONE defect, not one per probe; report it as such
  shifted=$(awk -F, 'NF{$1=$1+1; print}' OFS=, /tmp/d.txt | sort)
  # a uniform formatting difference (0.9 vs 0.90) is ONE defect, not one per probe. Normalise numbers
  # to two places on both sides and report it once, the same way 0-based event numbering is reported.
  norm(){ awk -F, '{for(i=1;i<=NF;i++) if($i+0==$i && $i ~ /\./) $i=sprintf("%.2f",$i); print}' OFS=, "$1" | sort; }
  gotn=$(norm /tmp/d.txt); wantn=$(norm "$O/probes/$n.expected")
  if [ "$got" = "$want" ]; then pass=$((pass+1)); printf "  [PASS] %s\n" "$n"
  elif [ "$shifted" = "$want" ]; then pass=$((pass+1)); printf "  [PASS*] %-26s (correct, but event numbers are 0-based)\n" "$n"
  elif [ "$gotn" = "$wantn" ]; then pass=$((pass+1)); printf "  [PASS*] %-26s (correct, but numbers not formatted to 2dp)\n" "$n"
  else printf "  [FAIL] %s\n         want: %s\n         got : %s\n" "$n" "$(echo $want)" "$(echo $got)"; fi
done
echo "  ---- decisions $pass/$total ----"

echo "--- evaluation order (accumulated across all probes) ---"
: > /tmp/all-eval.txt; i=0
for s in "$O"/probes/*.csv; do
  rm -f /tmp/d.txt /tmp/e.txt
  java -cp "$CP" "$MAIN" "$s" /tmp/d.txt /tmp/e.txt >/dev/null 2>&1
  [ -f /tmp/e.txt ] || continue
  # renumber so events from different probes never collide
  awk -v off="$i" -F, '{ $1 = $1 + off; print }' OFS=, /tmp/e.txt >> /tmp/all-eval.txt
  i=$((i+100))
done
if [ -s /tmp/all-eval.txt ]; then python3 "$O/score-order.py" /tmp/all-eval.txt
else echo "  no evaluation file produced — O1/O2/O4 unscoreable"; fi
