# Review — PR #58, the view-model spike's second element (2026-09-28)

**Reviewer:** Claude (analyser session). **Delta:** `proto/view-model-nodes..proto/view-model-nodes-2`, the four commits to
`e2850837`.

**Fixes pushed to `review/pr58-fixes`, not to the PR branch** (the owner's later instruction: no pushes to
`proto/view-model-nodes-2`). Review range: **`e2850837..7f1e288f`** (four commits; this file is the fifth). Cherry-pick
or merge them into the PR branch; nothing was rebased or force-pushed.

## Findings, most severe first

| # | severity | finding | disposition |
|---|---|---|---|
| F1 | **defect** | **A closed log keeps its identity warning on all three surfaces.** On close, `IdentityBanner.onStateChanged` set `emitted = null` and emitted nothing. The three backends are the banner's only writers; on the base branch `onSessionSnapshot` fed the cleared verdict. Scenario: a file is rewritten in place → "⚠ … reopen the log" on the table, the charts and the detail pane → *Audit log ▸ Close log* → the warning stays over an empty screen. | fixed `6fdd574e`; witnesses `IdentityBannerViewTest#closingTheLogTakesTheBannerDown` (red before: 2 views, expected 3) and `IdentityMarkFrameTest#closingTheLogTakesTheVerdictOffEverySurface` (real frame); controls `vm2-close-takes-banner-down`, `-frame` |
| F2 | **CI-breaking** | **Five mutation controls orphaned.** The PR re-anchored two static checks, but `set13-a-table-banner`, `set13-a-rendered-from-snapshot`, `m68-7-charts-not-rendered`, `m68-7-detail-not-rendered` and `m68-7-chart-text-bypasses-rule` still pointed at removed or rewritten code. `--mode preflight` fails: `('mutation anchor', 'set13-a-table-banner', 0)`. So does CI's mutation gate. | fixed `7f1e288f`: re-anchored to `warns(verdict)` and the three backends, each caught by its original witness |
| F3 | **measurement** | **Half of the +84,000 B is a dispatch fan-out, not the element's floor.** The generated `handleEvent(ViewRendered)` called *every* view node's `onViewRendered` (`//Default, no filter methods`), each with a trace line. The banner node paid 70 B on each of the status line's 600 answers. That is (view nodes × renders) — a per-element term that RESULTS-2's six-element extrapolation assumes away. `StatusLine.onViewRendered` also had no element check, so it audited the banner's renders as its own. | fixed `6ec55ed4`: `ViewRendered` implements Fluxtion's `Event.filterString()`, and both handlers declare `filterString`. Read from the 1.0.16 runtime jar, since `claude.txt` does not document it. The regenerated dispatch switches on the element. **Re-measured: +52,200 B (+2.0%), 0 records.** The remaining 10,200 B is the new `eventFilter:` line. Witness `aRenderAnswerReachesOnlyItsOwnNode`, control `vm2-render-answer-filtered` |
| F4 | gap closed | **No controls for the node** (the PR said so). | `fb170569`: changed-only emit, the `shown` policy, the table backend on a real frame, plus F1 and F3's. Every one is caught by a named assertion |
| F5 | correction | **Finding 3 / authoring-cost P6 ("regeneration needs the network") is wrong as stated.** `build-helper-maven-plugin:3.6.0` is simply not in *that* machine's local repository. Here, `mvn -o -q -Pregen process-classes` ran offline three times in this review, and the output was byte-identical to the committed processor. The true constraint is Maven's ordinary one: the first `-Pregen` on a machine needs the network. | not a defect; correct the finding |
| F6 | observation | **Authoring-cost P5 ("never opened the generated processor") hid F3.** The fan-out was stated in plain text in `handleEvent(ViewRendered)`. RESULTS-2 recorded its symptom ("lands in two cycle kinds") without asking why a node runs on another element's answer. Authoring did not need the output; understanding the cost did. | refine P5's claim |
| F7 | suggestion | **The remaining floor is per append, per node parented on `OpenLog`.** `OpenLog` is dirty on every append, so every view node that reads it is invoked, at 70 B, on appends it does not care about. At twenty such nodes that is twenty invocation lines inside every Follow `LogAppended` record. That is the selectivity risk the proposal names. A shared intermediate node (for example, one dirty only when identity, generation or open state moves) would pay it once. | suggested, not done |

## The questions the PR asked

1. **Same instrument?** Yes. The loop's events are unchanged. Reproduced exactly on both branches: 5,800 records;
   2,548,484 → 2,632,484 B. The comparison is valid. But the loop never changes the verdict, so it cannot measure a
   *changing* banner.
2. **0 renders in the loop?** Yes, and it is asserted indirectly: the instrument's `ViewRendered` count stays 600,
   the status line's alone. The first render happens at open, before the measurement starts.
3. **The ring?** It holds records. `SessionAuditSink` bounds each deque by count (`capacity`, `RESCOPE_CAPACITY` 200,
   `OBSERVATION_CAPACITY` 200), with no byte bound anywhere, so the Follow-window conclusion holds. Bytes matter only
   for memory and for any export of the session log.
4. **Too easy an element?** Yes, deliberately, and the extrapolation should say it is the best case. It needs no
   fact, and it reads one node that is dirty on every append. F3 and F7 are the terms that grow with graph size.
- **Re-anchoring on `MainFrame` text.** It moves the brittleness rather than removing it. A backend that is
  registered but never invoked (for example, a missing adapter `switch` case) passes the text check. The behavioural
  anchor is `IdentityMarkFrameTest`, which the controls now point at. Keep the `setIdentityNote`-absent assertion,
  but as a rule-9 guard: in `OneDispatchModelTest`, `setIdentityNote` called only inside a `ViewBackend.render`.
- **The banner on open.** Necessary for a reopen (the frame test covers it). It is not sufficient: close was F1.
- **No consistency gate.** Agreed. `identity` and `identityReason` are set in one handler, and cleared in one handler
  on open and close, so there is no half-landed state.

## Gates on `review/pr58-fixes`

- Headless: 2,602 run, 0 failed, 131 skipped (display).
- Frame suites, real display: 132 run, 0 failed, 1 skipped (`PersonAtTheScreenFrameTest`, focus-bound).
- `--mode preflight`: 24 frame suites, 261 anchors.
- **Mutation gate: 261 of 261** caught, restored byte-identical.
- The sweep is clean; every commit uses the personal author email.
