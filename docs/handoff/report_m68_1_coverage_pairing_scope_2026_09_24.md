# Report — M68.1 implementation, the coverage, pairing and scope verdicts

**Branch:** `feat/m68-1-coverage-pairing-scope` · **Commit:** `4b5b5a69` · **Base:** `main` at `296b5438`
**Brief:** [`handoff_m68_1_coverage_pairing_scope.md`](handoff_m68_1_coverage_pairing_scope.md) ·
**Spec:** [`spec-evidence-integrity.md`](../specs/spec-evidence-integrity.md) v3, D-E1, D-E2, D-E10, acceptance 1–3.
**Status:** implemented, unmerged, **not independently reviewed**. Tracker marks it ◧.

**Author's conflict, stated first.** The implementing session also wrote the spec, assessed its first review
and wrote the brief. Nothing here should be accepted on that session's say-so. Every claim below names the
command that checks it.

## What was built

| Correction | Where | Acceptance |
|---|---|---|
| Declared authorship before the class-name heuristic, every answer carrying DECLARED or INFERRED | `topology/Scaffolding.java` — `classify`, `Authorship`, `Basis`, `authorshipBasis` | 1 |
| The declared fact is node-scoped: event and exported-service vertices always use the fallback | `Scaffolding.nodeScoped` | 1 |
| Membership tested against every declared node, independently of classification | `topology/CoverageService.java` — uses `ProcessorTopology.match` | 1, 2 |
| No ratio when nothing is eligible to score, instead of a vacuous 1.0 | `CoverageService` — `ratioAvailable`, `ratioNote`; `NodeCoverage.denominator` | 2 |
| Membership reported as its own verdict, with scope | `CoverageService` — `membership` block | 2, 3 |
| Framework nodes disclosed outside the denominator | `CoverageService` — `frameworkNodesNotScored` | 1 |
| The pairing carries scope as data, and separates keep from fit | `topology/GraphPairing.java` — `recordsScanned`, `recordsTotal`, `evidenced`, `everyObservedIdDeclared`, `sampled`, `scope`, `withScope`, `facts`, `note` | 2, 3 |
| A kept-but-unjudged or partial pairing can no longer reach FULL | `session/CoveragePolicy.java` | 2 |
| Provenance conclusions removed from three surfaces | `CoverageService` warning, `GraphPairing.reason`, `ProcessorTopology.Match.describe` | 2 |
| Every agent surface states the pairing's facts beside `applies` | `ui/MainFrame.java` — graph-open echo and `context.graphPairing` via `GraphPairing.facts()` | 2, 3 |
| The panel note distinguishes fit, partial match and unjudged | `MainFrame.publishPairing` via `GraphPairing.note()` | 2, 3 |
| The graph-open echo says how the authored count was decided | `MainFrame` — `authorshipBasis` | 1 |
| **An exported PDF prints each table's notes, as the Reports tab does** — added after the report was first written, see below | `report/ReportRenderer.java` — `SectionContent.notes`, `tableNotes`; `MainFrame.renderReportPdf` | 2 (D-E2) |

**One recorded decision, not an omission.** `EntryPointResolver.addSoleExportedService` still calls the node-only
classifier. It only ever asks about exported-service vertices, where the declared fact is deliberately ignored,
so the declared-first overload would return the same answer for every node it reaches. The call site says so,
and `EntryPointAuthorshipTest` fails if the scope rule ever changes.

## Evidence, each item with the command that reproduces it

```sh
# unit and model tests — full suite
JAVA_HOME=<a Java 21> mvn -q test
# the seventeen new tests only
mvn -q -Dtest='EvidenceIntegrityCoverageTest,CoveragePolicyEvidenceTest,EntryPointAuthorshipTest' test
# regression closure: each of four mutations applied alone, sources always restored
python3 tools/mutate-m68-1.py
# end to end through the built jar's action socket; opens a window, isolated user.home
mvn -q package -DskipTests && python3 tools/verify-m68-1-coverage.py
# the same end-to-end checks against an OLDER jar, to watch them catch the defect
python3 tools/verify-m68-1-coverage.py <path-to-pre-change-jar>
```

| Check | Result on 2026-09-24 |
|---|---|
| Full suite | 1,895 tests, 0 failures, 0 errors, 62 skips. Was 1,877 before; the difference is the 18 new tests |
| New tests | 18 pass: 12 coverage and classification, 4 policy, 1 entry-point equivalence, 1 renderer |
| Mutation: ignore declared authorship | red, 4 tests |
| Mutation: authored-only membership | red, 3 tests |
| Mutation: derive membership from the ratio | red, 2 tests |
| Mutation: let retention reach the downstream claim | red, 1 test |
| Mutation: the PDF drops a table's notes again | red, 1 test |
| End to end on the branch jar | every check passes |
| End to end on a jar built from `main` | **17 checks fail**, and the first scenario reproduces the client's exact warning: declared 2, covered 2, "the graphml is probably from a different build, which makes every other figure here suspect". Two of the 17 are the exported-PDF checks added later |

**The fixture is the real artefact.** Tests and the end-to-end script read the committed graph at
`evidence/spring-g14-recovery-2026-09-24/MyProcessor.graphml` directly, not a copy. Every log is constructed and
says so inside its own records. None is a replay of the 2026-09-24 session.

## Existing tests changed, and why each change is deliberate

Four assertions **required** the phrase this slice removes. Each now **forbids** it, and keeps every substantive
check it had. Please confirm no substantive assertion was weakened:

- `TopologyMatchTest.anInstanceIdMissingFromTheGraphIsNamedWithoutClaimingWhichBuild` — renamed from
  `…SignalsAVersionMismatch`; still asserts the ids, the counts and the coverage fraction.
- `GraphPairingTest.aDifferentSystemIsClosed_theDefectM35ExistsToPrevent` — still asserts `applies=false`,
  three logged, zero matched, and that the reason states the fact, not the action.
- `GraphPairingTest.aLogThatWritesNothingCannotConvictTheGraph` — still asserts `applies=true`; now also asserts
  that nothing was compared.
- `CoveragePolicyTest.theRefusalsAreOrdered` and `CoverageClaimTest.theM353ExceptionDoesNotLicenceScoring` — the
  refusal and its ordering are unchanged; only the sentence's conclusion is gone.

## What was NOT verified

Stated so it is not read as verified by silence.

- **Swing counts on screen — now looked at, partly.** Screenshots through the built jar's own `screenshot` verb,
  under an isolated home, show the authored view going from **four nodes on `main` to five on the branch**, with
  the sink node now visible, and the raw index reading eighteen on both. **Two limits:** the pairing note is cut
  off by the panel's width at the default window size, so its on-screen text was not confirmed — it is the same
  `GraphPairing.note()` the tests assert; and with one more node the fit zoom drops from 56% to 36%, below the
  level at which node labels are drawn. That is existing rendering behaviour, but this change is what triggers it
  on this graph.
- **Audit readiness through the hide control.** Asserted over the full graph as a pure call. The panel is
  documented as passing the full topology; that path was read, not exercised.
- **A report export — now driven, and it found a defect, fixed in this slice.** See *The exported PDF dropped the
  warning* below.
- **Older graphs.** The fallback is unit-tested with a missing key, an invalid value, an unsupported major and no
  vocabulary. No real pre-vocabulary graph file was run end to end.
- **Whether `fluxtion.framework` is always correct.** It is believed under the trust policy and the recorded
  authority. The adoption report's own rebuttal section says registration windows remain. This slice does not
  and cannot establish that a producer never mislabels a node.

## Deviations from the brief

- **The brief says "four separate mutations, each must fail alone".** Done, and the harness is committed so it can
  be rerun rather than taken on trust. The mutations overlap in which tests they fail; each still fails alone.
- **The surface table asks the graph-open echo to label the eligible coverage size separately.** Not done. The
  echo already separated raw and authored counts from an earlier fix, and this slice added the authorship basis.
  The eligible size appears on `coverage` as `declared`. Adding it to the open echo needs the source resolver the
  echo does not have, so it was left rather than faked.

## The exported PDF dropped the warning — found by the implementer's own follow-up, fixed here

Driving a real export was listed above as unverified, so it was done. A coverage table exported from a log that
writes one undeclared id rendered **"declared 3 · covered 3" and no warning at all**, on `main` and on the branch
alike. The **on-screen Reports tab, rendering the same report, did show the warning** as a note under the table.
The PDF path routed the table's notes only into the reply's `warnings` for an agent and handed the page the bare
table. One report, two verdicts, is D-E2's defect exactly, and acceptance 2 asks for the report path to be tested,
so it is fixed in this slice rather than deferred.

The fix makes the page follow the screen's existing rule for **every** table kind, not only coverage: whatever
notes the tab prints under a table, the page prints under it too. Verified on the real artefact — the re-exported
PDF now carries the membership warning, the exclusions note and the dispatch note — and guarded three ways: a
renderer test, a fifth mutation, and a PDF scenario in the end-to-end script, which fails on `main`'s jar.

**Reviewer, please check** that printing notes under every table kind is right. Read, aggregate and series tables
can carry notes too, so their exported PDFs will now show text they did not show before. That is the same text
the screen already showed, and nothing changes for a table with no notes.

## Found while testing, not fixed here

**A combined open silently drops part of itself.** Opening a log and a graph together, where the graph declares
only half the logged ids, reports success, and the graph is then no longer loaded. The end-to-end witness first
used exactly that shape, saw no warning, and was investigated rather than trusted: coverage replied that no
topology was loaded. It belongs to M68.4, whole-or-refused requests, and is recorded there.

**Pre-existing, so it does not block this merge — checked after the report was first written.** The same probe
was run against a jar built from `main` at `15a9d33a` and one built from this branch at `6c624bd1`, each under
an isolated `user.home`, with a log writing one declared id and one foreign id:

| Request | `main` | this branch |
|---|---|---|
| `open {log, graphml}` in one call | reply `ok`, then no graph loaded | identical |
| `open {log}`, let it land, then `open {graphml}` | graph kept, `applies=false` | identical |

So the defect is not introduced here, and its trigger is narrower than first written: **only the combined
request drops the graph.** Opened separately, the existing rule keeps a deliberately opened graph and announces
the mismatch. The likeliest mechanism is still that the log-arrival rule, which closes a graph that does not fit,
runs after the graph-open in a combined request and treats a graph the caller just asked for as residue. That is
read, not traced.

**My own witness was wrong first.** The end-to-end script's wrong-result scenario initially planted a
half-foreign log, which could never exercise the warning for the reason above. A witness that silently stops
testing what it claims is the defect class this milestone exists for, and I built one. It now uses a log where
most ids are declared, so the graph is kept, coverage runs, and the warning must appear.

## Questions for the reviewer

1. Is node-scoping right? The fact is ignored for event and exported-service vertices even when present. Check
   whether any real graph declares it on those kinds with a meaning this discards.
2. Is `QUALIFIED` the right claim for a kept graph with no node output, rather than `REFUSED`? The number is
   computable, and every eligible node reads as uncovered. The reason says the pairing was not established.
3. Is the warning wording now factual, or has it moved the conclusion into a different sentence?
4. Does anything else consume `NodeCoverage.ratio()` as if a zero denominator were full coverage? The only
   production caller found was `CoverageService`.

---

## Addendum — the re-review fixes (2026-09-24)

**Review:** `review/m68-1-coverage-pairing-scope-2026-09-24` at `5c6f865d`, verdict *changes required*.
**Branch head before:** `efc5decd`. **Evidence:** `evidence/m68-1-rereview-2026-09-24/`, where every trial's
prediction was committed before it ran (`PREDICTIONS.md`) and every outcome is recorded, wrong ones included
(`RESULTS.md`, with raw outputs beside it). Counts below are summed from Surefire XML, not read from the console.

**RUN** means I executed it and read the output. **READ** means source or document inspection only.

### What each finding was, and what closed it

| Finding | Cause | Fix | Regression | Witness |
|---|---|---|---|---|
| **R1** parity frame test failed; CI never ran on the branch | the frame's pairing carried a scope, discovery's and the session's did not | scope all three identically (R2). The parity test's three equality assertions are **unchanged**; it now reads discovery through the product's own path, a source root plus the frame's `discoverGraphs0`, instead of a hand-built call that could only ever agree with an unscoped verdict, and it asserts discovery's scope | `PairingDuringLoadFrameTest` (display) | M6f, M7f RED at the parity test. **RUN** |
| **R2a** combined and graph-first opens published a sample as a whole-log claim | `session.node.Pairing.recompute` built `GraphPairing.of(declared, logged)` without scope, though it held `sampled` and `total`. **Confirmed, RUN**: the review's probe reproduced exactly (set 1, P2) | `.withScope(sampled, total)` | `SessionPairingScopeTest`, both orders, plus a whole-log case | M6 RED. **RUN** |
| **R2b** discovery's candidates unscoped | `discoverGraphs0` passed a sampled id set with no record count | `GraphmlDiscovery.scan(roots, ids, scanned, total)`; the two-argument form still exists and says "scope not recorded" | `PairingScopeSurfacesTest` | M7 RED. **RUN** |
| **R2c** a whole-log comparison never qualified the published pairing | no path from `coverage` to the pairing the window publishes | `PairingQualification`, built from coverage's own echo and bound to the exact pairing object it qualifies, so it is dropped the moment that pairing is replaced. Stated in `context.graphPairing.qualifiedBy`, the panel note and the `coverage` reply's `qualifiedPublishedPairing` | `PairingScopeSurfacesTest` (supersede, confirm, filtered, already-whole); end-to-end scenario 5 | M9 RED; 17 of the 24 scenario-5 checks fail on a `main` jar. **RUN** |
| **R2d / O2** discovery gave `appliesToOpenLog` without the facts | `Candidate.toMap` predated the facts | `putAll(pairing.facts())` | `PairingScopeSurfacesTest` | M8 RED. **RUN** |
| **R2e** the end-to-end script could not see R2 | it opened every scenario combined, and never checked scope | `open_in_order` and scenario 5: all three orders on a 600-record log, then coverage must qualify | the script itself | fails on `main`, passes here. **RUN** |
| **R3** the build conclusion survived | removed from three surfaces, alive on five | one wording class for the four code sites (finding export, step-through status, both focus-recall messages); help page; `support.md`; **and `user-guide/topology.md:101`, which the review missed** | `MismatchWordingTest`; `UserVisibleWordingGuardTest` over every Java string literal, the help page and the docs site, with its own wrong-result witness | M10, M10g, M11 RED. **RUN** |
| **R4a** the brief's third mutation unguarded | no test asserted a present ratio when nothing logged | `noOutputKeepsARatioOfZero` | the test | M3b RED. **RUN** |
| **R4b** the harness could misreport | no baseline, no check that tests ran, no restore check | rewritten: green baseline, only this run's reports, NOT RUN for a build that did not compile, byte-identical restore by SHA-256, and a mutation passes only if its **named** test fails | the harness | set 1 P4: the old harness reported an uncompilable mutation as STILL GREEN. **RUN** |
| **R5** the merged tree was never tested | `main` moved during review | **merged** `main` into the branch, not rebased — see below | both gates on the merged tree | headless 1,912 = `main`'s 1,894 + 18 before the fixes. **RUN** |
| **O1** the pairing note unreadable on screen | fifth part of one clipped label, no tooltip | the pairing leads the line; the whole line is the tooltip; **and, from P14, the note leads with whatever qualifies it** — the scope when sampled, the whole-log finding once coverage has one | frame assertion; `TopologyStatusTooltipTest`; `PairingScopeSurfacesTest` (set 3) | M14, M15f, M16, M17 RED. **RUN**, and screenshots |
| **O3** session audit log wrote `pairing: applies` for an unjudged pairing | the label read `applies()` | `GraphPairing.auditLabel()`: `keptUnjudged`, `keptPartial`, `applies`, `doesNotApply` | `SessionPairingScopeTest` | M12 RED. **RUN** |
| **O4** one reason hid the level caveat | `CoveragePolicy` returns one sentence | **accepted as a defect and fixed**: a pairing qualification now also states the level caveat when the log was not captured at TRACE. Adding a fact can never change the claim | `CoveragePolicyEvidenceTest` | M13 RED. **RUN** |
| **O5** focus-recall messages suggested a different build | carried over | changed, through the same wording class | `MismatchWordingTest` | M10 covers the class. **RUN** |
| **O6** the combined-open drop lands after the reply | — | recorded under M68.4, with the consequence that an acceptance must test the final state | — | **READ** |

### Gates on the final tree, all RUN

| Gate | Result |
|---|---|
| Headless `mvn test` | **1,927 tests, 0 failures, 0 errors, 62 skipped** — `main`'s 1,894 + this branch's 33 |
| All twelve `*FrameTest` classes with a display | **63 tests, 0 failures, 1 skipped.** The skip is a keyboard-focus assumption in `PersonAtTheScreenFrameTest` that aborts rather than pass falsely on this machine, and predates the branch |
| `tools/mutate-m68-1.py --frame` | baseline **52 green**; **21 of 21** mutations RED at their named test; every restore byte-identical |
| `tools/verify-m68-1-coverage.py`, branch jar | **46 pass, 0 fail**, all three open orders included |
| Same script, a jar built from `main` at `90746e83` | **34 failures** (set 2) |
| The review's own R2 probe, branch jar | *first 500 of 600 records*, `sampled=True`, in all three orders |
| Screenshots at the default window size | the status line leads with the sample scope before coverage, and with the whole-log finding after |

**Not run:** CI itself, since `ci.yml` still triggers only on `main`; the frame suite under xvfb, where the one
skipped test would actually run; and a pre-vocabulary graph end to end.

### What I got wrong

- **I claimed acceptance 3 without testing it on the paths that failed.** My unit test replicated the log-first
  path, and my end-to-end script opened everything combined without checking scope at all. The claim rested on
  the one path that happened to work.
- **"Full suite green" was true and not sufficient.** I changed the pairing contract without running the twelve
  frame classes, which the headless suite skips, and I did not know CI does not run on branches. The review found
  the red that merging would have put on `main`.
- **My harness's M3 was the converse of the brief's third mutation**, and I reported the brief's four mutations
  as done. They were not, as written.
- **The harness could not tell a run that did not happen.** I predicted this before measuring it (set 1, P4), and
  it held.
- **I removed the build conclusion from three surfaces and reported it gone.** It survived on five.
- **P12 was partly wrong**: two kinds of scenario-5 check pass on `main`, because an older fix already labelled
  the log-first sentence and coverage always warned about a foreign id. The checks that pass on `main` are the
  ones that were never the defect.
- **O1's first fix was incomplete, and my own screenshot showed it (P14).** Leading the line with the note was not
  enough: the clip fell inside the note, hiding the scope and, after coverage, showing the superseded verdict.

### What the review got wrong, or missed

- **"It would print a stale result as current."** Not quite: the old harness deleted old reports before each run,
  so it could not print a *stale* one. Its real defects were worse and are what set 1 measured: it reported a
  *missing* run as STILL GREEN, and with no baseline, one pre-existing failure would have made every mutation read
  RED. The review's required remedies were right; its description of the failure was not.
- **It missed a fifth surviving conclusion**, `docs/site/user-guide/topology.md:101`, "Treat the warning as a
  version mismatch". The repository guard now covers the docs site, so a sixth would fail the build.
- **Its line numbers are for `efc5decd`**; after the merge they moved (`MainFrame.java:1581`). Not an error.
- Everything else I checked held: the three-row R2 table reproduced exactly, the frame failure reproduced exactly,
  and the cause it READ for R2 is the cause.

### One deviation from the instructions

**Merged `main` into the branch rather than rebasing.** The branch is published, a rebase would need a force-push,
and CLAUDE.md rule 3 forbids force-pushing. A merge gives the review what it asked for — a merged tree, tested —
without rewriting published history. The one conflict was `CHANGELOG.md`, both sides unreleased entries, kept.

### Left for someone else, as instructed

The ledger disputes on `main` (`tracker.md:1094–1098` and `:557–562`, and archived H8.6, A10.7 and M14.6 with no
live home) are not this branch's work and are untouched.
