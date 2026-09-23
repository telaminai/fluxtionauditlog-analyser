# Canvas recovery result

## What was recovered

The prior run's blockage (`ACCEPTANCE-RESULT.md` step 14) was that no analyser
process was reachable at all (`manifest` raised `FileNotFoundError` on
`rest-endpoint`). In this session the analyser **is** reachable: `driver.py
manifest` returned a full verb/schema manifest immediately. This run drove it
directly; nothing was launched, substituted, or worked around.

## Analyser actions issued (in order)

1. `context` / `source_root` / `open {discover: graphml}` / `open {discover:
   diagnostics}` — initial discovery. No project was pre-open; no saved
   session to restore (`restoration.state: "none"`).
2. `source_root {add: [project]}`.
3. `open {log: exchange/run1.log, graphml:
   target/classes/.../MyProcessor.graphml}` — both loaded.
4. `open {design: src/main/fluxtion/designer/application-context.xml}` —
   loaded (`relationship: "unverified"` — the analyser itself flags this;
   matched by name only).
5. `context` — inspected loaded state before drawing conclusions (`freshness`
   checks on log/graphml both `unchanged-metadata`).
6. `read {recordIndex: 0}` — full raw text of the loaded log.
7. `coverage {}` — checked node/graph agreement.
8. `aggregate {metric: count}`, `series {expr: "rootNode.price"}` — confirmed
   the collapse described below.
9. `graph {name: "rootNode.price (collapsed log)", series: [rootNode.price],
   style: points, explanation: ...}` — chart built from the one value the
   analyser can actually extract, with the explanation stating exactly why
   it is one point, not four.
10. `flag {recordIndexes:[0], kind: fault, note, fix}` — durable finding
    about the log-format defect.
11. `topology {select: child, scope: neighbours, focus: true, saveFocusAs:
    "pause-reset-scenario"}` — durable named focus around the pause/reset
    logic.
12. `spotlight {targets: [tab:topology, records:row:0, graph:...]}` — pointed
    at the three things discussed, before describing them.
13. `report {name: "canvas-recovery", ...}` (built twice — second replaced
    the first once the topology section's required `focus` was supplied) —
    10 sections: narrative, finding(0), record(0), topology(focus), chart,
    4 narrative answers, 1 narrative limitations section. Confirmed via
    `report.applied.{created,replaced}: true` and by rereading `context`
    afterward (flag and graph both present with real data, not just echoed).
14. `screenshot {path: ...}` and `report {..., path: "canvas-recovery.pdf"}`
    — **both refused**: `"file exports are disabled — enable Settings ▸
    Assistant ▸ 'Allow assistant file exchange'"`. Re-checked via `context`
    (`exports.enabled: false`) before and after; not toggled by any action
    verb available through the manifest, so no PNG/PDF exists in
    `exchange/`. This is a recorded blockage of the export step only — the
    in-app report, chart, flag, spotlight and topology focus themselves were
    all created and verified present.

## Key finding surfaced by the analyser itself

`exchange/run1.log` has **zero** `---` record-separator lines (audit-log
Format 1 requires one before each record) despite containing 9
`eventLogRecord:` blocks. The analyser therefore loaded the entire file as
**one record**. Consequences, all analyser-verified:

- `aggregate {metric: count}` → 1 record total.
- `series {expr: "rootNode.price"}` → 1 point, value 40.0 (the last
  PriceUpdate in the file), not a 10→20→30→40 sequence.
- `read {fields: [...]}` → each field shows only its **last** occurrence
  within the record (e.g. `rootNode.price: "40.0"` even though 10/20/30 also
  occurred).
- `coverage` additionally flagged: `checked` (the sink node that emits
  "SINK checked:" lines) appears in the log but is **not declared** in the
  loaded `MyProcessor.graphml`, which coverage calls suspicious of the
  graphml being from a different build than this exact run.

This is a real defect in how `FluxtionMain.java`'s log sink was wired for
this scenario run (it never wrote `---` before records), not an analyser
bug. It was recorded as a fault flag, not silently worked around, and not
present in the prior acceptance report because the analyser was never
reachable then to reveal it.

## Answers to the four original questions (measured vs. inferred)

All four are answered from the **raw text of the single collapsed record**
(which still contains every `eventLogRecord:` block verbatim, in original
order — the collapse loses per-record charting, not the text) plus
`MyProcessor.java`/`Child.java` source. None of the four required a real
per-record analyser query, because the log defect ruled that out.

1. **Wrong filter (filterId 9) increments count?** No — measured. The
   `PriceUpdate{...,filterId=9}` block has `nodeLogs:` empty; no `rootNode`
   or `child` entry, no `SINK checked:` line.
2. **Price while paused (price 30, filterId 7) increments count?** No —
   measured. `child: {method: onChild, triggerFired: true}` does fire (event
   is eligible), but no `SINK checked:` line follows; the next one is
   `Checked[price=40.0, count=1]`, not `count=2`.
3. **Does `reset()` itself publish?** No — measured. Its record shows only
   `child: {method: reset}`, no accompanying `SINK checked:` line.
4. **What changes after reset?** Measured: next eligible price (40) publishes
   `Checked[price=40.0, count=1]` — count restarted at 1. **Not established**:
   a direct field-level read of `processedCount`/`paused` immediately after
   `reset()` and before the next event — the log has no getter/state-dump
   entry for that moment; this remains inference from the subsequent
   publish, exactly as flagged in the prior acceptance report and still true
   here (the analyser was not able to change this, since it only reads what
   the log/graphml contain).

## Evidence files / analyser objects

- `exchange/run1.log` — same file from the original run (not modified),
  opened as the analyser's log.
- `project/target/classes/com/example/myapp/generated/MyProcessor.graphml`
  — opened as the analyser's graph (coverage flagged as possibly
  build-mismatched — see above).
- `project/src/main/fluxtion/designer/application-context.xml` — opened as
  the analyser's design (`relationship: unverified`, per the tool's own
  caveat).
- In-app durable objects created this session (not files, but reviewable in
  the analyser UI/state): flag on record 0, graph
  `"rootNode.price (collapsed log)"`, named focus
  `"pause-reset-scenario"`, report `"canvas-recovery"` (10 sections).
- No PNG or PDF file exists in `exchange/` — export was refused by the
  analyser's own settings (`Allow assistant file exchange` off), not
  attempted via any other path.

## What remains unverified / not established

- No image or PDF artifact of the canvas could be produced (export
  disabled); the only durable proof of the in-app report/chart/flag/focus is
  the analyser's own echoed state (`report.applied`, `context.flags`,
  `context.graphScopes`), inspected after creation, not a rendered file.
- The graphml's relationship to this exact `run1.log`/build is flagged by
  the analyser's own `coverage` call as suspect (missing `checked` node) —
  treated here as a limitation, not resolved.
- The design XML's relationship to the run is explicitly `"unverified"` per
  the analyser (matched by name only).
- Question 4's "reset actually zeroes the fields" claim remains inference
  from the next publish, not a direct measurement, unchanged from the prior
  acceptance report.
- Per task constraints, the log-separator defect was recorded and flagged,
  not fixed (no application/log/ownership file was modified).
