# M44.5: predictions and design, recorded before any code (2026-09-27)

Owner direction: ONE way of handling dispatch and orchestration, the Fluxtion session processor. No hand-placed
dispatch. Regeneration is authorised, with the owner's key used by the plugin from its default location. The tracker
entry M44.5 is the scope.

## Design, fixed before code
1. **`LogEvidence`, a new state node** (graph id `logEvidence`). It owns the log's producer findings, its time-order
   report, and the last observed content signature, for the CURRENT log generation.
   - It reads `openLog`. A new generation clears its state and requests a scan; a close clears it.
   - It handles the facts `ProducerFindingsObserved` and `TimeOrderObserved`, refusing any fact that names another
     generation.
   - It handles `LogContentObserved(generation, total, bytes, pendingChars, readFailed)`, posted by every Follow
     poll, and requests a rescan only when the signature moved. That replaces the frame's
     `followNeedsDiagnosticRefresh` and the repeated-failure skip.
   - `LogAppended` also requests a rescan, which closes W1.
   - It requests effects through `@PushReference EffectQueue`, so in the graph effects descend from a decision
     (`SessionGraphShapeTest`'s rule).
2. **`ScanLogEvidenceEffect(opId, generation)`.**
   - The adapter answers `ScanScheduled(opId, generation)`, a new result type the operation gate does not handle,
     so a scan never marks an operation in flight or withdraws the published pairing.
   - The scan itself runs AFTER the current EDT task (`invokeLater`). `LogOpened` is submitted before
     `store = loaded`, so a scan inside `perform` would read the old store. It runs only if the generation is still
     current, and it posts both result facts.
3. **Provenance.** The project-environment match moves BEFORE `LogOpened`, which carries the final provenance and
   its source. `OpenLog` keeps both, and they are published. The frame's `logProvenance` fields are deleted.
4. **Follow state.** A new fact, `FollowToggled(generation, on)`, is kept on `OpenLog` and published, so the status
   line can be composed from the snapshot alone.
5. **The snapshot** gains `producerFindings`, `timeOrder`, `provenance`, `provenanceSource` and `following`, all
   immutable values.
6. **Surfaces render only from the snapshot, on the one listener:**
   - ONE status composer, used whether or not Follow is on, always carrying provenance, the time-order warning,
     the producer warning and the pending note (closes W2);
   - the tooltip;
   - the Reports tab (`setLogFindings(value)` plus a re-render when it changed);
   - `context`, the `report` reply and the PDF, which read the snapshot.

   Deleted from `MainFrame`: `producerDiagnostics`, `timeOrderReport`, `logProvenance`/`Source`,
   `followStreamEnd`/`followPendingChars`, `refreshFollowDiagnostics`, the Reports-tab refresh calls for findings,
   and the four status-line composers.
7. **The CLAUDE.md rule** (one dispatch model), and **a static check** (`OneDispatchModelTest`) that fails when the
   retired fields reappear or a status, tooltip or findings refresh is written outside the snapshot listener.
8. **Controls:** the p15/p16/p17 controls whose anchors are deleted are re-anchored to the one site each now has, or
   retired with the reason recorded. New controls cover the node's stale-generation refusal, LogAppended requesting
   the scan (W1), the composer keeping provenance and the time-order warning (W2), and the static check.

## Predictions
1. `mvn -Pregen process-classes` regenerates `SessionProcessor` with `logEvidence`. The generated source passes
   `GeneratedSourceIsPublishableTest`, stripped by the build's own step.
2. `SessionGraphShapeTest` passes after one expectation is added: `logEvidence` is a decision that fills the effect
   queue, and it descends from `openLog`.
3. **Processor-level tests** (`LogEvidenceTest`, on the real driver and a fake adapter) show:
   - an open requests exactly one scan, for the new generation;
   - `LogAppended` requests one;
   - `LogContentObserved` with an unchanged signature requests none, and a changed one requests one;
   - a findings or time-order fact for another generation is refused, and the snapshot is unchanged;
   - a close clears the findings and the time order;
   - provenance and its source from `LogOpened` are in the snapshot;
   - `FollowToggled` sets `following`.
4. **W1, as a real test:** a time-order violation appended under Follow appears in `context.timeOrder` and on the
   status line. It fails on `main` and passes here.
5. **W2, as a real test:** after a Follow tick, the status line still carries provenance and the time-order warning.
   It fails on `main` and passes here.
6. The existing surface tests stay green (`LogFindingsOnEverySurfaceFrameTest`, `StatusExplanationSurvivesFrameTest`,
   `StatusLineTest`, `PairingDuringLoadFrameTest`), with exact status-text expectations updated only where the Follow
   line now also carries provenance and the time-order warning.
7. Headless and display suites green; CI green, with the mutation gate over the re-anchored and new controls.

## Risks named now
- **Status text.** A single composer changes the Follow line's text, so tests asserting exact Follow text will need
  updating. Each change will be listed.
- **The say-hold.** R12-2 kept an explanation readable across idle Follow ticks. The composer must keep that
  behaviour: it composes on snapshot changes, and an idle tick changes none.
- **Scan cost.** The scans run on the EDT, as findings do today. Time order moves from the load's background job to
  the EDT pass: one linear scan per changed Follow poll. Measured on a large log if it looks slow; not optimised
  ahead of evidence.
