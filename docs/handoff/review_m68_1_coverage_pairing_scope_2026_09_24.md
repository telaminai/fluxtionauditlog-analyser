# Review — M68.1, the coverage, pairing and scope verdicts

**Verdict: CHANGES REQUIRED before merge.** The core fix is right and I reproduced it: the committed recovery
graph now reads 3 declared, 3 covered, ratio 1.0, with no out-of-topology warning and no build claim, where a jar
built from `main` still emits the client's exact sentence. But five things must change first:

- **R1.** The branch breaks a display test that CI's `ui-frame` job runs. CI has never run on this branch.
- **R2.** Acceptance 3 is met for only one of the three ways to open a log and a graph.
- **R3.** The different-build conclusion survives on two product surfaces and in the in-app help.
- **R4.** The brief's third regression mutation is unguarded.
- **R5.** The branch has never been tested against current `main`.

All five are small and specific.

**Subject:** `feat/m68-1-coverage-pairing-scope` at **`efc5decd`**, base `296b5438`. **`main` during review:**
`43ce82fc` when I measured the baseline, `1e51545d` when I branched this review. Three commits landed in between,
one touching `MainFrame.java`.

## Independence

**I took no part in any of the four earlier M68 review rounds** (`ed06170c`/`2f321705`, `7b8d9f51`, `d1e58bc9`,
`c583edf3`), nor in writing the spec, the brief or the implementation. This is checked against the record, not
recalled. My session's transcript mentions none of those commits, their branches or
`spec-evidence-integrity.md` before this request. The rounds' commits carry either no session trailer or a
different session's. The implementation commits carry a third. All of them share the owner's git identity, so
authorship alone cannot separate sessions.

**One interest to disclose.** In this session I implemented, and have unmerged, the Mongoose audit-production
work on `feat/mongoose-audit-production-rebased`. It also modifies `CoverageService` (MA-8 level annotations) and
`PerNodeLevelChanges`. The two branches will meet at merge. That gives me no stake in this branch's verdicts, but a
reader should know I have read `CoverageService` closely for another reason.

**What is claim and what is checked.** Every result below is marked **RUN** (I executed it and read the output),
**READ** (source or document inspection only), or **REPORT** (the author's claim, unverified). Nothing in the
implementation report was accepted on its word.

## Required before merge

### R1 — The branch fails `PairingDuringLoadFrameTest`, which CI runs; CI has never run here

**RUN.** With a display (`-Djava.awt.headless=false`), all 12 frame-test classes on the branch: **63 tests, 1
failure**:

```
PairingDuringLoadFrameTest.committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession:207
frame/discovery parity ==> expected: <GraphPairing[logged=3, matched=3, applies=true, …, recordsScanned=-1, recordsTotal=-1]>
                           but was: <GraphPairing[logged=3, matched=3, applies=true, …, recordsScanned=1, recordsTotal=1]>
```

The same class on `main`: **9 tests, 0 failures**. So this is the branch's regression.

`.github/workflows/ci.yml` runs this class under `xvfb` in the `ui-frame` job. That workflow triggers only on
pushes and pull requests to `main`, and the branch has no pull request. `gh run list` shows only *Starter evidence
static checks* ran on the branch's three commits. **The failure has never been seen, and merging would turn
`main` red on its first run.** The headless suite skips all 62 frame tests, which is why the report's "full suite
green" is true and not sufficient.

**This is a symptom of R2, not a test to update.** The frame's pairing now carries its scope. Discovery's
(`GraphmlDiscovery`) and the session graph's (`session.node.Pairing`) do not. That is one verdict stated with a
scope on one surface and without it on two, which is D-E2's defect. Correct it by carrying scope on the other two
paths, not by relaxing the parity assertion.

### R2 — Acceptance 3 holds only when the log is opened first

**RUN**, through the built branch jar's action socket, under an isolated `user.home`. The input is a constructed
600-record log whose only foreign id is in the last record, against the committed recovery graph.

| How it was opened | `context.graphPairing.pairingScope` | `pairingSampled` | `verdict` |
|---|---|---|---|
| `open {log, graphml}` in one call | **"scope not recorded"** | **false** | "the graph declares **all 3** node(s) this log writes" |
| graph first, then log | **"scope not recorded"** | **false** | "the graph declares **all 3** node(s) this log writes" |
| log first, then graph | "first 500 of 600 records" | true | "…(judged on the first 500 of 600 records)" |

In two of three orders, a 500-record sample is published as an unlabelled whole-log claim ("all 3 node(s) this log
writes") about a log that writes 4. That is the defect acceptance 3 exists to close. The combined open is also how
`tools/verify-m68-1-coverage.py` opens every scenario, which is why the end-to-end script could not see it.

**Cause (READ, and consistent with the table).** Only `MainFrame.judgeOpenedGraph → pairingAgainst` applies
`withScope`. The log-arrival path publishes the session's verdict (`MainFrame.java:3934`, also `:3144`), which
`session.node.Pairing.recompute` builds as `GraphPairing.of(declared, logged)` with no scope, although that node
already holds `sampled` and `total`.

**The second half of acceptance 3 is not met on any path.** After `coverage` has scanned the whole log and
warned about `foreignAfter500`, `context.graphPairing` is unchanged. Nothing qualifies or replaces the sampled
verdict and says that it did, which acceptance 3 requires. `coverage`'s own `claimNote` does say "judged from the
first 500 of 600 records": the policy knows the pairing was sampled, and the context surface does not.

**Required:**
- scope the session's verdict and discovery's candidates as `pairingAgainst` does;
- have the whole-log comparison qualify the published pairing and say so;
- add an end-to-end scenario for the combined open and for graph-first;
- fix R1 by the same means.

### R3 — The different-build conclusion has moved, not gone (question 6)

The six surfaces the brief names are clean. **RUN**: coverage, the pairing, `context`, the graph-open echo and
the exported PDF, on a kept partial match, all regex-searched for *different build / probably from / version
mismatch / suspect / build mismatch*, with no match. The Topology panel note is also clean (**READ**:
`GraphPairing.note()`). But the conclusion survives elsewhere (**READ**):

| Where | Text today on the branch | Reached |
|---|---|---|
| `MainFrame.java:1564`, single-record finding-report export echo | "none of this record's nodes are in the loaded topology — **the graphml is probably from a different build**" | agent reply for an exported finding |
| `TopologyPanel.java:1645`, step-through status line | "N not in this topology (**different build?**)" | every record stepped with an unknown id |
| `src/main/resources/help/help.html:172`, in-app help | "A topology from a *different build* … **Treat a mismatch as a version problem.**" | Help menu |
| `docs/site/support.md:20` | "If it says it does not, believe it: everything the graph tells you afterwards is about a different build." | published docs |

The first carries the incident's exact phrase on an exported-report path. The second's basis is already right,
since it tests `fullTopology.contains`; only its conclusion is wrong. The help page *instructs* the reader to draw
the conclusion this slice removes. **Required:** the first three. The docs page can follow in the same commit.
The focus-recall messages (`TopologyPanel.java:396`, `:408`) compare a saved focus against a graph rather than a
log. They are optional, noted below.

### R4 — The brief's third mutation is unguarded

The brief names four mutations, the third being "**derive no-ratio from no-membership**". The harness's M3 does
the converse: `membership.established := ratioAvailable`. I ran the brief's version. **RUN**, with a green
baseline, byte-identical restore and fresh reports only:

```
brief-3: ratioAvailable = coverage.denominator() > 0 && !logged.isEmpty()
         → STILL GREEN across 72 tests (the four M68.1 classes plus every Coverage*/GraphPairing*/TopologyMatch* test)
```

So a log with no node output could lose its ratio and be told "nothing eligible to score" while three nodes are
eligible, and no test would notice. The behaviour is currently correct: **RUN**, a log with no node output gives
`ratioAvailable: true, ratio: 0.0, claim: qualified`. It is simply unguarded. **Required:** a test asserting a
present ratio of 0.0 when eligible nodes exist and none logged, and the brief's variant added to
`tools/mutate-m68-1.py`. The report's line "four separate mutations… Done" should then be true as written.

### R5 — Rebase onto current `main` and re-run both gates

**RUN** (`git merge-tree`, no ref touched): the branch conflicts with `main` in `CHANGELOG.md` only, and
`MainFrame.java` auto-merges. `main` has since added 10 tests (1,877 → 1,887 at `43ce82fc`) and three more commits
since, one in `MainFrame.java`. The branch's 1,895 is 1,877 + 18, so **the merged tree has never been tested.**
Re-run the headless suite and the frame job on the rebased tree.

## Optional, or required only to close D-E2 rather than to merge

- **O1 — The on-screen pairing note cannot be read (question 9). RUN.** I captured the Topology panel through the
  jar's `screenshot` verb at the default size, then widened the window to 2400×1300 through System Events. The
  panel is 216 px wide by default and 378 px wide after widening; most added width goes to the records table. At
  both sizes the status line is cut at "… 18 nodes, 18 edges, 11 roots · No oth…". That is before the dispatch
  note, and the pairing note is the **fifth** part of that single line (`TopologyPanel.renderStatus`). The widget is
  a plain `JLabel` with no tooltip, so the clipped text cannot be read at all, and nothing says it was omitted.
  This predates the branch and is not made worse by it. But D-E2 ("where a surface cannot show the whole verdict it
  shows the part it can and names what it omitted") is not met on the human surface, and `support.md` and the help
  page promise the tab states the match "permanently". Put the pairing part first, since its own comment says it
  "qualifies everything below", or give the label the full text as a tooltip. The same screenshots confirm two
  report claims: the authored view is 5 nodes, and the fit zoom is 36%, below the level at which labels are drawn.
- **O2 — Discovery publishes `appliesToOpenLog` without `facts()`.** `GraphmlDiscovery.Candidate.toMap` gives an
  agent `appliesToOpenLog: true` and a verdict, but not `membershipEstablished`, `everyObservedIdDeclared` or scope,
  which `context` and the graph-open echo now carry. Folds naturally into R2.
- **O3 — The session's own audit log records an unjudged pairing as `pairing: applies`** (`session.node.Pairing:78`).
  It is internal, but that log is openable in the analyser, and it states the retention decision as the fit.
- **O4 — `CoveragePolicy` returns one reason.** A kept, unjudged pairing on a non-TRACE log reports only the pairing
  qualification, and the level caveat is not stated. This predates the branch; the new checks sit before the level
  check, so they now hide it more often.
- **O5 — The focus-recall messages** (`TopologyPanel.java:396`, `:408`) still suggest "a different build".
- **O6 — Combined-open drop timing.** In my first probe, `coverage` answered with the graph still present
  immediately after the combined open settled, and two seconds later the graph was gone. So the drop in question
  10 lands asynchronously, after the reply. Worth recording in M68.4.

## Dispositions, question by question

| # | Question | Disposition | Basis |
|---|---|---|---|
| 1 | Reproduce the evidence | **Reproduced, with two exceptions (R1, R4)** | RUN, see *Checks run* |
| 2 | The core fix on the evidence graph | **Confirmed** | RUN |
| 3 | Four changed assertions not weakened | **Confirmed, none weakened** | READ |
| 4 | Other readers of the old contract | **Three found; one required (R2)** | READ + RUN |
| 5 | QUALIFIED or REFUSED for a kept graph with no node output | **QUALIFIED is right** | READ + RUN |
| 6 | Has the build conclusion moved | **Yes, R3** | READ + RUN |
| 7 | Node-scoping and the entry-point test | **Correct; the test catches a rule change** | READ + RUN |
| 8 | PDF notes for every table kind | **Right to widen** | READ + REPORT |
| 9 | The pairing note on screen | **Not readable at any size tried, O1** | RUN |
| 10 | Combined-open defect is pre-existing | **Confirmed** | RUN on both jars |
| 11 | Three ledger entries | **Ticked, with disputes on two** | RUN + READ |
| 12 | Merge recommendation | **Merge after R1–R5** | — |

**2 — the core fix. RUN.** I extracted every vertex's facts from
`evidence/spring-g14-recovery-2026-09-24/MyProcessor.graphml` with a plain XML parser, not the analyser's code.
The graph is unchanged by the branch.

- 18 vertices: 10 node-kind, 6 events, 2 exported services. `fluxtion.authoredNodeCount = 3`.
- The only vertices declared `framework=false, auditCapable=true` are **`checked`, `child`, `rootNode`**.
- `checked`'s class is `com.telamin.fluxtion.runtime.output.SinkPublisher`, which `main`'s prefix list
  (`com.telamin.fluxtion.runtime.`) calls scaffolding. That gives 2, where the graph declares 3.
- `serviceRegistry` is declared `framework=true, auditCapable=true`. On the branch jar it stays out of the
  population (declared 3), raises no out-of-topology warning when it logs, and is listed under
  `frameworkNodesNotScored`. On `main` it lands in `loggedButNotInTopology`, reported as absent from the graph
  that declares it, under the different-build warning. The population is not 3 there either, because `main`'s
  prefix guess drops `checked`. **RUN** through the end-to-end script's scenario 2 on both jars.

3 declared and 3 covered is the honest result, reached through the declaration rather than by widening the
population.

**3 — changed assertions. READ.** Each keeps every substantive check: `REFUSED` and its ordering, `applies`, the
counts and the named ids. Each now also requires a *positive* factual phrase beside the prohibition, so none can
pass on empty or unrelated text. `aLogThatWritesNothingCannotConvictTheGraph` is strengthened: it adds
`evidenced()` and `everyObservedIdDeclared()` assertions.

**4 — readers of the old contract. READ, with RUN where noted.**

- `NodeCoverage.ratio()` has **one** production caller, `CoverageService`, which now checks `denominator()`. The
  report is right.
- The echo's `ratio` field is read by `tools/bench/bundle-client-bench.py:157` as `== 1.0` for "coverage is
  complete". When the ratio is absent that test fails rather than passes, which is the safe direction.
- `applies()` is consumed by the status bar (`MainFrame.java:4189`), which states `reason()` and is honest;
  discovery's ranking and map (O2); the session `Pairing` node (O3); `CoveragePolicy`, which is fixed; and
  `context`, which is fixed, but not on every open path (R2).
- None still reads a zero denominator as full coverage. The retention decision still reaches an agent unqualified
  through discovery (O2), and a sampled verdict reaches `context` unlabelled (R2).
- `NodeCoverage.buildMismatch()` has no production caller, so its name is harmless.

**5 — QUALIFIED is the right claim, not REFUSED.** The policy's own principle is stated at the coverage verb
(`ActionExecutor`, beside the `claim` echo): *"A QUALIFIED number is computable and must carry what it hides —
refusing it would be as much a failure as printing it bare."*

`REFUSED` is kept for numbers that cannot mean what they appear to:

- **An inferred graph.** The declared set *is* what ran, so coverage is 100% by construction.
- **No auditor installed.** Every node reads as never-logged, and the number would blame the nodes for the build.
- **A pairing that does not apply.** The denominator belongs to other nodes.

A kept graph with no node output is none of these. 0 of 3 is a real observation: nothing wrote audit output in
scope. It is also the observation a user most needs when their nodes are silent. What the number cannot tell apart
is "these nodes never logged" from "this graph is not this log's". That is a qualification, and the claim note
states it. **RUN:** `claim: qualified`, ratio 0.0, `membership.established: false` with its note. Refusing would
suppress the one finding the instrument does have, which is the failure the principle names.

One sharpening, optional: the note could say that every eligible node therefore reads as uncovered.

**7 — node-scoping.** **READ:** the archived M45.4 entry says in its own words that `fluxtion.framework` "is
NODE-scoped… Adopt it for nodes and leave the kind filter alone". `Scaffolding.nodeScoped` does exactly that, and
the vocabulary spec's M45.4 section agrees.

**RUN**, answering the report's own question 1: across all **21** committed GraphML files, 12 of which declare the
fact on some vertex, **no EVENT or EXPORT_SERVICE vertex carries it**. So node-scoping discards nothing in any
graph this repository holds. Whether the compiler ever emits it there is not established.

**RUN, the equivalence test:**
- widening the fact to exported services, or to every kind, fails `EntryPointAuthorshipTest` at its crafted
  service's assertion, and three other tests besides;
- the test's first half, over the fixtures' services, is vacuous because none carries the fact. Its crafted
  service is what makes it load-bearing, and that part works.

**8 — widening the PDF notes to every table kind is right. READ.** `ReportsPanel` prints `a.notes()` under every
assembled table with no condition on kind. For read, aggregate and series tables those notes are the 25-record
cap, "truncated: N more buckets than limit" and the no-fields warning. An exported aggregate that silently drops
"truncated" is the same D-E2 defect as the coverage case. Restricting the fix to coverage would have recreated it
for every other kind.

**REPORT, not RUN by me:** that the PDF already printed `rowWhen` and the scalar line before this change.

**10 — pre-existing. RUN** on a `main` jar (`43ce82fc`) and the branch jar, each under an isolated home, with a log
whose ids are half declared:

| | `main` | branch |
|---|---|---|
| `open {log, graphml}` | reply `ok`; then `topology` and `coverage` report no topology loaded | identical |
| log, then graph | graph kept, `applies=false` | identical |

The report's claim holds, including its narrowed trigger: only the combined request drops the graph. See O6 on
timing.

**11 — the ledger.** All three entries are ticked in `unreviewed-changes.md` on this branch.

- **`b4dbc2bd`.** **RUN:** `gh run view` and `gh run download` of run 35929392911. It has three jobs, two
  `spring-provisioning` and one `customer-download`, with no generation job. Both Spring results record
  `generationAttempted: false`, and the build/run/export/stop evidence comes from the analyser bundle with
  `generationKeyProvisioned: false`. So the split is right in both directions, and all three live SG-2 entries
  (`tracker.md:447`, `:486`, `:2187`) say so.
  - **Disputed, minor:** the tidy note at `tracker.md:1094–1098` still says the moved round's ☑ items include "SG-2
    shipped in playground 1.0.74" and that "the tick won on evidence". That is the overturned closure, still
    stated. The moved block in the completed tracker keeps the ☑ line verbatim under a correcting preamble, which
    is acceptable.
  - **READ:** OBL-1 (no file-load progress or cancel; only template download cancels) and OBL-2
    (`ChartPanel.setViewWindow` loops every point) are genuinely open. OBL-3 is cross-repository and not
    inspected, as its own entry says.
  - **Disputed:** the claim that no other archived item carries the pattern. Three tagged items in the completed
    tracker are ◧ or ☐ with no live home anywhere under `docs/specs`: **H8.6** (copy-row-as-YAML ☐), **A10.7**
    (`aggregate` over `nodeLogs` keys, deferred) and **M14.6** (windowed transforms, v2.0). Each may be
    obsolete; each needs a live home or an explicit withdrawal.
- **`aacc1ed4`. READ.** The accuracy constraints check out against the product:
  - repeatable analyses shipped (M38.5) and the stored report call re-issues exactly (M33.7);
  - cross-run delta is not shipped: `RecordDiffDialog` diffs two records within one log, and there is no diff verb;
  - the generated fix brief is open (M33.5);
  - runtime value escaping is open (the exporter escape, mongoose-plugins#39, is not it);
  - ND-2 is ☐ with exactly the cited wording.

  The file is not on the docs site, since it sits outside `docs_dir`, and the sweep is clean. **Disputed, minor:**
  the second hero draft says the analyser "opens it **as a notebook** you can question, rerun and argue with".
  That presents ND-2's notebook as a shipped capability, which the proposal's own constraint forbids. The first
  draft's "work the evidence *like* a notebook" is a simile and fine.
- **`296b5438`. RUN/READ.** Every source location the brief names matches `main` at that commit:
  - `NodeLogging:104` *Ask the graph first*, reading `fluxtion.auditCapable` at `:119`;
  - no `fluxtion.framework` reference in `Scaffolding`;
  - `CoverageService`'s two `removeAll(scope…)` lines;
  - `GraphPairing.declaredNodeIds`;
  - `PAIRING_SAMPLE = 500`;
  - `auditReadiness` passes `fullTopology`;
  - `addSoleExportedService → isScaffolding(node)`.

  Acceptance 8 agrees with the fix's own note that the diagnostic half is not closed. **Disputed, minor:** that
  note (`tracker.md:557–562`) still says "Pending merge/release", but PR #7 merged at 09:07 on 2026-09-24 and
  `c3523506` is in `v1.19.1`.

## Checks run, and their results

Java 21 (OpenJDK 21.0.11), macOS, a real display. The implementation checkout was not used: two throwaway
worktrees at `efc5decd` and `43ce82fc`, and this review's own. **Counts are summed from Surefire XML, not read
from the console.**

| Check | Branch `efc5decd` | `main` `43ce82fc` |
|---|---|---|
| Headless `mvn test` | **1,895 / 0 / 0 / 62 skipped** | **1,887 / 0 / 0 / 62** |
| The 62 skips | the 12 `*FrameTest` classes, all "requires a real frame" | the same 12 |
| Frame tests with a display | **63 run, 1 failure** (R1) | `PairingDuringLoadFrameTest` 9 / 0 |
| The 18 new tests | 12 + 4 + 1 + 1, all green, from fresh reports | — |
| `tools/mutate-m68-1.py` | **5 of 5 RED** (4, 3, 2, 1, 1 tests, exactly as reported); worktree byte-identical afterwards | — |
| My mutations (green baseline, byte-identical restore checked by SHA-256, only this run's reports read) | brief-3 **STILL GREEN** (R4); exported services read the fact → **RED, 4**; every kind reads the fact → **RED, 4** | — |
| `tools/verify-m68-1-coverage.py` against the jar | **22 pass / 0 fail** | **5 pass / 17 fail**; scenario 1 emits "the graphml is probably from a different build", declared 2, `loggedButNotInTopology: ['checked']` |
| Acceptance 3, 600 records, three open orders | R2 table | not run |
| Wording regex over every surface of a kept partial match, including the exported PDF | no match; the PDF carries the warning | not run |
| No node output (question 5) | `qualified`, ratio 0.0 present, membership not established | not run |
| Combined open (question 10) | as `main` | reply ok, then no topology |
| Screenshots, default and widened (question 9) | note not visible | not run |
| Trial merge into `main` | `CHANGELOG.md` conflict only | — |
| CI on the branch | only *Starter evidence static checks*; `ci.yml` never ran | — |

The sweep and `git config user.email` were checked on this review's branch before commit.

## What I did not check

- **The merged tree's suites.** It needs the `CHANGELOG.md` conflict resolved, which is not mine to do (R5).
- **Rendering on the page of `rowWhen` and the scalar line** in the PDF, as against the tab. I read the tab's rule,
  not the page's.
- **The PDF's visual layout.** I searched its raw text for the warning and the figures; I did not render it.
- **A real pre-vocabulary graph end to end.** Like the author, I have only the unit tests for the fallback.
- **Whether the compiler ever emits `fluxtion.framework` on event or service vertices** outside the 21 committed
  graphs.
- **OBL-3**, which is cross-repository, and whether H8.6, A10.7 and M14.6 are obsolete.
- **`main` moving under me.** Three commits landed on `main` during this review, `43ce82fc..1e51545d`, one of them
  in `MainFrame.java`. My `main` baseline is at `43ce82fc`.

## Reproducing R2, the finding the author's harness cannot see

Save beside `tools/`, build the branch jar, and run it with the jar path and the repository root as arguments.
It prints the three rows of the R2 table. Every log is constructed; none replays a session.

```python
import os, sys, tempfile, time, shutil
BR = sys.argv[2]; sys.path.insert(0, os.path.join(BR, "tools"))
from importlib import import_module
Analyser = import_module("verify-m46-agent-api").Analyser
GRAPH = os.path.join(BR, "docs/handoff/evidence/spring-g14-recovery-2026-09-24/MyProcessor.graphml")
def log(path, recs):
    t = 1000
    with open(path, "w") as f:
        for ids in recs:
            f.write(f"---\neventLogRecord: \n    eventTime: {t}\n    logTime: {t+1}\n    groupingId: null\n"
                    "    event: PriceUpdate\n    eventToString: CONSTRUCTED review input\n    thread: constructed\n"
                    "    nodeLogs: \n")
            for i in ids: f.write(f"        - {i}: {{ v: 1}}\n")
            f.write(f"    endTime: {t+2}\n"); t += 10
        f.write("---\n")
def show(a, label):
    for _ in range(30):
        c = a.context()
        if c.get("log") and not (c.get("graphPairing") or {}).get("loading"): break
        time.sleep(0.5)
    time.sleep(1.5); gp = a.context().get("graphPairing") or {}
    print(f"{label}: pairingScope={gp.get('pairingScope')!r} sampled={gp.get('pairingSampled')} verdict={gp.get('verdict')!r}")
home, work = tempfile.mkdtemp(), tempfile.mkdtemp()
try:
    with Analyser(sys.argv[1], home, "a3") as a:
        p = os.path.join(work, "a3.yaml"); ids = ["checked", "child", "rootNode"]
        log(p, [[ids[i % 3]] for i in range(599)] + [["foreignAfter500"]])
        a.act("open", close="all"); a.act("open", log=p, graphml=GRAPH); show(a, "combined open")
        a.act("open", close="all"); a.act("open", graphml=GRAPH); time.sleep(2); a.act("open", log=p); show(a, "graph first")
        a.act("open", close="all"); a.act("open", log=p); time.sleep(3); a.act("open", graphml=GRAPH); show(a, "log first")
finally:
    shutil.rmtree(home, True); shutil.rmtree(work, True)
```

R1 reproduces with
`mvn test -Djava.awt.headless=false -DargLine="-Djava.awt.headless=false" -Dtest=PairingDuringLoadFrameTest`.
