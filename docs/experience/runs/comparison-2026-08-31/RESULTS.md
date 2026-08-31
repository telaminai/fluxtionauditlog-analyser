# Comparison run — the authoring half, with a control arm

**Run 2026-08-31 by the compiler session**, against the pre-registration in
[`baseline-2026-08-31/RESULTS.md`](../baseline-2026-08-31/RESULTS.md) § *PRE-REGISTERED*.

Compiler under test: `fluxtion-compiler` `feature/compiler_diagnostics` @ `dbcbe17`.

## What was run, and what was not

**Candidate A (authoring) — RUN.** These are the two rows the baseline amendment marked *deferred, not
falsified*, because at `7a273a8` no production path emitted the sidecar. That is now fixed, so they are
measurable.

**Candidate B (Q1/Q2/Q3 — reading evidence) — NOT RUN, and not approximated.** The `spreadCalculator`
audit log and GraphML given to the three baseline agents are not in this repository;
`seed-project.sh` seeds docs only. Q2 is the falsification row for the whole capability claim, so
substituting a fixture of my own construction would have contaminated a pre-registered experiment
rather than tested it. **That half still needs the original artefacts, or M45.1.**

## Design — and why a control arm was added

The pre-registration says *"same instrument"*. I could not honestly claim that: the baseline's agents
are a different model and harness from the ones available to me, so comparing my treatment numbers
directly against the baseline would entangle *"the sidecar helped"* with *"these agents are better"*.

So the run is **treatment vs control, within one agent population**:

| arm | artefacts given | n |
|---|---|---|
| control | the failing source + the legacy console message, exactly as an author sees today | 2 |
| treatment | the same, PLUS the `fluxtion-diagnostics.json` FLX-1009 entry | 2 |

Identical prompts otherwise, same model, same sitting, abstention explicitly permitted, ~5 minutes each,
no web access and no access to Fluxtion source. The baseline numbers are retained below as a third
reference point, not as the comparison.

**The artefacts are real, not written for the run.** Both were produced by compiling the
PREDICTIONS.md fixture verbatim — `private final Map statsBySymbol`, `private final RootNode rootNode`,
one-argument constructor — against `dbcbe17`.

## A finding that precedes the agents: the console message did not change

The failing build still prints, byte for byte, the legacy string:

```
cannot find matching constructor for:Field{name=symbolStats, ...} failed to match for these
fields:[rootNode, statsBySymbol]
```

It still names **both** fields, including `rootNode`, which is correctly supplied — the exact text the
baseline recorded as actively misleading one of two agents. Every improvement is in the sidecar file.

**So this is a CEILING, on the same footing the GraphML half was given.** It measures what the artefact
makes possible for an author who has enabled `-Dfluxtion.diagnostics.sidecar` and reads it — not what
an author gets by default, which is unchanged.

## Results

| | baseline (their agents) | **control** (mine, legacy only) | **treatment** (mine, + sidecar) | predicted |
|---|---|---|---|---|
| predicted build attempts | 2–3, both | **2 and 1** | **1 and 1** | 1 |
| names `final` as the mapping trigger | 0 of 2 | **0 of 2** | **2 of 2** | 2 of 2 |
| working fix produced | 2 of 2 | 2 of 2 | 2 of 2 | — |

### Row 2 — CONFIRMED, and attributable

Neither control agent named `final`. Both described constructor-mapping as applying to *every* field:
one said the compiler matches "every field on a node class" against constructor parameters, the other
that "every field on a node class must be reproducible in generated source". Both rules are wrong in
the same direction — non-final fields are setter-wired, not constructor-mapped — and both would
misfire on an unfamiliar node, which is precisely what the baseline observed.

Both treatment agents named it explicitly and unprompted: *"every field that is final, non-transient,
and not `@FluxtionIgnore`"*. Both also gave all three supply routes (constructor / exclude / setter),
which PREDICTIONS.md defines as a **complete** rule.

Control agent 2 is the strongest evidence here. It knew a great deal — it named `@AssignToField` and
the same-type disambiguation rule unprompted — and *still* did not reach `final`. The fact is not
inferable from the message by a capable reader; it has to be carried.

**0 of 2 → 2 of 2, with the agent population held constant. This row is confirmed.**

### Row 1 — directionally consistent, NOT established

Control gave 2 and 1; treatment gave 1 and 1. The direction matches, but **one control agent already
reached 1 without the sidecar**, so at n=2 per arm this row cannot carry a claim.

Both treatment agents cited the same specific sentence for their confidence — that the compiler
*checked the remaining fields against the available constructors, so this exclusion is a complete
repair*. That is the repair-proving added in `9f55281` being used for exactly its purpose, which is
suggestive. It is not a result.

Note also that the baseline's 2–3 is worse than my control's 2-and-1. **That gap is the confound the
control arm exists to expose**: without it I would have reported "2–3 → 1" as a large effect, when part
of it is simply a stronger subject.

## Limits, stated plainly

- **n=2 per arm, one model, one sitting.** No effect size claimed. Row 2 is a clean split at small n,
  not a measured magnitude.
- **Different agent population from the baseline.** The baseline rows are a reference point; the
  comparison that licenses any claim is control-vs-treatment within this run.
- **Ceiling, not floor.** The sidecar is opt-in behind a system property and the console message is
  unchanged, so no author gets this without enabling it and reading the file.
- **I am not a neutral party.** I implemented the diagnostic being measured and wrote the prompt, which
  quoted the sidecar verbatim. A differently-framed prompt could move these numbers. The control arm
  limits this — both arms got the same framing — but it does not remove it.
- **Single fixture.** One node, one failure mode, the one FLX-1009 was designed against.
- **Abstention was permitted**; real authoring gives no such permission.

## SELF-ASSESSMENT — the treatment arm is close to circular

Added after scoring, because the result reads stronger than it is.

**The treatment prompt contained the sentence "constructor-maps every FINAL, non-transient instance
field".** I then scored whether the agent said `final`. That is largely a measurement of whether a
model can read a paragraph, which needed no experiment. Both treatment agents did more than parrot —
they applied the rule and gave all three routes — but the criticism stands, and I should have seen it
before running rather than after.

**The control arm does not have this problem, and it is the half worth keeping.** Two capable agents,
given the message an author sees today, produced wrong general rules — both said constructor-mapping
applies to *every* field. That is a finding about the current state which depends on nothing I built,
and it replicates the baseline's 0 of 2 on a different model family.

**The row that would have been informative is the one that did not establish.** Build attempts is
behavioural — it cannot be satisfied by recall — and at n=2 per arm it carries nothing.

So the honest summary is narrower than the table: **the legacy message reliably produces wrong rules in
capable readers, and this run does NOT show that the sidecar fixes that for the NEXT node rather than
for the one in front of them.** Testing transfer is what would show it — see
[`transfer-2026-08-31`](../transfer-2026-08-31/RESULTS.md).

## What this licenses, and what it does not

**Licensed:** capable readers do not reach the `final` rule from the message an author sees today —
0 of 2 here, 0 of 2 at baseline, on two different model families. That is the gap, measured.

**NOT licensed by this run:** that the sidecar closes it durably. The treatment arm handed the agents
the rule and asked whether they had it (see *Self-assessment*).

**Not licensed:** any statement about build cycles saved. Row 1 needs more agents, or a task-pressure
instrument rather than a question-answering one.

**Untouched:** the whole capability claim — Q1 and Q2, `topologicalRank` and `auditCapable`. Those are
the rows the metadata work rests on, and they remain unmeasured after the merge. Q2 is still the row
that falsifies.
