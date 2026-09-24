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
