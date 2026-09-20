# preview-smoke — compatibility smoke result

Bundle: m19-bundle/4 · fluxtion 1.0.16 · fluxtion-bom 1.0.71 · mongoose 1.0.29 · mongoose-plugins 1.0.43
Build/run JDK: OpenJDK Corretto 21.0.11 (Java 21). Maven wrapper: Apache Maven 3.9.9.
Full detail and raw command output: `evidence/EXPECTED.json`, `evidence/build.log`, `evidence/compare.py`,
`evidence/comparison-results.txt`, `scenarios/deploy/<A-F>/server.out` and `scenarios/deploy/<A-F>/logs/audit-preview-smoke.yaml`.

## Observations

- `./mvnw package` succeeded with **no Fluxtion API key** (BUILD SUCCESS, see `evidence/build.log`) — consistent with README: the generated processor (`src/main/java/com/acme/preview/generated/MarketProcessor.java`) ships committed, so the default build compiles it as-is and never calls the hosted generator. Regeneration (`-Pgenerate-fluxtion`) was not exercised, since no graph change was made.
- One jar was built once and reused, unmodified (identical MD5), across all six scenario deployment copies — no scenario triggered a rebuild or regeneration.
- Six independent deployment copies (`scenarios/deploy/A` … `/F`) were created under the project directory, each with its own `data/input.txt`, its own isolated Mongoose registry (`MONGOOSE_SERVERS_DIR`), and its own `logs/` output. Each was started with `./run-server.sh`, exported with `./export-audit.sh`, and stopped with `./stop-server.sh`, sequentially — one instance was fully stopped before the next started.
- For every scenario (A–F), the audit export (`logs/audit-preview-smoke.yaml`) contains exactly one `PriceEvent` audit record per CSV row, in file order, with `symbol`/`price`/`volume` matching the corresponding row of `evidence/EXPECTED.json` verbatim (`evidence/comparison-results.txt`: 6/6 PASS, 15 rows total, zero mismatches). This includes:
  - **B**: duplicate `ACME,10.25,3` rows produced two independent, identical audit records — no de-duplication.
  - **C**: zero and negative price/volume (`ZERO,0,0`, `NEG,-12.5,-4`) were accepted and logged as-is — no validation/rejection node exists.
  - **D**: the large volume `2000000000` and the fractional price `1000000.25` were logged exactly (long/double have ample range/precision for these values).
  - **E**: rows out of alphabetical/numeric order (`THIRD, FIRST, SECOND`) were logged in **file order**, not resorted — there is no sort/aggregation node in this graph.
  - **F**: the single-row scenario logged normally with no batching artifact.
- `data/output.txt` was empty (0 bytes) or absent in every scenario, including the original shipped scenario A. This is expected, not a defect: the generated `MarketProcessor`'s `ProcessorDescriptor` declares `new ProcessorDescriptor.Sink[] {}` (zero sinks), and neither `RootNode` nor `RiskCheck` calls any sink/publish API — both are `TODO` stubs. The `output` `FileMessageSink` is wired in `config/server-config.yml` but nothing in the graph ever writes to it.
- The comparison script (`evidence/compare.py`) was proven to actually discriminate: run a second time against a deliberately tampered copy of the expectations (`evidence/EXPECTED-tampered-F-price.json`, scenario F price changed from `7.75` to `999.99`), it reported `scenario F: FAIL — price expected 999.99 got 7.75` and exited non-zero. The original `evidence/EXPECTED.json` was never modified for this check (a separate tampered copy was used); it still reads `price: 7.75` for scenario F. The original `data/input.txt` in the project root is byte-identical to its pre-run content (never touched — all scenario inputs lived only in the deployment copies).
- All six deployment copies' Mongoose registries are empty after their respective `stop-server.sh` runs, and each stop command reported "Chronicle capture closed and the registry entry was removed" — no server instance was left running.

## Failures

None. The application built, ran, ingested every scenario's input, and produced audit records whose symbol/price/volume/order matched the independently-authored expectations in all six scenarios and in the deliberately-corrupted-expectation control case.

## Limits

- **"order" is not an application field.** `PriceEvent` has exactly three fields (`symbol`, `price`, `volume`); there is no sequence/order number anywhere in the event, node, or generated processor code. What was compared as "order" is the ordinal position of each row's audit record in the export, which happens to track input-file line order because the file feed is a single-threaded `EARLIEST` reader with one downstream processor — not a value the application itself computes or emits.
- **The sink was never a verified-with-data scenario.** `data/output.txt` is expected to be empty for every scenario per the shipped graph's design (zero wired sinks in `ProcessorDescriptor`), so an empty sink here reflects the shipped application's actual behaviour, not a scenario that exercised and verified sink content — no scenario in this run counts as a verified sink scenario, by design of the shipped graph.
- `RiskCheck.onRiskCheck()` only copies `rootNode.getLatestEvent()`; it performs no threshold, aggregation or risk calculation (its body is a `TODO` stub), so there is no derived/computed business value beyond the raw ingested `symbol`/`price`/`volume` to verify.
- `ps` is blocked in this sandboxed environment, so "no server process is running" was confirmed via the Mongoose registry state (each entry removed on stop, as reported by `stop-server.sh`) rather than a process-table check.
- CSV parsing uses `Double.parseDouble`/`Long.parseLong` with no bounds/sign validation and no malformed-row handling beyond the "fewer than 3 fields → zeroed event" fallback in `CsvToPriceEvent`; none of the six scenarios exercised a malformed row, so that fallback path itself was not exercised here.
