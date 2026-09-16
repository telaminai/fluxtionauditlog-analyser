# Analyser 1.13.1 — independent review, pass 3

## Verdict: NOT READY for 1.13.1

**R2-B1 and R2-B2 are CLOSED for their reported sequences.** Both fixes survive my original
reproductions, and both regression tests fail against isolated mutants for the intended reason.
The new display CI job is real: three tests executed, none skipped.

**One blocking regression remains, R3-B1:** a socket close after a human log arrival inherits the
human audience and opens a modal. The response's open audience question is therefore not just a
choice about whether a human should see a warning. A currently executing socket action can still
wait for a person. The exact sequence below is green on the previous candidate and fails here.

Reviewed production: `b662bc332f72bb74b6c22d831b8edcd59a5f86e1`, the single response commit over
`657ab6e4`. Isolated worktree: `/private/tmp/analyser-1.13.1-pass3-b662bc33`.
Reviewer: Codex. This is independent execution and source inspection, not adoption of the author's
verification record. No production source, tests or skills were edited. No compiler work or release
workflow was run. Only this review and the ledger annotation belong to the review commit.

## Findings, worst first

### R3-B1 · P2 · CONFIRMED — a human arrival changes the audience of a later socket close

Locations: `MainFrame.java:2682` (new persistent assignment), `:3986–3993` (socket close does not
declare its audience), `:2640–2641` (refresh observes the log before the graph), `:3684–3685`
(the inherited flag chooses a modal).

Exact sequence, with a real frame on the EDT and an isolated `user.home`:

1. Socket action `open {project: P}` creates the session and sets its audience non-interactive.
2. A person opens log A, containing `nodeA`, through `MainFrame.openFile(A)` — the public human
   open entrance. Wait for it to land.
3. Socket action `open {graphml: B}` opens a graph declaring only `nodeB`. It is deliberately kept
   with an explicit mismatch verdict, as intended for a graph someone chose to open.
4. Socket action `open {close: "graph"}`.

On `b662bc33`, step 4 opens a modal titled **Project**, saying:

> graph closed — the graph declares only 0 of the 1 node(s) this log writes, so it describes a different system or build. Reopen it deliberately if you meant to compare them.

The action returns only after my watchdog dismisses that dialog. Its log orders `DIALOG=Project`
before the successful `CLOSE=...` response. The observed audience remains `true`.

The same probe against the previous candidate's packaged code (`6b0a5258`; production unchanged
through `657ab6e4`) completes with **zero dialogs**, audience `false`. I checked that the previous
worktree's `MainFrame.java` equals the file at `657ab6e4`, not merely that the jar has a familiar name.
The final reproduction drives the full `ActionExecutor.render("open", {close: "graph"})` path,
not a fabricated effect or a direct call to the warning renderer.

**Cause:** this response correctly assigns the arriving log's audience, but leaves it as ambient
session state. The next socket close never replaces it. The already-known refresh/arrival defect
R2-F3 supplies the warning: `closeGraph` clears the view, then `noteLogState` judges the processor's
still-open graph before `noteGraphState` says it closed. The *spurious observation* is pre-existing;
making this previously non-interactive sequence modal is new. Deferring R2-F3 does not defer this
regression in the audience change.

**Required remedy:** make audience belong to the operation whose effects are executing, including
socket graph/log close and corresponding human menu entrances. Keep the captured `OpenRequest`
for asynchronous log arrival. Do not set "human" unconditionally inside shared `closeLog` or
`closeGraph`: session effects and socket operations call those same methods. A bounded fix need
not implement the whole asynchronous-session milestone, but must cover the public close path and
retain the no-dialog arrival test. Add this mixed-audience sequence to the display suite, with a
negative control and a human-warning positive control.

### R3-F2 · P3 · CONFIRMED — text-block colouring terminates at an escaped delimiter

Location: `JavaHighlighter.java:96–101`, especially `text.indexOf("\"\"\"", i + 3)`.

The response says text blocks are "coloured whole" and the changelog says the same. Ordinary text
blocks now are; escaped quote runs are not. I compiled this source with the local JDK 21 compiler
(exit 0), then rendered the exact same string:

```java
class TextBlockProof {
    static String t = """
        alpha \""" beta
        gamma
        """;
}
```

`alpha` is string green (`ff15803d`); `beta` and `gamma`, which are still inside the legal text
block, are base colour (`ff1f2328`). The search accepts the escaped triple-quote sequence as the
closing delimiter. This is a cosmetic follow-up, not a release blocker; the crash fix stands.

**Remedy:** scan the delimiter with escape awareness and pin colours after an escaped quote run,
or qualify the broad text-block claim until that is done. The remaining CR-only line-comment
colouring limitation is also still present, unchanged from pass 2.

### R3-F3 · P3 · CONFIRMED — the deferred finding is linked to M44.3, not actually recorded there

Location: response handoff line 11, which says R2-F3 "is filed there"; ledger's response entry
says the same. `docs/specs/tracker.md:482` and `spec-async-session-driver.md` contain no concrete
entry for the finding or its graph-identity remedy. The response commit changes neither file.

I checked the live tracker, its archived M44.3 entry, the linked spec, and the commit diff. The
handoff does retain the finding and points at a plausible future owner. That is not the same as
putting the accepted work in the canonical tracker.

**Remedy:** add a concrete M44.3 follow-up linking R2-F3 and its reproduction, or change "filed
there" to "proposed for M44.3". This does not independently block release.

## Response dispositions and independent evidence

| Item | Disposition | What I verified |
|---|---|---|
| R2-B1 explicit-reader pending lifecycle | **CLOSED** | Project → A/A → explicit `format: "yaml"` log B and graph B in one EDT turn. Context says pending/loading without verdict counts; topology note is blank; Project says not yet paired. After landing, B/B is 1/1 on all three surfaces. |
| R2-B2 fresh socket-arrival modal | **CLOSED for the reported sequence** | Fresh frame, no project, socket graph A then mismatching socket log B: graph closes, no modal. Project-first control also passes. Mixed later operations expose R3-B1 above. |
| R2-F4 highlighter | **Reported examples fixed; follow-up R3-F2** | Five current tests pass. New newline and text-block colour tests both fail against the previous candidate. Own span probe confirms LF/CR termination, terminal-backslash safety, closing quote at EOF, ordinary strings/chars, and quote-in-comment behaviour. |
| R2-F5 unchanged source viewport | **CLOSED** | Replayed the previous real-frame viewport probe: processor and node both remain at y=900 after an unchanged config refresh. Previously the processor moved to y=17. |
| R2-F6 display CI gate | **CLOSED** | Exact-commit CI log and local display run both report 3 tests, 0 failures/errors/skips. Workflow explicitly checks the Surefire XML for skips and zero tests. |
| R2-F3 refresh mistaken for arrival | **OPEN, deliberately deferred** | No implementation change claimed or found. Its stale observation also participates in R3-B1; I did not rerun the previous failed-load/two-graph replacement reproduction this pass. |

### Negative controls, without changing repository source

I generated two isolated `MainFrame.class` overrides in `/private/tmp`, using the JDK's bundled
bytecode tooling. All other production classes and the current compiled test methods were unchanged.
Each run printed the production class's actual code-source location.

- **B1 mutant:** remove `setBusy(true)` from `openFileWithReader` and put it on the human/auto-detect
  `openFile(Path, OpenRequest)` route only. The auto-detect frame test **passes**; the explicit-reader
  test **fails** at the pending echo assertion, showing the old A/B mismatch instead. Status-label
  text was not mutated; the lifecycle decision is what this control tests.
- **B2 mutant:** remove only the `onLoaded` assignment to `sessionInteractive`. The fresh-window
  no-dialog test **fails**, expected 0 dialogs but saw 1. The watchdog prevents an indefinite test
  hang, and the failure is the audience assertion, not an incidental timeout.
- **Colour controls:** current `JavaHighlighterLongLiteralTest` methods executed against the previous
  packaged highlighter fail at actual foreground-colour assertions for escaped LF and text-block
  body text. All five methods pass with current production.

These are my reruns, separate from the author's source-revert mutants. The positive frame suite
was also run through Maven, not only through the scratch reflective test runner.

### Answer to the open audience question

The response says: "A human File-menu close between them inherits the last one".

Confirmed: after a socket load, both File-menu closes leave the field false. In my ordinary
close-log/then-close-graph sequence no warning was needed, so this was harmless. A later human
log arrival re-arms human warnings; a following socket log arrival suppresses them again.

The rule should be **the current operation's audience**, not "whichever project or log operation
last wrote a flag". A human close should carry human provenance at its entrance, but not enable
dialogs globally for future socket actions. The reverse direction is demonstrably harmful now:
human arrival → socket close is R3-B1. Keep that distinct from whether a redundant warning should
be emitted at all; that latter decision belongs to the session model.

## Commands and results

Local environment: macOS, Corretto 21.0.9. Maven ran offline; HTTP-capable tests and visible frame
processes ran outside the sandbox with approval. UI probes used isolated JVM `user.home` directories.

```sh
git worktree add --detach /private/tmp/analyser-1.13.1-pass3-b662bc33 b662bc33
env JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.9/Contents/Home mvn -q -o clean verify
env JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.9/Contents/Home mvn -q -o \
  -Dtest=PairingDuringLoadFrameTest -Djava.awt.headless=false \
  '-DargLine=-Djava.awt.headless=false' test
gh run view 35139979566 --repo telaminai/fluxtionauditlog-analyser --json jobs,headSha,conclusion,url
gh run view 35139979566 --repo telaminai/fluxtionauditlog-analyser --job 104941782043 --log
```

- Clean verify: **185 suites, 1,406 tests, 0 failures, 0 errors, 3 skipped**. Counted from the XML
  before the focused display run overwrote that suite's XML. The skips are the three frame cases.
- Local display run: **3 tests, 0 failures, 0 errors, 0 skipped** (1.690 s in suite XML).
- [CI run 35139979566](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35139979566)
  is on the full reviewed SHA. All three job conclusions are success. The
  [ui-frame job](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35139979566/job/104941782043)
  records **3 / 0 / 0 / 0**, 2.665 s, and its subsequent XML check prints
  `tests="3" errors="0" skipped="0" failures="0"`.
- Public-content sweep: no non-exempt matches. Maven's generated reduced-POM line-ending-only
  change was inspected and restored in the isolated worktree; it is not part of the review.

Reviewer probes retained on this host:

- `/private/tmp/Analyser1131Pass2Frame.java` and `Analyser1131Probe.java`: original reproduction helpers.
- `/private/tmp/Analyser1131AudienceProbe.java`: public action-executor mixed-audience reproduction,
  mode `socket-close-after-human`; human path uses `frame.openFile(A)`.
- `/private/tmp/Analyser1131BytecodeMutant.java`, `Analyser1131TestRunner.java`,
  `analyser1131-pass3-test.cjs`: isolated negative controls and runner.
- `/private/tmp/Analyser1131TextBlockProbe.java`, `Analyser1131HighlighterProbe.java`,
  `Analyser1131SourceViewport.java`: colour/compiler and viewport probes.
- Evidence logs use `/private/tmp/analyser1131-pass3-*.log`; the new blocker is in
  `analyser1131-pass3-audience-verb-analyser-1.13.1-pass3-b662bc33.log`, with the identical baseline
  sequence in `analyser1131-pass3-audience-verb-analyser-1.13.1-pass2-6b0a5258.log`.

## WHAT I DID NOT CHECK

- No full re-audit of 1.13.0/1.13.1 or the previous pass's closed B2/F4/F5 findings. This pass is
  the response delta, the requested audience question, and the added source-panel changes.
- No socket transport timeout measurement: the new reproduction executes the same action executor
  the socket dispatches, on a real EDT. A visible modal and delayed method return are **CONFIRMED**;
  the eventual behaviour of a particular external client's timeout is **PLAUSIBLE**, not measured.
- No new S3, rolled-set, follow-rotation, cancellation or concurrent-load stress test; no claim
  that all asynchronous lifecycle combinations are now covered.
- No exhaustive Java lexer conformance or Unicode-escape preprocessing check. CR-only line-comment
  colouring remains a known cosmetic limit. No cross-platform visual/theme matrix.
- I inspected CI conclusions for build/loop-bench, not every benchmark assertion or benchmark timing.
  I did inspect the actual frame job's executed tests and no-skip check.
- No docs-site rebuild: this review changes only non-site handoff documents. No performance benchmark,
  native packaging, compiler repo mutation or release workflow.
- While this review ran, `9caf1527` (performance-spec status) and `2375c7bd` (regen builder pin)
  landed after the reviewed tip. They do not change application source or these handoff files;
  their claims and the regeneration profile are **not reviewed here**.

## Next handoff

Return a bounded audience fix with the four-step R3-B1 sequence pinned in the display suite, a
negative control, and retained human-warning and fresh-socket-arrival controls. Do not reopen the
two original blockers or describe the display suite as unverified: those checks now hold.
R3-F2 is a cosmetic follow-up; R3-F3 is a tracking correction.
