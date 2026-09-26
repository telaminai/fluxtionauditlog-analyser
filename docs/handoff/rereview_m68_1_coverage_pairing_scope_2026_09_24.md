# Re-review — M68.1 after its changes-required round

**Verdict: CHANGES REQUIRED, narrowly.** Every one of R1–R5 is fixed at the cause the earlier review named, not
at its symptom. Every gate the addendum claims reproduces here, from Surefire XML, and the harness does what it
says. Three new things must change before merge:

- **N1.** The whole-log qualification outlives its log under Follow.
- **N2.** A narrower comparison erases a broader one, and the superseded sample is shown again.
- **N3.** The repository wording guard misses text blocks and the assistant's own system prompt, so the
  addendum's "a sixth would fail the build" is not yet true.

N1 and N2 are the same object, attacked from two sides.

**Subject:** `feat/m68-1-coverage-pairing-scope` at **`550f98d8`**. It follows the review at `5c6f865d`.
`main` was **`1247aab4`** throughout; the branch last merged it at `90746e83`.

## Independence — this is not an independent review

**I wrote the earlier review** (`5c6f865d`) that this round answers. I did not take part in the four M68 spec
rounds, the spec, the brief or the implementation. A reader should weigh my agreement with my own earlier findings
accordingly.

The addendum also says where my review was wrong, and I checked those claims against the record rather than
defending the review (question 8). **Both corrections are right.**

**One interest, disclosed again:** I implemented the unmerged Mongoose audit-production branch, which also changes
`CoverageService`. Question 10 tests the two together.

Every result is marked **RUN** (executed, output read), **READ** (source or document inspection only) or
**REPORT** (the addendum's claim, unverified by me).

## Required before merge

### N1 — The qualification outlives its log under Follow (question 3; RUN)

The qualification is bound to the pairing *object* and dropped when that object is replaced. A Follow append
changes the log without replacing the pairing, so the qualification survives it.

**RUN**, branch jar, isolated `user.home`:

1. Open a constructed 600-record log whose ids are all declared, with the recovery graph.
2. Run `coverage`. The qualification reads "whole log (600 records)… all 3 logged id(s) are declared — this
   confirms the sampled pairing for the whole log".
3. Turn Follow on, and append one record that writes an undeclared id, `lateForeign`.
4. After three polls, the store holds **601** records, and `context.graphPairing.qualifiedBy` still says
   `whole log`, `recordsCompared: 600`, `notDeclared: []`, "**this confirms the sampled pairing for the whole
   log**".

The whole log now contains an undeclared id, and the tool says it has confirmed there is none.

The CHANGELOG line "The qualification is dropped as soon as the log or graph changes" is therefore false for the
commonest kind of change a live log has. D-E2 binds a verdict to "the same log revision", and an append is a new
revision.

**Required:** drop, or visibly mark stale, the qualification when the store grows. Comparing the store size to
`recordsCompared` would do. Assert it through Follow, with a witness.

### N2 — A narrower comparison erases a broader one (question 3; RUN, on screen too)

`qualifyPublishedPairing` overwrites whatever qualification is in force with the latest coverage result, whatever
its scope.

**RUN:**

1. Open the 600-record log with its only foreign id in record 600.
2. Run whole-log `coverage`. `qualifiedBy` names `foreignAfter500`, `supersedesSample: true`.
3. Filter to two records and run `coverage` again.
4. `qualifiedBy` is now `scope: current filter`, `recordsCompared: 2`, `notDeclared: []`, and the whole-log
   finding is gone from `context`.

**Screenshots** through the jar's `screenshot` verb, Topology scope, default size:

| State | First readable text on the status line |
|---|---|
| before coverage | "first 500 of 600 records: every node…" |
| after whole-log coverage | "whole log: 1 of 4 logged id(s) not de…" |
| **after a filtered coverage** | **"first 500 of 600 records: every node…"** — the superseded sample, again |

So after an ordinary interactive sequence — look at the whole log, then narrow the view — the panel's lead text
is once more the claim the tool has already proved wrong. That is the defect set 3 was built to remove.
`panelNote` handles a narrower comparison correctly *when it is the only one*; the loss is in the overwrite.

**Required:** a narrower comparison must not replace a wider one for the same published pairing. Keep both, or
keep the wider and append the narrower. Add a test of whole-log-then-filtered coverage, with a witness.

### N3 — The wording guard has holes the addendum's claim does not allow for (question 4; RUN)

The addendum says "a sixth would fail the build". I planted candidates one at a time in `MismatchWording.java`,
each restored byte-identical, and ran `UserVisibleWordingGuardTest`:

| Plant | Guard |
|---|---|
| a Java **text block** containing "the graphml is probably from a different build" | **stays green** |
| `"…is from a different " + "build, which makes…"`, split where neither half matches | **stays green** |
| `"…probably from a different " + "build"`, where the first half still matches | red, correctly |
| a synonym: "probably built from another version of the source" | stays green |

The first two are the ones that matter:

- **Text blocks.** The literal regex excludes newlines, so a text block's content is never read as a string, and
  `stripComments` then treats it as code. `src/main/java` already uses text blocks, in `Main.java` and
  `JavaHighlighter.java`.
- **Split literals.** Wrapping a long message at the line limit is ordinary in this codebase. The original
  incident sentence at `MainFrame.java:1564` was itself split across two literals.

**The guard also does not cover two surfaces an assistant or a person reads.** RUN (`unzip -l` of the branch jar;
`grep` of the files):

- **`src/main/resources/llm/system-prompt.md`** ships in the jar, and is the in-app assistant's own instructions.
  Not scanned. Clean today.
- **`docs/skills/`** holds the canonical skills served to assistants. Not scanned. Clean today.

**Required:**
- read text blocks;
- scan concatenated literals joined, or state that limit in the test's comment;
- add `llm/system-prompt.md` and `docs/skills` to the documents checked;
- and, until then, narrow the addendum's claim.

Synonyms are a property of any phrase list. State it; it is optional to widen.

## Optional

- **O-a, the release-notes exemption, question 4.** The whole of `CHANGELOG.md` ships in the jar as
  `release-notes/CHANGELOG.md` and is shown under **Help ▸ Release notes**. Two historical entries there still
  draw the conclusion:
  - `CHANGELOG.md:1174`, under 1.8.0: "describes a different system or build";
  - `CHANGELOG.md:1501`, under 1.1.0: "treat that as a version mismatch".

  The guard reads neither file: `docs/site/release-notes.md` is exempt, and `CHANGELOG.md` is not scanned.
  Leaving dated history as written is defensible. But the exemption should cover only **released** sections, so
  that a new `[Unreleased]` entry drawing the conclusion fails.
- **O-b, the error-counts-as-red rule in the harness, question 5.** It counts `<error>` as a failure at the named
  test. A mutation that makes the named test *crash*, rather than fail its assertion, is reported as guarded. It
  also does not re-run green after each restore. The SHA-256 check covers the file, not a flaky frame test that
  timed out under a mutated run.
- **O-c, the sample rule has three loops.** One constant, `PAIRING_SAMPLE`, now feeds three loops that each
  collect the sample:
  - `pairingAgainst`;
  - `discoverGraphs0`;
  - the log-opened event behind the session node.

  The parity test uses a 1-record log, so it never compares a *sampled* verdict across the three. Consider one
  method, or a sampled parity case.
- **O-d, merging instead of rebasing, question 7.** Defensible, since `main` already carries ten first-parent
  merge commits, including today's PR #7. A third option satisfies rule 3's "never force-push" *and* its "no merge
  bubbles": rebase onto a **new** branch name and leave the old one as history.
- **O-e, the new-project offer's two-argument `GraphmlDiscovery.scan(roots, Set.of())`,** which is correctly
  scope-less, since there is no log. READ only. No verdict is shown there, as far as I could see.

## Dispositions, question by question

| # | Question | Disposition | Basis |
|---|---|---|---|
| 1 | Reproduce the gates | **All reproduce** | RUN |
| 2 | R1–R5 at their causes | **All five at the cause; the R1 parity rewrite is a strengthening** | RUN + READ |
| 3 | Attack the qualification | **Two defects, N1 and N2**; five other attacks held | RUN |
| 4 | The wording guard | **Holes, N3, and one exemption to narrow, O-a** | RUN |
| 5 | The harness | **Does what it claims; two optional weaknesses, O-b** | RUN + READ |
| 6 | The panel note on screen | **The claim holds in both states**; a third state breaks it (N2) | RUN |
| 7 | The two deviations | **Both acceptable**, O-d | READ |
| 8 | What my review got wrong | **Both corrections are right** | READ |
| 9 | CHANGELOG lines | **All accurate but one** (N1) | RUN + READ |
| 10 | Overlap | **No conflict, textual or in tested meaning** | RUN |
| 11 | Not checked | below | — |
| 12 | Merge | **After N1–N3** | — |

**1 — the gates. RUN.** Counts are summed from Surefire XML in a fresh worktree at `550f98d8`, macOS, OpenJDK
21.0.11, with a real display.

| Gate | Addendum | Mine |
|---|---|---|
| headless `mvn test` | 1,927 / 0 / 0 / 62 | **1,927 / 0 / 0 / 62** |
| twelve `*FrameTest` classes, display | 63 / 0 / 0, 1 skipped | **63 / 0 / 0 / 0 skipped**. The focus-dependent test the addendum saw skip *ran and passed* here, so it is machine-dependent, as the addendum said |
| `tools/mutate-m68-1.py --frame` | baseline 52; 21 of 21 RED | **baseline 52 green; 21 of 21 RED at the named test; every restore byte-identical; worktree clean afterwards** |
| `verify-m68-1-coverage.py`, branch jar | 46 / 0 | **46 pass / 0 fail** |
| same, `main` jar | 34 failures at `90746e83` | **12 pass / 34 fail at `1247aab4`** |
| my earlier R2 probe, branch jar | first 500 of 600, all orders | **first 500 of 600 records, `sampled=True`, in all three orders.** On `main`: unscoped for combined and graph-first, as before |

One error of mine to record: my first headless sum read 1,858. My time window truncated the run's end to the whole
second and dropped the reports written in its last second. Summing every report in the fresh worktree gives the
1,927 above. The console agrees.

**2 — R1 to R5, each at its cause. READ, with RUN where marked.**

- **R1.** The three equality assertions — frame against discovery, session against discovery, and `matched == 3`
  — are **unchanged**. Reading discovery through a source root and `discoverGraphs0` is a **strengthening**. The
  old hand-built `GraphmlDiscovery.scan(ids)` call could only agree with an unscoped verdict, so it tested the
  fixture's arguments, not what an agent is shown. It also adds an explicit scope assertion on discovery. The one
  limit is O-c. RUN: M6f and M7f each go red at the parity test.
- **R2a.** `session.node.Pairing.recompute` now builds `GraphPairing.of(…).withScope(sampled, total)`. That is
  exactly the cause the earlier review read.
- **R2b.** The product's discovery call passes the sampled count and the total.
- **R2c.** N1 and N2 are the remaining defects.
- **R3.** One `MismatchWording` class serves the four code sites, and the docs are changed. The guard is N3.
- **R4.** `M3b` in the harness is exactly the mutation the earlier review planted, and it goes red at
  `noOutputKeepsARatioOfZero`. RUN.
- **R5.** The branch merges cleanly into current `main` (`git merge-tree`: no conflict). RUN.

**3 — attacking the qualification. RUN.** The same probe as N1 and N2. Held:

- reopening the same log drops the qualification;
- closing the graph drops it, and reopening the graph does not bring it back;
- switching to a clean log drops it;
- reopening the graph drops it;
- a clean 600-record log gets a *confirms* note;
- a 3-record log, which is not sampled, gets no supersede or confirm claim, only the finding.

It never claimed to supersede when it should not. It did claim to *confirm* when it should not (N1).

**5 — the harness. RUN + READ.**

- It stops on a non-green baseline (`sys.exit(2)`), and also when a named test did not run in the baseline.
- It reports NOT RUN when the build did not compile or wrote no reports.
- It deletes old reports **and** filters by start time.
- It checks each restore by SHA-256 and aborts on a mismatch.
- It passes a mutation only when its named test is among the failing ones.
- I could not make it report a false pass. The routes to a false *red* are O-b.

**6 — the panel note on screen. RUN,** screenshots through the jar's verb. See the N2 table. The claim holds in
both states it names.

**7 — the deviations. READ.**
- **Merging rather than rebasing:** see O-d.
- **P16's design change:** a whole-log confirmation also leads the note. It is sound: once the whole log is
  known, a clipped line should show the broader fact, and the sample adds nothing. The prediction was the weaker
  design, and it was recorded as changed rather than absorbed. That is the evidence discipline working.

**8 — what my review got wrong. READ, against the old sources.**
- **`topology.md:101`.** At `efc5decd` it said "**Treat the warning as a version mismatch**". My search pattern
  had no "version mismatch" term, and I missed it.
- **The old harness.** It did delete old reports before each run (`os.remove`, line 41), so it could not print a
  stale result. My description was wrong. Its real defects were the ones the addendum names: no reports read as
  STILL GREEN, and with no baseline a pre-existing failure would read as RED.

**9 — the CHANGELOG lines. RUN + READ.** Each new line matches the behaviour I saw or read — the declared
authorship, the mismatch wording, no ratio, the pairing facts, the PDF notes, the three open orders, the lead text,
the tooltip (READ: `TopologyStatusTooltipTest`) and the capture-level caveat (RUN: an INFO-level partial match
carries it) — **except** "The qualification is dropped as soon as the log or graph changes" (N1).

**10 — overlap. RUN.**
- **M68.2 verification commit `a27b4d13`:** it adds one investigation document about profile resolution, and is
  already merged into the branch. No overlap.
- **Current `main`:** four commits since the branch's merge, about charts and a proposal. The trial merge is
  clean.
- **The Mongoose audit-production branch** (`fc9b1f9c`): the trial merge is **textually clean**. Both branches'
  `CoverageService` changes auto-merge. I built the combined tree as a local, unpushed commit and ran its suite:
  **2,027 / 0 / 0 / 62**, exactly this branch's 1,927 plus that branch's 100 added tests. So neither side breaks
  the other's assertions.
- **In meaning (READ):** the Mongoose branch's level annotations apply only to `uncovered` nodes, and this
  branch's framework nodes never enter `uncovered`. With no ratio there are no uncovered nodes, so no annotations.
  The Mongoose branch's reload after a failed live read replaces the pairing, which drops this branch's
  qualification — consistent with it.

## What I could not check

- **CI's xvfb job.** `ci.yml` still runs only on `main` and pull requests, so no CI run exists for this branch.
  The frame suite passed here under a real macOS display, where the focus-dependent test ran. Under xvfb it may
  skip, or behave differently.
- **The tooltip by hovering.** I read the test that asserts it; I did not hover.
- **A pre-vocabulary graph end to end.**
- **Every CHANGELOG line against a rendering of the in-app notes.** I read the file the jar ships.
- **Whether the combined M68.1 + Mongoose tree passes its frame suite.** I ran only its headless suite.

## Reproduction

All probes use the repository's `tools/verify-m46-agent-api.py` harness against a built jar under an isolated
`user.home`. Every log is constructed; none replays a session.

- **N1:** open a 600-record all-declared log with the graph, run `coverage`, then `open {follow: true}`. Append
  one record writing an undeclared id, wait three seconds, and read `context.graphPairing.qualifiedBy`.
- **N2:** open the 600-record log with a foreign id in record 600, with the graph, and run `coverage`. Then run
  `filter {from: 1001, to: 1011}` and `coverage {filtered: true}`, read `qualifiedBy`, and take
  `screenshot {scope: "topology"}`.
- **N3:** add `public static final String X = """\n the graphml is probably from a different build\n """;` to any
  class under `src/main/java`, then run `mvn test -Dtest=UserVisibleWordingGuardTest`. It stays green.
