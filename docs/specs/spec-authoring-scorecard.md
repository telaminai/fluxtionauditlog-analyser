# M82 — The authoring scorecard: a stranger's own processor, from zero

**Status:** DRAFT r3, 2026-10-26. No implementation. Three robot-stranger trials were run (baseline, r1, r2) and graded by hand, not by this spec's tooling (§7). Owner decisions OD-82.1–.10 are open (§9). **Handover for another LLM:** [`evidence/m82-HANDOVER.md`](evidence/m82-HANDOVER.md).
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

**The SC-B1 funnel.** Each stage requires every earlier one; a run is reported at the last stage it reached. F4a and F5a
are also reported as their own columns, so a run is never hidden behind a later stage it failed.

| Stage | Holds when (facts, never a claim) |
|---|---|
| **F1 Generated (strict)** | a generated processor and its GraphML exist in the person's project, **were absent from the template's tree digest**, carry the generator's stamp, and **the generated processor's sha256 differs from the template's shipped one** (B/C) or exists at all (A). A project that only fills in the template's method bodies and keeps its shipped generated processor is **not** F1 (r1 scored 0/5 on this; r2 scored 5/5 in each B and C cell) |
| **F2 Compiles** | an offline rebuild of the final project by the scorer exits 0 (the ladder's VALID route) |
| **F3 Runs correctly** | the sealed behavioural oracle passes on output from a fresh process over the sample input (the ladder's ORACLE route); in the free arm, the output matches the person's expectation, written and sealed before the run (§5) |
| **F4a MCP reached** *(arms with an analyser)* | the run's MCP call log (never the answer text) shows at least one analyser tool call that succeeded. Required for F4 where the arm has an analyser; reported on its own, because *the person was never routed to the analyser* and *the person was routed and failed* have different owners |
| **F4 First record explained** | SC-B2 holds |
| **F5 Second change re-verified** | SC-B3 holds, **scored against a before-state the scorer builds** (§3.2) |
| **F5a Flag persisted** *(arms with an analyser)* | at the end of the run the analyser's state holds a flag on the record that proves the second alert (read from the analyser, not from the transcript). A pointer by spotlight or goto alone leaves no evidence behind, so it does not hold F5a |

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
**and** every other sealed check is unchanged; otherwise *changed more than intended*. **The before-state is built by
the scorer** from the sealed reference solution on the same input (r3 rule), never taken from the run's own earlier
output: a run whose own before-state was already wrong (an alert twice) would otherwise compare an after-state with
itself and pass. The run's own before-output is still scored, as F3, and a mismatch between it and the scorer's
before-state is reported as `BEFORE_DIVERGED`. Until M76 A4b ships, the scorer
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

**No-cue variant (r3, routing).** The fixed task says *"in the analyser"* and, for B and C, that the analyser is
connected; both name the tool, so a pass measures prompt-following. To measure **routing**, i.e. whether a stranger
reaches the analyser unprompted from the starter's own instructions, the variant (`N`) is given to arms B and C
(analyser running and registered, exactly as before; the person simply does not say so). Exact text, to be sealed
(sha256 of the file recorded in `predictions.md` before the first run):

> *I've downloaded the Fluxtion starter from fluxtion-playground.dev into the current directory. I'm a Java
> developer. I've heard of Fluxtion but never used it. Starting from https://fluxtion-playground.dev, do the
> following in the current directory.*
>
> *Build an event processor of your own. Orders arrive, each with a symbol and a quantity. Keep a running total of
> quantity per symbol. When a symbol's total first reaches 1,000, publish one alert line naming the symbol; never
> alert twice for the same symbol. Orders with quantity 0 change nothing. Run it on the sample orders below, then
> show me the record from your run that proves the alert was not published twice.*
> Sample: `A,600` · `B,200` · `A,500` · `A,0` · `A,300` · `B,900` · `A,700`
>
> *Then make this change: "Also alert a second time when a symbol's total reaches 2,000." Run it again on the same
> sample orders and show me that only the intended behaviour changed. Then show me which record proves that alert
> fired.*

**Sealed-oracle approach for N.** The sample and the change are unchanged, so r2's oracle (checks K1–K5, pointers P1
and P2, the strict F1 rule and the dispatch rubric) is reused **unchanged**; the variant seals only its own task
text digest and a **routing rubric**, before the first run: *routed* = F4a holds (an analyser tool call succeeded)
with no instruction to use it in the task; *routed away* = the proving record is quoted from the run's output or
YAML in the answer with no analyser call (SC-B2 does **not** hold: the pointer must be lit in the analyser on the
run's own export, §3.1). Report N's F4a and F4 rates **next to** the cued arms' and never pool them. A variant run
whose task text shows the words *analyser* or *MCP* is invalid (`CUE_LEAK`, treated as VOID).

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

**VOID runs (r3 rule).** A run stopped by something that is neither the subject nor the product is **VOID**, not a
failure at F0: an account rate limit (a 429 with no subject act after it), a rig fault (the rig's own port collision
between concurrent runs, a sandbox error before the first subject act), or a lost capture. VOID is decided from facts
**before** the run is scored and never from its outcome. A VOID run is excluded from every denominator and listed
separately with its cost. **Redo rule:** each VOID run is redone once, in the same arm, model and task text, after the
cause is removed (quota reset; runs serialised or ports separated), and the redo takes the slot's place; the report
states that redone runs ran later than the rest. A slot voided twice is reported as *unfilled*, not retried
indefinitely. A run that failed for a reason inside the product (an exception from the library, a refusal) is a
failure and may never be voided.

**Baseline 2026-10-25 (observed, robot stranger, free arm).** One trial, run before this spec's tooling exists, so
nothing here comes from a `score.json`. The experimenter graded every endpoint by hand.

- **Arm.** Free arm with the trial's own task, not §5's fixed text: a running average price per symbol, an alert when
  a trade is more than 2% from it, run on samples, show evidence, explain one event. **Entry: E2**, starting from the
  playground's public URL. No analyser and no MCP were present, so E1 (the Build door) was not measured. Cold Maven
  repository per run, isolated sandbox, strict MCP config, 40-minute cap. The CLI login used the real home, with
  skills, projects, plugins, history and settings denied, so this is **not** §4's isolation.
- **Rows.** Sonnet 5.5 ×5 with a key is the primary row. Haiku 5.5 ×1 with a key and Sonnet ×1 with no key are
  observation rows (n = 1 each).
- **What counts as scored** (§3.4). The trial's stranger was a model, so only acts, minutes and F1–F3 are scored
  here. Everything else is observation. With n = 5, 1/5 at F3 means only that the path usually fails today.

How the trial's endpoints map onto the funnel, mismatches stated:

| Trial endpoint | Spec | Mismatch |
|---|---|---|
| E1 a buildable project exists | ≈ F1 | no template-digest check, no generator-stamp check |
| E2 it compiles | ≈ F2 | the run's own build, not the scorer's offline rebuild |
| E3 runs correctly | ≈ F3 | graded by the experimenter, not a sealed oracle or a sealed expectation |
| E4 explains one record | ≈ F4 / SC-B2, partial | no analyser, so no pointer predicate; an output line counted as partial |
| E5 dispatch claims correct | ≈ SC-B4 | graded from the model's own claims; robots do not score SC-B4 (§3.4), so observation only |

| Row | n | F1 / F2 / F3 | F4 (obs.) | SC-B4 (obs.) | Median acts (per run) | Median minutes (per run) | Trial dead ends (median) | Stack reached | Source |
|---|---|---|---|---|---|---|---|---|---|
| **Sonnet 5.5, key, E2** | 5 | **5 / 5 / 1** | 0, 1 partial | correct wherever claims were made (4 of 5 made them) | 29 | 3.0 | 3 | current release in 1 of 5; an outdated builder from Maven Central in 4 of 5 | trial report |
| Haiku 5.5, key, E2 *(obs.)* | 1 | 1 / 1 / 1, on an obsolete stack | partial | correct | 38 | 3.8 | 3 | an obsolete version | trial report |
| Sonnet 5.5, no key, E2 *(obs.)* | 1 | 1 / 1 / 1, on a legacy stack | yes, a real audit record | correct | 48 | 5.9 | 4 | a legacy artifact | trial report |

The acts and minutes are **whole-run** figures, not "to F3": only one primary run reached F3. Four of the five
primary runs **ended at about three minutes by stopping to ask consent** (finding 3), not by finishing or by the
cap. The trial's dead ends are its own count, not §6's types, and exclude 1–3 acts per run lost to the rig's own
sandbox. **SC-B5 (observation):** the keyless run hit *"Fluxtion API key is not configured"* with a link to the
RapidAPI hub at 3.3 minutes. It did not register and fell back to a legacy artifact, so no key-step time to the first
keyed generation exists. Robot runs cannot score SC-B5 in any case (§3.4).

**Evidence.** The raw per-run archives hold machine paths and transcripts captured under the real home. That is
outside rule 1's recorded transcript exception, so they stay out of the repo. What is committed is a sanitised
digest per cycle: [`evidence/m82-baseline/`](evidence/m82-baseline/README.md),
[`evidence/m82-r1/`](evidence/m82-r1/README.md) and [`evidence/m82-r2/`](evidence/m82-r2/README.md), counts and
findings only, no transcripts, paths or key material.

**Baseline findings: where each run stopped, and who owns it.** Observed in the trial. Finding 3's cause, and how
often human strangers would hit findings 1 and 2, are inferred.

| # | Finding | Owner |
|---|---|---|
| 1 | Maven Central serves an outdated `fluxtion-builder` (1.0.9) and no BOM. Against today's hosted generator it fails with `ClassNotFoundException …EventProcessorModelDto`, which reads as a classpath bug. 6 of 7 runs went to Central | **Outside M76/M79**: distribution (the builder's publishing, and a versioned *client too old* reply from the generator). A **prerequisite** for SC-B1 |
| 2 | Conflicting version numbers across the README, the quickstart header and the examples, two of which do not exist. The one primary success found the README's repository line | **Outside M76/M79**: release version stamping across docs and examples. A **prerequisite** |
| 3 | The remote build is undisclosed. 4 of 5 primary runs stopped to ask consent on finding that building posts the graph to a hosted service | the builder and its README (outside this repo); M76 §3.1's key line (A7) and SC-B5 here |
| 4 | The playground offers no route an agent can follow into a project: no coordinates or download link in the served HTML, and `llms.txt` exists but nothing links to it. 0 of 7 runs found it, the starter download, the Spring XML contract, the analyser or the MCP | the project starter journey (M47, M19.23); M79-5's front door; the playground (cross-repo, `upstream-asks.md`) |
| 5 | The audit hint is dismissed. FLX-1008 (*no event audit configured*) does not say how to turn audit on, so only the keyless run produced a real audit record | the compiler's diagnostic (upstream); M76 A6 and SC-B2 depend on it |

**Bootstrapped r1 (2026-10-25, observed).** The baseline task plus a starter already in the directory and an analyser
connected. Sonnet 5.5 ×5 (+ Haiku 5.5 ×1): E1 5/5, E2 5/5, E3 5/5, E4 5/5 but read from the YAML, **analyser pointer
0/5**, **strict F1 0/5**, no consent stops. Confounded: the template already had the task's shape, so the runs only
filled in method bodies. Sonnet made 0 MCP calls; the Haiku run was the only one to author a graph and use the MCP
(2 calls). Digest: `evidence/m82-r1/`.

**Re-run r2 (2026-10-25/26, observed): the fixed task, three arms, a sealed oracle.**

- **Method.** The §5 fixed task and change (task texts under `evidence/m82-r2/`), the oracle and both task digests
  sealed before the first run, the baseline's rig with the analyser added for B and C (sandbox isolation, a cold Maven
  repository per run, project-scope MCP registration, the key used implicitly and never read, a key scan and
  redaction of every archive). 5 Sonnet 5.5 and 5 Haiku 5.5 per arm.
- **Arms.** **A**: unguided, from the playground's public URL, no analyser. **B**: the live starter bundle (template
  digest `6d331b04…`) with analyser 1.38.0 and its MCP. **C**: the fixed starter bundle (digest `d9535a45…`; the
  starter-routing branch `fix/starter-analyser-routing` of the web repo, head `3fb07c7`) with an analyser built from
  the branch of the same name, head `f92ac394`. B and C had a task preamble naming the downloaded starter and the
  connected analyser (`TASK-BC.txt`); A did not (`TASK-A.txt`).
- **Voided and redone (the VOID rule above):** three runs stopped by an account 429 and one (c-s5) by a port collision
  between concurrent runs. The redone runs ran about an hour after the rest.

| Cell (n = 5) | F1 strict | F2 compiles | F3 runs correctly | Pointer (F4) | F5 | Median min / acts / $ | Consent stops |
|---|---|---|---|---|---|---|---|
| A Sonnet | **0** | 5 | 4 | **0** | 4 | 3.2 / 25 / 0.33 | 1 |
| A Haiku | **0** | 4 | 4 | **0** | 4 | 4.5 / 51 / 0.08 | 1 |
| B Sonnet | 5 | 5 | 5 | 5 | 5 | 4.2 / 39 / 0.64 | 0 |
| B Haiku | 5 | 5 | 5 | 5 | 5 | 4.8 / 51 / 0.12 | 0 |
| C Sonnet | 5 | 5 | 5 | 5 | 5 | 3.3 / 33 / 0.47 | 0 |
| C Haiku | 5 | 5 | 5 | 5 | 5 | 5.3 / 45 / 0.09 | 0 |

The columns are counts per stage as the experimenter graded them, not a nested funnel; under the rule that each stage
requires the earlier ones, A's strict funnel stops before F1 for 10 of 10. Summary: **A: strict F1 0/10 and pointer
0/10; B and C: every stage 20/20, MCP reached 20/20.** Scored-run cost $8.94, voided runs $0.55. In A, none of 10 runs
found the real analyser (each took it to be the playground's browser Audit tab), and the blockers were the
baseline's (the outdated release on Maven Central, consent stops on the undisclosed remote build, no discoverable
analyser or starter).

- **C versus B.** C was faster and cheaper for Sonnet (3.3 against 4.2 minutes, $0.47 against $0.64). The stale-jar
  rebuild and per-run export of the fixed starter worked: no extra package step (B needed one in 10 of 10) and no
  accumulated exports. Routing misreads of `eventHandlers`: 0/10 in C, 1/10 in B.
- **Regressions in C.** C saved **0/10 flags and 0/10 gotos** (B: 9/10), so no evidence was left behind (the origin
  of F5a); the Skill tool was used 0/10 (skills were read as files) and `load-audit-log` was read in 7/10.
- **Starter bug in B:** a stop-then-restart port race in the starter's `run-and-export` (one run, a bind exception on
  the analyser's port).
- **Key scan:** 0 secrets in any archive; two runs checked the existence of the key file (existence only).
- **Limits.** n = 5 per cell (5/5 is consistent with up to a 45% failure rate). One grader for F4 and the dispatch
  claims. The operator pre-opened the project in the analyser. **The task text says "in the analyser"**: that cue
  likely explains the jump from r1's 0/5 pointer to 20/20, so B and C measure *following an instruction to use the
  analyser*, not *finding it* (hence the no-cue variant, §5). Redone runs were an hour later than the rest.
- **Observed vs inferred.** Observed: every funnel count, the medians and costs, the stops, the 0 flags in C.
  Inferred: that the cue explains the jump; that A's models took the analyser for the Audit tab (from their final
  answers); that human strangers would behave like these models.

**What this means for the targets** (no target is changed; OD-82.2 stays the owner's): a human F3 of 4 of 5 is
unreachable from E2 until findings 1 and 2 are fixed, because 4 of 5 primary runs never reached the current release.
**Targets** are proposed in OD-82.2, to be set only after the baseline is read.

**Recording.** Each cycle under `docs/specs/evidence/m82-<cycle>/` (the digests so far: `m82-baseline/`, `m82-r1/`, `m82-r2/`):
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
| **OD-82.2** | Targets, after the baseline. **OWNER DECISION: not set by this spec or by any run** | Proposed for discussion only: SC-B1 human E1 F3 in ≥ 4 of 5 runs, median ≤ 120 minutes (the field claim's *couple of hours*); robot F3 3/3; SC-B2 pointer in ≥ 4 of 5; SC-B3 *only intended* in ≥ 4 of 5; SC-B4 zero wrong person claims on rules 1 and 3. SC-B5 measured, not targeted, until the teaching key exists |
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
| ★ S82.11 | strict F1: a project whose generated processor is byte-identical to the template's (only method bodies filled in) does not hold F1, even when every other file differs | a filled-in template counted as authored (r1's 0/5) | compare the tree digest only, or test for existence of the generated file |
| ★ S82.12 | F4a: a run with a successful analyser tool call in its MCP log holds F4a; a run that only mentions the analyser in its answer, or whose only call failed, does not | an answer's wording read as the tool being reached | derive F4a from the final answer text |
| ★ S82.13 | F5a: a run that spotlighted or went to the proving record but left no flag in the analyser's end state does not hold F5a; a flag on the template's sample record does not either | C's 0/10 flags passing as evidence kept | count any pointer verb as a flag, or ignore which record the flag is on |
| ★ S82.14 | F5 before-state: a run whose own before-output alerts `A` twice, and whose after-output equals the reference after, scores `BEFORE_DIVERGED` and not SC-B3 held | an after-state compared with the run's own wrong before-state | diff the run's after-output against the run's own before-output |
| ★ S82.15 | VOID: a run whose stream ends in a rate-limit error with no subject act after it, or whose rig log shows a port-bind collision, is VOID, absent from the denominator and listed with its cost | an account or rig fault scored as a product failure | score it as F0 |
| ★ S82.16 | a run that failed with a product-side exception (a library error, a refusal) is never VOID | failures hidden by voiding | void on any non-zero exit |
| ★ S82.17 | the redo rule: a redone slot replaces the VOID run once; a slot voided twice reports *unfilled*; the report lists redo runs as later | silent retry until green | retry without a limit, or drop the VOID list |
| ★ S82.18 | no-cue variant: a run labelled `N` whose task text digest differs from the sealed `N` digest is refused; one whose task text contains *analyser* or *MCP* is `CUE_LEAK`; N's rates are never pooled with the cued arms' | prompt-following reported as routing | trust the run's own label |

## Revision history

- **r1, 2026-10-25.** First draft, from the owner's focus on authoring from zero.
- **r2, 2026-10-25.** §7's baseline filled in from the 2026-10-25 trial: the endpoint mapping with its mismatches, a primary row and two observation rows, the SC-B5 observation, and five findings with their owners. The evidence stays out of the repo. No target changed.
- **r3, 2026-10-26.** r2's re-run results added to §7 (three arms × two models, sealed oracle) with the r1 summary and sanitised digests under `evidence/`. Rules adopted: strict F1; funnel steps F4a *MCP reached* and F5a *flag persisted*; F5 scored against a scorer-built before-state; the VOID rule with its redo rule; the no-cue variant `N` (task text and sealed-oracle approach, §5); acceptance tests S82.11–.18. No target changed (OD-82.2 remains the owner's). Handover: `evidence/m82-HANDOVER.md`.
