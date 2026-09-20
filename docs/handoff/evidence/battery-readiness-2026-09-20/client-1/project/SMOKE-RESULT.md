# preview-smoke — compatibility smoke result

Scope: run the downloaded bundle as shipped and check that audited business values match
expectations written **before** the first run. No application file, dependency coordinate or
configuration was changed. Nothing was regenerated (no compilation-service key is provisioned,
and the graph was not modified, so none was needed).

## Shipped versions and supported commands

| Component | Version | Source |
|---|---|---|
| fluxtion | 1.0.16 | README.md version table |
| fluxtion-bom | 1.0.71 | README.md / PROJECT.md / pom.xml |
| mongoose | 1.0.29 | README.md / config runbook |
| mongoose-plugins | 1.0.43 | README.md |
| JDK used | Corretto 21.0.11 | `java -version` |
| artifact | `preview-smoke-1.0.0-SNAPSHOT.jar` (22.4 MB fat jar) | build output |

Commands exercised: `./mvnw package` (build), `./run-server.sh` (boot), `./export-audit.sh`
(export beat), `./stop-server.sh` (stop). Not exercised: `./mvnw -Pgenerate-fluxtion package`
and `./check-fluxtion-key.sh` — regeneration needs a key and the graph was deliberately left
untouched.

## What the shipped application can substantiate

Each CSV line becomes one `PriceEvent` and one audited event cycle. The audit log records, per
cycle, `rootNode … receivedEvent: PriceEvent{symbol=…, price=…, volume=…}, price: …, volume: …`
and a `riskCheck … method: onRiskCheck` entry.

- **symbol, price, volume** — substantiated, twice per row (inside `PriceEvent.toString()` and as
  separate `price`/`volume` audit fields). The comparison requires both renderings to agree.
- **order** — substantiated: one `FileEventSource` (`readStrategy: EARLIEST`) is the only feed, so
  cycle order in the audit log is file line order.

## What it cannot substantiate

- **The file sink `data/output.txt`.** It is declared in `config/server-config.yml`, but no node
  holds a `MessageSink` or publishes anything (`RootNode` stores the event and audits it;
  `RiskCheck` copies the reference and returns `true`). The file was created and stayed **0 bytes**
  in all six runs. Predicted as 0 rows in `EXPECTED.json` and confirmed — but per the task rule this
  is **not a verified sink scenario**; sink delivery remains untested by this bundle.
- **Any derived business value** — no risk verdict, aggregation, threshold, filter or running state
  exists in the shipped graph. `RiskCheck` computes nothing.
- **Validation of zero/negative/extreme inputs** — none exists; values pass through unchanged
  (scenario C confirms `-12.5 / -4` and `0.0 / 0` are accepted and audited verbatim).
- **Regeneration from the Spring XML design** — untested; no key provisioned.

## Method

`./run-scenario.sh <ID> <input>` (added for this test) makes a clean deployment copy under
`runs/<ID>/`: descriptor, the three lifecycle scripts, `src`, the **already-built** jar, and only
`data/input.txt` differing. It asserts the copy carries no inherited `audit/` or `logs/`, so each
scenario's Chronicle capture and YAML export contain that scenario only (the export is cumulative
within a capture directory — a fresh directory per run is why one run per export holds here). Runs
are strictly sequential: start → registry entry appears → export → stop → confirm entry removed.
The original download is untouched; `data/input.txt` for scenario A is a copy.

`compare.py` parses each `evidence/<ID>/audit-<ID>.yaml` into business rows and checks symbol,
price, volume and order against `EXPECTED.json`, plus the sink row count. Exit 0 = pass.

## Observations (all six scenarios)

Build: `./mvnw package` succeeded, exit 0. Every run booted, published its registry entry
(`http://127.0.0.1:8181`, authMode NONE), exported (`audit file id: marketProcessor`) and stopped
with `stopped server pid … — Chronicle capture closed and the registry entry was removed`; the
registry directory was verified empty after each stop.

| Scenario | Rows in | Audited cycles | symbol/price/volume/order | Sink |
|---|---|---|---|---|
| A (shipped input) | 5 | 5 | all match | 0 bytes |
| B (duplicate row) | 3 | 3 | all match — both ACME rows delivered, not deduplicated | 0 bytes |
| C (zero/negative) | 3 | 3 | all match — `0.0/0` and `-12.5/-4` pass through unvalidated | 0 bytes |
| D (extremes) | 3 | 3 | all match — `1000000.25` and volume `2000000000` exact | 0 bytes |
| E (unsorted) | 3 | 3 | all match — delivered THIRD, FIRST, SECOND; no reordering | 0 bytes |
| F (single row) | 1 | 1 | all match | 0 bytes |

No trailing-blank-line phantom row appeared in any run, despite the mapper's `PriceEvent(line, 0.0, 0)`
fallback for short lines. `compare.py` exit code 0.

Evidence: `evidence/<ID>/` holds `server.log`, `registry-entry.json`, `export.log`, `stop.log`,
`audit-<ID>.yaml`, `output.txt`, `input.txt`; `evidence/compare-all.txt` holds the full comparison.

## Negative control

`EXPECTED-wrong-control.json` is a copy of `EXPECTED.json` with one number changed —
scenario D `LARGE` price `1000000.25` → `1000000.35`. Running the same comparison against it:

```
FAIL row 2 (LARGE): price: expected 1000000.35 actual 1000000.25
RESULT: FAIL — 1 mismatch(es)     (exit code 1)
```

Saved at `evidence/negative-control.txt`. The application was not modified and `EXPECTED.json` was
not overwritten.

## Failures

None. The build, all six deployments, all exports and all stops succeeded, and every compared
business row matched.

## Limits of this result

- **Correctness scope is narrow.** What passed is CSV parsing, per-event audit capture and delivery
  ordering. The bundle contains no business computation, so "all rows match" says nothing about any
  risk, pricing or aggregation logic.
- **Sink path untested** (see above) — the only declared output destination produced nothing in
  every scenario.
- **No key path exercised.** Regeneration from `src/main/fluxtion/designer/application-context.xml`
  is unverified; a graph change would need `~/.fluxtion/fluxtion.apiKeyFile` (the builder does not
  read `FLUXTION_API_KEY`, so `check-fluxtion-key.sh` passing would not prove the build can).
- **Analyser not exercised.** The exports are analyser-readable YAML by construction of the export
  API, but no analyser instance (jbang/MCP) was started, so analyser compatibility with these files
  is unverified.
- **Ordering claim is scoped to one feed.** With a single `FileEventSource`, order is file order;
  this establishes nothing about interleaving of independent feeds, which the hosting runbook
  explicitly does not promise.
- **Timing.** Each run drained after a fixed 8 s wait rather than a completion signal; counts were
  checked afterwards and are exact, but a slower host could need longer.

## Final state

Original shipped files unchanged — `data/input.txt`, `config/server-config.yml`, `pom.xml`, `src/`
and the lifecycle scripts all retain their download timestamp (2026-09-20 21:34:29); digests in
`evidence/shipped-file-digests.txt`. Added by this test only: `EXPECTED.json`,
`EXPECTED-wrong-control.json`, `compare.py`, `run-scenario.sh`, `scenarios/`, `runs/`, `evidence/`,
`SMOKE-RESULT.md`, and `target/` build output. No server is running: no `preview-smoke` process and
the registry directory is empty.
