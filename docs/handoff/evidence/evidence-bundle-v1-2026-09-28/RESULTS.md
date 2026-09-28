# Evidence bundles — results (2026-09-28)

Spec: `docs/specs/spec-evidence-bundle-packaging.md` **r5** (§13–§13.7). Branch `feat/evidence-bundle-v1`, PR #63.
This file has two parts. First, **the state now**, after three reviews and the convergence. Then, **the v1
record** as it was written after B4, kept because the predictions are scored against it.

## The state now

**What ships:**
- capture as one operation on the running analyser, `report {bundle: {path, notes?, from?, to?}}`, decided by the
  `evidenceCapture` session node;
- an optional time-window excerpt, and a log growing under Follow captured as the records read so far;
- verify and unpack for the recipient, in bounded memory;
- no skills. The docs site is the procedure.

**Driven:** `tools/evidence-bundle-demo.py`, 43 passed / 0 failed. A sender makes three captures: the whole log
with notes, an excerpt holding the breach, and one missing it. A cold recipient opens the two good bundles and plays
each walk, with every target CURRENT and lit, the breach at row 7 (whole) and row 3 (excerpt). The received file
reaches the walk's end in ~0.6 s whole and ~1.2 s as an excerpt, plus ~2.5–3 s to start the analyser. One machine,
two isolated homes.

**Findings, by round** (each with a regression, red before the fix where one could be written, and a control):

| round | finding | outcome |
|---|---|---|
| driving B4 | the profile file lags the session (debounce) | `context.project.unsavedEdits`; later superseded by EB.F11 |
| driving B4 | an agent stepping by `play` re-stated the caveat | a play of the walk showing continues it |
| v1 review F1 | `--verify` died on a 2 MiB zip bomb (OOM) | streamed, manifest-first, declared sizes bound members; 160 MiB verified in a 64 MiB heap |
| v1 review F2 | "holds no paths" was false: a path mid-prose travelled | a path-valued key refuses; a path in prose is redacted and named |
| convergence | the capture skill was all mechanism, except a rule it never stated (a log growing under Follow) | capture moved into the analyser; the rule became EB.F6 |
| convergence | WalkAuthoring's generation check is frame-side (rule 9) | capture applies it in its node; the walk save is EB.F7 |
| convergence review | the empty-window refusal was the frame deciding | an observed count; the node refuses |
| convergence review | cleanup deleted every `.capture-*` folder, a neighbour's included | reviewer's age gate rejected; owned folders under an OS lock |
| reaper review | the reaper closed a descriptor to its own marker, releasing its own lock (POSIX) | ownership settled in memory by real path, before the file is touched |
| EB.F9 | the moved-generation rule never provoked from the frame | held by a latch seam while a real load lands; no timing |
| EB.F11 | a read-only profile made the bundle silently stale | the capture serialises the live settings and never reads the file |
| driving | the chart spotlight has no room at the default window | EB.F1: accepted, the window is pinned |

**Gates at `HEAD`:** see the latest commit message and PR #63. Local: `mvn -o clean test` green; the display gate
green except the two known keyboard-focus skips; every `eb-` / `rf` / `cv-` control caught; the driver 43 / 0.
CI's Linux frame suites are the PR's to show.

**Open:**
- EB.F10: an excerpt of a log that is not time ordered. A confusing excerpt, not a false claim.
- EB.F7: the walk save's frame-side check.
- EB.F3: a second machine.
- EB.F4: small leftovers.
- EB.F5: the second delivery.
- EB.F8: the declared unwitnessed branches.

**Predictions:** only B0–B2 had predictions committed before their code (scored below). The later rounds did not.
Each review wrote and scored its own predictions in its own document on its review branch. Most findings in the
table above were not predicted by anyone.

---

# The v1 record (after B4, before the reviews)

Spec: `docs/specs/spec-evidence-bundle-packaging.md` **r3**. Branch `feat/evidence-bundle-v1`, cut from `dff81924`.
Predictions: `PREDICTIONS.md` beside this file. **B3 and B4 had no predictions committed before their code**, which
breaks the usual discipline; they are scored below against the spec's acceptance instead, and say so.

## The driven run (B4)

`python3 tools/evidence-bundle-demo.py` runs two analysers from the built jar, under two isolated homes on two
paths (`/tmp/fluxtion-evidence-demo/sender`, `…/recipient`), on the DEMO log and graph:

- **Sender:** a DEMO project. Opens the log and graph; saves a chart, a chart with an external CSV series, a
  three-step walk (graph node, record, chart) and a report. Captures by the skill's steps: a live capture with no
  log refuses first.
- **Recipient:** a cold home with its own project and no source roots. It receives only the `.fexp`. Verify,
  unpack, open project, open log and graph, play the walk by the verb, one screenshot per step, then return to its
  own project.

**Three consecutive runs: 20 passed, 0 failed each.** Timings (seconds, run 1 / 2 / 3):

| step | 1 | 2 | 3 |
|---|---|---|---|
| recipient analyser start (to its REST endpoint, plus a fixed 1.5 s settle) | 2.52 | 3.03 | 2.52 |
| `--unpack` (a whole JVM; Corretto 21 with CDS, checked by `time`: 0.04 s real) | 0.06 | 0.05 | 0.06 |
| open project, log and graph, until loaded | 0.23 | 0.22 | 0.22 |
| the walk, three steps, with screenshots | 0.30 | 0.32 | 0.31 |
| **received bundle to the walk's last step** | **0.58** | **0.60** | **0.59** |

These are machine-driven timings on one machine, not a person's. A person reading each step is the real cost.

**By eye (EP-A11):** the three recipient screenshots (painted by the app, `screenshot` verb) show DEMO data and
neutral `/tmp` paths only; the topology node, the breach record and the chart series are each lit with its caption,
and the "not re-checked" caveat appears once, on step 2.

## Found by driving it: none of it was predicted

1. **A walk saved just before capture was missing from the bundle.** Project writes are debounced (800 ms), and
   the capture copies the file. Fix: `context.project.unsavedEdits`, and the skill waits for it to clear.
   Regression `ContextLogGenerationFrameTest#theProjectSaysWhenItsFileLagsTheSession`; control
   `eb-b3-context-publishes-unsaved-edits`.
2. **There was no headless way to write the allow-listed profile.** The export is a dialog. Fix: `--bundle-profile`
   (spec r3 §3.3). `BundleProfileTest`, on a real sender profile; seven `eb-b3-*` controls.
3. **An agent stepping a walk re-stated the caveat on every step.** The verb steps with `{play, step}`, and B0 reset
   the caveat on every play request. Fix: a play of the walk already showing, on the same log, continues it.
   `WalkPlaybackTest#anAgentSteppingByPlayContinuesTheShowingSoTheCaveatIsNotRepeated`; control
   `eb-b4-play-continues-the-showing`. The re-anchored `eb-b0-caveat-again-per-showing` still catches its mutant.
4. **At the default window (1200×800) the chart step is not lit:** *"no room at 192×247 px — widen the window"*.
   `--default-window` reproduces it (17 passed, 2 failed). Pre-existing; tracker EB.F1. The demo pins 1440×900.
5. **`log.identity` is absent until a check runs**, so the spec's refusal field was not the constant one; the
   skill uses `log.freshness` as well.
6. **Test-method finding:** a hand-written profile is rewritten on the analyser's first write of it (normalised, and
   given a nonce), which a naive before/after blamed on the bundle. EP-A7's baseline is the analyser's own write.

## Predictions

| # | verdict | evidence |
|---|---|---|
| 1 | held | `1ba84083` touches only `WalkPlayback`, its test, the controls and the changelog; no generated file |
| 2 | held | the control removing the guard (`eb-b0-caveat-once-per-walk`) fails the named assertion |
| 3 | held | `1ba84083` adds to `WalkPlaybackTest` and deletes nothing |
| 4 | **held as stated, wrong as design** | a new play does re-state it. But the verb's only way to step is a new play, so an agent's walk repeated it on every step (finding 3) |
| 5 | held | one `put`; `329949a8` touches `MainFrame` only in `src/main` |
| 6 | held | red before at its named assertion; `eb-b1-context-publishes-the-generation` |
| 7 | held | the full suite is green |
| 8 | **missed** | B0 added one headless test, not three. The frame suite is registered in both CI lists |
| 9 | **half** | three new controls, all caught; but `m69-r1b-caveat-goes-when-assessed` **was** orphaned and re-anchored. Again in r3: `eb-b0-caveat-again-per-showing` was orphaned by finding 3's edit, and re-anchored |
| 10 | held, extended | one pure class and flags before any UI, no regeneration. r3 adds a fourth flag and a second class (`BundleProfile`) |
| 11 | held, one gap | every case, including a duplicate entry built by renaming bytes. Two branches have no witness: two manifests, and a manifest listing a member twice |
| 12 | held | `EvidenceBundleTest#unpackIsVerifiedFreshAndLeavesTheBundleAlone`; the driver re-hashes the received file |
| 13 | held, tested differently | the test rejects "authenticated", "authentic", "signed by", "trusted" and "genuine" outside the denial, because "unsigned" contains "signed" |
| 14 | **missed** | 11 controls, not 8–10; the size check is a declared equivalent mutant |
| 15 | not shown wrong | two packs at one clock give one identity; the identity is pinned as a literal |

## B3 and B4, against the acceptance (no predictions were made)

See spec r3 §7: EP-A1…A12 all RAN, with EP-A6 on **one machine** (two homes), EP-A8 at a pinned window, and EP-A1's
moved-generation deletion code only, not provoked live.

## Gates at the final commit

Recorded in the commit message: `mvn -o clean test`, the display gate for the frame suites, the preflight, every
`eb-*` control, `mkdocs build --strict`, `test_evidence_bundle_demo.py`, the driver, and the rule-1 sweep.
