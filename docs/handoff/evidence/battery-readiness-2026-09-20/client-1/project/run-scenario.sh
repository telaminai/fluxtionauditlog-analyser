#!/bin/bash
# Evidence harness (created for the compatibility test; not part of the shipped bundle).
# Makes a clean deployment copy per scenario, swaps only data/input.txt, then uses the
# bundle's own run/export/stop commands. No audit or logs evidence is inherited, and no
# processor is regenerated — the copy reuses the already-built jar.
set -u
ORIG="/private/tmp/fluxtion-readiness-1/project"
ID="$1"
RUN="$ORIG/runs/$ID"
EV="$ORIG/evidence/$ID"
rm -rf "$RUN"; mkdir -p "$RUN/data" "$RUN/target" "$EV"

# deployment copy: descriptor, scripts, sources (web console sourceRoots), built jar
cp -R "$ORIG/config" "$RUN/config"
cp "$ORIG/run-server.sh" "$ORIG/export-audit.sh" "$ORIG/stop-server.sh" "$RUN/"
cp -R "$ORIG/src" "$RUN/src"
cp "$ORIG/target/preview-smoke-1.0.0-SNAPSHOT.jar" "$RUN/target/"
cp "$2" "$RUN/data/input.txt"
# assert no inherited evidence
[ -e "$RUN/audit" ] && { echo "FATAL: inherited audit dir"; exit 1; }
[ -e "$RUN/logs" ] && { echo "FATAL: inherited logs dir"; exit 1; }

cd "$RUN"
( ./run-server.sh > "$EV/server.log" 2>&1 ) &
SRV=$!

# wait for the registry entry this server publishes
for i in $(seq 1 60); do
  [ -f "$MONGOOSE_SERVERS_DIR/preview-smoke" ] && break
  sleep 0.5
done
if [ ! -f "$MONGOOSE_SERVERS_DIR/preview-smoke" ]; then
  echo "FATAL: no registry entry for $ID"; kill $SRV 2>/dev/null; exit 1
fi
cp "$MONGOOSE_SERVERS_DIR/preview-smoke" "$EV/registry-entry.json"

sleep 8   # let the file feed drain and audit capture flush

./export-audit.sh > "$EV/export.log" 2>&1; echo "export exit=$?" >> "$EV/export.log"
[ -f logs/audit-preview-smoke.yaml ] && cp logs/audit-preview-smoke.yaml "$EV/audit-$ID.yaml"
[ -f data/output.txt ] && cp data/output.txt "$EV/output.txt"
ls -la data > "$EV/data-listing.txt"
cp data/input.txt "$EV/input.txt"

./stop-server.sh > "$EV/stop.log" 2>&1; echo "stop exit=$?" >> "$EV/stop.log"
sleep 2
if [ -f "$MONGOOSE_SERVERS_DIR/preview-smoke" ]; then
  echo "registry entry STILL PRESENT after stop" >> "$EV/stop.log"
else
  echo "registry entry removed" >> "$EV/stop.log"
fi
wait $SRV 2>/dev/null
echo "scenario $ID complete"
