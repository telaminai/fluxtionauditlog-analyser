# PREDICTION — round 48, choosing from a catalogue

**Written and committed BEFORE either arm is launched.** No result seen.

## What changed

Rounds 44–47 gave each library **one** entry point, so the only decision was *declare it*. This round
makes the consumer **choose**, which is what integrating a component market actually involves.

- **A shared `contracts` artifact** holds the events and the published interfaces. No component
  depends on another component's classes. This removes the objection recorded in round 42 — that all
  five suppliers depended on one `Events` class shipping inside marketdata's jar, so whoever owned
  the schema owned the market.
- **Nine entry points across five jars**, declared in each jar's manifest with what they provide,
  require and consume. Four of the five jars offer a *smaller* option that still builds.
- **Choosing wrong is silent.** `MarketDataCore` omits `vol` and `ewma`; `PricingSpot` omits the
  spread; `RiskBasic` omits limit supervision; `CapitalCore` omits alerting and the counts. Each
  compiles, runs, and produces fewer figures.
- One coupling makes a wrong choice **loud**: `CapitalRegulated` requires `LimitApi`, which only
  `RiskSupervised` publishes. Picking `RiskBasic` makes the correct capital component undeclarable.

Both arms get the same catalogue, the same manifests and the same brief. Neither is told which
variant to pick — the requirements are stated as business needs and the mapping is the work.

## Three things I verified before writing this

1. **The trigger edge survives an interface.** A node declaring a constructor parameter of interface
   type still gets the edge — `guardCheck_adjusted() { return isDirty_depth | isDirty_mid; }`.
2. **An entry-point class is not a node.** It carries no annotations, so `isDirty_marketdata` does
   not exist and anything wired through it never fires. Measured: **8 stages instead of 17**, with a
   clean build. Filed as `UP-FLX-29`, which also asks for a diagnostic.
3. **One instance satisfying several contracts is ambiguous.** When a component provides both
   `MidApi` and `DepthApi`, a node taking both gets the same instance twice and needs
   `@AssignToField`. Without it: `FLX-1001 … these fields share a type`.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| W1 | **Both arms select all five correct components.** The manifest states provides/requires plainly and the brief lists the required figures; this is a matching exercise, not an inference. | medium-high |
| W2 | **If either mis-selects, it is `MarketDataPlus` vs `MarketDataCore`** — the only choice driven purely by the figures list, with no structural consequence to catch it. | medium |
| W3 | **The Fluxtion arm wires at least one component through the entry point rather than the node**, producing a quietly smaller graph — the failure `UP-FLX-29` describes. The guide warns about it explicitly, which is exactly the kind of warning round 47 showed an arm can read and still walk into. | medium |
| W4 | **The plain-Java arm writes a dispatch engine again** (round 47: `Node` + `Graph` + `RiskEngine`, 207 lines). The catalogue changes what it assembles, not that it must assemble it. | high |
| W5 | **The Fluxtion arm needs ≤4 `mvn` runs.** Round 47's ten were an FQN guessed from memory (now `FQN.md`), a phantom generator bug, a fat-jar detour and a reinvented `registerService` — all four addressed in the toolkit note and the guide. | medium |
| W6 | **Neither arm's own tests catch its own defect**, if it has one. That has held every round. | medium-high |

## Falsifier

**If the plain-Java arm again scores full marks**, then three rounds running it has matched Fluxtion
on correctness given a capable model, and the honest position is that the framework's case rests on
surface area and on failure modes being loud — not on getting more answers right. I have said this
twice; a third would settle it.

## The measurement I owe

Cost is reported per arm **with the model named** — Fluxtion on Haiku, plain Java on the stronger
model — because the commercial claim is the priced cost of a *correct* result, not tokens. Round 47
showed the Fluxtion arm's token count is dominated by Haiku's step size (76 output tokens per turn
against 672), so raw totals across models measure granularity, not approach. **A third cell —
Fluxtion on the stronger model — is what would make the priced comparison clean, and it is not in
this round.**

---

## AMENDMENT — round 48b, after the Fluxtion arm was invalidated

**The round-48 Fluxtion result is not a measurement and is not reported as one.** It took 16 builds,
got the fee wrong, and violated the Evidence contract — and the causes were mine:

| what it did | why |
|---|---|
| built an 87-line `OutputNode` re-emitting the 14 figures | FX's Evidence never said the components record themselves. **VAN's did.** I gave one arm a better brief than the other. |
| reflected on `Buffer.value` / `Fee.value` | the arm's error — both are `public transient double`, directly readable |
| built a `FeeStrategyNode` holding a string that nothing reads | the arm's error — `feeStrategy(FeeStrategy,String)` and `FeeStrategies.byName` are both in plain `javap`, and `registerService` needs no lookup at all |

I initially proposed adding `Fluxtion-Accepts-Service` to the manifest. **That was wrong and is
dropped.** A node declares `@ServiceRegistered`; the application sends a service; the framework
delivers it to whatever accepts that type, or to nothing. There is no catalogue entry to consult and
declaring one would rot separately from the annotation.

## What changed for 48b

The owner's point, and it reframes the whole sub-series: **this integration is a Spring bean file.**
Writing Spring XML is among the best-represented tasks in any model's training data. When it takes 16
builds, the instructions are the suspect — not the model, and not the framework.

So the task is **closed**:

- **What you write:** the bean file, and a ~40-line `Main` doing five listed things.
- **What you must NOT write:** node classes, event types, any output/report/aggregator class,
  reflection, a fat jar. With the reason attached — *"if you are writing a class with `@OnTrigger`,
  you have misread the task."*
- Evidence now states the components record themselves, matching what VAN was told.

## Prediction for 48b

| # | Prediction | Confidence |
|---|---|---|
| X1 | **≤3 `mvn` runs.** The remaining work is one XML file and boilerplate. | medium-high |
| X2 | **Consumer Java under 60 lines**, against 199 in the invalid run. | medium-high |
| X3 | **The fee is correct**, because `registerService` is now spelled out in the scope note. | high |
| X4 | **Component selection stays correct** — it was already right, and that was never the difficulty. | high |
| X5 | **It writes no node class and uses no reflection.** If it does either after being told not to, that is a finding about instruction-following, not about Spring. | medium-high |

**On fairness, corrected by the owner.** I first recorded 48b's heavier scoping as a cost to the
comparison, on the grounds that the two arms are no longer equally free. **That was the wrong
measure.** The plain-Java arm's manual is in the weights — decades of Java is baked into the model,
and that documentation cost nothing to supply. Fluxtion's is not, so it has to be shipped. What must
be equalised is **access to knowledge of the tool**, not the number of words in the brief.

That gives the rule this series has been missing:

| kind of knowledge | fair to supply |
|---|---|
| **tool knowledge** — the framework's API, its build, its idioms | **yes, to both.** Java's arrives free; Fluxtion's must be written |
| **task knowledge** — which components to select, which arguments are triggers, what the design should be | **no, to either** |

Round 46 broke that rule and is why it was rerun: *"`Streak` hangs off exposure, not the detector"*
is task knowledge, and giving it handed over the answer. `FQN.md`, the `process-classes` note and
`registerService` are tool knowledge — the manual a buyer has on day one — and withholding them was
never a fair test, only a slower one.

**The commercial consequence:** adopting Fluxtion includes writing the manual that Java gets free from
the training corpus. That is a real cost, and a **fixed** one — written once, amortised over every
user — not a per-integration cost. It lands where the supplier-side argument landed: what matters is
the recurring cost, and the framework's is lower provided correctness holds.
