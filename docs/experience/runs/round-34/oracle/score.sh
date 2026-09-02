#!/usr/bin/env bash
# score.sh <project-dir> <main-class>  — values AND evaluation order
D="$1"; MAIN="$2"; O="$(cd "$(dirname "$0")" && pwd)"
[ -f "$D/cp.txt" ] || (cd "$D" && mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt 2>/dev/null)
CP="$D/target/classes:$(cat "$D/cp.txt" 2>/dev/null)"
vp=0; ep=0; n=0
for s in "$O"/probes/*.csv; do
  b=$(basename "$s" .csv); n=$((n+1))
  rm -f /tmp/r.txt /tmp/ev.txt
  java -cp "$CP" "$MAIN" "$s" /tmp/r.txt /tmp/ev.txt >/dev/null 2>&1
  if [ -f /tmp/r.txt ] && diff -q <(sort /tmp/r.txt) <(sort "$O/probes/$b.expected") >/dev/null 2>&1; then
    vp=$((vp+1)); printf "  [PASS] values %s\n" "$b"
  else
    printf "  [FAIL] values %-12s want: %s  got: %s\n" "$b" "$(tr '\n' ' ' < "$O/probes/$b.expected")" "$(tr '\n' ' ' < /tmp/r.txt 2>/dev/null)"
  fi
  if [ -f /tmp/ev.txt ] && diff -q <(sort /tmp/ev.txt) <(sort "$O/probes/$b.eval") >/dev/null 2>&1; then
    ep=$((ep+1)); printf "  [PASS] order  %s\n" "$b"
  else
    printf "  [FAIL] order  %-12s want: %s  got: %s\n" "$b" "$(head -1 "$O/probes/$b.eval")" "$(head -1 /tmp/ev.txt 2>/dev/null)"
  fi
done
echo "  ---- values $vp/$n   order $ep/$n ----"
