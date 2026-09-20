# Spring authoring participant feedback — 2026-09-19

[Original feedback](ANALYSER-FEEDBACK.md) is preserved byte-for-byte from the staged project.
It records ten issues, proposed priorities and copy-ready implementation prompts. The prompts
are participant proposals, not accepted specifications or independently verified root causes.

The authoring session added a Trade event, position node and mark-to-market node to the worked
sample. This packet preserves its evidence without changing the live project or analyser.
It is a local, provisioned session, not the post-publication fresh-client acceptance gate.

## Evidence locations

- `evidence/`: the three participant screenshots, plus the latest audit and host-observation CSVs.
- `src/main/fluxtion/`: edited Spring XML.
- `src/main/resources/`: generated GraphML (the full generated Java and PNG are deliberately omitted).
- `src/main/java/`: application sources, excluding the generated processor. This is the final source
  after the participant's edits; it does not preserve the initial unaudited stubs from issue 6.
- `fluxtion-authoring.json`: final ownership record.
- `producer-results/`: files copied from the project's `target/`, preserving the remaining relative
  path, including `classes/fluxtion-diagnostics.json`. This rename avoids Git's build-output ignore.
- `baseline/analyser-context.json`: the launcher's saved context before the participant's edits.
  It is not a new context call and not the post-edit excerpt quoted in the participant report.
- `manifest.json`: original relative locations, saved locations, SHA-256 hashes, capture time and
  analyser checkout head. No raw MCP transcript beyond the participant's quoted excerpts was supplied.

Original absolute paths in the feedback identify the live reproduction environment. For offline
inspection use the corresponding paths in this packet and the producer-results mapping above.
This packet is evidence, not a self-contained executable project or a staged analyser profile.

All three screenshots were opened and visually inspected before preservation. They show the
isolated sample application and its neutral identifiers. Text artifacts passed the repo sweep;
private provider artifacts, credentials and full generated processor source were not copied.

## Intake, without inferred causes

| Original issue | Owner / next investigation |
|---|---|
| 1 | Analyser: reproduce mixed loaded/on-disk log metadata and graph freshness. Check existing Follow/reopen semantics before adopting the proposed reload contract. |
| 2–4 | Analyser: reproduce viewport clipping, non-positive bounds and lost spotlight accounting. Screenshots support visible problems; a common root cause is not established. |
| 5 | Analyser proposal: changeset navigation/spotlights require an actual prior text snapshot; hashes cannot reconstruct it. Design approval remains separate from correctness fixes. |
| 6 | Compiler/starter + playground: compare audit scaffolding on initial download and reconcile-add. The report observes method trace entries with no values, so “never appears” is too broad. Audit capability and emitted application values must be tested separately. |
| 7 | Compiler/starter: connect the formatting portion to existing F5/G13/F10; parameter names and event-shell hints are additional requests. |
| 8 | Compiler/starter: verify default URL/sample-output volume and the proposed quiet/verbose policy. |
| 9 | Analyser: reproduce producer-result/receipt supersession and stale source-hash verdicts while preserving explicit intake. |
| 10 | Analyser, explicitly unconfirmed: establish whether nodeTypes is a lazy cache or a complete map before calling it incomplete. |

Do not regress the reported strengths: context orientation, runbook pointers, reconciliation
preserving implementations, and honest XML freshness. Existing DX-02–05 and the release gates
remain open; this report neither closes them nor silently renumbers these ten participant issues.
