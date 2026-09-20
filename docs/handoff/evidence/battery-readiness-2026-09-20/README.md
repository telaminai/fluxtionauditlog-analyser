# Readiness evidence

See [the operator report](../../report_battery_readiness_2026_09_20.md) for verdicts and limits.
This packet separates one credentialed customer generation attempt from provisioned-project client
compatibility. It is not the public empty-directory battery or a release acceptance packet.

- `generation-predictions.json` was written before the one generation attempt.
- `generation/` contains the added node, changed XML, actual input/audit and command output/status.
  `regeneration-verification.json` records independently checked totals and artifact digests.
- `PROTOCOL.md`, the two input prompts and `predictions.json` are frozen by `protocol-seal.json`.
- `client-*/smoke-events.jsonl` are visible tool/text projections with observer receive times and raw
  transcript line numbers. Private thinking/signature blocks are omitted. They contain mistakes as
  well as successful commands. Source-pointer declarations are not inferred where missing.
- `client-*/independent-checks.json` records local baseline comparisons, expected-write events, actual
  model IDs, matched scenarios and environment friction. `audit-A.yaml` through `audit-F.yaml` are
  observed outputs; `project/` contains the subject's expectations, checker and report where supplied.
- `refusal-adjacency.json` re-examines historical trials. Provider error timestamps are labelled
  separately from current observer receive times. First refusal and final termination differ.
- `catalogue-launchers.json` inventories actual default generator outputs, not guessed launcher names.
- `manifest.json` pins raw/exported file digests. Exports apply rule-1 redaction and trim trailing
  whitespace; the unedited private archive and pristine baselines remain durable and git-ignored.

Verify the public packet without a client session or generation key:

```sh
python3 docs/handoff/evidence/battery-readiness-2026-09-20/verify_evidence.py
```

The checker verifies sealed inputs, published hashes, all 54 positive-trial business rows and five
changed-graph totals. It does not independently re-establish a credential's route, local baseline
identity, provider reliability, shutdown, or causal attribution. Those require the underlying local
artifacts and transcript review. No assertion relies on a subject's invented elapsed time.
