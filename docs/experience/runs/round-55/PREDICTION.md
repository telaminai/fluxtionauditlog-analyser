# PREDICTION — round 55, finding Haiku's ceiling on SELECTION

**Committed before the fixture is built and before any agent runs.**

## The question

Every round to date measured **wiring**, not **judgement**. `spec-minimal-authoring-instructions.md`
says so in its own limits: *"the catalogue's selection had zero degrees of freedom — the requires
chain forced every choice."*

`RiskBasic` and `RiskSupervised` both publish `notional/exposure/var` and require the same three
interfaces, but Capital requires `LimitApi`, so **only one type-checks**. The model never chooses; the
type system does.

**This round removes that.** How much selection ambiguity can Haiku 4.5 absorb, with the measured-optimal
instruction set held constant, before it picks the wrong component?

## Design — a titration on one axis

Instructions fixed at the round-48 optimum (`MANUAL-compressed.md`, 659 w). Model fixed at Haiku 4.5.
Only the number of **type-indistinguishable** choices varies.

| rung | ambiguity | discriminated by |
|---|---|---|
| **0** control | none — the existing fixture | the requires chain |
| **1** | one pair: two variants with **identical** `Provides`/`Requires`/`Constructor`/`Consumes` | `Fluxtion-Description` alone |
| **2** | three such pairs, at pricing, risk and capital | descriptions, compounding |
| **3** | as rung 2, plus one pair whose descriptions are both plausible and require the **requirement text** to disambiguate | the brief, not the catalogue |

## Metric — mechanical, cheap, and not a scenario run

**Primary: did the emitted bean file name the correct class?** Readable straight out of the XML, no
reference run needed. Secondary: turns, `javap` calls, builds, weighted cost, and
`cache_read_input_tokens` (mandatory per P3a — a cost comparison is meaningless if a prefix was
silently uncached).

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| S1 | **Rung 1 passes.** One description-only choice is within Haiku's reach with the optimal manual. | medium-high |
| S2 | **Rung 1 costs +15 to +40 turns** over rung 0's 51, and **breaks the zero-`javap` result** — the model will inspect at least one ambiguous class. | medium |
| S3 | **Rung 2 passes but with ≥3 `javap` calls**, because three compounding ambiguities exceed what a one-line description resolves. | low-medium |
| S4 | **Rung 3 fails** — at least one wrong component selected. | medium |
| S5 | **The failure, when it comes, will be traceable to a sentence in the manual rather than to the model.** | medium |

## S5 is the real hypothesis, and it is stated sharply because it is falsifiable

The measured-optimal manual contains this line:

> *"A component's `Fluxtion-Requires` chain usually forces the choice: if only one variant publishes an
> interface a downstream component requires, that variant is the only one that works."*

**That sentence is TRUE of the round-48 fixture and FALSE from rung 1 onward.** It was earned by
measurement on a fixture with no degrees of freedom, and it instructs the reader to resolve selection
by type — exactly the strategy that cannot work here.

> **If rung 1 or 2 fails, the ceiling being measured is the manual's, not the model's.**

That would be the most useful outcome available, and it generalises: **an instruction validated by
ablation on one task can be actively wrong on a harder one.** No principle currently in the spec
catches this — P1 retires discovery aids when the answer is precomputed, but says nothing about an
instruction that silently stops being true when the *task* changes. If S5 holds, the spec needs a new
principle, and a rule that any instruction containing "usually" or "always" is a candidate for
re-ablation whenever task complexity moves.

## Falsifiers, stated up front

- **If rung 3 passes**, the ceiling is above this titration and the whole design is too easy — report
  that and escalate, rather than presenting rung 3 as a limit.
- **If rung 1 fails**, either the manual line is the cause (S5, checkable in the transcript) or one
  description-only choice is genuinely beyond Haiku with 659 words — and those are very different
  findings. **The transcript must be read before attributing it**, because this project has attributed
  a failure to the wrong cause once already this cycle.
- **n is 1 per rung initially.** Any rung that fails MUST be repeated before the failure is reported as
  a result. A single failing run is a candidate, not a ceiling.

---

## AMENDMENT — design changed on owner instruction, BEFORE any run

**No agent has been launched and no result exists at the time of writing.** Recorded rather than
silently edited, because this project's protocol treats an uncommitted prediction as a contaminated
one.

The owner's steer — *"add more entry point jars in the jar, probably the easiest way to test"* — is
both cheaper and harsher than the pair-based rungs above, and it titrates a single clean axis. The
design changes to:

**Rung = the number of entry points sharing ONE type surface, exactly one of which is correct.**

Rung 1 as built: the pricing jar now carries **seven** entry points. `PricingSpot` is type-eliminable
(it publishes no `SpreadApi`). The remaining **six — `PricingFull`, `PricingHedged`, `PricingNetted`,
`PricingGross`, `PricingCapped`, `PricingSmoothed` — have byte-identical `Fluxtion-Provides`,
`Fluxtion-Requires`, `Fluxtion-Constructor` and `Fluxtion-Consumes`.** Only `Fluxtion-Description`
differs. Verified mechanically, not by eye.

**The correct choice is `PricingHedged`, and it is deliberately not the obvious one.** `PricingFull`
was correct in every previous round and is the plainest name in the family, so a "pick the default"
prior selects wrongly. The brief specifies the desk's hedging overlay in business terms and never
names a class.

**Predictions S1–S5 stand unchanged in substance**, with S2's numbers now applying to a 1-in-6 choice
rather than 1-in-2, which should make S1 harder rather than easier. **S5 is unchanged and is still the
hypothesis that matters**: the manual's line *"the `Fluxtion-Requires` chain usually forces the
choice"* is now false for six of the seven entry points in this jar, and it is the first thing the
manual tells the reader to rely on.

One prediction is **added**, because the new design admits a failure mode the old one did not:

| # | Prediction | Confidence |
|---|---|---|
| S6 | **If it fails, it selects `PricingFull`** — the plainest name, correct in every prior round — rather than a randomly wrong sibling. A wrong-but-non-`PricingFull` answer would indicate it engaged with the descriptions and misread them, which is a different and less worrying failure. | medium |
