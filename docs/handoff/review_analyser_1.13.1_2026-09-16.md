# Review — analyser 1.13.1 candidate, 2026-09-16

## VERDICT: NOT READY for 1.13.1

Two release-blocking findings: **B1**, the pending-open change leaves a previous graph's verdict attached
to the newly opened graph in `context`; **B2**, the new `tracedOnly` result makes false assertions about
logged values and invocation evidence. Both were reproduced against the packaged candidate. These are
bounded fixes, not a rejection of the direction or a reason to reopen the 1.13.0 release.

Reviewed `7960462d` (`v1.13.0`) through `ec4c43c5`, including `c443c865`, `a5f94e57`, `04d56bea`, and
[the handoff](handoff_analyser_1.13.1_2026-09-16.md). An isolated detached worktree was used. No production
source, existing test, skill, release file, or original handoff was edited. This review and the two
ledger annotations are the only changes made in the primary checkout. Nothing was committed or pushed.

**Independently measured:** `mvn -q -o clean verify` exited 0; Surefire XML contains **1,395 tests in
183 suites, 0 failures, 0 errors, 0 skipped**. `mkdocs build --strict` exited 0. Four behavioural tests
fail against the packaged 1.13.0 baseline for the expected assertions; the two control tests still pass.
All six pass against the candidate. Green tests do not cover the counterexamples below.

Line numbers below refer to `ec4c43c5`; Java paths are relative to
`src/main/java/telamin/fluxtion/audit/analyser/analyser/`.

## B1 · P1 · CONFIRMED — pending applies only to the echo, not the authoritative pairing

**Files:** `ui/MainFrame.java:4138` and `:4470`; `docs/skills/common/load-audit-log/SKILL.md`, step 4.

`openGraphml` loads the new topology and returns early when `loadInFlight`, without invalidating
`lastPairing`. `context` then combines the **new** topology's name/path with the **old** pairing's
`applies`, counts and reason. There is no pending qualifier on that result. The new skill explicitly
tells the caller to consult this result after `open`.

Reproduced in a visible `MainFrame`, using its real `ActionExecutor` and app-control adapter, synthetic
files and an isolated home:

1. Open a project, then log A containing `nodeA`, then graph A declaring `nodeA`. Verdict: fits, 1/1.
2. Start opening log B containing only `nodeB`.
3. Before its completion callback can run, open graph B declaring only `nodeB`, then request context.
4. The graph-open echo correctly says pending. Context simultaneously reports **log A, graph B,
   `applies:true`, matched 1/1**. Graph B declares none of log A's nodes. That verdict belonged to A/A.
5. After loading completes, B/B is correctly judged 1/1; the status bar says the graph was kept.

The actions in steps 2–3 ran in one EDT turn to hold the asynchronous completion behind them. No pairing,
store or `loadInFlight` field was fabricated. This exercises the in-flight state deterministically,
without depending on a file being slow enough. It is two action calls, not a mocked adapter; the HTTP
transport itself was not part of this probe.

**Negative control:** on 1.13.0, the same in-flight context reports A/B as **false, 0/1**. That old echo
still answered about the wrong *requested* log, which motivated the fix, but context did not attribute
graph A's positive verdict to graph B. The candidate introduces that misattribution.

**Required correction:** expose pending/loaded identity consistently in the echo, context and displayed
pairing. Invalidate or identity-bind a verdict when either participant changes; do not attach an old
verdict to a new graph. Tell the skill how to wait for the requested log and its pairing, including a
failed load. Pin the real-frame two-call path, context during the load, and the final verdict—not just
the mock echo rewrite.

## B2 · P2 · CONFIRMED — `tracedOnly` can contradict the record it accompanies

**Files:** `llm/ReadService.java:174–194`; `llm/VerbSchemas.java:63`; new `ReadServiceTest` case.

There are two distinct counterexamples to the claim that these names denote nodes that ran and logged
no value:

**Repeated node contributions.** The model explicitly permits multiple `NodeLog` entries with the same
instance id in one record. The projection correctly combines them; `traceOnlyNodes` instead adds an id
if **any** occurrence qualifies and never removes it when another occurrence logs a value. Given:

```text
eventLogRecord:
  logTime: 1
  event: Tick
  nodeLogs:
    - risk: { thread: main, method: onEvent}
    - risk: { value: 99}
  endTime: 2
```

Reading `fields:["risk.value"]` through `HeapLogStore` and `ReadService.read(..., store::record)` returns
both `values:{"risk.value":"99"}` and `tracedOnly:["risk"]`. The same defect reproduces under the
reader-declared `QUOTED_SCALARS` grammar with a bare `@invoked` contribution followed by a separate value
contribution. That second probe covers the binary text/parser branch, not a newly generated FLXA file.

**Business spelling mistaken for trace provenance.** A legacy record containing only
`risk: { thread: worker-queue}` returns its business value and `tracedOnly:["risk"]`. It has no method
entry or explicit trace marker. Legacy keys are not reserved; the existing `AuditTrace` inference is
more restrictive than this new per-entry test. The new function independently invents a stronger claim
from weaker evidence.

The new test's value-bearing control puts the value in a **different record**. It does not exercise
multiple contributions by one node in the same record, or a business `thread`/`method` key.

**Required correction:** decide at the record/instance-id level, considering every contribution and
every business entry, independent of which fields were projected. Keep explicit trace provenance
separate from a legacy inference. If legacy evidence is ambiguous, qualify it as trace-like rather
than asserting that no value was logged. Add duplicate-node cases in both grammars, business-key
controls, and an explicit bare-trace positive control. No need to extend the raw-text response to fix
this: keeping `tracedOnly` exclusive to projections is a reasonable scope choice.

## F3 · P2 · CONFIRMED, PRE-EXISTING — the promised final pairing needs an initialized session

**Files:** `ui/MainFrame.java:2950`, `:3561`, `:3711`, `:3720`.

In a fresh window with no project transition, opening logs and graphs through the ordinary action
executor does not necessarily instantiate the lazy session driver. When the second log lands,
`repairLoadedGraph` calls two observation methods that return immediately for `session == null`, then
sets `lastPairing` to null. Context contains the new log and graph, but no applies/counts/verdict. Waiting
for the background load does not resolve it; the driver remains null.

This was reproduced against **both** the candidate and 1.13.0, so it is not a new regression. Opening
a project first initializes the driver, and the candidate then produces the expected final B/B verdict
and status. This is why the active-project and fresh-window cases must be distinguished.

**Disposition:** track explicitly or fix alongside B1; do not claim unconditionally that `onLoaded`
already completes the promised pairing. The revised skill starts by opening a project, which avoids
this particular initialization gap, but the public `open` API does not require one. A regression test
should start with a genuinely fresh frame, not one whose setup has initialized the session incidentally.

## F4 · P2 · CONFIRMED — a missing node pane still reports the old project's roots

**File:** `ui/SourcePanel.java:219–222`.

`rerenderIfChanged` compares only source content. If the node was missing under both the old roots and
the new roots, both strings are empty and it never re-renders the placeholder. The processor pane
recovers because `showSelectedProcessor` separately navigates to it; the node pane has no equivalent.

Reproduced on the EDT with a bound real `SourcePanel`: configure old roots, open a missing processor
and missing node, configure new roots, call `showSelectedProcessor`. The processor placeholder names
the new root; the node placeholder still names the old root. The same content-only test also fails to
notice changes in the supplier's project/offer hint.

**Correction:** invalidate missing-source placeholders on a lookup-context change, not only a change
in file contents. Add a missing→missing node-pane test that asserts the displayed roots and project
hint, not just `hasProcessorOpen()`.

This is incomplete coverage of the advertised repair, not a reason to discard it: the reported
processor missing→found switch is fixed. In the visible-frame project test the pending offer named
both remedies, and opening the offered project displayed its processor source without another click.

## F5 · P2 · CONFIRMED — the handoff diagnoses FLX-1009 at the wrong boundary

**Files:** handoff §4, lines 96–114; the corresponding compiler attribution in the review ledger.

The legacy-message observation is correct; the proposed remote/server-only remedy does not reach the
reported failure. Checked at compiler tag `v1.0.67`, not the moving compiler worktree:

- The client-side DTO builder invokes `LiveGraphSourceGenExtractor`.
- On the source-generation path it rethrows the diagnostic refusal.
- `EventProcessorGenerator` catches that refusal, invokes `DiagnosticSidecar.write`, and rethrows it
  **before** calling the remote combined generator.
- The sidecar is opt-in using `-Dfluxtion.diagnostics.sidecar=<path>` or `=true`.

Thus the remote client does have an error-rendering shortfall, but FLX-1009 from this extractor does
not first travel through server `writeError` and a returned envelope. Changing only server error prose
or rendering only the remote response would leave this constructor error unchanged.

**Corrected handoff direction:** improve human rendering at the client-side diagnostic boundary while
preserving the structured diagnostic; describe the existing sidecar as an available machine-readable
route. Treat remote-error rendering and truthful `/health` version reporting as separate asks. I did
not query the deployed service or run a new failing remote compilation; this finding is confirmed by
the released source's call order and exception propagation, not a claim about its current deployment.

## Other dispositions

- **Source refresh:** accepted for the original processor-pane defect and live pending-project hint,
  with F4 outstanding. No source re-read performance claim is made.
- **New tests:** two source-switch tests, the asynchronous echo test and the trace-only projection test
  each fail against 1.13.0 on the intended assertion. The synchronous/graph-only echo controls pass on
  both versions. The two new placeholder-helper tests pass in the candidate; they were not counted as
  behavioural negative controls because the helper is a new API.
- **Skill publication:** all six `m19-skills/2` hashes independently match the bytes at its pinned
  `a5f94e5790beda291b11054681c50ed3edf2e48d`. Checking the immutable v1 index against its pinned revision,
  rather than today's worktree, is appropriate. The canonical skill tests also pass in full verify.
- **Adding logging without regeneration:** the stated route is supported for an existing ordinary node
  in an already audit-enabled graph: generated auditor initialization registers the nodes, and runtime
  `nodeRegistered` supplies a logger according to the live node's `EventLogSource` interface. It is not
  a claim that installing an auditor in an unaudited generated graph is body-only. The skill's literal
  “every final field” wording should be narrowed to eligible instance fields: static fields and other
  exclusions/constructor annotations exist in the actual mapper.
- **Mongoose skill:** the captured session supports caution about cumulative exports and stale registry
  entries. Do not turn a daily roll cycle into a one-day retention guarantee: inspected local capture
  code selects `FAST_DAILY`, which establishes file rolling, not deletion. Scope input-tailing advice
  to the starter's configured source. Prefer a fresh per-run capture location; if advising deletion,
  require a stopped writer and an explicit decision to discard old evidence. I did not redeploy a
  server to verify the reported shutdown or capture behaviour.

## Plotting report: narrower conclusions from source and probes

These behaviours pre-date this candidate. They are follow-up product/spec work, not additional 1.13.1
blockers.

| Item | Review result |
|---|---|
| a — plots ignore filters | **Too broad.** Graph extraction honours non-time filters, extracts across time, and the chart applies the current time window unless pinned. Formula carry can therefore come from before the visible window. A three-record probe with `from=200` produced a value at 300 using a key last seen at 100; bounded extraction produced none. Distinguish display window, contributing-data scope and carried state in the echo. This probe exercised extraction, not the chart's pixels. |
| b — marker conditions carry | **Confirmed.** A bare-key marker fires when that key is present; a condition marker uses carried values on each qualifying record. `n.a > 0` fired on all three probe records although only the first logged `n.a`. `MarkerSpec` has no resolve policy and the verb schema does not explain this default. Formula series have a separate rule that requires a touched reference for a LOCF point; do not conflate the two. |
| c — string equality unavailable | **Confirmed.** `n.symbol == "DEMO"` was refused with the duration-literal diagnostic. This establishes the gap, not the design or size of a group-by/string-language extension. |
| d — open echo | Echo repair accepted; the larger result is **not closed**, per B1 and F3. |
| e — overwrite refusal | Consistent with the unchanged export policy; no request to weaken it. |
| f — record-index axis | No user-facing option found in the graph schema/config; the series model carries log-time x coordinates. A useful proposal, not implemented here. |

## Verification record and local reproduction aids

- Candidate worktree: `/private/tmp/analyser-1.13.1-review-ec4c43c5`.
- Baseline worktree: `/private/tmp/analyser-1.13.1-negative-7960462d`.
- Java: Corretto 21.0.9. Candidate build command: `mvn -q -o clean verify`; baseline package:
  `mvn -q -o -DskipTests package`. `JAVA_HOME` was explicitly set to that JDK.
- Candidate full-build log: `/tmp/analyser-1.13.1-review-full.log`.
- Strict documentation build log: `/private/tmp/analyser1131-docs.log`.
- Scratch probes, outside both repositories: `/private/tmp/Analyser1131Probe.java`,
  `/private/tmp/Analyser1131Negative.java`, `/private/tmp/Analyser1131PlotProbe.java`.
- Live candidate through the real action executor: `/private/tmp/analyser1131-live-probe-v2.log`
  (fresh session), `/private/tmp/analyser1131-live-probe-v3.log` (project first).
- Same live sequence against released code: `/private/tmp/analyser1131-live-baseline.log`.
- The negative-control runner invoked candidate-compiled test methods with the **baseline production
  jar**, candidate test classes, and JUnit assertion dependencies. All four failures were assertion
  failures, not missing-method/class/linkage errors. The same runner with the candidate production jar
  passed all six selected tests. The full candidate suite independently passed under Maven/JUnit.
- Post-write checks: the three new local links resolve, `git diff --check` is clean, and neither review
  document change contains a prohibited sweep term. A broader ledger-link check stopped on the
  pre-existing `review_response_180a1e7_4c1c9d8.txt` link: that file now lives under `completed/`.
  This unrelated archival link was not changed; the whole ledger is not claimed link-clean.

The paths above are local aids, not a claim of a portable acceptance package. The B1/B2/F4 sequences
and inputs above are sufficient to turn the findings into committed regressions without those files.

## WHAT I DID NOT CHECK

- No remote release/deploy workflow was triggered, and the claimed historical CI runs were not
  independently re-queried. Local build and test results above are the reviewer's measurements.
- No raw REST/MCP round trip or mouse-driven interaction: live checks used a visible Swing frame,
  the production action executor/app-control adapter and actual pane text, with EDT sequencing.
- No source re-read benchmark on large files, network roots, or Maven source archives. The handoff's
  overhead question remains open; typical synthetic-file success is not a performance measurement.
- No fresh-model run of the published skills, regenerated playground bundle, paid/remote generator
  call, Mongoose stop/start, or deployed API/health probe.
- No writer-to-reader FLXA corpus rerun beyond the existing full suite. The additional binary-grammar
  counterexample was parsed under the explicitly declared grammar, not emitted by a runtime writer.
- No new chart screenshot/pixel inspection, rendered PDF, record-index-axis implementation, or string
  expression design. Plotting conclusions above identify which parts were executed versus source-read.
- No re-audit of core/compiler release quality, no C++ work, and no closure of deferred M43 or broader
  authoring work. F5 only checks the specific released compiler path cited by this handoff.

**Next review:** return the fixes with B1/B2 negative controls, the F3 disposition, the F4 UI regression,
and a corrected F5 attribution. Keep author verification distinct from independent review.
