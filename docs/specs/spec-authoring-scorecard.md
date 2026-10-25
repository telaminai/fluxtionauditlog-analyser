# M82 — The authoring scorecard: a stranger's own processor, from zero

**Status:** DRAFT r1, 2026-10-25. No implementation, no scored run. Owner decisions OD-82.1–.10 are open (§9).
**Milestone:** M82 (verified free: M81 is the highest in use, `grep -rn 'M82\b' docs src tools` returns nothing).
**Tracker:** [tracker.md](tracker.md) ▸ M82. **Extends:** M79's scorecard ([`spec-first-minute.md`](spec-first-minute.md)
§12) with the Build door's tasks. **Measures:** M76 ([`spec-authoring-journey.md`](spec-authoring-journey.md)), M80
([`spec-design-definitions.md`](spec-design-definitions.md)), the [project starter journey](spec-project-starter-journey.md)
and the [Spring authoring edit loop](spec-spring-authoring-edit-loop.md). **Reuses:** the M74 runner (`tools/m74_onboarding_runner.py`), its
[scorecard](../proposals/beta-testing/m74-onboarding/SCORECARD.md) and
[protocol](../proposals/beta-testing/m74-onboarding/PROTOCOL.md); the authoring-ladder rig (PRs #129, #131, #140).

**Why a separate spec, not rows in M79 §12** (OD-82.1). M79 is ACCEPTED and every §12 row gates an M79 phase. These
tasks gate phases of four other specs (§8), need their own task text, oracles and isolation, and will change as M76
is built; as rows they would put draft content under an accepted status. M79 §12 and M76 link here.

## 1. Observed: what is measured today, and what is not

- **The owner's focus** (2026-10-25): a stranger authors their own processor from zero, without the owner.
- **Field evidence** (M79 §1, OD-79.6, owner-reported, not in the repo as a record): the paying customer's developers
  reached zero-to-hero without the owner; their teacher was their own Claude plus the analyser MCP, verbs and project
  context. The lead engineer's claim that a new hire is *"trained in a couple of hours"* is the owner's report; no
  measurement of it exists.
- **M79 §12 has no authoring task.** SC-T9, SC-C1/C2 measure understanding of a shipped run, SC-T1/T10 setup,
  SC-T11/T12 teaching. None starts at the Build door or ends at the person's own processor.
- **The authoring ladder is prestaged and robot-only.** Every rung's task starts inside an already-unpacked starter
  project (`r2/TASK-R2.txt`, `r6/TASK-R6.txt`), and the rig is *"the prestaged G14 rig, so it is never an
  acceptance"* (PR #131). On the released stack (PR #140), Haiku 5.5 passed 14 of 15 runs (rungs 2–6, n = 3 each),
  PASS = ORACLE (a behavioural check) + VALID (`./validate.sh`) + CHART + S6′ (no key leaked). It measures a model
  extending a project, not a person starting from nothing.
- **A model's claim is not evidence of completion.** Ladder run `lad-r3-s5-1` failed ORACLE (*"the counter running on
  every trade"*) while *"its final answer claimed success"* (`authoring-ladder-2026-10-12/RESULTS.md`).
- **M74's T4 authoring track** (PROTOCOL §5.4) starts from a template download and scores `node-logged-once` on the
  added node plus a pointer. It needs M74 P3, and no T4 run is recorded under `docs/specs/evidence/`.
- **There are no keyless runs** (owner, M79 §16): generating a processor needs a Fluxtion key. The teaching-scoped
  hosted key is a follow-on that does not exist yet (M79 §14).
- **M79's scoring rule:** comprehension is scored only by human or stranger runs; the robot walk counts acts only
  (§12, *Who scores what*).

## 2. Proposed: the tasks

No `SC-B` id exists in the tree. *Acts*, *hesitation* and *dead end* keep M79 §12's meanings, with M74's dead-end
types (SCORECARD §4) plus §6's where the runner scores.

| # | Task | Scored as | Scored by |
|---|---|---|---|
| **SC-B1** | **Author from zero.** From an entry point (§4) to the person's **own** processor generated, compiled and run on the sample input with correct output, then explained and changed | the funnel F1–F5 below, with acts, minutes and dead ends to each stage reached | robot: acts, minutes, F1–F3; human/stranger: all |
| **SC-B2** | **Explain your own run.** *"Show me the record that proves {behaviour} of your graph"*, answered by pointing at a record of the person's own run (the authoring analogue of SC-T9) | the `pointer` predicate, restricted to the person's run (§3.1) | robot: acts to the pointer; human/stranger: whether the person's explanation is right |
| **SC-B3** | **Change and re-verify.** One requirement change (a node, an edge or a condition), regenerated, then shown by evidence that **only** the intended behaviour changed | before/after on the same input (§3.2) | robot: acts, minutes, the oracle; human/stranger: the person's account of the difference |
| **SC-B4** | **Claims correctness.** Every statement about dispatch the person, or their AI client, makes in the transcript, graded against the Spring XML contract and M79 §11.7's four teaching rules | correct / wrong / unsupported per claim, **attributed to its speaker** (§3.3) | human/stranger runs only, two graders |
| **SC-B5** | **First-key friction.** Acts and minutes from the first point the run needs a key to the first successful generation with it | acts, minutes, dead ends at the key step | human/stranger runs that start with no key only |
| **SC-B6** | **Field run** (optional, consent-based). The paying customer's next new hire does SC-B1–B3 on the fixed task, timed against the *"couple of hours"* claim | minutes to F3 and F5, acts, dead ends; only counts leave the customer | the customer's lead engineer (OD-82.7) |

**The SC-B1 funnel.** Each stage requires every earlier one; a run is reported at the last stage it reached.

| Stage | Holds when (facts, never a claim) |
|---|---|
| **F1 Generated** | a generated processor and its GraphML exist in the person's project, **were absent from the template's tree digest**, and carry the generator's stamp |
| **F2 Compiles** | an offline rebuild of the final project by the scorer exits 0 (the ladder's VALID route) |
| **F3 Runs correctly** | the sealed behavioural oracle passes on output from a fresh process over the sample input (the ladder's ORACLE route); in the free arm, the output matches the person's expectation, written and sealed before the run (§5) |
| **F4 First record explained** | SC-B2 holds |
| **F5 Second change re-verified** | SC-B3 holds |

## 3. Proposed: the scoring rules

### 3.1 The `pointer` predicate, on the person's own run

M74's `pointer` predicate holds when a resolved pointer (a selection, flag or spotlight target, read from the
analyser's state) matches the oracle. SC-B2 adds two conditions:

1. **The record belongs to the person's run:** the run's GraphML pairs with the project built in this run (its
   fingerprint, or F1's tree digest until M76's run header exists, OD-82.9), not with the template's shipped sample,
   the DEMO set or a diamond.
2. **The pointer is the answer, not a decoration of a source read:** a pointer set only after the subject read the
   generated dispatch code (the ladder's `measure_reads` pattern over the client transcript) scores
   `SOURCE_ANSWERED`, not held. Reading the person's own node classes is allowed.

The oracle (the record's resolved keys, and the nodes that must and must not appear) is measured on a throwaway build
of the fixed task's reference solution and sealed before the cycle (PROTOCOL §3). The free arm's SC-B2 is graded by a
human.

### 3.2 Before and after

SC-B3 runs the **same** sample input on the before and after builds. It holds when the sealed *intended* check flips
**and** every other sealed check is unchanged; otherwise *changed more than intended*. Until M76 A4b ships, the scorer
compares the two oracle outputs; after it, the scorer reads the acceptance view's verdicts.

### 3.3 Claims and their speaker

Claims are read from the transcript (captured by `capture-conversations` under an isolated home, or sanitised under
rule 1's recorded Claude Code exception). Each claim about dispatch, membership, propagation or silence is graded
against a **rubric sealed before the cycle**: the contract's three relationships (*constructor-arg ref* is topology;
services are never constructor-wired; `eventHandlers` states an expectation, not routing) and §11.7's rules 1–4. A
regular-expression pass (the runner's `claims.forbid`) proposes; two graders decide; disagreements are reported, not
resolved by a third model.

**Speaker attribution is the point.** Every claim is scored `person` or `client`. A correct statement that appears
only in client turns gives the person nothing; a person who repeats it in their own words after the client said it
scores `person, prompted`. A run whose only correct answers came from the client scores *person: 0 correct*,
whatever the client scored.

### 3.4 What the robot walk may score

Acts, minutes, dead ends, F1–F3 and the SC-B2 pointer's acts; **never** SC-B4 or an explanation's correctness
(M79's rule). Robot runs start with the key readable, as every ladder run did, so they never score SC-B5.

## 4. Proposed: entry points and isolation

| Entry | Starts at | Ends the setup when |
|---|---|---|
| **E1 Build door** | first launch of the pinned analyser jar, the first minute played or skipped, the Build door's *Start from a template* (M79 §5.3; the M47 starter chooser) | a project is open in the analyser |
| **E2 Playground** | the public playground's start page in a fresh browser profile, the person's own Spring XML (skill.md's design conversation) | the downloaded project is unpacked |

E1 and E2 are scored separately and never pooled.

**Isolation, per run** (PROTOCOL §2.1): a fresh base under `/private/tmp`, never reused; `-Duser.home` and `HOME`
inside it; an empty project directory; a fresh client (empty `CLAUDE_CONFIG_DIR`, the trial token, L1,
`--strict-mcp-config`); a per-run Maven repository from the provenance-checked seed. Human runs use a fresh OS account
or the same base. The person's **own** client is OD-82.4.

## 5. Proposed: the fixed task texts

**Fixed arm (scored by oracle).** Domain-neutral, one conditional, so M79 §11.7 rule 3 (the returned boolean) is exercised.
Given to the person verbatim:

> *Build an event processor of your own. Orders arrive, each with a symbol and a quantity. Keep a running total of
> quantity per symbol. When a symbol's total first reaches 1,000, publish one alert line naming the symbol; never
> alert twice for the same symbol. Orders with quantity 0 change nothing. Run it on the sample orders below, then show
> me, in the analyser, the record that proves the alert was not published twice.*
> Sample: `A,600` · `B,200` · `A,500` · `A,0` · `A,300` · `B,900` · `A,700`

Reference outcome: alerts for `A` (at the third order) and `B` (at the sixth), none at the fifth or seventh. The
proof the probe asks for is an absence, so it holds only in a traced record (M79 §11.7 rule 4).

**Change (SC-B3):** *"Also alert a second time when a symbol's total reaches 2,000."* The intended flip: one new
alert, for `A` at the seventh order; every other check unchanged.

**Free arm (funnel only).** *"Build an event processor for something from your own work."* The person writes down, before
the first run, what the output should be for their own sample input; the observer seals it. F3 is that expectation;
SC-B2 and SC-B4 are graded by people.

**SC-B2 probe:** asked after F3, verbatim: *"Show me the record that proves {behaviour}."* For the fixed task,
{behaviour} is *the alert was not published twice*.

## 6. Proposed: authoring dead ends

Added to M74's types (SCORECARD §4), each detected from facts:

| Type | Means |
|---|---|
| `BUILD_NEVER_GREEN` | a build or generation failure the run never got past |
| `REFUSED_TWICE` | the same validation, reconcile or generation refusal twice in a row |
| `KEY_BLOCKED` | the run stopped, or the person asked for help, at the key step |
| `SOURCE_ANSWERED` | SC-B2 answered from generated dispatch code (§3.1) |
| `ASKED_A_HUMAN` | a human run's request to the observer for help, logged with its time |

## 7. Proposed: samples, baselines, targets, recording

**Samples, and what they can detect.**

| Who | n per cell | Detects | Cannot detect |
|---|---|---|---|
| Robot (Haiku 5.5, the ladder's held model) | 3 per entry point per release candidate that touches authoring | a collapse (≤ 1/3 at F3); a large act or cost jump | anything about people; a rate between 1/3 and 1 |
| Human/stranger | 5 per entry point per cycle | a large failure rate | small differences: 5/5 is consistent with a true failure rate up to 45%, and 4/5 with a pass rate as low as 34% (one-sided 95%); a median over 5 runs registers only about a doubling |
| Field (SC-B6) | 1 per new hire, as they occur | whether the claim is wildly wrong | anything general: n ≈ 1, one customer |

As in M79 §12, five runs are a smoke gate, not a measurement, and are always reported with their counts.

**Baseline 2026-10-25 (pending).** To be filled from the baseline trial now running, from its `score.json` files,
never typed. Empty cells stay empty; a target without a baseline is a target, not an improvement claim.

| Task | Entry | Who | n | Reached F1 / F2 / F3 / F4 / F5 | Median acts to F3 | Median minutes to F3 | Dead ends by type | Source |
|---|---|---|---|---|---|---|---|---|
| SC-B1 | E1 | robot | | | | | | pending |
| SC-B1 | E2 | robot | | | | | | pending |
| SC-B1 | E1 | human | | | | | | pending |
| SC-B2 | E1 | robot / human | | pointer held: | | | | pending |
| SC-B3 | E1 | robot / human | | only intended: | | | | pending |
| SC-B4 | E1 | human | | person correct / wrong / unsupported: | | | | pending |
| SC-B5 | E1 | human | | | acts at key: | minutes at key: | | pending |

**Targets** are proposed in OD-82.2, to be set only after the baseline is read.

**Recording.** Each cycle under `docs/specs/evidence/m82-<cycle>/` (the baseline: `m82-baseline-2026-10-25/`):
`predictions.md` with the sealed oracle and rubric digests first; one folder per run in the M74 layout (`config.json`,
`operator.jsonl`, `snapshots.jsonl`, `meta.json`, `score.json`) plus `funnel.json` and, for human runs, the graders'
sheets; a generated `RESULTS.md`. Transcripts and session records are never committed raw (SCORECARD §1, R6); what is
kept is sanitised, read and swept (rule 1). SC-B6 commits counts and minutes only.

## 8. Proposed: what each task gates

| Task | Gates (a regression here blocks the phase's acceptance) |
|---|---|
| SC-B1 | M79-1a-i's Build door (M79 §5.3); the starter journey's download and project landing (M47, M19.23); M76 A5 (scenarios), A7 (S7, the authoring strip's *runnable application* end condition); M80 PR2/PR3 (the design shown for a Java or multi-file project); the edit loop's A (compile the edited model) and G (the downloaded project teaches the path) |
| SC-B2 | M76 A6 (the explanation tour); M79-6's `teach-fluxtion` skill (A6.4); M69/M80 source spotlights |
| SC-B3 | M76 A1, A4a/A4b (criteria, comparison), A5 (scenario pairing); the edit loop's purpose (*make the second edit routine*); the starter's reconciliation and `withdraw` |
| SC-B4 | M79-6 (a) the bridge instructions paragraph and (b) the skill; M79-2's teaching conversation; M79 §11.7 |
| SC-B5 | M79's key decisions (M79 §16) and the teaching-scoped key follow-on (M79 §14); M76 §3.1's key line (A7) |
| SC-B6 | none; it validates the whole path in the field |

## 9. Owner decisions (open)

| # | Question | Recommendation |
|---|---|---|
| **OD-82.1** | A separate spec (M82), or rows in M79 §12? | Separate, linked from M79 §12 and M76 (the reasons in the header) |
| **OD-82.2** | Targets, after the baseline | Proposed for discussion only: SC-B1 human E1 F3 in ≥ 4 of 5 runs, median ≤ 120 minutes (the field claim's *couple of hours*); robot F3 3/3; SC-B2 pointer in ≥ 4 of 5; SC-B3 *only intended* in ≥ 4 of 5; SC-B4 zero wrong person claims on rules 1 and 3. SC-B5 measured, not targeted, until the teaching key exists |
| **OD-82.3** | Fixed arm, free arm, or both? | Both; only the fixed arm is oracle-scored and compared across cycles |
| **OD-82.4** | Stranger runs with a virgin client, or the person's own? | Both arms, never pooled: virgin is reproducible, own is the field condition |
| **OD-82.5** | Which key route SC-B5 measures while no teaching key exists | The route a stranger has today, recorded per run; never an operator-supplied key in an SC-B5-scored run |
| **OD-82.6** | Warm or cold Maven for human runs | Warm (the ladder's default), with one cold run per cycle reported separately |
| **OD-82.7** | SC-B6: consent, what is committed, who observes | Written consent; counts and minutes only (rule 1); the lead engineer times, the owner's protocol |
| **OD-82.8** | SC-B4 grading | Two graders on a sealed rubric; regex only proposes |
| **OD-82.9** | Tooling home, and run identity before M76's run header | Extend the M74 runner with a track B (`TRACKS`), not a new script; F1's tree digest as identity until A4a |
| **OD-82.10** | Cadence | Robot on every release candidate that touches authoring; humans per cycle; SC-B6 as hires occur |

## 10. Acceptance tests for the scoring tooling (rule 8: ★ marks a wrong-result witness)

Headless, over recorded fixture runs under `tools/fixtures/m82-authoring/`, in the M74 runner's test file pattern.

| # | Test | Catches | Witness (the mutation that must turn it red) |
|---|---|---|---|
| ★ S82.1 | a run whose rebuild never exits 0 reports F1 at most, even with a correct-looking output file present | a stale or template-shipped output scored *runs correctly* | score F3 from output alone, without F2 |
| ★ S82.2 | `lad-r3-s5-1`'s shape: output fails the oracle, the final answer says it works → F2, not F3 | a model's claim taken as completion | read F3 from the answer text |
| ★ S82.3 | a processor present in the template's tree digest does not satisfy F1 | a prestaged project counted as authored | drop the template-digest check |
| ★ S82.4 | a pointer at the DEMO, diamond or template-sample record does not hold SC-B2 | another run's record accepted as the person's | match on `event` and `logTime` without the run identity |
| ★ S82.5 | a correct answer from a generated-dispatch read, with no pointer or a pointer set only after the read, scores `SOURCE_ANSWERED` | explaining by reading generated source | count the answer's record index as a pointer |
| ★ S82.6 | an after build that adds the second alert and also changes a sealed unrelated check scores *changed more than intended* | an over-broad change passing SC-B3 | evaluate the intended check only |
| ★ S82.7 | a transcript whose only correct claim is in client turns scores *person: 0 correct* | the client's knowledge credited to the person | pool speakers |
| ★ S82.8 | a run that starts with a key readable reports SC-B5 *not measured*, not 0 acts | a prestaged key read as no friction | report the act count regardless |
| ★ S82.9 | a missing capture or a changed oracle or rubric digest is VOID or a refusal, never PASS or FAIL | a run with no evidence scored | default absent files to empty |
| S82.10 | every committed `m82-*` folder passes the rule 1 sweep and contains no raw transcript | a leaked name or transcript | — |

## Revision history

- **r1, 2026-10-25.** First draft, from the owner's focus on authoring from zero.
