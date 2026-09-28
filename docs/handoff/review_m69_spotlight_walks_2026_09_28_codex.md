# PR #57: M69 spotlight walks — implementation review

**Verdict: not mergeable yet.** The tests and controls reproduce, but the real presenter/frame still allow a
walk to make an unsupported identity claim, misnumber a target, or claim a requested record is shown when it is
not selected. One bounded defect is fixed here; eight required corrections remain suggested for the author.

Reviewed implementation: `7a68b262..42309ed2445ea246dceebc1a3d0f744f8836310e`, including r4/r4a and the
S0–S4 commits. Code correction: `42309ed2..1a6ddf63`. The following review/evidence-only commit completes the
review delta on `feat/m69-spotlight-walks`; no main changes were made and nothing was merged or released.

Independence: this session previously reviewed the specification. This is not a new independent vote on that
design. The implementation findings below were checked against source and new probes; agreement with the
earlier R1–R9 specification review is not evidence. **PR57 R1–R9 below are implementation finding IDs.**

**RAN** means executed locally; **READ** means inspected source or CI logs; **REPORTED** means an author's claim
not independently exercised. Source references below abbreviate
`src/main/java/telamin/fluxtion/audit/analyser/analyser/`; test references use the corresponding `src/test/java/`
package. Line numbers refer to the reviewed tree with `1a6ddf63` applied.

## Required corrections

### PR57 R1 — High: a degraded session identity still produces CURRENT, available observational targets

**Suggested; blocks merge. RAN + READ.** `session/node/WalkPlayback.java:174`, `ui/WalkPresenter.java:97`,
`:213`, `:232`; `ui/MainFrame.java:2520`; `llm/ActionDispatcher.java:120`.

Input: save and play `records:row:0` against the demo log. Publish `LogIdentityObserved` for the current
generation with `UNVERIFIED`, then wait for the new ticket to finish preparing. Result:

```text
identity: session=UNVERIFIED, target=CURRENT, available=true, lit=true
```

The node requests re-resolution, but the verdict only travels as explanatory text. The presenter compares
the retained raw text again without taking the published identity into account. This violates §3.8 and W-A8,
which require observational targets to become unresolved on this change. The probe posts the fact into a real
session; it does not claim to reproduce the physical file-observation trigger.

There is a related policy mismatch: `ActionDispatcher.readsRecords` treats only a `walk` call containing
`steps` as reading records, and its comment says playing reads none. The presenter actually calls
`store.rawText(index)` during playback and re-resolution. The right-click capture also reads raw text outside
that dispatcher guard. **READ:** the chart run basis comes from `loadedLogIdentity`, captured at open, rather
than a newly established identity after Follow changes. The frame probe above establishes the record case;
the Follow/chart consequence is source inspection, not a second executed trial.

Required: make the node's identity decision constrain the published target states, and make every actual
record-reading operation honour the read policy without suspending list/bin operations. Establish the chart's
current run basis from current evidence. Add a real presenter/frame witness for identity degradation; a node
test that supplies already-unresolved target facts cannot catch this defect. Keep the decision in the session.

### PR57 R2 — High: appending a step rebinds all existing chart steps to the new run

**Suggested; blocks merge. RAN + READ.** `ui/WalkAuthoring.java:185`, `:197`, `:206`.

Input: save a chart step on run A, change the current run basis to B, then append a status step. `append` retains
the old chart step but calls `save`, which replaces the entire walk's run basis and fingerprint with B:

```text
append: old chart HISTORICAL -> CURRENT; old step unchanged=true
```

`replace` takes the same path and can rebind all the steps it did not replace. The production authoring and
resolver reproduce this with supplied run/drawn facts; it is not a chart-paint trial.

Required: never silently change the evidence basis of an untouched step. Either refuse an append/replace that
mixes incompatible run bases, or retain the basis per step. Test that the old chart stays historical under B
after appending a non-chart step. A save-generation check alone does not protect an already-saved walk.

### PR57 R3 — High: actual spotlight numbering differs from the published step numbering

**Suggested; blocks merge. RAN + READ.** `ui/WalkPresenter.java:105`, `ui/MainFrame.java:2288`.

Input: a step whose first target is missing `topology:node:DEMO_missing` and whose second target is the existing
`topology:node:priceListener`. The node correctly retains numbers 1 and 2. The presenter discards `n` when
converting available targets to requests; the ordinary spotlight call renumbers the reduced list:

```text
numbering: session=[1:false, 2:true], overlay=[1:second]
```

The strip/context and the visible numbered explanation now disagree, violating §3.4, W-A5 and W-A15.
Required: preserve the assigned number through the effect-to-overlay path. The regression must inspect the
real overlay's `Lit.n`, not only the node's targets or the number of requests sent to a fake frame.

### PR57 R4 — Medium: the saved record representation was ignored

**Fixed in `1a6ddf63`. RAN + READ.** `walk/WalkResolver.java:69`, `ui/WalkPresenter.java:237`.

Input: equal record-text digests, with a saved representation different from the current store representation.
Original result: `CURRENT`, available. §3.5 explicitly says a different representation is unresolved.

The resolver now requires a known matching representation before comparing record digests. The presenter
supplies the current representation using the same store-kind convention that authoring records. Blank saved
or unknown current representations are refused; known matching representations still work.

Regression: `walk/WalkResolverTest.java:58`, `recordRepresentationMustBeKnownAndMatch`. Before the fix it ran
**1 / 1 / 0 / 0**, failing at “equal text must not certify a different or unknown record representation”. After
the fix it passes. Control `m69-review-record-representation` (`tools/mutation_controls_session.py:724`)
removes only the guard and fails at that same named assertion, restores bytes, then passes. The existing
changed-record and run-basis controls were also rerun and caught. This closes R4, not R1's session identity gap.

### PR57 R5 — High: an unavailable view record can still be reported and lit as SHOWN

**Suggested; blocks merge. RAN + READ.** `ui/WalkPresenter.java:87`, `:132`, `:213`;
`ui/MainFrame.java:5862`.

Input: a step selects record 1 but its filter has an empty dimension set, with target `detail` and caption
“record one claim”. The real frame reports:

```text
hidden detail: phase=SHOWN, target=CURRENT, available=true, selection=[], reason=record 1 is hidden by this step's filter, or not in this log
```

The failed selection becomes only a note. The resolver verifies the requested record's text, then finds the
generic detail surface visible, even though the record was not selected. A reason string does not make the
`SHOWN` and `CURRENT` claims true. The supposed whole-view validation checks syntax, not these prerequisites;
moreover, the old spotlight is cleared before `applyView` can refuse.

Required: distinguish a successful application of the requested record/focus from a visible container. A
failed prerequisite must not produce an available dependent target. Honour §3.4's whole-view refusal and
prior-view preservation, or explicitly resolve the contract change before implementation. Test the real
selection and displayed record as well as the phase.

### PR57 R6 — High: active definitions are neither frozen nor invalidated through the node

**Suggested; blocks merge. RAN + READ.** `session/node/WalkPlayback.java:64`,
`ui/WalkPresenter.java:81`, `ui/WalkAuthoring.java:190`, `ui/WalkVerb.java:146`.

Input: play a two-step walk, then replace the same named definition with a one-step walk via the verb:

```text
replace active: published count=2, saved count=1, lit=original
```

The node holds a name and count, not the definition §3.8 says the play fact carries. Each subsequent effect
reads mutable config again. The active state therefore is not a coherent frozen version: a later step can be
looked up in the replacement while the node still uses the old count. **READ:** delete and rename instead
inspect playback state in the verb and manually decide to post End, leaving save/replace with no equivalent
fact. This is precisely the scattered invalidation that rule 9 forbids.

Required: give the node a coherent definition/revision and the relevant definition-change facts. Decide
centrally whether replacement ends playback or keeps a frozen definition; adapters must not infer which
mutation needs a manual refresh/end. Test replacement while active and the next navigation, plus rename/delete.

### PR57 R7 — Medium: a refused play request can return success

**Suggested; blocks merge. RAN + READ.** `ui/WalkVerb.java:184`,
`session/node/WalkPlayback.java:70`.

Input: while `DEMO_edit` is already showing, request `walk {name: "DEMO_edit", play: true, step: 99}`.
The node refuses the step but retains the existing showing state. The verb interprets that old state as success:

```text
out-of-range play while active: ok=true, node reason=walk 'DEMO_edit' has 1 step(s) — there is no step 99
```

Required: report the outcome of this request, correlated to its operation/ticket, rather than infer acceptance
from any previously showing walk with the same name. Retaining the existing presentation is reasonable;
reporting that the invalid request started is not. Keep the range decision in the node.

### PR57 R8 — Medium: an omitted filter inherits the user's dirty filter

**Suggested; blocks merge. RAN + READ.** `walk/WalkSteps.java:170`, `:179`;
`ui/WalkPresenter.java:134`.

Input: start with RAW_EVENT grouping, a restricted time/dimension set and text `dirty`; parse and apply a step
with `view: {tab: "topology"}` and no filter. Output remains `group=RAW_EVENT, text=dirty`. §3.3 says missing
stored filter fields take defaults, never the existing selection. The existing dirty-filter test supplies
`Filter.ALL` explicitly and misses this route.

Required: normalise an omitted stored filter to the documented complete defaults, including old/imported
steps, or obtain an explicit contract amendment. Add a parser-to-presenter regression starting with the entire
filter object omitted. A chart population must not depend on an unrecorded text filter.

### PR57 R9 — Medium: a large record index wraps to a different valid record

**Suggested; blocks merge. RAN + READ.** `walk/WalkSteps.java:202`; also `ui/WalkVerb.java:190`.

Input: `view.record: 4294967296`. The parser accepts it and stores record **0**, because `Number.intValue()`
narrows before range validation. A saved detail step can consequently bind and present the wrong record.
The probe prints `accepted=true, resolved=0`. The play-step parser has the same narrowing pattern (READ).

Required: validate finiteness, integrality and supported range before conversion. Refuse the entire request
with the field named. Include a large integral JSON number as the regression, not just negative/fractional input.

## Other reviewed areas and optional improvements

- **READ + RAN, storage and sharing:** SettingsShare export/preview/apply now handle the walk family independently
  within REPORTS, and project snapshots/global-tier saves carry walk definitions while the deletion bin remains
  machine-local. Walk-only shares do not require `report.count`. The relevant tests passed in the full suite;
  the registered storage/bin controls were caught. No separate storage correction was found.
- **READ, generated dispatch:** checked the framework reference's propagation semantics and the generated
  `SessionProcessor` against `WalkPlayback` handlers and OpenLog triggers. The owner-approved r4a node is present.
  I did not regenerate it. The removed generation-only control is redundant on the legitimate stale-completion
  path: a generation change already advances the ticket/ends playback, so the ticket guard rejects first.
  An invented fact with the current ticket and a false generation could reach the defensive guard; that is not
  evidence of an ordinary adapter path. The removal does not close R1 or R6.
- **READ + RAN, verb contracts:** the seventeenth schema, dispatcher/executor routing, manifest-derived lists,
  destructive annotation, context projection and verb counts are covered by the green suite and caught M69
  controls. The operation-specific read policy is an exception: R1. One-operation validation does not cure R7.
- **READ + RAN, presentation-only application:** the inspected apply path selects existing charts and recalls
  focuses using transient operations. The existing presenter tests protect the callback boundary. I did not
  perform W-A4's complete byte comparison of both real settings files after a dirty-start playback; do not equate
  a zero fake persistence callback with that whole acceptance. R8 independently breaks the dirty-start claim.
- **READ + RAN, drawn facts:** `ChartPanel.drawnFact` uses the paint outcome, size, data revision and showing state;
  it does not introduce a second plot-size formula. Existing readiness controls were caught. This does not prove
  that every extraction-error path supplies a current population; that broader case was not probed.
- **READ + RAN, overlay input:** input classification precedes dismissal, and the existing real-frame strip/save
  tests passed. They dispatch Swing mouse events; this review did not add a native Robot trial. The keyboard
  test skipped locally for lack of focus, and passed in CI. Focused-combo and native menu-dismissal behaviour are
  not independently established by my probes.
- **Optional, READ:** `SpotlightOverlay.java:396` paints only the first three reason lines. A six-target walk can
  leave later refusal reasons available in context but absent from the visible strip. Consider an explicit
  expandable/scrollable reasons surface and a real-frame check for target 4–6. I did not run that scenario.
- **Optional, READ:** `SpotlightIsNeverPersistedTest.java:47` strips the phrase `spotlight walk` before its lexical
  scan. It still catches ordinary live-overlay references, but is a lexical tripwire, not proof that everything
  beginning with that phrase is a saved definition. Document that limit and prefer an executable persistence
  regression alongside it. The existing guard and its registered control both ran green/red as intended.

## Verification and limits

Platform: macOS, JDK 21 (Corretto 21.0.8). Fresh source-mapped Surefire XML was counted and preserved before
later targeted runs overwrote reports. Counts below are **total / failures / errors / skips**, not passes.

| Check | My result |
|---|---|
| Original `mvn -o -q clean test`, sandboxed | **2663 / 0 / 29 / 137**; socket permission errors. This attempt is not green. |
| Original `mvn -o -q test`, outside sandbox | **2663 / 0 / 0 / 137**, 357 reports, **0 orphans**. |
| After the representation fix, `mvn -o -q test`, outside sandbox | **2664 / 0 / 0 / 137**, 357 reports, **0 orphans**. |
| Original registered `--mode display` | **137 / 0 / 0 / 1**, 28 suites; **gate exits 1**, not green. `WalkArrowKeysFrameTest#arrowKeysMove(Path)` skipped for focus. |
| Post-fix `WalkPlaybackFrameTest,WalkVerbFrameTest` with both headless flags false | **3 / 0 / 0 / 0**; sequential display JVM, no other display run concurrent. |
| Original `m69-*` controls, fast engine | **62/62 caught**, **235.3 s**; requested and completed name sets match. |
| `m69-review-record-representation`, `m69-s2-resolver-changed-record-not-lit`, `m69-s2-resolver-run-basis` | **3/3 caught**, **8.0 s**; named assertion failures, byte-identical source/class restoration, restored green. |
| Preflight | Original **28 suites / 311 anchors**; after fix **28 / 312**. |
| `python3 tools/test_project_chart_review.py` | **5 tests**, green. |
| `mkdocs build --strict`; `git diff --check`; tracked-file and added-content public-data sweeps | Green/clean. |

The registered display attempt also logged an uncaught EDT `NullPointerException` in
`MainFrame.onLoaded:4443` (`summaryPanel` was null during a delayed load). Surefire counted no error for it.
I have not isolated that exception or attributed it to this PR; it must not disappear behind the zero-error
column. The post-fix two-class run did not log that exception. This is an unresolved verification limitation,
separate from the reproduced blockers.

**READ, original-head CI:** read run **36358165965**, including `ui-frame` job **108729955507**:
**137 / 0 / 0 / 0**. The mutation collector job **108731176993** reports
**311 controls caught exactly once across four shards**. Those are CI's counts at the original head, not my
local counts and not evidence for the subsequently pushed fix. The full non-M69 mutation gate was not rerun
locally. The new head must run CI again.

The [probe sources, outputs and control summary](evidence/m69-review-2026-09-28/README.md) distinguish real-frame
execution, supplied facts and fixture limitations. Initial probe setup used an unsupported graph address and
was corrected before recording results. The regression was run red before the product fix. No prior evidence
was rewritten. The author's prediction commits precede the corresponding S0–S4 implementation commits; their
reported misses are retained. Green controls do not establish untested boundaries exposed above.

## Owner decisions and remaining acceptance

No new owner decision is needed to justify a false CURRENT or SHOWN result. Whether active definition edits
end playback or retain a frozen version needs a consistent policy implemented by the node (R6), not separate
adapter guesses. The existing approved node design, separate verb, strip and right-click menu are not reopened.

W-A11 native capture/Screen Recording permission, hash-pinned skills publication, §7 exclusions and P-6's
unmeasured readiness time remain as disclosed by the author. I did not run a native screenshot capture,
published-client session, performance trial, provider or compilation. No claim of complete W-A1–W-A17 acceptance
is made. Resolve the required findings, add their cross-component witnesses, then rerun the affected gates and
CI before merging.
