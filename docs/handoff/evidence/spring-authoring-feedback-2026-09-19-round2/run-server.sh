#!/usr/bin/env bash
# Boot MyProcessor inside a Mongoose server, fed by data/market.csv (see config/server-config.yml).
#   ./run-server.sh                 run until Ctrl-C; new lines appended to data/market.csv are processed live
#   ./run-server.sh --drain [dir]   process the file, stop when the audit log goes quiet; audit -> dir/audit.yaml
#   --clean                         forget the feed's read position first, so the file is re-read from the start
#   --paced                         with --drain: start on an empty feed and append the rows one every 30 ms, the way
#                                   a live writer would, so events get distinct timestamps (charts need that)
# --add-opens flags are required: Agrona (via Mongoose) accesses jdk.internal.misc.Unsafe.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
source .demo-env.sh
DRAIN=0; CLEAN=0; PACED=0; OUT=evidence/mongoose
while [ $# -gt 0 ]; do
  case "$1" in
    --drain) DRAIN=1 ;;
    --clean) CLEAN=1 ;;
    --paced) PACED=1 ;;
    *) OUT="$1" ;;
  esac
  shift
done
if [ "$CLEAN" = 1 ]; then
  # FileEventSource keeps its read pointer beside the data file.
  find data -maxdepth 1 -name 'market.csv.*' -print -delete
fi
mkdir -p "$OUT"
JAVA=(java -Djava.awt.headless=true "-Daudit.file=$OUT/audit.yaml"
  -DmongooseServer.config.file=config/server-config.yml
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED
  --add-opens java.base/java.io=ALL-UNNAMED
  --add-opens java.base/java.nio=ALL-UNNAMED
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED
  --add-opens java.base/jdk.internal.ref=ALL-UNNAMED
  --add-opens java.base/jdk.internal.util=ALL-UNNAMED
  -cp "target/classes:$(cat .fluxtion/classpath)" com.example.myapp.MongooseMain)
if [ "$DRAIN" = 0 ]; then
  exec "${JAVA[@]}"
fi
if [ "$PACED" = 1 ]; then
  ROWS="$(mktemp)"; cp data/market.csv "$ROWS"
  trap 'cp "$ROWS" data/market.csv; rm -f "$ROWS"' EXIT   # always put the data file back
  : > data/market.csv
fi
"${JAVA[@]}" > "$OUT/server.log" 2>&1 &
PID=$!
if [ "$PACED" = 1 ]; then
  sleep 2   # let the server boot and the feed start tailing
  while IFS= read -r row; do printf '%s\n' "$row" >> data/market.csv; sleep 0.03; done < "$ROWS"
fi
# Drained = the audit log exists and has not grown for 2 seconds.
last=-1; quiet=0
for _ in $(seq 1 60); do
  sleep 0.5
  kill -0 "$PID" 2>/dev/null || { echo "server exited early; see $OUT/server.log" >&2; exit 1; }
  size=$(wc -c < "$OUT/audit.yaml" 2>/dev/null || echo 0)
  if [ "$size" -gt 0 ] && [ "$size" = "$last" ]; then quiet=$((quiet + 1)); else quiet=0; fi
  last=$size
  [ "$quiet" -ge 4 ] && break
done
kill -TERM "$PID"; wait "$PID" 2>/dev/null || true
echo "records: $(grep -c '^eventLogRecord' "$OUT/audit.yaml")  audit: $OUT/audit.yaml  server log: $OUT/server.log"
