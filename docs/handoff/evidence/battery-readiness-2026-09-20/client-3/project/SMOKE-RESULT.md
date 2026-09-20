# preview-smoke — compatibility smoke result

Scope: compatibility test of the downloaded bundle. No shipped file, source, dependency coordinate
or configuration was changed. All new material is under `evidence/`, plus `EXPECTED.json` and this
file.

## Shipped versions and supported commands

| Component | Version (README.md / PROJECT.md / pom.xml) |
|---|---|
| fluxtion | 1.0.16 |
| fluxtion-bom | 1.0.71 |
| mongoose | 1.0.29 |
| mongoose-plugins | 1.0.43 |
| JDK used | Corretto 21.0.11 |

Commands exercised: `./mvnw package` (documented build), `./run-server.sh`, `./export-audit.sh`,
`./stop-server.sh`. Not exercised: `./mvnw -Pgenerate-fluxtion package` and
`./check-fluxtion-key.sh` — regeneration needs a compilation-service key, which is not provisioned
here and is out of scope (the graph was not changed).

## Observations

- **Build**: `./mvnw package` succeeded (`evidence/build.log`, BUILD SUCCESS). No key was needed —
  the AOT-generated `com.acme.preview.generated.MarketProcessor` ships in the project.
- **Deployments**: six clean copies under `evidence/scenarios/<ID>/deploy/`, each containing only
  the reused jar, `config/`, the three lifecycle scripts and its own `data/input.txt`. No `audit/`,
  `logs/` or `output.txt` was inherited, and no processor was regenerated. Scenarios ran strictly
  sequentially; each was stopped before the next started (`stop.log` per scenario).
- **Lifecycle**: every scenario exported successfully (`exported logs/audit-preview-smoke.yaml
  (audit file id: marketProcessor)`) and stopped cleanly (`stopped server pid N — Chronicle capture
  closed and the registry entry was removed`).
- **Business rows**: `evidence/compare.py` compared symbol, price, volume and order for every row of
  all six scenarios against `EXPECTED.json` (written before any run). Result: **PASS**, 18 rows
  across A–F (`evidence/compare-EXPECTED.log`). The comparison also cross-checks the per-event
  `eventToString` against the dedicated `rootNode` `price:`/`volume:` audit entries in the same
  cycle.
  - A: shipped input, 5 rows, in file order.
  - B: the duplicate row appears twice — no dedup, as predicted.
  - C: `0`, `-12.5`, `-4` pass through unchanged — no validation or rejection.
  - D: `0.125`, `1000000.25`, volume `2000000000` preserved exactly.
  - E: file order preserved (`THIRD, FIRST, SECOND`) — no sorting.
  - F: single row handled.
- **Negative control**: `python3 evidence/compare.py evidence/EXPECTED-WRONG-D.json D` exits 1 and
  reports `D row 2: price: expected 1000000.75 observed 1000000.25`
  (`evidence/compare-WRONG-D.log`). The wrong value lives in a separate copy;
  `EXPECTED.json` was not overwritten, and the application was not changed.
- **Final state**: no `preview-smoke` java processes remain; the Mongoose registry directory is
  empty; the project root has no `logs/` or `audit/` (all capture stayed inside the scenario copies).
- **Originals unchanged**: MD5 of `data/input.txt`, `config/server-config.yml`,
  `src/main/fluxtion/designer/application-context.xml` and `pom.xml` are identical to the checksums
  taken before any run (`evidence/original-checksums.txt`).

## Failures

None. No command failed; no expected business value mismatched.

## Limits — what this run does NOT establish

- **The sink is unverified for all six scenarios.** `config/server-config.yml` declares an
  `eventSinks` entry writing `data/output.txt`, but no graph node publishes to a `MessageSink`:
  `RootNode.onPriceEvent` only audits and stores `latestEvent`, and `RiskCheck.onRiskCheck` only
  copies it (both carry `TODO` comments). Every scenario produced a 0-byte `data/output.txt`. Per
  the task rule, an empty sink is not a verified sink scenario, and `compare.py` prints
  `SINK UNVERIFIED` rather than a pass.
- **No business logic beyond pass-through exists to test.** There is no risk decision, notional,
  aggregation, dedup or per-symbol state in the shipped graph, so "correct" here means only that
  each CSV row reached `RootNode` with its symbol, price and volume intact, in file order.
- **Order** is verified as observed dispatch order on the single configured feed route. The hosting
  runbook is explicit that independent feeds promise no deterministic interleaving; this bundle has
  one feed, so nothing here validates multi-feed ordering.
- **Regeneration path untested**: `./mvnw -Pgenerate-fluxtion package` was not run (no key). A
  compatibility claim for the Spring-XML → generated-processor round trip is therefore not made.
- **Analyser untested**: the exported YAML was parsed by `evidence/compare.py`, not opened in the
  Fluxtion Audit Log Analyser; no statement is made about analyser ≥ 1.12.0 compatibility.
- Chronicle emitted repeated `DiskSpaceMonitor` warnings (host disk ~98.5% full) in every run. No
  run crashed and every export/stop succeeded, but this is a host condition worth noting.

## Evidence map

```
EXPECTED.json                         expectations, written before any run
evidence/build.log                    documented build output
evidence/input-[A-F].txt              the six scenario inputs (A = copy of shipped input)
evidence/run-scenario.sh              clean-copy deploy → run → export → stop, per scenario
evidence/compare.py                   executable comparison (symbol, price, volume, order)
evidence/compare-EXPECTED.log         PASS across A–F
evidence/EXPECTED-WRONG-D.json        one deliberately wrong price
evidence/compare-WRONG-D.log          the rejection (exit 1)
evidence/original-checksums.txt       pre-run checksums of shipped files
evidence/scenarios/<ID>/              server-stdout.log, export.log, stop.log,
                                      audit-<ID>.yaml, output-<ID>.txt, deploy/
```
