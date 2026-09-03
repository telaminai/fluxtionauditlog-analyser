#!/usr/bin/env bash
set -euo pipefail
BIN="$1"; LABEL="$2"
for ARM in plainGuarded fluxtionStreamClock fluxtionFlatStreamClock; do
  for REP in 1 2 3 4 5; do
    "$BIN" -Darm=$ARM -Dwarm=5000000 -Diters=200000000 2>/dev/null \
      | grep '^RESULT' | awk -v l="$LABEL" -v r="$REP" '{print l","$2","$3","$4","$5","$6","$7","r}'
  done
done
