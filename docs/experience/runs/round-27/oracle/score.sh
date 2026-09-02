#!/usr/bin/env bash
# score.sh <project-dir> <main-class>   — runs every probe, diffs against expected
D="$1"; MAIN="${2:-com.acme.app.Main}"
O="$(cd "$(dirname "$0")" && pwd)"
[ -f "$D/cp.txt" ] || (cd "$D" && mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt 2>/dev/null)
pass=0; total=0
for s in "$O"/probes/*.csv; do
  n=$(basename "$s" .csv); total=$((total+1))
  java -cp "$D/target/classes:$(cat "$D/cp.txt")" "$MAIN" "$s" /tmp/sc.txt /tmp/sc.yaml >/dev/null 2>&1
  got=$(sort /tmp/sc.txt 2>/dev/null); want=$(sort "$O/probes/$n.expected")
  if [ "$got" = "$want" ]; then pass=$((pass+1)); printf "  [PASS] %s\n" "$n"
  else printf "  [FAIL] %s\n         want: %s\n         got : %s\n" "$n" "$(echo $want)" "$(echo $got)"; fi
done
echo "  ---- $pass/$total ----"
