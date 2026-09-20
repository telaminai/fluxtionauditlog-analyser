# Smoke test result — preview-smoke

## Versions (from README.md / pom.xml, unchanged)
fluxtion 1.0.16, fluxtion-bom 1.0.71, mongoose 1.0.29, mongoose-plugins 1.0.43. Build/run
path used: `./run-server.sh` (build-if-needed + run), no Fluxtion API key required since
the generated processor ships in the project (no regeneration attempted).

## Commands used

1. `./run-server.sh` (backgrounded) — builds on first use, boots the server, publishes
   `~/.mongoose/servers/preview-smoke` (redirected via `$MONGOOSE_SERVERS_DIR` to the
   isolated registry dir). Full log: `run-server-output.log`.
2. `./export-audit.sh` — writes `logs/audit-preview-smoke.yaml` via the admin API. Full
   log: `export-audit-output.log`.
3. `./stop-server.sh` — SIGTERM via the registry pid. Full log: `stop-server-output.log`.

## Observed results

- Build and boot succeeded: Chronicle audit capture started for processor
  `marketProcessor`, the `input` file feed opened `data/input.txt`, and the admin
  console/services started on `http://127.0.0.1:8181` (registry entry
  `preview-smoke` confirmed pid 641, `authMode: NONE`).
- Export succeeded: `logs/audit-preview-smoke.yaml` (253 lines) contains one
  `PriceEvent` audit record per CSV row in `data/input.txt`, each with `rootNode`
  `nodeLogs` carrying `receivedEvent`, `price`, `volume`.
- Comparison (full detail in `COMPARISON.md`): all 5 recorded business-event values
  (AAPL, MSFT, GOOG, AMZN, NVDA — price and volume) match `EXPECTED.json` exactly.
  `data/output.txt` is empty, which is expected because `RootNode`/`RiskCheck` are
  unmodified starter TODO stubs that never publish to the sink.
- Stop succeeded: `stop-server.sh` reported "stopped server pid 641 — Chronicle
  capture closed and the registry entry was removed"; the registry directory is now
  empty, confirming the entry was removed and the process is no longer registered.

## Failures

None. All three steps (build+run, export+compare, stop+verify) completed as
documented, with no repair or substitution needed.

## Result

Smoke test passed: unchanged project built, ran, produced audit evidence matching
independently-derived expected values, and stopped cleanly.
