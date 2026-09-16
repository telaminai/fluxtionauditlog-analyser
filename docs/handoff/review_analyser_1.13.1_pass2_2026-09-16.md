# Independent review — analyser 1.13.1, second pass, 2026-09-16

## VERDICT: NOT READY for 1.13.1

Two bounded release blockers remain: **R2-B1**, explicit-reader opens bypass the pending lifecycle;
**R2-B2**, the fresh-session fix introduces a modal warning on a socket-driven load. The original
trace-only counterexamples and missing-node placeholder are fixed. The highlighter's stack overflow
is fixed, with non-blocking colouring limitations identified below.

Reviewed production range **`26c10d45..6b0a5258`**, in an isolated worktree. All behavioural results
below are my executions, not the author's mutation results. Production, tests and skills were not
edited. The original review is unchanged. While this review ran, `main` gained documentation-only
commits `9b5bc646` and `1cc5c3b8`; I checked that `src`, `pom.xml` and the reduced POM did not change,
and based these two review-document edits on that updated main without undoing the tidy. Those two
documentation commits are not independently reviewed here.

Java paths below are relative to `src/main/java/telamin/fluxtion/audit/analyser/analyser/`;
line numbers refer to `6b0a5258`.

## Per-finding disposition

| Finding | Disposition | Independent reproduction |
|---|---|---|
| **B1** stale pairing during load | **OPEN, original sequence fixed** | Project → A/A → open B and graph B in one EDT turn now gives pending without `applies`/counts, then B/B 1/1. Explicit `format: yaml` bypasses that protection: R2-B1. |
| **B2** false `tracedOnly` | **CLOSED** | Both legacy counterexamples and declared-grammar marker-plus-value return their values without `tracedOnly`; bare wire marker remains a positive control. Additional spelling/grammar cases below. |
| **F3** fresh-window pairing | **OPEN, null-verdict defect fixed but response regresses the socket path** | Original no-project A/A → B/B now finishes with a verdict. Graph-first mismatch produces a new modal warning: R2-B2. |
| **F4** missing→missing node placeholder | **CLOSED** | Both panes name the new root and new project hint, excluding the old ones. Unchanged hits have zero document changes; node scroll survives. Processor scroll is a separate pre-existing issue, R2-F5. |
| **F5** diagnostic attribution | **CLOSED for the corrected §4 and cited boundary** | §4 now describes the client-side refusal before remote generation, consistent with the first-pass check at v1.0.67. The cited proposed spec uses that boundary. One summary-table phrase remains stale, noted below. |
| **Highlighter** first review | **CLOSED for the reported overflow; colouring follow-ups OPEN** | All three new test methods fail with `StackOverflowError` using the 1.13.0 production jar and pass with the candidate. Ordinary colour spans match; newline/text-block qualifications are R2-F4. |

## Blocking findings

### R2-B1 · P2 · CONFIRMED — an explicit reader never starts the pending lifecycle

**Location:** `ui/MainFrame.java:3915–3924`, `:2473`; pending state at `:2099` and `:4496`.

The changelog claims: **“A graph opened while a log is loading is not judged against the previous
log — anywhere.”** That is still false for the public `format` option.

Exact sequence, through the real action executor in a visible frame:

1. Open a project, log A containing `nodeA`, then graph A declaring `nodeA`: A/A fits, 1/1.
2. In one EDT turn, call `open {log: B, format: "yaml"}`, then `open {graphml: graphB}`, then context.
3. The log echo reports `loading:true`. Context instead reports log A and graph B with
   `applies:false`, `loggedNodes:1`, `declaredByGraph:0`, **no pending state**. The topology note
   says `DOES NOT FIT THIS LOG` and the Project panel renders the negative verdict.
4. Once B lands, B/B correctly becomes 1/1.

Unlike the old B1, this is not graph A's verdict mislabelled as graph B's: it is an actual comparison
against the old log while the requested new one is loading. It defeats the same waiting contract:
an agent following the echo into context sees a completed-looking verdict immediately.

The explicit-format branch calls `openFileWithReader` directly; only `openFile` calls `setBusy(true)`.
The response's invalidation and context guard therefore never run. The same sequence reproduces on
the pre-response `ec4c43c5` jar: this is an uncovered existing entrance to the advertised fix, not a
new misattribution introduced by `96880f2b`.

**Required remedy:** put load-start bookkeeping on the common asynchronous load entrance, including
explicit readers, and pair each completion/failure with that load. Pin the real explicit-format
sequence, not only auto-detection or a mocked echo. Do not just add pending prose to another renderer.

### R2-B2 · P2 · CONFIRMED — lazy session creation introduces a socket-path modal

**Location:** `ui/MainFrame.java:2963–2967`, `:3571`, `:3585–3586`, `:3671–3672`.

Exact sequence:

1. Start a genuinely fresh frame under an isolated home; never open a project.
2. Through the action executor, open graph A declaring only `nodeA`.
3. Through the same executor, open log B containing only `nodeB`; wait for its background arrival.
4. A modal dialog titled **`Project`** appears, with text starting **“graph closed — the graph
   declares only 0 of the 1 node(s) this log writes”**. The socket caller did not request a dialog.

The graph is correctly closed. The defect is that a background/socket path now waits for a human.
I captured the dialog and the contemporaneous context before dismissing only that test frame's
dialog. Context names B and `openedBy:"action socket"`; it is not a human-open reproduction.

**Controls:** open a project through the socket first, and the same graph-first mismatch closes
without that dialog. Against `ec4c43c5`, the fresh-window sequence leaves the stale graph without a
verdict (the old F3) but opens no dialog. The response fixes that missing decision and newly exposes
the wrong audience flag: `sessionInteractive` starts true and is set by project transitions, not by
the `OpenRequest` belonging to this log.

On the requested initialization-order question: `session()` observes the log before the graph, so
the first `LogArrival` sees no graph and takes `noGraph`; the graph observation then computes a
pairing. A later observation through `updateLifecycleMenu` performs the mismatch closure. Both
project-first and fresh paths eventually close the mismatched graph in my probe. I did not find an
incorrect first-observation refusal; the confirmed problem is the new modal and its inherited
project-audience state.

**Required remedy:** carry the current log request's audience to its session effects instead of
borrowing the last project's flag. Pin graph-first mismatch in a fresh frame with a socket request,
including absence of an owned modal dialog. Preserve the warning in the status/context surfaces.

## Accepted corrections and their limits

### B1's original sequence and failed-load restoration

I reran the original `Analyser1131Probe` against the candidate jar in both project-first and fresh
windows. During the ordinary auto-detected B load, context contains `pairing:"pending …"` and
`loading:true`, with no `applies`, `loggedNodes` or `declaredByGraph`. The topology pairing note is
null. The Project panel says **“not yet paired”**, not a stale verdict; it is not literally blank.
After landing, context, topology and Project panel show B/B 1/1.

Two actual failed-load cases used a readable file containing unsupported content, not fabricated
`store`, session or busy fields:

- A/A → start the invalid load, leave graph A alone → failure restores A/A true, 1/1.
- A/A → start that load and open graph B in the same EDT turn → failure retains log A and graph B,
  restoring **false, 0/1**, not the old A/A verdict. The session observes the new graph through
  `updateLifecycleMenu`, so the “verdict predates the graph” concern did not reproduce on this path.

The error handler opens a `Load failed` dialog even for these socket requests; that handler predates
this response. The probe captured and dismissed it to read the restored state. I do not count that
old error dialog as evidence that R2-B2's newly reachable `Project` dialog is acceptable.

Source inspection confirms `openFile`, `openS3` and `openRolledSet` all call `setBusy(true)` before
their background work, and their failure handlers call `setBusy(false)`. I did not execute real S3
or rolled-set failures; the restoration executions above cover local auto-detected loads only.

### B2 — rule-level adversarial cases

Executed through `ReadService.read(..., recordProvider)`, with parsed record grammar declared rather
than inferred. The declared marker is **`@invoked: true`**; the bare *key*, not a valueless entry, is
the reserved syntax.

| Record contributions for one `risk` instance | Result |
|---|---|
| Legacy `thread/method`, then `value:99` | Value preserved; neither trace-only list. |
| Legacy business `thread` alone | Value preserved; neither list. |
| Declared grammar marker, then `value:99` | Value preserved; neither list. |
| Declared grammar marker alone | `tracedOnly:["risk"]`, empty values. |
| Declared grammar marker, then only `thread/method` | Both business entries preserved; neither list. |
| Legacy `method:onEvent`, then business `method:business` | Last value preserved; **`traceLikeOnly`**, never `tracedOnly`. |
| The same duplicate-method bytes under declared grammar | Last value preserved; neither list. |
| One legacy record followed by a declared-grammar record | Only the legacy record is trace-like; no grammar leakage. |

The legacy duplicate-method inference can be wrong about the business meaning, but is now defensible
as an explicitly named spelling inference. No spelling-only rule can distinguish those two writers.
Adding a `thread` requirement would not solve that ambiguity. The schema explicitly says **“inferred
from spelling”**; consumers must not promote `traceLikeOnly` to proof that nothing was logged.
The old jar reproduces the false `tracedOnly` on the original repeated-value and marker-plus-value
cases; the candidate does not. This closes the first-pass blocker, not all possible legacy inference.

### F4 — placeholders, unchanged hits and one measured file

The missing→missing reproduction now reports the new root **and** new supplier-provided project
hint in both panes, with neither old string remaining. The same probe against `ec4c43c5` keeps the
old root and hint in the node pane, confirming this is the repair rather than a vacuous setup.

With unchanged source hits, document listeners recorded **zero changes in both panes** on a
configuration refresh. The node's caret and actual visible viewport position survived. The processor
still navigates to its type declaration despite no re-render: see R2-F5.

I also timed a locally cached **1,260,041-byte** node source on the EDT, after initial rendering:
10 warm-ups, 30 measured iterations. Median `sourceForFqn` read **0.356 ms**; median complete
`showSelectedProcessor` refresh **0.492 ms**, observed maximum **2.052 ms**. These include rereading
the unchanged large node file, not re-highlighting it. This is one warm local-file diagnostic,
not a network/archive/cold-cache or worst-case performance claim.

### F5 — corrected boundary, not a compiler review

The revised handoff §4 says **“the constructor check runs client-side, while the DTO is built and
before any request is sent”** and describes the opt-in sidecar. That matches the first pass's
released-source check at v1.0.67; I did not rerun a paid/remote compilation.

Read-only inspection of `entry-point-rendering.md` on `docs/diagnostics-entry-point-rendering`
at `96bd8264` confirms it targets the three builder entry points invoked by the plugin, preserves
the in-process API, and labels itself **“PROPOSED”**. I checked its boundary, not its design or
implementation. No compiler files or refs were changed.

The active handoff's earlier triage row still says **“already in 1.0.67 as FLX-1009 — but unreachable
on the remote path”**. Replace that summary with the client-entry-point rendering gap; §4 is now
correct. The ledger's older account is explicitly retained as reviewed history and should not be
mistaken for a newly endorsed diagnosis.

## Other findings / follow-ups, ranked

### R2-F3 · P2 · CONFIRMED, PRE-EXISTING — an observation can close the graph that replaced the one judged

**Location:** `ui/MainFrame.java:2633–2634`; `session/node/LogArrival.java:65` and its close effect.

A/A → start a failing load → open graph B → reopen graph A, all in one EDT turn. When the failure
settles, **no graph remains**, although the last deliberate request was A, which fits the still-open
log A. `updateLifecycleMenu` observes the unchanged log before the new graph; `LogArrival` judges
against the previously observed B and its close effect clears the current A. The same sequence
reproduces on `ec4c43c5`, so it is not attributed to this response and is not an additional release
blocker here.

**Remedy:** distinguish a real log arrival from refreshing observed state, and bind a close effect
to the graph identity actually judged. Do not repair this by changing display wording.

### R2-F4 · P3 · CONFIRMED — scanner boundary claims exceed its colouring contract

**Location:** `ui/JavaHighlighter.java:98–109`; changelog highlighter bullet.

The changelog says **“Literals are now scanned by hand, stop at the end of the line as Java literals
must, and an unterminated one colours nothing.”** Independent colour-span comparisons found:

- A final backslash does not throw and colours no unclosed literal; a closing quote exactly at EOF
  is coloured correctly. Ordinary strings/chars with escapes and quotes inside a line comment have
  identical final spans to 1.13.0.
- `"abc` + backslash + LF + `next"` is coloured as one string. The backslash branch skips the LF
  before the newline check. The old regex did **not** colour this invalid multiline string.
- `"abc` + CR + `next"` is also one string: CR is never treated as a terminator. This limitation
  was already present in the old regex. CR-only line comments likewise run past CR in both versions.
- A valid Java text block `"""` + LF + `alpha` + LF + `beta` + LF + `"""` previously had its body
  coloured as a string (incidentally, through the regex); now only pairs of delimiter quotes are
  green and the body is ordinary text. Java text blocks are the exception to the prose's blanket
  claim. This is a colouring regression, not loss of source text or a new overflow.

**Remedy:** check LF/CR before allowing an escape to advance across them; explicitly support text
blocks or document them as unsupported. Add colour-span assertions: the long escaped-literal test
currently asserts only that rendering does not throw, despite its name promising correct boundaries.
These are follow-ups, not reasons to restore the recursive regex.

The other identified grouped repetitions in main match modifier tokens (`SourceNavigation`) or
dotted name segments (`GraphPanel`, `TemplateClient`); `PathForm.ANCHOR` is bounded to five segments.
That confirms the narrower source claim, not a proof against stack exhaustion for arbitrarily many
tokens. I did not stress those unrelated patterns or benchmark malformed-literal scanning complexity.

### R2-F5 · P3 · CONFIRMED, PRE-EXISTING — unchanged processor source still loses scroll position

**Location:** `ui/SourcePanel.java:211–215`, `:377–385`.

In a visible split panel, set both viewports to y=900, reconfigure unchanged roots/processor, call
`showSelectedProcessor`. The node stays at y=900; the processor jumps to y=17, its type declaration.
Neither document is re-rendered. A headless caret check also resets the processor to the type offset
while leaving the node at 1000; that behaviour reproduces on `ec4c43c5`.

**Remedy:** separate configuration refresh from explicit navigation; unchanged hits should retain
viewport/caret unless the user asked to navigate. This does not reopen F4's repaired placeholder.

### R2-F6 · P2 process follow-up — the frame regression is not a default CI gate

**Location:** `pom.xml:102–105`; `PairingDuringLoadFrameTest`'s headless assumption.

I ran the requested override: **1 test, 0 failures/errors/skips**, Surefire duration **1.08 s** on
this machine. Under the default Maven configuration it skips. The skip is recorded in Surefire;
quiet successful console output is not evidence that the UI sequence ran.

This is useful developer coverage, but not an acceptable *sole automated pin* for B1/F3. Add a
display-enabled CI/release-gate run that asserts this suite did not skip, or expose a headless
lifecycle/context seam. I do not require a large extraction merely to land this patch: an enforced
display job is also valid. Manual execution here provides review evidence, not future CI protection.

## Verification record

- Candidate runtime/probe target: **`6b0a5258`**, isolated worktree
  `/private/tmp/analyser-1.13.1-pass2-6b0a5258`.
- Baseline lifecycle/data probes: packaged **`ec4c43c5`**, unchanged production at the review-only
  `26c10d45`. Highlighter negative controls: **`7960462d` / v1.13.0** production jar with the
  candidate's compiled test methods and JUnit dependencies. Loaded code-source paths were printed.
- Corretto **21.0.9**, selected explicitly with `JAVA_HOME`.
- `mvn -q -o clean verify`: exit 0; **1,401 tests / 185 suites / 0 failures / 0 errors / 1 skipped**.
  The skipped case is the frame test. Build used loopback access for local-server tests.
- `mvn -q -o -Dtest=PairingDuringLoadFrameTest -Djava.awt.headless=false
  -DargLine="-Djava.awt.headless=false" test`: exit 0, **1/1 executed**. Then reran the default
  headless command and independently confirmed its one skip.
- The three highlighter negative controls each fail specifically with `StackOverflowError`, not
  linkage errors. Their traces contain the reported repeating `Loop`, `GroupHead`, `Branch`,
  `CharProperty`, `BranchConn`, `GroupTail` shape. All three pass with candidate production.
- `mkdocs build --strict`: exit 0, after the concurrent docs-only commits. The initial attempt used
  a no-longer-present virtualenv; the successful run used `/private/tmp/analyser-review-docs-20260915`.
- The build's only tracked side effect was the reduced POM's line-ending rewrite. It was restored
  to HEAD after inspection; no implementation or test edit remains.
- Final review-document gate: `mvn -q -o test` exited 0 with the same 1,401 / 0 / 0 / 1 totals.
  Review/ledger file links resolve, staged `git diff --check` is clean, and the public-repository
  sweep printed no non-exempt match. Only this review and the two ledger annotations are committed.

Local evidence under `/private/tmp/`:

- `analyser1131-pass2-full.log`, `analyser1131-pass2-frame-test.log`,
  `analyser1131-pass2-headless-frame.log`, `analyser1131-pass2-docs.log`.
- `analyser1131-pass2-original-project.log`, `analyser1131-pass2-original-fresh.log`.
- `analyser1131-pass2-explicit-format.log`, `analyser1131-pass2-failed-unchanged.log`,
  `analyser1131-pass2-failed-graph-change.log`, `analyser1131-pass2-failed-two-graphs.log`.
- `analyser1131-pass2-normal-success-v2.log`, `analyser1131-pass2-graph-first-fresh-v2.log`,
  `analyser1131-pass2-graph-first-project-v2.log`, corresponding `baseline-…` logs.
- `analyser1131-pass2-data-final.log`, `analyser1131-pass2-data-baseline.log`,
  `analyser1131-pass2-viewport.log`.
- `analyser1131-pass2-highlighter-positive.log`, `analyser1131-pass2-highlighter-negative.log`,
  `analyser1131-pass2-highlighter-colours.log`, `analyser1131-pass2-highlighter-colours-old.log`.

Scratch probes are outside the repo: original `Analyser1131Probe.java`, plus
`Analyser1131Pass2Frame.java`, `Analyser1131Pass2Data.java`, `Analyser1131SourceViewport.java`,
`Analyser1131HighlighterProbe.java`, and `analyser1131-pass2-run.cjs`. No production state was
fabricated for the lifecycle sequences. Reflection read private UI state; public action calls
performed the opens. These local paths are aids, not a portable acceptance package.

## WHAT I DID NOT CHECK

- No release workflow, push, deployment, remote CI result, paid generator call, new skill publication,
  or external account operation. No compiler code/spec implementation review or compiler mutation.
- No actual S3 transfer, failed rolled-set open, plugin-reader fault injection or arbitrary overlapping
  load-completion permutations. Local failure restoration is confirmed; universality is not claimed.
- No raw HTTP/MCP transport or mouse-driven open sequence. Tests used visible frames and the real
  action executor/app-control adapter, plus actual pane/viewport state on the EDT.
- No independent reproduction of the owner's exact 23,310-character generated source or its exact
  4,115-character tail. The three supplied tests independently reproduce the failure class and trace
  on this JDK; stack-overflow thresholds are not portable constants.
- No exhaustive Java lexer, Unicode-escape/text-block matrix, adversarial scanner performance study,
  dark-theme pixel review, network-source reread benchmark or cold-cache latency measurement.
- No full writer→new-binary-file experiment for B2. The extra cases use genuinely parsed records with
  explicit grammar; mixed grammar was exercised through the record-provider overload, not a live
  mixed-format rolled store. Existing conformance tests ran in the full suite.
- No author mutation exercise was accepted on trust as an independent result. I reran the old
  counterexamples and highlighter baseline controls; I did not reproduce every author's one-line mutant.

Return R2-B1/R2-B2 with public-path negative controls and a fresh-window, no-dialog assertion.
Keep the confirmed pre-existing follow-ups separate from those two release blockers.
