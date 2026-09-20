# Audit comparison against EXPECTED.json

Compared `logs/audit-preview-smoke.yaml` (produced by `./export-audit.sh`) against
`EXPECTED.json` (written before the run, from `data/input.txt`).

Only `com.acme.preview.node.RootNode.onPriceEvent` emits `auditLog` entries
(`receivedEvent`, `price`, `volume`); `RiskCheck` does not extend `EventLogNode` and
logs nothing.

| symbol | expected price | recorded price | expected volume | recorded volume | receivedEvent match |
|---|---|---|---|---|---|
| AAPL | 195.30 | 195.3 | 1200 | 1200 | match |
| MSFT | 410.10 | 410.1 | 800  | 800  | match |
| GOOG | 155.75 | 155.75 | 1500 | 1500 | match |
| AMZN | 178.22 | 178.22 | 640  | 640  | match |
| NVDA | 905.60 | 905.6 | 2100 | 2100 | match |

Event count: expected 5, recorded 5 (`PriceEvent` records in the audit log). All five
business-event values recorded by `rootNode` in `logs/audit-preview-smoke.yaml` match
`EXPECTED.json` exactly (numeric formatting differs only by trailing-zero display,
e.g. 195.30 vs 195.3 — same double value).

`data/output.txt` is empty after the run: `RiskCheck.onRiskCheck()` and
`RootNode.onPriceEvent()` are unmodified starter TODO stubs and never publish to the
file sink, so no sink output is expected.
